package lunatrius.schematica.debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import lunatrius.schematica.EasyPlace;
import lunatrius.schematica.HotbarRestock;
import lunatrius.schematica.MaterialList;
import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.SchematicMemory;
import lunatrius.schematica.SchematicPaste;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaConfig;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.compat.CreativeMode;
import lunatrius.schematica.gui.InfoHud;
import lunatrius.schematica.gui.MaterialListScreen;
import lunatrius.schematica.gui.SchematicControlScreen;
import lunatrius.schematica.gui.SchematicLoadScreen;
import lunatrius.schematica.gui.SchematicSaveScreen;
import lunatrius.schematica.gui.SchematicaKeysScreen;
import lunatrius.schematica.gui.SchematicaSettingsScreen;
import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicFormat;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3i;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.Item;
import net.minecraft.MultiplayerInteractionManager;
import net.minecraft.SingleplayerInteractionManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.class_260;
import net.minecraft.class_27;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Temporary end-to-end harness. Enabled with {@code -Dschematica.smoketest=true}.
 *
 * <p>Runs in two parts: the data layer (NBT round trip, rotate, mirror) at the title screen, then
 * the in-world part - build a structure, save it, load it back, aim the camera at the ghost and
 * screenshot. Not part of the shipped mod.
 */
public final class SmokeTest {
	private static final boolean ENABLED = Boolean.getBoolean("schematica.smoketest");
	private static final boolean WORLD_TEST = !Boolean.getBoolean("schematica.smoketest.dataonly");
	/**
	 * Joins a server on localhost instead of making a world, and checks easy place against it. The
	 * inventory swap is the half of easy place that only exists on a server: in single player the
	 * clicks are applied on the spot, and none of the asking and answering happens at all.
	 */
	private static final boolean MULTIPLAYER = Boolean.getBoolean("schematica.smoketest.multiplayer");
	/**
	 * The second half of a two-run check. A game can leave a note about a world and come back to it
	 * inside one run, but it cannot start cold - and starting cold is when the world is handed over
	 * in a different order, so it is the only way to see what a player really gets.
	 */
	private static final boolean RESTORE_TEST = Boolean.getBoolean("schematica.smoketest.restore");
	/** The save folder the test world lives in, which is also how remembering tells it apart. */
	private static final String WORLD_NAME = "schematica-smoketest";
	/** Where the schematic is parked before logging off, so the place it comes back to is its own. */
	private static final int REJOIN_X = -321;
	private static final int REJOIN_Y = 72;
	private static final int REJOIN_Z = 654;
	/** The same again on a server, where the world is only ever a stub of the one being played. */
	private static final int SERVER_REJOIN_X = -111;
	private static final int SERVER_REJOIN_Y = 66;
	private static final int SERVER_REJOIN_Z = 222;
	private static final String SERVER_HOST = "localhost";
	private static final int SERVER_PORT = 25565;

	private static final int STRUCTURE_SIZE = 5;
	/** The highest the test structure is built, leaving room for the ghost and the camera above it. */
	private static final int MAX_BUILD_Y = 100;

	/** Big enough to span several render regions in every axis, with the camera inside it. */
	private static final int LARGE_WIDTH = 48;
	private static final int LARGE_HEIGHT = 32;
	private static final int LARGE_LENGTH = 48;

	private static final int GOLD = 41;
	private static final int TORCH = 50;
	private static final int WOOD_STAIRS = 53;
	private static final int STANDING_SIGN = 63;
	private static final int WORKBENCH = 58;
	private static final int WOOL = 35;
	private static final int RED_WOOL = 14;
	private static final int PISTON = 33;
	private static final int REDSTONE_TORCH = 75;
	/** The lit one, which is a block of its own rather than a state of the one above. */
	private static final int LIT_REDSTONE_TORCH = 76;
	/** The metadata a torch carries when it stands on the floor rather than hanging on a wall. */
	private static final int TORCH_ON_FLOOR = 5;
	/** The metadata a torch carries when it hangs on the block to its east - the side of it that
	 * lands last in a sweep counting x upwards, and so the one that used to arrive too late. */
	private static final int TORCH_AGAINST_EAST = 2;
	/** The wall torch schematic the paste test builds for itself. */
	private static final int TORCH_TEST_WIDTH = 6;
	private static final int TORCH_TEST_LENGTH = 5;
	/** The piston schematic the paste test builds for itself. */
	private static final int PISTON_TEST_WIDTH = 5;
	private static final int PISTON_TEST_HEIGHT = 3;
	private static final int PISTON_TEST_LENGTH = 4;
	private static final int COBBLESTONE = 4;
	private static final int SNOW_LAYER = 78;
	private static final int SAND = 12;
	private static final int REDSTONE_WIRE = 55;
	private static final int REDSTONE_DUST = 331;
	private static final int WOODEN_DOOR = 64;
	private static final int WOODEN_DOOR_ITEM = 324;
	/** The metadata bit the top half of a door carries, which no item places. */
	private static final int DOOR_TOP = 8;
	private static final int PISTON_HEAD = 34;
	/** The metadata bit a piston carries while it is extended, and its head is out. */
	private static final int EXTENDED = 8;
	private static final int FLOWING_WATER = 8;
	private static final int STILL_WATER = 9;
	private static final int STILL_LAVA = 11;
	private static final int WATER_BUCKET = 326;
	private static final int LAVA_BUCKET = 327;
	private static final int DOUBLE_SLAB = 43;
	private static final int SLAB = 44;
	/** A slab laid upside down, which is metadata b1.7.3 never made and a later version did. */
	private static final int UPSIDE_DOWN = 8;

	/** Nine blocks to fill the bar plus the gold, which is what the server is asked to hand out. */
	private static final int TEST_STACKS = 10;
	/** Where the hotbar starts in the player container. */
	private static final int HOTBAR_FIRST_SLOT = 36;

	/** A slot in the inventory proper, out of the hotbar and so out of reach without a swap. */
	private static final int MAIN_SLOT = 20;

	private static final int HOTBAR_SIZE = 9;
	private static final int GOLD_SLOT = 3;
	/** Hotbar slots the mid-air checks lay out for themselves. */
	private static final int MIDAIR_GOLD_SLOT = 0;
	private static final int MIDAIR_REDSTONE_SLOT = 1;
	private static final int MIDAIR_SAND_SLOT = 2;
	private static final int PICKAXE_SLOT = 5;
	/** Faces of the block a click lands on, in vanilla's order. */
	private static final int SIDE_UP = 1;
	private static final int SIDE_EAST = 5;

	/** Give up rather than hang forever if world generation never finishes. */
	private static final int WORLD_TIMEOUT_TICKS = 20 * 60 * 8;

	/** Frames to let the game settle before each screenshot. */
	private static final int SETTLE_TICKS = 25;

	/** Frames the overlay may spend catching up after that before something is declared wrong. */
	private static final int REBUILD_TIMEOUT_TICKS = 20 * 30;

	/** How far above the ghost the paste test parks the schematic, clear of everything else. */
	private static final int PASTE_TEST_HEIGHT = 6;

	/** Long enough to outlast the wait a schematic gets for a world that never says it is ready. */
	private static final int RESTORE_WAIT_TICKS = 70;

	private enum Phase { DATA, START_WORLD, WAIT_WORLD, BUILD, SAVE, LOAD, SHOOT, MULTIPLAYER, LEAVE, REJOIN, RESTORED, DONE }

	private static Phase phase = Phase.DATA;
	private static int ticks = 0;
	private static int failures = 0;
	private static int scene = 0;
	private static int restoreStep = 0;
	private static int restoredGhosts = -1;
	private static int silentlyCleared = 0;

	private static int baseX;
	private static int baseY;
	private static int baseZ;
	private static int worldStairsMetadata;
	private static int worldTorchMetadata;

	/** Turned between the last few scenes so the frustum culling is exercised from several angles. */
	private static float cameraYaw = 0.0F;
	private static boolean largeSchematicLoaded = false;
	private static int rebuildTicks = 0;

	private SmokeTest() {
	}

	public static boolean isEnabled() {
		return ENABLED;
	}

	public static void tick(Minecraft mc) {
		if (!ENABLED || phase == Phase.DONE) {
			return;
		}

		ticks++;
		try {
			step(mc);
		} catch (Throwable throwable) {
			fail("Unhandled exception in phase " + phase, throwable);
			finish(mc);
		}
	}

	private static void step(Minecraft mc) {
		switch (phase) {
			case DATA:
				if (mc.currentScreen instanceof TitleScreen && ticks > 20) {
					useScratchMemory();
					runDataTests();
					runKeybindTests(mc);
					runModMenuTests();
					runPasteGateTests(mc);
					phase = WORLD_TEST ? Phase.START_WORLD : Phase.SHOOT;
					ticks = 0;
				}
				return;

			case START_WORLD:
				if (MULTIPLAYER) {
					Log.info("SMOKETEST: connecting to " + SERVER_HOST + ":" + SERVER_PORT);
					phase = Phase.WAIT_WORLD;
					ticks = 0;
					mc.setScreen(new ConnectScreen(mc, SERVER_HOST, SERVER_PORT));
					return;
				}

				Log.info("SMOKETEST: creating world (this takes a while on first run)");
				// Set the phase first: method_2120 blocks for the whole of world generation, and the
				// game keeps ticking underneath it.
				phase = Phase.WAIT_WORLD;
				ticks = 0;
				// SelectWorldScreen installs this before starting a world; method_2120 needs it.
				mc.interactionManager = new SingleplayerInteractionManager(mc);
				mc.method_2120(WORLD_NAME, "SmokeTest", 1L);
				Log.info("SMOKETEST: world creation returned");
				return;

			case WAIT_WORLD:
				if (ticks % 100 == 0) {
					Log.info("SMOKETEST: waiting for world - tick " + ticks
							+ " world=" + (mc.world != null) + " player=" + (mc.player != null)
							+ " screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName()));
				}
				if (ticks > WORLD_TIMEOUT_TICKS) {
					fail("timed out waiting for the world", null);
					finish(mc);
					return;
				}
				if (mc.world != null && mc.player != null && ticks > 60) {
					// method_2120 leaves whatever screen was open in place; close it so the world renders.
					mc.setScreen(null);
					phase = RESTORE_TEST ? Phase.RESTORED : (MULTIPLAYER ? Phase.MULTIPLAYER : Phase.BUILD);
					ticks = 0;
				}
				return;

			case LEAVE:
				leaveWorld(mc);
				return;

			case REJOIN:
				if (ticks > WORLD_TIMEOUT_TICKS) {
					fail("timed out waiting for the world to come back", null);
					finish(mc);
					return;
				}
				if (mc.world != null && mc.player != null && ticks > 60) {
					checkRejoin(mc);
					finish(mc);
				}
				return;

			case RESTORED:
				runRestoredOverlayTests(mc);
				return;

			case MULTIPLAYER:
				runMultiplayerTests(mc);
				return;

			case BUILD:
				buildStructure(mc);
				phase = Phase.SAVE;
				ticks = 0;
				return;

			case SAVE:
				saveStructure();
				phase = Phase.LOAD;
				ticks = 0;
				return;

			case LOAD:
				runOrientationTests();
				runMemoryTests(mc);
				// Before the run's own schematic is loaded, since this one loads its own and
				// loadAndTransform puts the right one back straight afterwards.
				runWallTorchTests(mc);
				runPistonTests(mc);
				loadAndTransform(mc);
				phase = Phase.SHOOT;
				ticks = 0;
				return;

			case SHOOT:
				// One settle-and-shoot per scene, so every render path gets its own screenshot.
				if (mc.currentScreen == null) {
					parkCamera(mc);
				}
				boolean rebuilding = isRebuilding();
				if (rebuilding) {
					rebuildTicks++;
				}
				if (ticks < SETTLE_TICKS || (rebuilding && rebuildTicks < REBUILD_TIMEOUT_TICKS)) {
					return;
				}
				if (rebuilding) {
					fail("the overlay never finished rebuilding", null);
				} else if (rebuildTicks > 0) {
					// Counted in client ticks, not frames - this runs from the tick handler. The old
					// renderer did all of this work inside a single frame instead.
					Log.info("SMOKETEST: the overlay was still rebuilding " + rebuildTicks
							+ " tick(s) - about " + (rebuildTicks * 50) + " ms - into this scene");
				}
				rebuildTicks = 0;
				ticks = 0;
				shootScene(mc);
				return;

			default:
		}
	}

	// --- data layer, no world required -------------------------------------------------------

	private static void runDataTests() {
		Log.info("SMOKETEST: --- data layer ---");

		// 3 wide, 2 high, 4 long, so a rotation swapping the axes is visible in the assertions.
		int[][][] blocks = new int[3][2][4];
		int[][][] metadata = new int[3][2][4];
		blocks[0][0][0] = GOLD;
		blocks[2][1][3] = TORCH;
		metadata[2][1][3] = 1; // torch against the west face
		blocks[1][0][2] = WOOD_STAIRS;
		metadata[1][0][2] = 2; // stairs facing north

		List<BlockEntity> blockEntities = new ArrayList<>();
		SignBlockEntity sign = new SignBlockEntity();
		sign.x = 0;
		sign.y = 1;
		sign.z = 3;
		sign.texts[0] = "SMOKE";
		blockEntities.add(sign);
		blocks[0][1][3] = STANDING_SIGN;

		Schematic original = new Schematic(blocks, metadata, blockEntities, 3, 2, 4);

		// NBT round trip.
		NbtCompound nbt = SchematicFormat.toNbt(original);
		Schematic restored = SchematicFormat.fromNbt(nbt).schematic;
		check("nbt width", restored.getWidth(), 3);
		check("nbt height", restored.getHeight(), 2);
		check("nbt length", restored.getLength(), 4);
		check("nbt gold", restored.getBlockId(0, 0, 0), GOLD);
		check("nbt torch", restored.getBlockId(2, 1, 3), TORCH);
		check("nbt torch metadata", restored.getMetadata(2, 1, 3), 1);
		check("nbt stairs metadata", restored.getMetadata(1, 0, 2), 2);
		check("nbt block entities", restored.getBlockEntities().size(), 1);

		BlockEntity roundTripped = restored.getBlockEntity(0, 1, 3);
		if (roundTripped instanceof SignBlockEntity) {
			checkText("nbt sign text", ((SignBlockEntity) roundTripped).texts[0], "SMOKE");
		} else {
			fail("sign did not survive the NBT round trip, got " + roundTripped, null);
		}

		// Rotate: (x, z) -> (z, width - 1 - x); width and length swap.
		restored.rotate();
		check("rotated width", restored.getWidth(), 4);
		check("rotated length", restored.getLength(), 3);
		check("rotated gold", restored.getBlockId(0, 0, 2), GOLD);
		check("rotated torch", restored.getBlockId(3, 1, 0), TORCH);
		check("rotated torch metadata", restored.getMetadata(3, 1, 0), 4);
		check("rotated stairs metadata", restored.getMetadata(2, 0, 1), 0);
		BlockEntity rotatedSign = restored.getBlockEntity(3, 1, 2);
		if (!(rotatedSign instanceof SignBlockEntity)) {
			fail("sign did not follow the rotation", null);
		}

		// Four rotations must be the identity.
		restored.rotate();
		restored.rotate();
		restored.rotate();
		check("four rotations width", restored.getWidth(), 3);
		check("four rotations length", restored.getLength(), 4);
		check("four rotations gold", restored.getBlockId(0, 0, 0), GOLD);
		check("four rotations torch metadata", restored.getMetadata(2, 1, 3), 1);
		check("four rotations stairs metadata", restored.getMetadata(1, 0, 2), 2);
		BlockEntity fullCircleSign = restored.getBlockEntity(0, 1, 3);
		if (!(fullCircleSign instanceof SignBlockEntity)) {
			fail("sign did not come back after four rotations", null);
		}

		// Two mirrors must also be the identity.
		restored.mirrorZ();
		check("mirrored torch z", restored.getBlockId(2, 1, 0), TORCH);
		check("mirrored stairs metadata", restored.getMetadata(1, 0, 1), 3);
		restored.mirrorZ();
		check("double mirror torch z", restored.getBlockId(2, 1, 3), TORCH);
		check("double mirror stairs metadata", restored.getMetadata(1, 0, 2), 2);

		// Block ids past 255 cannot exist in b1.7.3 and must be dropped, not crash.
		NbtCompound modern = SchematicFormat.toNbt(original);
		byte[] add = new byte[3 * 2 * 4];
		add[0] = 1; // pushes the block at (0,0,0) to id 256
		modern.putByteArray("Add", add);
		SchematicFormat.LoadResult result = SchematicFormat.fromNbt(modern);
		check("dropped block count", result.droppedBlocks, 1);
		check("dropped block became air", result.schematic.getBlockId(0, 0, 0), 0);

		runMaterialListTests();
		runInfoHudTests();

		Log.info("SMOKETEST: --- data layer done ---");
	}

	/**
	 * Points remembering at a file of its own before any world is entered, so a run never reads or
	 * writes what the player is really playing. The mod itself is left switched on and running from
	 * the tick: the hooks that name a world and the poll that keeps the note up to date are then
	 * the real ones, and the checks below are looking at what a player would get.
	 */
	private static void useScratchMemory() {
		File file = memoryFile();
		// A restore run is the second half of a pair and lives off what the first half left behind,
		// so it is the one run that reads the note rather than starting without one.
		if (!RESTORE_TEST && file.isFile() && !file.delete()) {
			fail("could not clear " + file.getName(), null);
			return;
		}
		SchematicMemory.load(file);
	}

	private static File memoryFile() {
		return new File(Minecraft.getRunDirectory(), "config/schematica-worlds-smoketest.properties");
	}

	/**
	 * Coming back to a world, as far as remembering is concerned: which world it is, and that it has
	 * finished being handed over. A real return is told both by the mixins, one at each end of the
	 * swap; a test driving it by hand has to say both as well.
	 */
	private static void comeBackTo(String world) {
		SchematicMemory.onSingleplayerWorld(world);
		SchematicMemory.onWorldReady();
	}

	/**
	 * The material list, which is arithmetic over a schematic and an inventory and so needs no
	 * world. Three things make it more than a count of blocks, and each is checked here: an item
	 * that is not the block it places (redstone), a block put down by one item and standing as two
	 * (a door), and one item whose stacks are different materials (wool colours).
	 */
	private static void runMaterialListTests() {
		Log.info("SMOKETEST: --- material list ---");

		int[][][] blocks = new int[2][3][2];
		int[][][] metadata = new int[2][3][2];
		blocks[0][0][0] = GOLD;
		blocks[1][0][0] = GOLD;
		blocks[0][0][1] = GOLD;
		blocks[1][0][1] = REDSTONE_WIRE;
		blocks[0][1][0] = WOOL;
		blocks[1][1][0] = WOOL;
		metadata[1][1][0] = RED_WOOL;
		blocks[0][1][1] = WOODEN_DOOR;
		blocks[0][2][1] = WOODEN_DOOR;
		metadata[0][2][1] = DOOR_TOP;
		// Two pistons facing different ways: the same stack puts down either, so they are one row.
		blocks[1][2][0] = PISTON;
		metadata[1][2][0] = 2;
		blocks[1][2][1] = PISTON;
		metadata[1][2][1] = 4;

		MaterialList list = MaterialList.of(new Schematic(blocks, metadata, new ArrayList<>(), 2, 3, 2));

		check("material rows", list.getEntries().size(), 6);
		check("material total", list.getTotalNeeded(), 9);
		check("gold needed", materialNeeded(list, GOLD, 0), 3);
		check("wire is asked for as dust", materialNeeded(list, REDSTONE_DUST, 0), 1);
		check("a door is one item, not two blocks", materialNeeded(list, WOODEN_DOOR_ITEM, 0), 1);
		check("white wool needed", materialNeeded(list, WOOL, 0), 1);
		check("red wool is a row of its own", materialNeeded(list, WOOL, RED_WOOL), 1);
		check("both pistons are the one row", materialNeeded(list, PISTON, 0), 2);
		checkTrue("the biggest pile is at the top", list.getEntries().get(0).getStack().itemId == GOLD);
		checkText("rows are named the way the game names them",
				materialRow(list, WOOL, RED_WOOL).getName(), "Red Wool");

		// Nothing carried: everything is still to be gathered.
		list.countInventory(new ItemStack[0]);
		check("with an empty pack everything is missing", list.getTotalMissing(), 9);

		ItemStack[] carried = new ItemStack[4];
		carried[0] = new ItemStack(GOLD, 2, 0);
		carried[1] = new ItemStack(WOOL, 5, RED_WOOL);
		carried[2] = new ItemStack(Item.WOODEN_PICKAXE);
		list.countInventory(carried);

		check("gold in the pack", materialHave(list, GOLD, 0), 2);
		check("so one gold block is still to be found", materialMissing(list, GOLD, 0), 1);
		check("red wool went to its own row", materialHave(list, WOOL, RED_WOOL), 5);
		check("and a spare stack is not a debt", materialMissing(list, WOOL, RED_WOOL), 0);
		check("the white wool row did not take it", materialHave(list, WOOL, 0), 0);
		check("what is left to gather", list.getTotalMissing(), 6);

		// The whole point of the screen: the numbers fall as the blocks are picked up.
		carried[0] = new ItemStack(GOLD, 3, 0);
		list.countInventory(carried);
		check("a row counts down as blocks are picked up", materialMissing(list, GOLD, 0), 0);
		check("and so does the total", list.getTotalMissing(), 5);

		check("an empty schematic has nothing in it", MaterialList.of(null).getTotalNeeded(), 0);

		// Blocks that are not their own material: what you fetch for them is something else, or
		// there is nothing to fetch at all because they come with another block.
		int[][][] parts = new int[2][2][2];
		int[][][] partsMetadata = new int[2][2][2];
		parts[0][0][0] = PISTON;
		partsMetadata[0][0][0] = EXTENDED + 1;
		parts[0][1][0] = PISTON_HEAD;
		parts[1][0][0] = STILL_WATER;
		parts[1][1][0] = FLOWING_WATER;
		partsMetadata[1][1][0] = 3;
		parts[0][0][1] = STILL_LAVA;
		parts[0][1][1] = DOUBLE_SLAB;
		parts[1][0][1] = WOOD_STAIRS;
		parts[1][1][1] = WOOD_STAIRS;
		partsMetadata[1][1][1] = 2;

		MaterialList built = MaterialList.of(new Schematic(parts, partsMetadata, new ArrayList<>(), 2, 2, 2));

		check("a piston head is no material of its own", materialNeeded(built, PISTON_HEAD, 0), -1);
		check("the piston it came out of is", materialNeeded(built, PISTON, 0), 1);
		check("water is asked for by the bucket, and only for the source",
				materialNeeded(built, WATER_BUCKET, 0), 1);
		check("and there is no row for the block itself", materialNeeded(built, STILL_WATER, 0), -1);
		check("lava the same", materialNeeded(built, LAVA_BUCKET, 0), 1);
		check("a double slab is two slabs", materialNeeded(built, SLAB, 0), 2);
		check("stairs facing different ways are one material", materialNeeded(built, WOOD_STAIRS, 0), 2);
		check("and nothing else crept in", built.getEntries().size(), 5);

		// Metadata a later version wrote that this one never made. The row has to survive being
		// named and drawn, and the sort names every row it compares.
		int[][][] modern = new int[2][1][1];
		int[][][] modernMetadata = new int[2][1][1];
		modern[0][0][0] = SLAB;
		modernMetadata[0][0][0] = UPSIDE_DOWN;
		modern[1][0][0] = GOLD;

		MaterialList later = MaterialList.of(new Schematic(modern, modernMetadata, new ArrayList<>(), 2, 1, 1));
		check("a slab from a later version is still a row", later.getEntries().size(), 2);
		checkText("named out of what this version does have",
				materialRow(later, SLAB, 0).getName(), "Stone Slab");

		Log.info("SMOKETEST: --- material list done ---");
	}

	/**
	 * The info HUD's half of the list: which rows it shows, and in what order. Still only
	 * arithmetic over a material list, so it needs no world and no screen either.
	 */
	private static void runInfoHudTests() {
		Log.info("SMOKETEST: --- info hud ---");

		// Three materials in plainly different amounts, so an order is unambiguous: four gold, two
		// wool, one redstone.
		int[][][] blocks = new int[4][2][1];
		int[][][] metadata = new int[4][2][1];
		blocks[0][0][0] = GOLD;
		blocks[1][0][0] = GOLD;
		blocks[2][0][0] = GOLD;
		blocks[3][0][0] = GOLD;
		blocks[0][1][0] = WOOL;
		blocks[1][1][0] = WOOL;
		blocks[2][1][0] = REDSTONE_WIRE;

		MaterialList list = MaterialList.of(new Schematic(blocks, metadata, new ArrayList<>(), 4, 2, 1));
		list.countInventory(new ItemStack[0]);

		List<MaterialList.Entry> most = list.getOutstanding(MaterialList.Sort.DESCENDING);
		check("the hud lists every row that is short", most.size(), 3);
		check("the most to gather leads", most.get(0).getStack().itemId, GOLD);
		check("then the next", most.get(1).getStack().itemId, WOOL);
		check("then the least", most.get(2).getStack().itemId, REDSTONE_DUST);

		List<MaterialList.Entry> fewest = list.getOutstanding(MaterialList.Sort.ASCENDING);
		check("the other way up starts at the other end", fewest.get(0).getStack().itemId, REDSTONE_DUST);
		check("and ends at this one", fewest.get(2).getStack().itemId, GOLD);

		// A row leaves the hud once the player has enough of it, and is back the moment they do not.
		ItemStack[] carried = new ItemStack[1];
		carried[0] = new ItemStack(WOOL, 2, 0);
		list.countInventory(carried);
		check("a gathered row drops out", list.getOutstanding(MaterialList.Sort.DESCENDING).size(), 2);
		checkTrue("and it is the gathered one that went",
				hudRow(list.getOutstanding(MaterialList.Sort.DESCENDING), WOOL) == null);

		carried[0] = new ItemStack(WOOL, 1, 0);
		list.countInventory(carried);
		List<MaterialList.Entry> back = list.getOutstanding(MaterialList.Sort.DESCENDING);
		check("and comes back when the stack leaves the pack", back.size(), 3);
		check("asking for what is still short, not the whole row", hudRow(back, WOOL).getMissing(), 1);

		// The order is the schematic's, not the pack's: a row being worked on holds its place, and
		// only leaves by being finished. Carrying eleven of the thirty redstone a build wants must
		// not drop it under twenty cobblestone nobody has touched.
		List<MaterialList.Entry> settled = list.getOutstanding(MaterialList.Sort.DESCENDING);
		carried[0] = new ItemStack(GOLD, 3, 0);
		list.countInventory(carried);
		List<MaterialList.Entry> started = list.getOutstanding(MaterialList.Sort.DESCENDING);
		check("gathering some of a row leaves every row where it was", started.size(), settled.size());
		checkTrue("in the same order it was in before",
				started.get(0) == settled.get(0) && started.get(1) == settled.get(1)
						&& started.get(2) == settled.get(2));
		check("even though the row at the top now has the least left to gather",
				started.get(0).getMissing(), 1);
		check("and the row under it has more", started.get(1).getMissing(), 2);
		checkTrue("because the order is what the schematic takes",
				started.get(0).getNeeded() > started.get(1).getNeeded());
		checkTrue("and the other way up is still the reverse of it",
				list.getOutstanding(MaterialList.Sort.ASCENDING).get(0) == started.get(2));

		ItemStack[] everything = {
				new ItemStack(GOLD, 4, 0), new ItemStack(WOOL, 2, 0), new ItemStack(REDSTONE_DUST, 1, 0) };
		list.countInventory(everything);
		check("nothing left to gather leaves the hud with no rows",
				list.getOutstanding(MaterialList.Sort.DESCENDING).size(), 0);
		check("though the list behind it still has every one", list.getEntries().size(), 3);

		Log.info("SMOKETEST: --- info hud done ---");
	}

	/** The row for an item among the ones the hud is showing, or null if it is not showing it. */
	private static MaterialList.Entry hudRow(List<MaterialList.Entry> rows, int itemId) {
		for (MaterialList.Entry entry : rows) {
			if (entry.getStack().itemId == itemId) {
				return entry;
			}
		}
		return null;
	}

	/** The row asking for a particular stack, by the item and damage it wants. */
	private static MaterialList.Entry materialRow(MaterialList list, int itemId, int damage) {
		for (MaterialList.Entry entry : list.getEntries()) {
			if (entry.getStack().itemId == itemId && entry.getStack().getDamage() == damage) {
				return entry;
			}
		}
		return null;
	}

	private static int materialNeeded(MaterialList list, int itemId, int damage) {
		MaterialList.Entry entry = materialRow(list, itemId, damage);
		return entry == null ? -1 : entry.getNeeded();
	}

	private static int materialHave(MaterialList list, int itemId, int damage) {
		MaterialList.Entry entry = materialRow(list, itemId, damage);
		return entry == null ? -1 : entry.getHave();
	}

	private static int materialMissing(MaterialList list, int itemId, int damage) {
		MaterialList.Entry entry = materialRow(list, itemId, damage);
		return entry == null ? -1 : entry.getMissing();
	}

	/**
	 * The screen over the top of the list: that it counts the schematic that is actually loaded,
	 * and that its numbers fall as blocks are picked up, which is the whole point of it. Puts the
	 * inventory and whatever screen was open back the way it found them.
	 */
	private static void runMaterialScreenTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null || mc.player == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the material list screen checks");
			return;
		}

		Log.info("SMOKETEST: --- material list screen ---");
		PlayerInventory inventory = mc.player.inventory;
		ItemStack[] saved = inventory.main.clone();
		Screen before = mc.currentScreen;
		for (int slot = 0; slot < inventory.main.length; slot++) {
			inventory.main[slot] = null;
		}

		// Opening the screen is what counts the schematic, exactly as the key and the button do.
		MaterialListScreen screen = new MaterialListScreen(null);
		mc.setScreen(screen);
		MaterialList list = screen.getMaterials();

		int gold = materialNeeded(list, GOLD, 0);
		checkTrue("the screen counted the schematic that is loaded", gold > 0);
		check("with an empty pack the whole schematic is still to be gathered",
				list.getTotalMissing(), list.getTotalNeeded());

		inventory.main[0] = new ItemStack(GOLD, 5, 0);
		screen.tick();
		check("the screen counts what the player picked up", materialHave(list, GOLD, 0), 5);
		check("and takes it off what is missing", materialMissing(list, GOLD, 0), Math.max(0, gold - 5));

		System.arraycopy(saved, 0, inventory.main, 0, saved.length);
		mc.setScreen(before);
		Log.info("SMOKETEST: --- material list screen done ---");
	}

	/**
	 * The two buttons under the material list, and that the HUD they drive picks up what they say.
	 * The drawing itself is left to the screenshot of scene 8; what is checked here is that the
	 * buttons reach the HUD at all. Puts the settings back the way it found them.
	 */
	private static void runInfoHudScreenTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null || mc.player == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the info hud checks");
			return;
		}

		Log.info("SMOKETEST: --- info hud screen ---");
		SchematicaConfig config = Schematica.CONFIG;
		boolean savedHud = config.infoHud;
		MaterialList.Sort savedSort = config.infoHudSort;
		Screen before = mc.currentScreen;

		config.infoHud = false;
		config.infoHudSort = MaterialList.Sort.DESCENDING;

		MaterialListScreen screen = new MaterialListScreen(null);
		mc.setScreen(screen);
		InfoHud.tick(mc);
		checkTrue("with the hud off there is nothing to draw", !InfoHud.isShowing());

		clickButton(screen, buttonAt(screen, 0));
		checkTrue("the button turns the hud on", config.infoHud);
		InfoHud.tick(mc);
		checkTrue("and the hud has it on the next tick", InfoHud.isShowing());
		checkTrue("with something in it", !InfoHud.getRows().isEmpty());

		List<MaterialList.Entry> most = InfoHud.getRows();
		checkTrue("the most the schematic takes leading",
				most.get(0).getNeeded() >= most.get(most.size() - 1).getNeeded());

		clickButton(screen, buttonAt(screen, 1));
		checkTrue("the sort button turns the list round",
				config.infoHudSort == MaterialList.Sort.ASCENDING);
		InfoHud.tick(mc);
		List<MaterialList.Entry> fewest = InfoHud.getRows();
		checkTrue("and the hud draws it that way up",
				fewest.get(0).getNeeded() <= fewest.get(fewest.size() - 1).getNeeded());

		clickButton(screen, buttonAt(screen, 0));
		checkTrue("the button turns the hud off again", !config.infoHud);
		InfoHud.tick(mc);
		checkTrue("and the hud goes with it", !InfoHud.isShowing());
		check("with nothing left to draw", InfoHud.getRows().size(), 0);

		config.infoHud = savedHud;
		config.infoHudSort = savedSort;
		config.save();
		mc.setScreen(before);
		Log.info("SMOKETEST: --- info hud screen done ---");
	}

	/** The mod's keys have to behave exactly like vanilla's once they are in the controls list. */
	private static void runKeybindTests(Minecraft mc) {
		Log.info("SMOKETEST: --- keybinds ---");

		SchematicaConfig config = Schematica.CONFIG;
		KeyBinding[] all = mc.options.allKeys;
		int loadIndex = indexOf(all, config.keyLoad.binding);

		checkTrue("load binding is in the controls list", loadIndex >= 0);
		checkTrue("save binding is in the controls list", indexOf(all, config.keySave.binding) >= 0);
		checkTrue("control binding is in the controls list", indexOf(all, config.keyControl.binding) >= 0);
		checkTrue("toggle binding is in the controls list", indexOf(all, config.keyToggle.binding) >= 0);
		checkTrue("settings binding is in the controls list", indexOf(all, config.keySettings.binding) >= 0);
		checkTrue("layer up binding is in the controls list", indexOf(all, config.keyLayerUp.binding) >= 0);
		checkTrue("layer down binding is in the controls list", indexOf(all, config.keyLayerDown.binding) >= 0);
		checkTrue("easy place binding is in the controls list", indexOf(all, config.keyEasyPlace.binding) >= 0);
		checkTrue("material list binding is in the controls list", indexOf(all, config.keyMaterials.binding) >= 0);
		check("controls list length", all.length, 19);
		check("every binding the mod owns is listed", config.getKeybinds().size(), 9);

		// A raw key would show up on the controls screen as the untranslated key, which is the usual
		// symptom of a language file that never made it into the vanilla table.
		String label = Translations.get(config.keyLoad.binding.translationKey);
		checkText("controls screen label", label, "Load Schematic");
		StringBuilder keyNames = new StringBuilder();
		for (SchematicaConfig.Keybind keybind : config.getKeybinds()) {
			keyNames.append(keyNames.length() == 0 ? "" : ", ").append(keybind.getKeyName());
		}
		Log.info("SMOKETEST: default keys are " + keyNames);

		if (loadIndex < 0) {
			return;
		}

		// This is exactly what the controls screen does when a key is pressed on a selected row.
		int original = config.keyLoad.getCode();
		mc.options.setKeybindKey(loadIndex, Keyboard.KEY_F9);
		check("rebinding reaches the mod", config.keyLoad.getCode(), Keyboard.KEY_F9);
		checkOptionsFile("key_" + config.keyLoad.binding.translationKey + ":" + Keyboard.KEY_F9);

		mc.options.setKeybindKey(loadIndex, original);
		check("rebinding is undone", config.keyLoad.getCode(), original);

		runKeysScreenTests(mc, config, original);

		Log.info("SMOKETEST: --- keybinds done ---");
	}

	/**
	 * The mod's own controls screen, driven through its buttons and key handler. It writes the
	 * binding itself rather than going through {@code GameOptions.setKeybindKey}, so it has to reach
	 * {@code options.txt} on its own.
	 */
	private static void runKeysScreenTests(Minecraft mc, SchematicaConfig config, int original) {
		Screen previous = mc.currentScreen;
		SchematicaKeysScreen keys = new SchematicaKeysScreen(null);
		mc.setScreen(keys);

		ButtonWidget row = buttonAt(keys, 0);
		if (row == null) {
			fail("the controls screen has no rows", null);
			mc.setScreen(previous);
			return;
		}

		clickButton(keys, row);
		checkText("a clicked row waits for a key", row.text, "> " + config.keyLoad.getKeyName() + " <");

		pressKey(keys, 'x', Keyboard.KEY_F9);
		check("the mod's controls screen rebinds", config.keyLoad.getCode(), Keyboard.KEY_F9);
		checkText("the row shows the new key", row.text, Keyboard.getKeyName(Keyboard.KEY_F9));
		checkOptionsFile("key_" + config.keyLoad.binding.translationKey + ":" + Keyboard.KEY_F9);

		// Escape has to give the rebind up rather than land on the binding, or the key that closes
		// every screen in the game would be the one bound.
		clickButton(keys, row);
		pressKey(keys, (char) 27, Keyboard.KEY_ESCAPE);
		check("escape gives up the rebind", config.keyLoad.getCode(), Keyboard.KEY_F9);

		clickButton(keys, row);
		pressKey(keys, 'x', original);
		check("the mod's controls screen puts it back", config.keyLoad.getCode(), original);

		mc.setScreen(previous);
	}

	/** Mod Menu is optional, so this only runs when it is actually on the classpath. */
	private static void runModMenuTests() {
		Class<?> modMenu;
		try {
			modMenu = Class.forName("io.github.prospector.modmenu.ModMenu");
		} catch (ClassNotFoundException exception) {
			Log.info("SMOKETEST: Mod Menu is not installed, skipping its checks");
			return;
		}

		Log.info("SMOKETEST: --- mod menu ---");
		try {
			boolean registered = (Boolean) modMenu
					.getMethod("hasConfigScreenFactory", String.class)
					.invoke(null, Schematica.MOD_ID);
			checkTrue("mod menu found the modmenu entrypoint", registered);

			Object screen = modMenu
					.getMethod("getConfigScreen", String.class, Screen.class)
					.invoke(null, Schematica.MOD_ID, null);
			checkTrue("mod menu builds the settings screen", screen instanceof SchematicaSettingsScreen);
		} catch (ReflectiveOperationException exception) {
			fail("mod menu integration is not wired up", exception);
		}
		Log.info("SMOKETEST: --- mod menu done ---");
	}

	private static int indexOf(KeyBinding[] keys, KeyBinding key) {
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] == key) {
				return i;
			}
		}
		return -1;
	}

	private static void checkOptionsFile(String expectedLine) {
		File file = new File(Minecraft.getRunDirectory(), "options.txt");
		try {
			for (String line : java.nio.file.Files.readAllLines(file.toPath())) {
				if (line.trim().equals(expectedLine)) {
					Log.info("SMOKETEST: ok - options.txt carries \"" + expectedLine + "\"");
					return;
				}
			}
			fail("options.txt has no line \"" + expectedLine + "\"", null);
		} catch (java.io.IOException exception) {
			fail("could not read options.txt", exception);
		}
	}

	// --- on a server -------------------------------------------------------------------------

	private static int serverStep = 0;
	private static int hitX;
	private static int hitY;
	private static int hitZ;
	private static int targetX;
	private static int targetY;
	private static int targetZ;
	private static double joinX;
	private static double joinY;
	private static double joinZ;
	private static int airX;
	private static int airY;
	private static int airZ;

	/**
	 * Easy place against a real server, which is the only place half of it exists: in single player
	 * a slot click is applied on the spot and there is nobody to ask, so nothing about asking and
	 * being answered is exercised at all.
	 *
	 * <p>Written as steps rather than as one method because most of it is waiting - for the items
	 * the server hands out, for it to answer a swap, and for it to take back a block it did not like.
	 * The server is expected to have been told to give the player a full hotbar of cobblestone and
	 * then the gold, which is what puts the gold out of reach in the inventory proper.
	 */
	private static void runMultiplayerTests(Minecraft mc) {
		if (mc.player == null) {
			// Between the death and the respawn there is no player to ask about anything.
			fail("the player went away mid-test", null);
			finish(mc);
			return;
		}

		// Straight down, and kept there while the items are handed out. Beta throws a given item in
		// the direction the player faces, and looking down drops it at their feet instead of sending
		// it skidding out of reach - so nothing has to be chased, and nothing wanders into a hole.
		if (serverStep < 2) {
			mc.player.pitch = 90.0F;
		}

		switch (serverStep) {
			case 0:
				// The join is still settling: the player is dropping into place and chunks are arriving.
				if (ticks < 100) {
					return;
				}
				Log.info("SMOKETEST: --- easy place on a server ---");
				checkTrue("the client is talking to a server",
						mc.interactionManager instanceof MultiplayerInteractionManager);
				Log.info("SMOKETEST: joined as " + mc.session.username + " at "
						+ (int) mc.player.x + ", " + (int) mc.player.y + ", " + (int) mc.player.z);
				joinX = mc.player.x;
				joinY = mc.player.y;
				joinZ = mc.player.z;
				runServerMemoryTests();
				nextStep(1);
				return;

			case 1:
				// Waited for rather than timed: the server hands the items out when it gets round to it.
				if (stackCount(mc.player.inventory) < TEST_STACKS) {
					if (ticks % 40 == 0) {
						Log.info("SMOKETEST: waiting for items - inventory is " + describe(mc.player.inventory));
					}
					if (ticks > 20 * 90) {
						fail("the server never handed out the test items - inventory is "
								+ describe(mc.player.inventory), null);
						finish(mc);
					}
					return;
				}

				// Whichever slots the items happened to land in, put them where the test needs them:
				// the bar full of blocks this schematic does not use, and the gold out of reach in the
				// pack. Plain shift clicks, which is setup rather than the thing being tested.
				arrangeInventory(mc);
				nextStep(2);
				return;

			case 2:
				// Give the server a moment to agree with the setup clicks before testing on top of them.
				if (ticks < 20) {
					return;
				}
				Log.info("SMOKETEST: set up with " + describe(mc.player.inventory));
				checkTrue("the hotbar is full, so the swap has to give something up",
						hotbarSlotOf(mc.player.inventory, 0) < 0);
				checkTrue("and the gold is in the pack rather than on the bar",
						mainInventorySlotOf(mc.player.inventory, GOLD) >= 0);
				nextStep(3);
				return;

			case 3:
				if (!setUpServerTarget(mc)) {
					finish(mc);
					return;
				}
				nextStep(4);
				return;

			case 4:
				// A schematic is loaded now, so the paste gate has something to turn down. The world
				// being drawn here belongs to the server, and writing into the client's copy of it
				// would be a lie the next chunk update takes back.
				checkText("pasting is not offered on a server",
						SchematicPaste.availability(mc).name(),
						SchematicPaste.Availability.ON_A_SERVER.name());
				check("and asking anyway is refused", SchematicPaste.paste(mc), SchematicPaste.REFUSED);
				check("so the block the schematic wants is still not there",
						mc.world.getBlockId(targetX, targetY, targetZ), 0);

				Schematica.STATE.isEasyPlace = true;
				HotbarRestock.reset();
				mc.field_2823 = hitResult(hitX, hitY, hitZ, SIDE_UP);
				checkTrue("the click that finds a block in the inventory places nothing",
						EasyPlace.interceptUse(mc));
				Log.info("SMOKETEST: after the swap the inventory is " + describe(mc.player.inventory));
				checkTrue("the block is swapped onto the hotbar", hotbarSlotOf(mc.player.inventory, GOLD) >= 0);
				checkTrue("and the swap waits on the server before anything is built",
						HotbarRestock.isWaitingForServer());
				nextStep(5);
				return;

			case 5:
				// The answer arrives over the network, so this is a wait rather than a check.
				if (HotbarRestock.isBusy()) {
					if (ticks > 20 * 10) {
						fail("the server never answered the swap", null);
						finish(mc);
					}
					return;
				}
				check("the server took the swap", HotbarRestock.getRefusals(), 0);
				checkTrue("and the block is still on the hotbar now it has agreed",
						hotbarSlotOf(mc.player.inventory, GOLD) >= 0);
				nextStep(6);
				return;

			case 6:
				mc.field_2823 = hitResult(hitX, hitY, hitZ, SIDE_UP);
				checkTrue("the next click goes through", !EasyPlace.interceptUse(mc));
				check("with the block that was swapped over in hand",
						itemIdAt(mc.player.inventory, mc.player.inventory.selectedSlot), GOLD);
				rightClick(mc);
				nextStep(7);
				return;

			case 7:
				// A block the server did not accept would have been taken back off us by now.
				if (ticks < 40) {
					return;
				}
				check("the server kept the block that was placed",
						mc.world.getBlockId(targetX, targetY, targetZ), GOLD);

				// On to the half of it that has nothing to build against. A server is entitled to
				// refuse a click on a position that holds nothing at all, so whether it goes through
				// is a question only a real one can answer.
				if (!setUpAirTarget(mc)) {
					finish(mc);
					return;
				}
				nextStep(8);
				return;

			case 8:
				// Straight up, which is the one aim that needs nothing worked out to know where it
				// lands, and nothing faked: the mod finds the position from the look itself.
				mc.player.pitch = -90.0F;
				mc.field_2823 = null;
				rightClick(mc);
				check("a block with nothing to build against goes down on a server",
						mc.world.getBlockId(airX, airY, airZ), GOLD);
				nextStep(9);
				return;

			default:
				// The same wait again: a placement the server threw out comes back as a block update
				// putting the air back, so a block still standing here is one the server agreed to.
				if (ticks < 40) {
					return;
				}
				check("and the server kept that one too",
						mc.world.getBlockId(airX, airY, airZ), GOLD);
				check("no swap was turned down anywhere along the way", HotbarRestock.getRefusals(), 0);
				Log.info("SMOKETEST: --- easy place on a server done ---");

				runServerRestoreTests(mc);
				finish(mc);
		}
	}

	/**
	 * The one part of remembering only a real server can answer for: whether the address dialled on
	 * the way in reaches the world and names it. Nothing here sets that address - the connect screen
	 * did. Everything else about remembering runs the same in single player and is checked there.
	 */
	private static void runServerMemoryTests() {
		checkText("a server is known by the address dialled and the dimension",
				SchematicMemory.getWorldId(), "server/" + SERVER_HOST + ":" + SERVER_PORT + "/0");
	}

	/**
	 * Putting a schematic back into a world that came off a server. Single player proves the reading
	 * and the writing; what only a server can show is a schematic being loaded into a world it does
	 * not own - the client holds a stub of the server world, and the overlay is drawn against it.
	 *
	 * <p>Rather than log off and back on, which would mean waiting out a second handshake, the note
	 * is written and the world let go of and picked straight back up. That is the same three calls a
	 * real return makes, one after another instead of a minute apart.
	 */
	private static void runServerRestoreTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		Log.info("SMOKETEST: --- what was open on the server ---");

		state.getActive().offset.set(SERVER_REJOIN_X, SERVER_REJOIN_Y, SERVER_REJOIN_Z);
		state.rotateSchematic();
		state.rotateSchematic();

		SchematicMemory.onWorldLeaving();
		state.clearSchematic();
		SchematicMemory.load(memoryFile());
		SchematicMemory.onServerConnect(SERVER_HOST, SERVER_PORT);
		SchematicMemory.onWorldReady();
		SchematicMemory.tick(mc);

		checkTrue("the schematic left open on a server is put back", state.getActive().schematic != null);
		checkText("and it is the same file", state.getLoadedName(), "smoketest-server.schematic");
		check("standing where it was left, x", state.getActive().offset.x, SERVER_REJOIN_X);
		check("standing where it was left, y", state.getActive().offset.y, SERVER_REJOIN_Y);
		check("standing where it was left, z", state.getActive().offset.z, SERVER_REJOIN_Z);
		check("turned the way it was left", state.getTurns(), 2);
		checkTrue("into a world that belongs to a server rather than to us", mc.isWorldRemote());

		// Leave the note a restore run needs. A lump of the world itself, saved and then parked
		// exactly where it came from, is a schematic an overlay drawn correctly has nothing at all
		// to draw - so anything it does draw on the way back in is something being got wrong. Taken
		// from under the player, where a server world sits still.
		int x = (int) Math.floor(mc.player.x) - 2;
		int y = Math.max(1, (int) Math.floor(mc.player.y) - 8);
		int z = (int) Math.floor(mc.player.z) - 2;
		File area = new File(state.getSchematicDirectory(), "smoketest-server-area.schematic");
		if (state.saveSchematic(area, new Vec3i(x, y, z), new Vec3i(x + 4, y + 4, z + 4))
				&& state.loadSchematic(area)) {
			state.getActive().offset.set(x, y, z);
			state.setRenderingLayer(-1);
			state.getActive().isRenderingSchematic = true;
			Log.info("SMOKETEST: leaving a note for a restore run - " + state.getLoadedName()
					+ " at " + x + ", " + y + ", " + z);
		} else {
			fail("could not save a lump of the server world to come back to", null);
		}
	}

	/**
	 * Finds a block of air above the player with nothing touching it, which is the position the
	 * schematic is then moved to. Straight up, because that is the one direction a player standing
	 * outdoors can be relied on to have clear.
	 */
	private static boolean setUpAirTarget(Minecraft mc) {
		int px = (int) Math.floor(mc.player.x);
		int py = (int) Math.floor(mc.player.y);
		int pz = (int) Math.floor(mc.player.z);

		// One above the eye is still the player's own space; from two up the line of sight is clear.
		for (int up = 2; up <= 5; up++) {
			int y = py + up;
			if (y > 126 || !isSurroundedByAir(mc, px, y, pz) || !isClearBetween(mc, px, py, pz, y)) {
				continue;
			}

			airX = px;
			airY = y;
			airZ = pz;
			Schematica.STATE.getActive().offset.set(airX, airY, airZ);
			Schematica.STATE.getActive().needsUpdate = true;
			Log.info("SMOKETEST: one gold block wanted in mid air at " + airX + ", " + airY + ", " + airZ);
			checkTrue("there is nothing at all around the position in the air",
					isSurroundedByAir(mc, airX, airY, airZ));
			return true;
		}

		StringBuilder above = new StringBuilder();
		for (int up = 1; up <= 6; up++) {
			above.append(above.length() == 0 ? "" : ", ").append('y').append(py + up).append('=')
					.append(mc.world.getBlockId(px, py + up, pz));
		}
		fail("found no clear air above the player at " + px + ", " + py + ", " + pz + " - " + above, null);
		return false;
	}

	/** Whether the line of sight from the eye up to a position has nothing standing in it. */
	private static boolean isClearBetween(Minecraft mc, int x, int py, int z, int y) {
		for (int between = py + 1; between < y; between++) {
			if (mc.world.getBlockId(x, between, z) != 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean isSurroundedByAir(Minecraft mc, int x, int y, int z) {
		return mc.world.getBlockId(x, y, z) == 0
				&& mc.world.getBlockId(x, y - 1, z) == 0
				&& mc.world.getBlockId(x, y + 1, z) == 0
				&& mc.world.getBlockId(x - 1, y, z) == 0
				&& mc.world.getBlockId(x + 1, y, z) == 0
				&& mc.world.getBlockId(x, y, z - 1) == 0
				&& mc.world.getBlockId(x, y, z + 1) == 0;
	}

	private static void nextStep(int next) {
		serverStep = next;
		ticks = 0;
	}

	/**
	 * Finds somewhere beside the player to build - a full block with two clear blocks over it, near
	 * enough that the server allows the reach - and puts a one block schematic over the empty space.
	 */
	private static boolean setUpServerTarget(Minecraft mc) {
		int px = (int) Math.floor(mc.player.x);
		int py = (int) Math.floor(mc.player.y);
		int pz = (int) Math.floor(mc.player.z);

		// Nearest columns first, so the block goes down where the player can see it, and never in the
		// two columns they are standing in - a placement there would be refused for being inside them.
		for (int radius = 2; radius <= 5; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					if (findGround(mc, px + dx, py, pz + dz)) {
						return loadSingleBlockSchematic(mc);
					}
				}
			}
		}

		StringBuilder around = new StringBuilder();
		for (int dy = 2; dy >= -12; dy--) {
			around.append(around.length() == 0 ? "" : ", ").append('y').append(py + dy).append('=')
					.append(mc.world.getBlockId(px, py + dy, pz));
		}
		fail("found nowhere near the player to build - standing at " + px + ", " + py + ", " + pz
				+ " over " + around, null);
		return false;
	}

	/**
	 * The first block in a column that something can be built on top of, searching down from head
	 * height. Down rather than in a fixed window because where the ground is depends entirely on
	 * where the server dropped the player - a ledge, a hillside or the floor of a hole.
	 *
	 * <p>A snow layer counts as well: it is the one block a placement replaces where it stands
	 * rather than landing on top of, and in a snowy biome it covers everything.
	 */
	private static boolean findGround(Minecraft mc, int x, int py, int z) {
		for (int y = py + 2; y >= py - 12 && y > 0; y--) {
			if (mc.world.getBlockId(x, y + 1, z) != 0 || mc.world.getBlockId(x, y + 2, z) != 0) {
				continue;
			}

			boolean snow = mc.world.getBlockId(x, y, z) == SNOW_LAYER;
			if (!snow && !mc.world.method_1783(x, y, z)) {
				continue;
			}

			// The server measures the reach to the block that was clicked and throws out anything
			// past eight; well inside that, so a spot found down a hillside is still a fair test.
			if (mc.player.method_1347(x + 0.5, y + 0.5, z + 0.5) > 36.0) {
				continue;
			}

			hitX = x;
			hitY = y;
			hitZ = z;
			targetX = x;
			targetY = snow ? y : y + 1;
			targetZ = z;
			return true;
		}
		return false;
	}

	private static boolean loadSingleBlockSchematic(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		int[][][] blocks = new int[1][1][1];
		blocks[0][0][0] = GOLD;

		File file = new File(state.getSchematicDirectory(), "smoketest-server.schematic");
		try {
			SchematicFormat.write(file, new Schematic(blocks, new int[1][1][1], new ArrayList<>(), 1, 1, 1));
		} catch (java.io.IOException exception) {
			fail("could not write the server test schematic", exception);
			return false;
		}

		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false on the server", null);
			return false;
		}

		state.getActive().offset.set(targetX, targetY, targetZ);
		state.getActive().isRenderingSchematic = true;
		state.getActive().needsUpdate = true;
		Log.info("SMOKETEST: one gold block wanted at " + targetX + ", " + targetY + ", " + targetZ
				+ ", reached by clicking the top of " + hitX + ", " + hitY + ", " + hitZ);
		return true;
	}

	/**
	 * Puts the picked-up stacks where the test needs them: the bar full of blocks the schematic does
	 * not use, and the gold out of reach in the pack. Which slot each stack landed in depends on the
	 * order they were swept up, so this arranges them rather than hoping.
	 *
	 * <p>Plain shift clicks, the same ones a player makes in their inventory screen. That is setup,
	 * not the thing under test - what is being tested is that the mod can do this for itself, with
	 * no screen open, and have the server agree.
	 */
	private static void arrangeInventory(Minecraft mc) {
		PlayerInventory inventory = mc.player.inventory;
		int gold = hotbarSlotOf(inventory, GOLD);
		if (gold < 0) {
			return;
		}

		mc.interactionManager.clickSlot(0, HOTBAR_FIRST_SLOT + gold, 0, true, mc.player);
		for (int slot = HOTBAR_SIZE; slot < inventory.main.length; slot++) {
			if (itemIdAt(inventory, slot) != 0 && itemIdAt(inventory, slot) != GOLD) {
				mc.interactionManager.clickSlot(0, slot, 0, true, mc.player);
				return;
			}
		}
	}

	private static int stackCount(PlayerInventory inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.main.length; slot++) {
			if (inventory.main[slot] != null) {
				count++;
			}
		}
		return count;
	}

	/** Every slot that holds something, as {@code slot:item x count}. */
	private static String describe(PlayerInventory inventory) {
		StringBuilder text = new StringBuilder();
		for (int slot = 0; slot < inventory.main.length; slot++) {
			ItemStack stack = inventory.main[slot];
			if (stack != null) {
				text.append(text.length() == 0 ? "" : ", ")
						.append(slot).append(':').append(stack.itemId).append('x').append(stack.count);
			}
		}
		return text.length() == 0 ? "empty" : text.toString();
	}

	private static int hotbarSlotOf(PlayerInventory inventory, int itemId) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (itemIdAt(inventory, slot) == itemId) {
				return slot;
			}
		}
		return -1;
	}

	// --- in world ----------------------------------------------------------------------------

	private static void buildStructure(Minecraft mc) {
		World world = mc.world;

		// Nothing is being drawn yet, which is exactly when the camera tracking used to be skipped:
		// the GUI buttons that drop a point "here" would then land at the origin.
		Vec3i here = new Vec3i();
		Schematica.STATE.moveHere(here);
		if (Math.abs(here.x - (int) Math.floor(mc.player.x)) > 2
				|| Math.abs(here.z - (int) Math.floor(mc.player.z)) > 2) {
			fail("moveHere placed a point at " + here + " but the player is at "
					+ (int) Math.floor(mc.player.x) + ", " + (int) Math.floor(mc.player.z), null);
		} else {
			Log.info("SMOKETEST: ok - moveHere tracks the camera with nothing rendered: " + here);
		}

		baseX = (int) Math.floor(mc.player.x) + 4;
		// The camera is parked above the structure for the screenshots, and that is where the player is
		// when the world is saved - so a reused world would build higher every run, and eventually out
		// of the top of the world altogether. Capped so the same save can be run against for ever.
		baseY = Math.min((int) Math.floor(mc.player.y), MAX_BUILD_Y);
		baseZ = (int) Math.floor(mc.player.z) - 2;

		Log.info("SMOKETEST: building at " + baseX + ", " + baseY + ", " + baseZ);

		// A slab of gold with an asymmetric corner, so a rotation is obvious in the screenshot.
		for (int x = 0; x < STRUCTURE_SIZE; x++) {
			for (int z = 0; z < STRUCTURE_SIZE; z++) {
				for (int y = 0; y < 3; y++) {
					int id = (y == 0 || (x == 0 && z < 2)) ? GOLD : 0;
					world.method_201(baseX + x, baseY + y, baseZ + z, id, 0);
				}
			}
		}

		world.method_201(baseX + 2, baseY + 1, baseZ + 2, WOOD_STAIRS, 0);
		world.method_201(baseX + 3, baseY + 1, baseZ + 2, TORCH, 1);

		world.method_201(baseX + 1, baseY + 1, baseZ + 4, STANDING_SIGN, 8);
		BlockEntity blockEntity = world.method_1777(baseX + 1, baseY + 1, baseZ + 4);
		if (blockEntity instanceof SignBlockEntity) {
			((SignBlockEntity) blockEntity).texts[0] = "SMOKE";
			((SignBlockEntity) blockEntity).texts[1] = "TEST";
		} else {
			fail("expected a SignBlockEntity in the world, got " + blockEntity, null);
		}

		// The torch re-orients itself on placement, so record what the world settled on rather than
		// what was asked for; the schematic has to reproduce exactly this.
		worldStairsMetadata = world.method_1778(baseX + 2, baseY + 1, baseZ + 2);
		worldTorchMetadata = world.method_1778(baseX + 3, baseY + 1, baseZ + 2);
		Log.info("SMOKETEST: world stairs metadata = " + worldStairsMetadata
				+ ", world torch metadata = " + worldTorchMetadata);
	}

	private static void saveStructure() {
		SchematicaState state = Schematica.STATE;
		state.pointA.set(baseX, baseY, baseZ);
		state.pointB.set(baseX + STRUCTURE_SIZE - 1, baseY + 2, baseZ + STRUCTURE_SIZE - 1);
		state.updatePoints();
		state.isRenderingGuide = true;

		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");
		if (!state.saveSchematic(file, state.pointMin, state.pointMax)) {
			fail("saveSchematic returned false", null);
			return;
		}
		if (!file.isFile() || file.length() == 0) {
			fail("schematic file was not written", null);
			return;
		}
		Log.info("SMOKETEST: wrote " + file.getName() + " (" + file.length() + " bytes)");
	}

	private static void loadAndTransform(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");

		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false", null);
			return;
		}

		Schematic schematic = state.getActive().schematic.getSchematic();
		check("width", schematic.getWidth(), STRUCTURE_SIZE);
		check("height", schematic.getHeight(), 3);
		check("length", schematic.getLength(), STRUCTURE_SIZE);
		check("floor block", schematic.getBlockId(0, 0, 0), GOLD);
		check("stairs block", schematic.getBlockId(2, 1, 2), WOOD_STAIRS);
		check("torch block", schematic.getBlockId(3, 1, 2), TORCH);
		check("torch metadata matches the world", schematic.getMetadata(3, 1, 2), worldTorchMetadata);
		check("stairs metadata matches the world", schematic.getMetadata(2, 1, 2), worldStairsMetadata);
		check("block entity count", schematic.getBlockEntities().size(), 1);

		BlockEntity blockEntity = schematic.getBlockEntity(1, 1, 4);
		if (blockEntity instanceof SignBlockEntity) {
			checkText("sign line 1", ((SignBlockEntity) blockEntity).texts[0], "SMOKE");
			checkText("sign line 2", ((SignBlockEntity) blockEntity).texts[1], "TEST");
		} else {
			fail("sign block entity missing from the loaded schematic, got " + blockEntity, null);
		}

		// Park the ghost next to the original and look at both.
		state.getActive().offset.set(baseX, baseY, baseZ + STRUCTURE_SIZE + 2);
		state.getActive().isRenderingSchematic = true;
		state.getActive().needsUpdate = true;

		parkCamera(mc);
		mc.options.thirdPerson = false;
	}

	/**
	 * Logs off with a schematic open and comes straight back in, which is the whole feature the way
	 * a player meets it. Nothing below is driven by hand: the mod is told a schematic is open the
	 * same way any screen tells it, and everything after that is its own hooks and its own tick.
	 */
	private static void leaveWorld(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");
		// One schematic, whatever the scenes above left open: what comes back is checked one field
		// at a time on the way in, and that only means anything against a known set.
		state.closeAll();
		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false", null);
			finish(mc);
			return;
		}

		state.getActive().offset.set(REJOIN_X, REJOIN_Y, REJOIN_Z);
		state.rotateSchematic();
		state.setRenderingLayer(1);
		Log.info("SMOKETEST: --- logging off with " + state.getLoadedName() + " open ---");

		// method_2120 blocks for the whole of the world load with the game ticking underneath it,
		// so the phase has to be set before it is called.
		phase = Phase.REJOIN;
		ticks = 0;

		mc.setWorld(null);
		checkTrue("leaving the world lets go of it", SchematicMemory.getWorldId() == null);
		checkTrue("and closes the schematic on the way out", state.getActive().schematic == null);

		mc.method_2120(WORLD_NAME, "SmokeTest", 1L);
	}

	/** What the player sees on the way back in. */
	private static void checkRejoin(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		mc.setScreen(null);

		Log.info("SMOKETEST: --- back in the world ---");
		checkText("coming back names the same world",
				SchematicMemory.getWorldId(), "singleplayer/" + WORLD_NAME + "/0");
		checkTrue("the schematic that was open is open again", state.getActive().schematic != null);
		checkText("and it is the same file", state.getLoadedName(), "smoketest.schematic");
		check("standing where it was left, x", state.getActive().offset.x, REJOIN_X);
		check("standing where it was left, y", state.getActive().offset.y, REJOIN_Y);
		check("standing where it was left, z", state.getActive().offset.z, REJOIN_Z);
		check("turned the way it was left", state.getTurns(), 1);
		check("sliced the way it was left", state.getActive().renderingLayer, 1);
		checkTrue("and showing, the way it was left", state.getActive().isRenderingSchematic);

		// Leave behind the note the restore run needs: the schematic square on top of the structure
		// this run built, so a cold start has a world that really does hold those blocks to draw
		// itself against. Written out for us when the game shuts down at the end of the run.
		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");
		if (state.loadSchematic(file)) {
			state.getActive().offset.set(baseX, baseY, baseZ);
			state.setRenderingLayer(-1);
			state.getActive().isRenderingSchematic = true;
			Log.info("SMOKETEST: leaving a note for a restore run - " + state.getLoadedName()
					+ " at " + baseX + ", " + baseY + ", " + baseZ);
		}
	}

	/**
	 * The half of remembering that needs a game which has really been closed and opened again: not
	 * whether the note was read, which is checked plenty of other ways, but whether what got drawn
	 * from it is right.
	 *
	 * <p>The overlay is built by comparing the schematic against the world, so it is only ever as
	 * good as the world underneath it at the moment it was built - and a world being handed over is
	 * not the same world it settles into. Rather than trusting a count, the overlay is asked what it
	 * is drawing, then made to build itself again now that everything has arrived, and the two
	 * answers compared. They can only differ if the first one was drawn against a world that was
	 * not there yet.
	 */
	private static void runRestoredOverlayTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer == null || mc.player == null) {
			fail("no renderer or no player in the restore run", null);
			finish(mc);
			return;
		}

		if (state.getActive().schematic != null && !MULTIPLAYER) {
			// Stood back from the schematic and looking at it, so the rebuild budget reaches it and
			// the screenshots show what is being talked about. Left alone on a server, which would
			// have to be talked into the move and has no reason to agree.
			mc.player.method_1341(state.getActive().offset.x + 2.5, state.getActive().offset.y + 6.0, state.getActive().offset.z - 9.0,
					0.0F, 22.0F);
		}

		if (ticks < SETTLE_TICKS || renderer.isRebuilding()) {
			if (ticks > REBUILD_TIMEOUT_TICKS) {
				fail("the overlay never stopped rebuilding at step " + restoreStep, null);
				finish(mc);
			}
			return;
		}

		switch (restoreStep) {
			case 0:
				Log.info("SMOKETEST: --- the overlay a cold start put back ---");
				checkTrue("starting the game cold puts the schematic back", state.getActive().schematic != null);
				if (state.getActive().schematic == null) {
					finish(mc);
					return;
				}
				Log.info("SMOKETEST: back with " + state.getLoadedName() + " at "
						+ state.getActive().offset.x + ", " + state.getActive().offset.y + ", " + state.getActive().offset.z);

				restoredGhosts = renderer.getGhostBlockCount();
				screenshot(mc, 0);
				// Work the comparison out again, against a world that has all arrived by now.
				state.getActive().needsUpdate = true;
				break;

			case 1:
				int settled = renderer.getGhostBlockCount();
				Log.info("SMOKETEST: the restored overlay drew " + restoredGhosts
						+ " block(s) as still to place; built again it draws " + settled);
				screenshot(mc, 1);
				check("what a cold start drew is what the world actually says", restoredGhosts, settled);
				check("and the world here really does hold the whole schematic", settled, 0);

				// Now the other half of it, and the half that has nothing to do with starting cold:
				// change the world underneath the overlay without telling it. Writing into the chunk
				// is what a chunk arriving amounts to - no block change, nothing announced, nothing
				// for any hook to hear - which is exactly how an overlay goes quietly out of date.
				Log.info("SMOKETEST: --- the overlay follows the world ---");
				silentlyCleared = clearSilently(mc, state);
				checkTrue("the world under the schematic can be changed without a word",
						silentlyCleared > 0);
				break;

			case 2:
				// What the overlay makes of the world now, before anything is asked to rebuild.
				restoredGhosts = renderer.getGhostBlockCount();
				screenshot(mc, 2);
				state.getActive().needsUpdate = true;
				break;

			default:
				int truth = renderer.getGhostBlockCount();
				Log.info("SMOKETEST: " + silentlyCleared + " block(s) taken out from under the overlay "
						+ "without telling it; it drew " + restoredGhosts
						+ " as still to place, and built again it draws " + truth);
				screenshot(mc, 3);
				checkTrue("taking the world away really does leave something to draw", truth > 0);
				check("the overlay notices the world changing underneath it", restoredGhosts, truth);
				finish(mc);
				return;
		}

		restoreStep++;
		ticks = 0;
	}

	/**
	 * Takes the bottom layer of the world out from under the schematic by writing straight into the
	 * chunk, the way a chunk being filled in does it. Nothing is notified, so an overlay that only
	 * ever hears about block changes has no way of knowing.
	 */
	private static int clearSilently(Minecraft mc, SchematicaState state) {
		Schematic schematic = state.getActive().schematic.getSchematic();
		int cleared = 0;

		for (int x = 0; x < schematic.getWidth(); x++) {
			for (int z = 0; z < schematic.getLength(); z++) {
				if (schematic.getBlockId(x, 0, z) == 0) {
					continue;
				}
				int worldX = state.getActive().offset.x + x;
				int worldY = state.getActive().offset.y;
				int worldZ = state.getActive().offset.z + z;
				if (mc.world.getBlockId(worldX, worldY, worldZ) == 0) {
					continue;
				}
				mc.world.method_214(worldX >> 4, worldZ >> 4)
						.method_861(worldX & 15, worldY, worldZ & 15, 0, 0);
				cleared++;
			}
		}
		return cleared;
	}

	// --- what was open here ------------------------------------------------------------------

	/**
	 * The pair the mod writes down - a flip and a count of quarter turns - has to be able to say
	 * what any run of rotations and mirrors did, because putting a schematic back means handing that
	 * pair to a fresh copy and expecting the same building.
	 */
	private static void runOrientationTests() {
		Log.info("SMOKETEST: --- orientation ---");
		SchematicaState state = Schematica.STATE;
		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");

		// r turns, m flips. Runs long enough to come back on themselves, so the pair is being kept
		// rather than the clicks counted.
		String[] runs = { "", "r", "rr", "rrr", "m", "mr", "mrr", "mrrr", "rm", "rrm", "rrrm",
				"mrm", "rmr", "rrrmrrm", "mmm", "rrrr", "mrrrrm", "rmrmrmrm" };

		for (String run : runs) {
			if (!state.loadSchematic(file)) {
				fail("loadSchematic returned false", null);
				return;
			}
			for (int i = 0; i < run.length(); i++) {
				if (run.charAt(i) == 'r') {
					state.rotateSchematic();
				} else {
					state.mirrorSchematic();
				}
			}

			int turns = state.getTurns();
			boolean mirrored = state.isMirrored();
			int[] worked = snapshot(state.getActive().schematic.getSchematic());

			if (!state.loadSchematic(file)) {
				fail("loadSchematic returned false", null);
				return;
			}
			state.setOrientation(turns, mirrored);

			checkTrue("\"" + (run.isEmpty() ? "-" : run) + "\" is put back by "
					+ (mirrored ? "a flip and " : "") + turns + " turn(s)",
					Arrays.equals(worked, snapshot(state.getActive().schematic.getSchematic())));
		}

		state.clearSchematic();
	}

	/** Every block and its metadata, flattened, so two schematics can be compared whole. */
	private static int[] snapshot(Schematic schematic) {
		int width = schematic.getWidth();
		int height = schematic.getHeight();
		int length = schematic.getLength();

		int[] flat = new int[3 + width * height * length * 2];
		flat[0] = width;
		flat[1] = height;
		flat[2] = length;

		int index = 3;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					flat[index++] = schematic.getBlockId(x, y, z);
					flat[index++] = schematic.getMetadata(x, y, z);
				}
			}
		}
		return flat;
	}

	/**
	 * Logging off and coming back, which no single run of the game can really do - so the world is
	 * named by hand from here on and the rest of it, the reading and the writing and the putting
	 * back, is the same code a real return runs.
	 */
	private static void runMemoryTests(Minecraft mc) {
		Log.info("SMOKETEST: --- what was open here ---");
		SchematicaState state = Schematica.STATE;

		// Nothing set this up: the mod named this world on its own when it was started, and has been
		// watching it from the tick ever since. Everything after this drives the same code by hand,
		// because a smoke test cannot log off and come back inside one run.
		checkText("the world the mod has been watching is the one that was started",
				SchematicMemory.getWorldId(), "singleplayer/" + WORLD_NAME + "/0");

		File file = memoryFile();
		SchematicMemory.load(file);

		comeBackTo("memory-a");
		SchematicMemory.tick(mc);
		checkText("a world is known by its folder and its dimension",
				SchematicMemory.getWorldId(), "singleplayer/memory-a/0");
		checkTrue("nothing is put back in a world never played before", state.getActive().schematic == null);

		File schematic = new File(state.getSchematicDirectory(), "smoketest.schematic");
		if (!state.loadSchematic(schematic)) {
			fail("loadSchematic returned false", null);
			return;
		}
		state.getActive().offset.set(12, 34, -56);
		state.rotateSchematic();
		state.mirrorSchematic();
		state.setRenderingLayer(1);
		// Left hidden on purpose: a schematic that comes back showing when it was put away is as
		// wrong as one that does not come back at all.
		state.getActive().isRenderingSchematic = false;
		int turns = state.getTurns();
		boolean mirrored = state.isMirrored();

		SchematicMemory.onWorldLeaving();
		checkTrue("leaving a world writes down what was open in it", file.isFile() && file.length() > 0);

		state.clearSchematic();
		comeBackTo("memory-b");
		SchematicMemory.tick(mc);
		checkTrue("another world does not get the first one schematic", state.getActive().schematic == null);
		SchematicMemory.onWorldLeaving();

		// Reading the file back off disk is what a freshly started game does, so this is the part
		// that says the mod remembers across being closed and not only across a logout.
		state.clearSchematic();
		SchematicMemory.load(file);
		comeBackTo("memory-a");
		SchematicMemory.tick(mc);
		checkTrue("coming back to a world puts the schematic back", state.getActive().schematic != null);
		checkText("the same file", state.getLoadedName(), "smoketest.schematic");
		check("at the same x", state.getActive().offset.x, 12);
		check("at the same y", state.getActive().offset.y, 34);
		check("at the same z", state.getActive().offset.z, -56);
		check("turned the same way", state.getTurns(), turns);
		checkTrue("flipped the same way", state.isMirrored() == mirrored);
		check("sliced to the same layer", state.getActive().renderingLayer, 1);
		checkTrue("and still hidden, because that is how it was left", !state.getActive().isRenderingSchematic);
		checkTrue("with the load list pointing at it",
				"smoketest.schematic".equals(state.getSchematicFiles().get(state.selectedSchematic)));

		// Nothing announces a schematic being moved, so the note is kept up to date while playing.
		// That is also what is left behind by a game killed rather than closed.
		state.getActive().offset.set(70, 71, 72);
		for (int i = 0; i < 25; i++) {
			SchematicMemory.tick(mc);
		}
		state.clearSchematic();
		SchematicMemory.load(file);
		comeBackTo("memory-a");
		SchematicMemory.tick(mc);
		check("a schematic moved while playing is written down where it stopped", state.getActive().offset.x, 70);

		runSilentWorldMemoryTests(mc, file);
		runMultiMemoryTests(mc, file);
		runServerIdMemoryTests(mc, file);
		runDimensionMemoryTests(mc, file);
	}

	/**
	 * A server's note, which is where the shape of the file is most easily got wrong.
	 *
	 * <p>Each schematic is written under the number of its place in the world it was opened in, so a
	 * line reads {@code <world>.<number>.<field>}. A world named by an address is full of dots and
	 * ends in a number of its own - the dimension - so telling one number from the other is the
	 * whole of what makes a server's note readable. An address with four numbers in it is the worst
	 * case there is, and this uses one.
	 *
	 * <p>It also reads a note written before the mod could hold more than one schematic per world,
	 * which has no number in it at all and has to come back as the first of a set.
	 */
	private static void runServerIdMemoryTests(Minecraft mc, File file) {
		SchematicaState state = Schematica.STATE;
		String host = "192.168.1.5";
		String id = "server/" + host + ":25565/0";

		Properties handMade = new Properties();
		handMade.setProperty(id + ".schematic", "smoketest.schematic");
		handMade.setProperty(id + ".x", "11");
		handMade.setProperty(id + ".y", "12");
		handMade.setProperty(id + ".z", "13");
		try (OutputStream stream = new FileOutputStream(file)) {
			handMade.store(stream, "written by the smoke test");
		} catch (IOException exception) {
			fail("could not write a server memory file by hand", exception);
			return;
		}

		state.closeAll();
		SchematicMemory.load(file);
		SchematicMemory.onServerConnect(host, 25565);
		SchematicMemory.onWorldReady();
		SchematicMemory.tick(mc);

		checkText("a server world is named by the address dialled", SchematicMemory.getWorldId(), id);
		checkTrue("a note written against it before this version is read back",
				!state.getActive().isEmpty());
		check("at the place it names", state.getActive().offset.x, 11);

		File schematic = new File(state.getSchematicDirectory(), "smoketest.schematic");
		if (!state.openSchematic(schematic)) {
			fail("openSchematic returned false against a server", null);
			return;
		}
		state.getActive().offset.set(22, 33, 44);

		SchematicMemory.onWorldLeaving();
		state.closeAll();
		SchematicMemory.load(file);
		SchematicMemory.onServerConnect(host, 25565);
		SchematicMemory.onWorldReady();
		SchematicMemory.tick(mc);

		check("two schematics against an address full of numbers come back", state.getOpenCount(), 2);
		check("the one that was already there", state.getOpen().get(0).offset.x, 11);
		check("and the one opened beside it", state.getOpen().get(1).offset.x, 22);

		state.closeAll();
		SchematicMemory.onWorldLeaving();
	}

	/**
	 * A world left holding several schematics comes back holding all of them.
	 *
	 * <p>The half of the feature that cannot be seen in one sitting: the note is a list now, and a
	 * list is only right if the places, the slices and which one the controls were on all survive
	 * being written down and read back. The file is read off disk in between, which is what a game
	 * started cold does.
	 */
	private static void runMultiMemoryTests(Minecraft mc, File file) {
		SchematicaState state = Schematica.STATE;

		SchematicMemory.onWorldLeaving();
		state.closeAll();
		SchematicMemory.load(file);
		comeBackTo("memory-d");
		SchematicMemory.tick(mc);
		check("a world never played before starts with one empty slot", state.getOpenCount(), 1);

		File schematic = new File(state.getSchematicDirectory(), "smoketest.schematic");
		for (int i = 0; i < 3; i++) {
			if (!state.openSchematic(schematic)) {
				fail("openSchematic returned false for copy " + (i + 1), null);
				return;
			}
			state.getActive().offset.set(1 + i * 3, 2 + i * 3, 3 + i * 3);
		}
		check("three of them are open", state.getOpenCount(), 3);
		state.setActiveIndex(1);
		state.setRenderingLayer(2);
		state.getOpen().get(2).isRenderingSchematic = false;

		SchematicMemory.onWorldLeaving();
		state.closeAll();
		SchematicMemory.load(file);
		comeBackTo("memory-d");
		SchematicMemory.tick(mc);

		check("all three come back", state.getOpenCount(), 3);
		check("the first where it was left", state.getOpen().get(0).offset.x, 1);
		check("the second where it was left", state.getOpen().get(1).offset.x, 4);
		check("the third where it was left", state.getOpen().get(2).offset.x, 7);
		check("in the order they were opened", state.getOpen().get(2).offset.z, 9);
		check("sliced the way it was left", state.getOpen().get(1).renderingLayer, 2);
		check("and only that one", state.getOpen().get(0).renderingLayer, -1);
		checkTrue("the one that was hidden is still hidden",
				!state.getOpen().get(2).isRenderingSchematic);
		check("with the controls back on the one they were on", state.getActiveIndex(), 1);

		// Closing one is how a schematic is forgotten, and it must not take the others with it.
		state.setActiveIndex(0);
		state.closeActive();
		SchematicMemory.onWorldLeaving();
		state.closeAll();
		SchematicMemory.load(file);
		comeBackTo("memory-d");
		SchematicMemory.tick(mc);
		check("closing one forgets that one and no more", state.getOpenCount(), 2);
		check("and the rest keep their places", state.getOpen().get(0).offset.x, 4);

		// Leave nothing behind: the world is closed empty, which is what rubs its note out.
		state.closeAll();
		SchematicMemory.onWorldLeaving();
	}

	/**
	 * A world that never says it has finished loading.
	 *
	 * <p>Every check above is driven with both ends of the world swap announced, which is what the
	 * game on its own does. A game with two dozen other mods in it is not owed that: one of them can
	 * reach the same swap and take the far end away, and nothing says so - the mod simply stops
	 * being told that a world has arrived. That used to stop it remembering anything at all, in a
	 * way no test could see, because every test said the far end out loud.
	 *
	 * <p>So this one does not. Nothing here calls {@code onWorldReady}: the world is named and then
	 * left silent, exactly as it arrives in a game where something else got there first.
	 */
	private static void runSilentWorldMemoryTests(Minecraft mc, File file) {
		SchematicaState state = Schematica.STATE;

		// Let go of the world the checks above left attached, which also takes back the readiness
		// the last comeBackTo announced.
		SchematicMemory.onWorldLeaving();
		state.clearSchematic();
		SchematicMemory.load(file);

		SchematicMemory.onSingleplayerWorld("memory-a");
		SchematicMemory.tick(mc);
		checkText("a world that never says it is ready is still worked out",
				SchematicMemory.getWorldId(), "singleplayer/memory-a/0");
		checkTrue("and nothing has been put into it yet", state.getActive().schematic == null);

		for (int i = 0; i < RESTORE_WAIT_TICKS; i++) {
			SchematicMemory.tick(mc);
		}
		checkTrue("but waiting for it to say so does not last forever", state.getActive().schematic != null);
		check("and what was left there comes back", state.getActive().offset.x, 70);

		// The half that actually broke: a world nobody announced was never written down either, so
		// closing the game lost everything that happened in it.
		state.getActive().offset.set(81, 82, 83);
		for (int i = 0; i < 25; i++) {
			SchematicMemory.tick(mc);
		}
		state.clearSchematic();
		SchematicMemory.load(file);
		comeBackTo("memory-a");
		SchematicMemory.tick(mc);
		check("and a silent world is written down like any other", state.getActive().offset.x, 81);
	}

	/**
	 * The Nether keeps its own note. The coordinates a build sits at in the overworld are eight
	 * times out and somewhere else entirely down there, so the two ends of a portal cannot share.
	 */
	private static void runDimensionMemoryTests(Minecraft mc, File file) {
		SchematicaState state = Schematica.STATE;

		Properties handMade = new Properties();
		handMade.setProperty("singleplayer/memory-c/0.schematic", "smoketest.schematic");
		handMade.setProperty("singleplayer/memory-c/0.x", "1");
		handMade.setProperty("singleplayer/memory-c/0.y", "2");
		handMade.setProperty("singleplayer/memory-c/0.z", "3");
		handMade.setProperty("singleplayer/memory-c/-1.schematic", "smoketest.schematic");
		handMade.setProperty("singleplayer/memory-c/-1.x", "99");
		try (OutputStream stream = new FileOutputStream(file)) {
			handMade.store(stream, "written by the smoke test");
		} catch (IOException exception) {
			fail("could not write a memory file by hand", exception);
			return;
		}

		state.clearSchematic();
		SchematicMemory.load(file);
		comeBackTo("memory-c");
		SchematicMemory.tick(mc);
		checkTrue("a hand written file is read back", state.getActive().schematic != null);
		check("and the overworld gets the overworld note, not the Nether one", state.getActive().offset.x, 1);

		// Closing a schematic is the only way to be rid of it, so it has to be one.
		state.clearSchematic();
		SchematicMemory.onWorldLeaving();
		SchematicMemory.load(file);
		comeBackTo("memory-c");
		SchematicMemory.tick(mc);
		checkTrue("closing a schematic before leaving forgets it", state.getActive().schematic == null);

		// Hand the real world back. The tick picks it up again next time round, and the rest of the
		// run is watched the way any other session would be.
		SchematicMemory.load(memoryFile());
		comeBackTo(WORLD_NAME);
	}

	/** Holds the camera in front of both structures; re-applied every tick so gravity cannot drift it. */
	private static void parkCamera(Minecraft mc) {
		if (mc.player != null) {
			mc.player.method_1341(baseX + 2.5, baseY + 7.0, baseZ - 9.0, cameraYaw, 22.0F);
		}
	}

	/**
	 * Whether the overlay is still catching up. A shot is held until it is not, so a screenshot
	 * never catches a rebuild half-finished - and counting the frames it takes is the measurement
	 * that says the rebuild really is being spread rather than done in one go.
	 */
	private static boolean isRebuilding() {
		SchematicRenderer renderer = Schematica.getRenderer();
		return renderer != null && renderer.isRebuilding();
	}

	/**
	 * Screenshots the current scene, then sets up the next one. Each scene exercises a different
	 * part of the render path.
	 */
	private static void shootScene(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		screenshot(mc, scene);

		switch (scene++) {
			case 0: // as loaded -> rotate it
				runOverlayUpdateTests();
				runLayerTests();
				runEasyPlaceTests(mc);
				runPasteTests(mc);
				runCoordinateFieldTests(mc);
				runMaterialScreenTests(mc);
				runInfoHudScreenTests(mc);
				runMultipleSchematicTests(mc);
				state.rotateSchematic();
				Log.info("SMOKETEST: scene 1 - rotated");
				return;
			case 1: // rotated -> mirror it back and slice to a single layer
				runChunkArrivalTests();
				state.rotateSchematic();
				state.rotateSchematic();
				state.rotateSchematic();
				state.getActive().renderingLayer = 1;
				state.getActive().needsUpdate = true;
				Log.info("SMOKETEST: scene 2 - single layer");
				return;
			case 2: // layer slice -> the control screen
				state.getActive().renderingLayer = -1;
				state.getActive().needsUpdate = true;
				mc.setScreen(new SchematicControlScreen());
				Log.info("SMOKETEST: scene 3 - control screen");
				return;
			case 3: // the same screen, with a coordinate half typed into it
				typeDraft(mc);
				Log.info("SMOKETEST: scene 4 - control screen, a coordinate being typed");
				return;
			case 4:
				mc.setScreen(new SchematicSaveScreen());
				Log.info("SMOKETEST: scene 5 - save screen");
				return;
			case 5:
				mc.setScreen(new SchematicLoadScreen(null));
				Log.info("SMOKETEST: scene 6 - load screen");
				return;
			case 6:
				mc.setScreen(new MaterialListScreen(null));
				Log.info("SMOKETEST: scene 7 - material list");
				return;
			case 7:
				// Back out to the world with the hud on. It draws from the vanilla hud rather than
				// from a screen, so this is the only scene that can show it doing so.
				mc.setScreen(null);
				Schematica.CONFIG.infoHud = true;
				Log.info("SMOKETEST: scene 8 - the info hud");
				return;
			case 8:
				// Left on over a screen, which is where a shot shows it drawing behind one.
				mc.setScreen(new SchematicaSettingsScreen(null));
				Log.info("SMOKETEST: scene 9 - settings screen, the info hud behind it");
				return;
			case 9:
				Schematica.CONFIG.infoHud = false;
				mc.setScreen(new KeybindsScreen(null, mc.options));
				Log.info("SMOKETEST: scene 10 - vanilla controls screen");
				return;
			case 10:
				mc.setScreen(new SchematicaKeysScreen(null));
				Log.info("SMOKETEST: scene 11 - the mod's controls screen");
				return;
			case 11:
				if (openModList(mc)) {
					Log.info("SMOKETEST: scene 12 - mod menu list");
					return;
				}
				Log.info("SMOKETEST: Mod Menu is not installed, moving on");
				// falls through when Mod Menu is not installed
			case 12:
				// Guarded rather than plain, because the case above can fall into it.
				loadLargeSchematic(mc);
				return;
			case 13:
			case 14:
				cameraYaw += 50.0F;
				Log.info("SMOKETEST: scene " + scene + " - the same schematic, camera turned to "
						+ (int) cameraYaw + " degrees");
				return;
			case 15:
				setUpPickerScene(mc);
				return;
			default:
				mc.setScreen(null);
				if (WORLD_TEST && !MULTIPLAYER) {
					phase = Phase.LEAVE;
					ticks = 0;
					return;
				}
				finish(mc);
		}
	}

	/**
	 * Two schematics standing side by side with the move screen over them and its picker dropped
	 * open.
	 *
	 * <p>The one scene that shows the list at all. What it is really watching for is depth: the list
	 * is drawn after everything else so that it lies over the buttons it covers, and a shot where
	 * the buttons show through it is the whole of the evidence that it does not.
	 */
	private static void setUpPickerScene(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (mc.world == null) {
			Log.info("SMOKETEST: no world, skipping the picker scene");
			return;
		}

		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");
		state.closeAll();
		for (int i = 0; i < 2; i++) {
			if (!state.openSchematic(file)) {
				fail("openSchematic returned false setting up the picker scene", null);
				return;
			}
			state.getActive().offset.set(baseX + i * (STRUCTURE_SIZE + 2), baseY, baseZ + STRUCTURE_SIZE + 2);
			state.getActive().isRenderingSchematic = true;
			state.getActive().needsUpdate = true;
		}
		state.setActiveIndex(0);
		parkCamera(mc);

		SchematicControlScreen control = new SchematicControlScreen();
		mc.setScreen(control);

		ButtonWidget picker = widgetNamed(control, "SchematicPickerWidget");
		if (picker == null) {
			fail("the move screen has no picker on it", null);
			return;
		}
		clickButton(control, picker);

		Log.info("SMOKETEST: scene " + scene + " - the move screen with " + state.getOpenCount()
				+ " schematics open and the picker dropped");
	}

	/**
	 * Swaps in a schematic large enough to span several render regions, with the camera inside it.
	 * The small structure above fits in a single region, so nothing until now has exercised the
	 * region grid, the per-frame rebuild budget or the frustum culling.
	 */
	private static void loadLargeSchematic(Minecraft mc) {
		if (largeSchematicLoaded) {
			return;
		}
		largeSchematicLoaded = true;
		mc.setScreen(null);

		if (mc.world == null) {
			// A data-only run has no world to load a schematic into, and never had one.
			Log.info("SMOKETEST: no world, skipping the large schematic");
			return;
		}

		SchematicaState state = Schematica.STATE;
		int[][][] blocks = new int[LARGE_WIDTH][LARGE_HEIGHT][LARGE_LENGTH];
		int[][][] metadata = new int[LARGE_WIDTH][LARGE_HEIGHT][LARGE_LENGTH];
		int solid = 0;

		for (int x = 0; x < LARGE_WIDTH; x++) {
			for (int y = 0; y < LARGE_HEIGHT; y++) {
				for (int z = 0; z < LARGE_LENGTH; z++) {
					// A lattice: dense enough to be a real load, open enough that anything the culling
					// wrongly dropped would leave a visible hole.
					if (x % 4 == 0 || y % 4 == 0 || z % 4 == 0) {
						blocks[x][y][z] = GOLD;
						solid++;
					}
				}
			}
		}

		File file = new File(state.getSchematicDirectory(), "smoketest-large.schematic");
		try {
			SchematicFormat.write(file, new Schematic(blocks, metadata, new ArrayList<>(),
					LARGE_WIDTH, LARGE_HEIGHT, LARGE_LENGTH));
		} catch (java.io.IOException exception) {
			fail("could not write the large schematic", exception);
			return;
		}

		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false for the large schematic", null);
			return;
		}

		// Centred on the camera and up in the air, so the world underneath is empty and every block
		// draws as a ghost with a missing-block box over it - the heaviest thing the overlay does.
		state.isRenderingGuide = false;
		state.getActive().renderingLayer = -1;
		state.getActive().offset.set(baseX + 2 - LARGE_WIDTH / 2, baseY + 2, baseZ - 9 - LARGE_LENGTH / 2);
		state.getActive().isRenderingSchematic = true;
		state.getActive().needsUpdate = true;

		Log.info("SMOKETEST: scene " + scene + " - " + LARGE_WIDTH + "x" + LARGE_HEIGHT + "x" + LARGE_LENGTH
				+ " schematic, " + solid + " blocks, " + file.length() + " bytes on disk");
	}

	/**
	 * Both halves of what keeps a large overlay cheap, checked once the renderer has had frames to
	 * lay its regions out. {@code needsUpdate} is the renderer's "rebuild all of it" flag, so it
	 * staying down is the observable half of "only the region that changed was touched".
	 */
	private static void runOverlayUpdateTests() {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the overlay update checks");
			return;
		}

		Log.info("SMOKETEST: --- overlay updates ---");

		// Several blocks in one chunk at once, which is how a server reports a tick in which more
		// than one of them moved - anyone else building beside you, water running, leaves going.
		// Nothing about them reaches the single block path, so they are their own hook.
		SchematicRenderer renderer = Schematica.getRenderer();
		checkTrue("nothing is waiting to be rebuilt to start with",
				renderer != null && !renderer.isRebuilding());

		int chunkX = state.getActive().offset.x >> 4;
		int chunkZ = state.getActive().offset.z >> 4;
		short[] corner = { packBlockChange(state.getActive().offset.x, state.getActive().offset.y, state.getActive().offset.z) };

		state.onWorldBlocksChanged(chunkX, chunkZ, corner, 0);
		checkTrue("a packet carrying no changes marks nothing",
				!state.getActive().needsUpdate && !renderer.isRebuilding());

		state.onWorldBlocksChanged(chunkX + 8, chunkZ + 8, corner, 1);
		checkTrue("blocks changing in a chunk the overlay is nowhere near leave it alone",
				!state.getActive().needsUpdate && !renderer.isRebuilding());

		short[] above = { packBlockChange(state.getActive().offset.x, state.getActive().offset.y + 60, state.getActive().offset.z) };
		state.onWorldBlocksChanged(chunkX, chunkZ, above, 1);
		checkTrue("nor do blocks in the right chunk but over the top of it",
				!state.getActive().needsUpdate && !renderer.isRebuilding());

		state.onWorldBlocksChanged(chunkX, chunkZ, corner, 1);
		checkTrue("a block under the overlay marks it for rebuilding", renderer.isRebuilding());
		checkTrue("without invalidating all of it", !state.getActive().needsUpdate);

		state.getActive().needsUpdate = false;
		state.onWorldBlockChanged(state.getActive().offset.x + 1, state.getActive().offset.y + 1, state.getActive().offset.z + 1);
		checkTrue("a block inside the overlay does not invalidate all of it", !state.getActive().needsUpdate);

		state.onWorldBlockChanged(state.getActive().offset.x - 50, state.getActive().offset.y, state.getActive().offset.z - 50);
		checkTrue("a block outside the overlay invalidates nothing", !state.getActive().needsUpdate);

		state.toggleRendering();
		checkTrue("the toggle hides the schematic", !state.getActive().isRenderingSchematic);
		state.toggleRendering();
		checkTrue("the toggle brings it back", state.getActive().isRenderingSchematic);
		checkTrue("toggling keeps the cached geometry", !state.getActive().needsUpdate);

		Log.info("SMOKETEST: --- overlay updates done ---");
	}

	/**
	 * Whole chunks arriving, which is the other half of a world that turns up in pieces. Run from a
	 * later scene than the checks above because it needs the same thing they do - nothing waiting to
	 * be rebuilt - and each scene is only shot once the overlay has caught up.
	 */
	private static void runChunkArrivalTests() {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the chunk arrival checks");
			return;
		}

		Log.info("SMOKETEST: --- chunks arriving ---");

		// A chunk brings a slab of world in one packet and never reports the blocks in it, so the
		// part of the overlay standing in it has to be told separately.
		SchematicRenderer renderer = Schematica.getRenderer();
		checkTrue("nothing is waiting to be rebuilt to start with",
				renderer != null && !renderer.isRebuilding());

		state.getActive().needsUpdate = false;
		state.onWorldChunkLoaded(state.getActive().offset.x - 100, 0, state.getActive().offset.z - 100, 16, 128, 16);
		checkTrue("a chunk nowhere near the overlay leaves it alone",
				!state.getActive().needsUpdate && !renderer.isRebuilding());

		state.onWorldChunkLoaded(state.getActive().offset.x, 0, state.getActive().offset.z, 16, 128, 16);
		checkTrue("a chunk the overlay stands in marks it for rebuilding", renderer.isRebuilding());
		checkTrue("without invalidating all of it", !state.getActive().needsUpdate);

		Log.info("SMOKETEST: --- chunks arriving done ---");
	}

	/** The way the multi block change packet packs a position: X and Z in the chunk, then Y. */
	private static short packBlockChange(int x, int y, int z) {
		return (short) ((x & 0xF) << 12 | (z & 0xF) << 8 | (y & 0xFF));
	}

	/**
	 * The step behind the layer keys and the move screen's layer buttons, which share it. Only the
	 * step is checked here - the keys themselves go through vanilla's {@code Keyboard}, which cannot
	 * be driven from inside a tick.
	 */
	private static void runLayerTests() {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the layer checks");
			return;
		}

		Log.info("SMOKETEST: --- layers ---");

		int saved = state.getActive().renderingLayer;
		int max = state.getMaxRenderingLayer();

		state.setRenderingLayer(-1);
		state.getActive().needsUpdate = false;
		state.setRenderingLayer(state.getActive().renderingLayer + 1);
		check("a layer step up leaves all-layers for the bottom one", state.getActive().renderingLayer, 0);
		checkTrue("changing the layer invalidates the overlay", state.getActive().needsUpdate);

		for (int i = 0; i <= max + 1; i++) {
			state.setRenderingLayer(state.getActive().renderingLayer + 1);
		}
		check("stepping up stops at the top layer", state.getActive().renderingLayer, max);

		state.getActive().needsUpdate = false;
		state.setRenderingLayer(state.getActive().renderingLayer + 1);
		checkTrue("a step past the top rebuilds nothing", !state.getActive().needsUpdate);

		for (int i = 0; i <= max + 1; i++) {
			state.setRenderingLayer(state.getActive().renderingLayer - 1);
		}
		check("stepping down stops at all layers", state.getActive().renderingLayer, -1);

		state.setRenderingLayer(saved);
		check("the layer is back where it started", state.getActive().renderingLayer, saved);

		Log.info("SMOKETEST: --- layers done ---");
	}

	/**
	 * Several schematics open at once: that opening one keeps the others, that everything the move
	 * screen does reaches only the one it is pointed at, and that the set has a bottom and a top.
	 *
	 * <p>Puts the scene back exactly as it found it on the way out, since the screenshots after
	 * this are of the one schematic it started with.
	 */
	private static void runMultipleSchematicTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the multiple schematic checks");
			return;
		}

		Log.info("SMOKETEST: --- several schematics at once ---");

		OpenSchematic first = state.getActive();
		int savedX = first.offset.x;
		int savedY = first.offset.y;
		int savedZ = first.offset.z;
		int savedLayer = first.renderingLayer;
		int savedTurns = first.getTurns();
		boolean savedMirrored = first.isMirrored();
		boolean savedShowing = first.isRenderingSchematic;
		File file = new File(state.getSchematicDirectory(), "smoketest.schematic");

		check("one schematic is open to start with", state.getOpenCount(), 1);
		checkTrue("and there is room for another", state.canOpenAnother());

		if (!state.openSchematic(file)) {
			fail("openSchematic returned false for a second copy", null);
			return;
		}

		check("opening another keeps the first one", state.getOpenCount(), 2);
		check("and points the controls at the new one", state.getActiveIndex(), 1);
		checkTrue("the first one is still loaded", !state.getOpen().get(0).isEmpty());
		check("and has not moved", state.getOpen().get(0).offset.x, savedX);

		OpenSchematic second = state.getActive();
		second.offset.set(savedX + 40, savedY, savedZ);
		second.isRenderingSchematic = true;
		second.setRenderingLayer(-1);

		// Which schematic answers for a world position is the question easy place asks of the set,
		// and the one that decides which build a click is building.
		checkTrue("the schematic covering a position is the one standing there",
				state.schematicAt(savedX, savedY, savedZ) == first);
		checkTrue("and the other answers for its own ground",
				state.schematicAt(second.offset.x, second.offset.y, second.offset.z) == second);
		checkTrue("with neither answering for open sky",
				state.schematicAt(savedX, savedY + 40, savedZ) == null);

		state.rotateSchematic();
		check("turning the active one leaves the other where it was",
				state.getOpen().get(0).getTurns(), savedTurns);
		check("and turns the active one", second.getTurns(), 1);
		state.rotateSchematic();
		state.rotateSchematic();
		state.rotateSchematic();
		check("and back round to where it started", second.getTurns(), 0);

		state.setRenderingLayer(1);
		check("slicing the active one leaves the other whole",
				state.getOpen().get(0).renderingLayer, savedLayer);
		check("and slices the active one", second.renderingLayer, 1);
		second.setRenderingLayer(-1);

		// The move screen's Hide button is pointed at one of them; the key is pointed at all of them.
		second.toggleRendering();
		checkTrue("hiding one leaves the rest being drawn", state.isAnyRendering());
		state.toggleAllRendering();
		checkTrue("the key hides every one of them", !state.isAnyRendering());
		state.toggleAllRendering();
		checkTrue("and brings them all back", state.isAnyRendering());
		checkTrue("including the one that was hidden on its own", second.isRenderingSchematic);

		while (state.getOpenCount() < SchematicaState.MAX_OPEN) {
			if (!state.openSchematic(file)) {
				fail("openSchematic refused below the cap, at " + state.getOpenCount(), null);
				break;
			}
		}
		check("as many as the cap allows can be open", state.getOpenCount(), SchematicaState.MAX_OPEN);
		checkTrue("and no more are offered", !state.canOpenAnother());
		checkTrue("opening one past the cap is refused", !state.openSchematic(file));
		check("and nothing was opened by the refusal", state.getOpenCount(), SchematicaState.MAX_OPEN);

		while (state.getOpenCount() > 1) {
			state.setActiveIndex(state.getOpenCount() - 1);
			state.closeActive();
		}
		check("closing them one by one leaves one", state.getOpenCount(), 1);
		check("with the controls on it", state.getActiveIndex(), 0);
		checkTrue("and it is the one that was open first", state.getActive() == first);
		check("still standing where it was", state.getActive().offset.x, savedX);

		state.closeActive();
		check("closing the last one leaves the slot rather than nothing", state.getOpenCount(), 1);
		checkTrue("with nothing in it", state.getActive().isEmpty());

		// And back to the one schematic the scenes after this are of.
		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false putting the scene back", null);
			return;
		}
		state.getActive().setOrientation(savedTurns, savedMirrored);
		state.getActive().offset.set(savedX, savedY, savedZ);
		state.getActive().setRenderingLayer(savedLayer);
		state.getActive().isRenderingSchematic = savedShowing;
		state.getActive().needsUpdate = true;

		Log.info("SMOKETEST: --- several schematics at once done ---");
	}

	/**
	 * Easy place, driven the way the mixin drives it: point the hit result at a face and ask what
	 * would happen to the click. The mouse itself cannot be faked from inside a tick, but everything
	 * downstream of it - the target position, the schematic lookup and the hand switch - is here.
	 */
	private static void runEasyPlaceTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null || mc.player == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the easy place checks");
			return;
		}

		Log.info("SMOKETEST: --- easy place ---");

		World world = mc.world;
		PlayerInventory inventory = mc.player.inventory;

		boolean savedMode = state.isEasyPlace;
		int savedLayer = state.getActive().renderingLayer;
		int savedSlot = inventory.selectedSlot;
		ItemStack[] savedHotbar = new ItemStack[HOTBAR_SIZE];
		System.arraycopy(inventory.main, 0, savedHotbar, 0, HOTBAR_SIZE);
		class_27 savedHit = mc.field_2823;

		// The ghost is parked clear of the built structure, so its own space is free to write into.
		int x = state.getActive().offset.x + 2;
		int y = state.getActive().offset.y;
		int z = state.getActive().offset.z + 2;
		int[][] touched = { { x, y, z }, { x - 1, y, z }, { x - 1, y + 1, z } };
		int[] savedBlocks = new int[touched.length];
		int[] savedMetadata = new int[touched.length];
		for (int i = 0; i < touched.length; i++) {
			savedBlocks[i] = world.getBlockId(touched[i][0], touched[i][1], touched[i][2]);
			savedMetadata[i] = world.method_1778(touched[i][0], touched[i][1], touched[i][2]);
		}

		try {
			// A gap in the ghost's floor with a block beside it to click on. The schematic wants gold
			// in the gap, so a click across that face is the one placement easy place should allow.
			world.method_201(x, y, z, 0, 0);
			world.method_201(x - 1, y, z, GOLD, 0);
			world.method_201(x - 1, y + 1, z, 0, 0);
			check("the schematic wants gold in the gap", state.getActive().schematic.getBlockId(2, 0, 2), GOLD);
			check("the schematic wants nothing above it", state.getActive().schematic.getBlockId(1, 1, 2), 0);

			for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
				inventory.main[slot] = null;
			}
			inventory.main[GOLD_SLOT] = new ItemStack(GOLD, 64, 0);
			inventory.main[PICKAXE_SLOT] = new ItemStack(Item.WOODEN_PICKAXE);
			inventory.selectedSlot = 0;

			mc.field_2823 = hitResult(x - 1, y, z, SIDE_EAST);

			state.isEasyPlace = false;
			checkTrue("a click is left alone while easy place is off", !EasyPlace.interceptUse(mc));
			check("and nothing is put in hand", inventory.selectedSlot, 0);

			// Empty handed, which is the case the hand switch is really for.
			state.isEasyPlace = true;
			checkTrue("a click at the right position goes through", !EasyPlace.interceptUse(mc));
			check("the block the schematic wants is put in hand", inventory.selectedSlot, GOLD_SLOT);

			// Same aim, but the block is now standing there: the click has nothing left to do.
			world.method_201(x, y, z, GOLD, 0);
			checkTrue("a block already built is not placed again", EasyPlace.interceptUse(mc));
			world.method_201(x, y, z, 0, 0);

			// Aimed at the top face instead, where the schematic wants nothing at all.
			mc.field_2823 = hitResult(x - 1, y, z, SIDE_UP);
			checkTrue("a click the schematic has no block for is dropped", EasyPlace.interceptUse(mc));

			// The same click with a tool in hand is not a placement, so it is not easy place's to take.
			inventory.selectedSlot = PICKAXE_SLOT;
			checkTrue("a click with a tool in hand is left alone", !EasyPlace.interceptUse(mc));
			check("and the tool stays in hand", inventory.selectedSlot, PICKAXE_SLOT);
			inventory.selectedSlot = GOLD_SLOT;

			// A block that does something of its own with a right click keeps it, or a workbench in
			// the way of a build would stop opening as soon as the mode was turned on.
			world.method_201(x - 1, y, z, WORKBENCH, 0);
			checkTrue("a block that handles right clicks keeps them", !EasyPlace.interceptUse(mc));
			world.method_201(x - 1, y, z, GOLD, 0);

			// Only the course being built is a target, so a slice above it takes the gap out of reach.
			mc.field_2823 = hitResult(x - 1, y, z, SIDE_EAST);
			state.setRenderingLayer(1);
			checkTrue("a position outside the layer slice is not a target", EasyPlace.interceptUse(mc));
			state.setRenderingLayer(savedLayer);

			runMetadataMatchTests(mc);
			runRestockTests(mc, x, y, z);
			runMidairTests(mc, x, y, z);

			// Everything above asks the mod what it would do. The rest goes through the client's own
			// click handler, which is what the mixin hangs off, so a block really does or does not
			// end up in the world - and the same click is made twice to show that what changed is
			// easy place rather than the aim.
			mc.field_2823 = hitResult(x - 1, y, z, SIDE_UP);
			inventory.selectedSlot = GOLD_SLOT;
			state.isEasyPlace = false;
			rightClick(mc);
			check("vanilla puts a block there", world.getBlockId(x - 1, y + 1, z), GOLD);
			world.method_201(x - 1, y + 1, z, 0, 0);

			state.isEasyPlace = true;
			rightClick(mc);
			check("easy place drops the same click", world.getBlockId(x - 1, y + 1, z), 0);

			// Empty handed at the one position the schematic does want a block.
			mc.field_2823 = hitResult(x - 1, y, z, SIDE_EAST);
			inventory.selectedSlot = 0;
			rightClick(mc);
			check("a click at the right position builds it", world.getBlockId(x, y, z), GOLD);
			check("out of the slot the schematic asked for", inventory.selectedSlot, GOLD_SLOT);
			world.method_201(x, y, z, 0, 0);

			check("easy place places every tick", (int) EasyPlace.useRepeatDivisor(4.0F), 20);

			// The faster rate has to be tied to the same thing the click gate is. With nothing to
			// build against, a mode that is still on must not leave the player placing at four times
			// vanilla's speed everywhere else in the world.
			state.getActive().isRenderingSchematic = false;
			check("no faster placing with the schematic hidden", (int) EasyPlace.useRepeatDivisor(4.0F), 4);
			state.getActive().isRenderingSchematic = true;

			SchematicWorld loaded = state.getActive().schematic;
			state.getActive().schematic = null;
			check("no faster placing with nothing loaded", (int) EasyPlace.useRepeatDivisor(4.0F), 4);
			state.getActive().schematic = loaded;

			state.isEasyPlace = false;
			check("and vanilla's rate is back when it is off", (int) EasyPlace.useRepeatDivisor(4.0F), 4);
		} finally {
			for (int i = 0; i < touched.length; i++) {
				world.method_201(touched[i][0], touched[i][1], touched[i][2], savedBlocks[i], savedMetadata[i]);
			}
			System.arraycopy(savedHotbar, 0, inventory.main, 0, HOTBAR_SIZE);
			inventory.selectedSlot = savedSlot;
			mc.field_2823 = savedHit;
			state.setRenderingLayer(savedLayer);
			state.isEasyPlace = savedMode;
		}

		Log.info("SMOKETEST: --- easy place done ---");
	}

	/**
	 * Which stack easy place reaches for when several could place the same block. Wool takes its
	 * colour from the stack, so the wrong one is the wrong block; a piston takes its facing from how
	 * it is placed, and insisting on a stack that matches would refuse to place one at all.
	 *
	 * <p>Called with the aim already set at a gap the schematic has a block for, and puts the
	 * schematic back the way it found it.
	 */
	private static void runMetadataMatchTests(Minecraft mc) {
		Schematic schematic = Schematica.STATE.getActive().schematic.getSchematic();
		PlayerInventory inventory = mc.player.inventory;
		int savedBlock = schematic.getBlockId(2, 0, 2);
		int savedMetadata = schematic.getMetadata(2, 0, 2);

		try {
			schematic.setBlockId(2, 0, 2, WOOL);
			schematic.setMetadata(2, 0, 2, RED_WOOL);

			inventory.main[GOLD_SLOT] = new ItemStack(WOOL, 64, 0);
			inventory.selectedSlot = 0;
			checkTrue("the wrong colour of wool is not placed", EasyPlace.interceptUse(mc));

			inventory.main[GOLD_SLOT] = new ItemStack(WOOL, 64, RED_WOOL);
			checkTrue("the right colour of wool is", !EasyPlace.interceptUse(mc));
			check("and it is the one put in hand", inventory.selectedSlot, GOLD_SLOT);

			// A piston reports metadata 7 whatever the stack, so nothing in the hotbar can ever match
			// the facing the schematic wants - and it still has to go down.
			schematic.setBlockId(2, 0, 2, PISTON);
			schematic.setMetadata(2, 0, 2, 3);
			inventory.main[GOLD_SLOT] = new ItemStack(PISTON, 64, 0);
			inventory.selectedSlot = 0;
			checkTrue("a block whose metadata is not in the stack is still placed", !EasyPlace.interceptUse(mc));
			check("and that stack is put in hand", inventory.selectedSlot, GOLD_SLOT);
		} finally {
			schematic.setBlockId(2, 0, 2, savedBlock);
			schematic.setMetadata(2, 0, 2, savedMetadata);
			inventory.main[GOLD_SLOT] = new ItemStack(GOLD, 64, 0);
			inventory.selectedSlot = GOLD_SLOT;
			Schematica.STATE.getActive().needsUpdate = true;
		}
	}

	/**
	 * Bringing a block onto the hotbar from the rest of the inventory. The click that finds the
	 * block is spent on the swap, so what is checked is that it places nothing, that the block is on
	 * the bar afterwards, and that the next click builds with it.
	 *
	 * <p>Called with the aim already set at a gap the schematic wants gold in, and puts the
	 * inventory and the schematic back the way it found them.
	 */
	private static void runRestockTests(Minecraft mc, int x, int y, int z) {
		Schematic schematic = Schematica.STATE.getActive().schematic.getSchematic();
		PlayerInventory inventory = mc.player.inventory;
		ItemStack[] saved = new ItemStack[inventory.main.length];
		System.arraycopy(inventory.main, 0, saved, 0, saved.length);
		int savedBlock = schematic.getBlockId(2, 0, 2);
		int savedMetadata = schematic.getMetadata(2, 0, 2);

		try {
			// Empty handed, with the gold the schematic wants sitting out of reach in the inventory.
			clearInventory(inventory);
			inventory.main[MAIN_SLOT] = new ItemStack(GOLD, 64, 0);
			inventory.selectedSlot = HOTBAR_SIZE - 1;
			HotbarRestock.reset();

			checkTrue("the click that finds a block in the inventory places nothing",
					EasyPlace.interceptUse(mc));
			check("the block is moved onto the hotbar", itemIdAt(inventory, 0), GOLD);
			check("and out of the inventory", itemIdAt(inventory, MAIN_SLOT), 0);

			checkTrue("the next click goes through", !EasyPlace.interceptUse(mc));
			check("with the block that was brought over in hand", inventory.selectedSlot, 0);
			rightClick(mc);
			check("and it builds", mc.world.getBlockId(x, y, z), GOLD);
			mc.world.method_201(x, y, z, 0, 0);

			// A full bar has to give something up. The tool is not it, and neither is a block this
			// schematic is built out of - the cobblestone left over from something else is.
			schematic.setBlockId(2, 0, 2, WOOL);
			schematic.setMetadata(2, 0, 2, 0);
			clearInventory(inventory);
			inventory.main[0] = new ItemStack(Item.WOODEN_PICKAXE);
			inventory.main[1] = new ItemStack(COBBLESTONE, 64, 0);
			for (int slot = 2; slot < HOTBAR_SIZE; slot++) {
				inventory.main[slot] = new ItemStack(GOLD, 64, 0);
			}
			inventory.main[MAIN_SLOT] = new ItemStack(WOOL, 64, 0);
			inventory.selectedSlot = HOTBAR_SIZE - 1;
			HotbarRestock.reset();

			checkTrue("a full bar still takes the block", EasyPlace.interceptUse(mc));
			check("the block the schematic does not use is the one given up", itemIdAt(inventory, 1), WOOL);
			check("the tool is left alone", itemIdAt(inventory, 0), Item.WOODEN_PICKAXE.id);
			// Somewhere in the inventory rather than a slot of its own: the container fills the first
			// free one, which is not the slot the block came out of.
			checkTrue("and what was given up went to the inventory",
					mainInventorySlotOf(inventory, COBBLESTONE) >= HOTBAR_SIZE);

			// A bar with nothing to spare - tools, and the block in hand - keeps all of it.
			clearInventory(inventory);
			for (int slot = 0; slot < HOTBAR_SIZE - 1; slot++) {
				inventory.main[slot] = new ItemStack(Item.WOODEN_PICKAXE);
			}
			inventory.main[HOTBAR_SIZE - 1] = new ItemStack(GOLD, 64, 0);
			inventory.main[MAIN_SLOT] = new ItemStack(WOOL, 64, 0);
			inventory.selectedSlot = HOTBAR_SIZE - 1;
			HotbarRestock.reset();

			checkTrue("a bar with nothing to spare takes nothing", EasyPlace.interceptUse(mc));
			check("and keeps the tools", itemIdAt(inventory, 0), Item.WOODEN_PICKAXE.id);
			check("leaving the block where it was", itemIdAt(inventory, MAIN_SLOT), WOOL);
		} finally {
			schematic.setBlockId(2, 0, 2, savedBlock);
			schematic.setMetadata(2, 0, 2, savedMetadata);
			System.arraycopy(saved, 0, inventory.main, 0, saved.length);
			inventory.selectedSlot = GOLD_SLOT;
			HotbarRestock.reset();
			Schematica.STATE.getActive().needsUpdate = true;
		}
	}

	private static void clearInventory(PlayerInventory inventory) {
		for (int slot = 0; slot < inventory.main.length; slot++) {
			inventory.main[slot] = null;
		}
	}

	/** The item in a slot, or 0 for an empty one, so an empty slot reads as a value like any other. */
	private static int itemIdAt(PlayerInventory inventory, int slot) {
		ItemStack stack = inventory.main[slot];
		return stack == null ? 0 : stack.itemId;
	}

	private static int mainInventorySlotOf(PlayerInventory inventory, int itemId) {
		for (int slot = HOTBAR_SIZE; slot < inventory.main.length; slot++) {
			if (itemIdAt(inventory, slot) == itemId) {
				return slot;
			}
		}
		return -1;
	}

	private static class_27 hitResult(int x, int y, int z, int side) {
		return new class_27(x, y, z, side, Vec3d.createCached(x, y, z));
	}

	/**
	 * The client's own handler for a right click on the world - the method the easy place mixin
	 * injects into. Reached by reflection because it is private, exactly as the coordinate rows are
	 * driven through the screens' protected handlers.
	 */
	private static void rightClick(Minecraft mc) {
		try {
			java.lang.reflect.Method use = Minecraft.class.getDeclaredMethod("method_2107", int.class);
			use.setAccessible(true);
			use.invoke(mc, 1);
		} catch (ReflectiveOperationException exception) {
			fail("could not drive the right click handler", exception);
		}
	}

	/**
	 * What hangs on a wall is still on its wall after a paste.
	 *
	 * <p>The case that found this was a redstone torch on the east face of a block: written before
	 * the block it hangs on, it was still standing on nothing when the run of redstone wire beside it
	 * arrived. A wire landing tells the wire next to it, and that one tells all of its own
	 * neighbours - which is the torch being asked whether it still has a wall. It had not, so it took
	 * itself down and dropped on the floor as an item, leaving a hole in the build and a circuit that
	 * no longer worked.
	 *
	 * <p>The second torch here is a plain one, which fails a different way: unlike a redstone torch
	 * it re-reads its own facing from whichever neighbour is solid every time it is placed, so with a
	 * wall on both sides it comes out facing the first one rather than the one it was saved against.
	 *
	 * <p>Builds its own schematic rather than using the run's, because both faults need a particular
	 * arrangement of block, torch and wire. Puts the world back the way it found it; the schematic it
	 * loads is replaced by the caller's next load.
	 */
	private static void runWallTorchTests(Minecraft mc) {
		Log.info("SMOKETEST: --- pasting what hangs on a wall ---");
		SchematicaState state = Schematica.STATE;
		World world = mc.world;

		int[][][] blocks = new int[TORCH_TEST_WIDTH][2][TORCH_TEST_LENGTH];
		int[][][] metadata = new int[TORCH_TEST_WIDTH][2][TORCH_TEST_LENGTH];

		// A floor, so the wire has something to lie on and nothing falls through.
		for (int x = 0; x < TORCH_TEST_WIDTH; x++) {
			for (int z = 0; z < TORCH_TEST_LENGTH; z++) {
				blocks[x][0][z] = COBBLESTONE;
			}
		}

		// The redstone torch, its wall one block east of it, and the pair of wires that used to knock
		// it down. Two of them, because one wire on its own tells nobody: a wire landing looks for
		// other wires beside it and tells their neighbours, so it takes the second wire to reach past
		// the first one and ask the torch whether it still has a wall.
		blocks[1][1][1] = REDSTONE_TORCH;
		metadata[1][1][1] = TORCH_AGAINST_EAST;
		blocks[2][1][1] = COBBLESTONE;
		blocks[1][1][2] = REDSTONE_WIRE;
		blocks[1][1][3] = REDSTONE_WIRE;

		// The plain torch, walled on both sides, saved against the eastern one.
		blocks[3][1][3] = COBBLESTONE;
		blocks[4][1][3] = TORCH;
		metadata[4][1][3] = TORCH_AGAINST_EAST;
		blocks[5][1][3] = COBBLESTONE;

		File file = new File(state.getSchematicDirectory(), "smoketest-torch.schematic");
		try {
			SchematicFormat.write(file, new Schematic(blocks, metadata, new ArrayList<>(),
					TORCH_TEST_WIDTH, 2, TORCH_TEST_LENGTH));
		} catch (java.io.IOException exception) {
			fail("could not write the wall torch schematic", exception);
			return;
		}
		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false for the wall torch schematic", null);
			return;
		}

		int pasteX = baseX;
		int pasteY = baseY + PASTE_TEST_HEIGHT;
		int pasteZ = baseZ;

		int[][][] savedBlocks = new int[TORCH_TEST_WIDTH][2][TORCH_TEST_LENGTH];
		int[][][] savedMetadata = new int[TORCH_TEST_WIDTH][2][TORCH_TEST_LENGTH];
		for (int x = 0; x < TORCH_TEST_WIDTH; x++) {
			for (int y = 0; y < 2; y++) {
				for (int z = 0; z < TORCH_TEST_LENGTH; z++) {
					savedBlocks[x][y][z] = world.getBlockId(pasteX + x, pasteY + y, pasteZ + z);
					savedMetadata[x][y][z] = world.method_1778(pasteX + x, pasteY + y, pasteZ + z);
					world.method_201(pasteX + x, pasteY + y, pasteZ + z, 0, 0);
				}
			}
		}

		try {
			state.getActive().offset.set(pasteX, pasteY, pasteZ);
			int dropped = world.method_174(net.minecraft.class_142.class);

			SchematicPaste.write(world, state);

			check("the wall torch is where it was put",
					world.getBlockId(pasteX + 1, pasteY + 1, pasteZ + 1), REDSTONE_TORCH);
			check("on the wall it was saved against",
					world.method_1778(pasteX + 1, pasteY + 1, pasteZ + 1), TORCH_AGAINST_EAST);
			check("the wall itself is there to be on",
					world.getBlockId(pasteX + 2, pasteY + 1, pasteZ + 1), COBBLESTONE);
			check("and nothing was knocked off onto the floor",
					world.method_174(net.minecraft.class_142.class), dropped);

			check("a plain torch walled on both sides is there too",
					world.getBlockId(pasteX + 4, pasteY + 1, pasteZ + 3), TORCH);
			check("facing the wall it was saved against rather than the other one",
					world.method_1778(pasteX + 4, pasteY + 1, pasteZ + 3), TORCH_AGAINST_EAST);
		} finally {
			for (int x = 0; x < TORCH_TEST_WIDTH; x++) {
				for (int y = 0; y < 2; y++) {
					for (int z = 0; z < TORCH_TEST_LENGTH; z++) {
						world.method_201(pasteX + x, pasteY + y, pasteZ + z,
								savedBlocks[x][y][z], savedMetadata[x][y][z]);
					}
				}
			}
			state.getActive().needsUpdate = true;
		}

		Log.info("SMOKETEST: --- pasting what hangs on a wall done ---");
	}

	/**
	 * Pasting a piston that is out - two blocks the game only treats as one machine when it finds
	 * both of them together.
	 *
	 * <p>A piston reads the circuit around it the instant it lands, and an extended one that finds
	 * nothing powering it throws its arm off and pulls itself back in. Written in reading order it
	 * reads too early: the torch holding this one out is one cell further along and has not been
	 * written yet, so the piston came in retracted and its arm landed afterwards on top of it - a
	 * block no player can obtain, with nothing behind it that would ever take it away again. In a
	 * farm with a torch on either side of every piston that is half of them, and which half is
	 * decided by nothing but which side each one's torch happens to be on.
	 *
	 * <p>The second piston here has nothing powering it at all, and is the other half of the same
	 * question: it is meant to pull itself in, and to take its arm with it when it does.
	 *
	 * <p>Builds its own schematic, since both halves need the torch on a particular side. Puts the
	 * world back the way it found it; the schematic it loads is replaced by the caller's next load.
	 */
	private static void runPistonTests(Minecraft mc) {
		Log.info("SMOKETEST: --- pasting a piston that is out ---");
		SchematicaState state = Schematica.STATE;
		World world = mc.world;

		int[][][] blocks = new int[PISTON_TEST_WIDTH][PISTON_TEST_HEIGHT][PISTON_TEST_LENGTH];
		int[][][] metadata = new int[PISTON_TEST_WIDTH][PISTON_TEST_HEIGHT][PISTON_TEST_LENGTH];

		// A floor, for the torch to stand on.
		for (int x = 0; x < PISTON_TEST_WIDTH; x++) {
			for (int z = 0; z < PISTON_TEST_LENGTH; z++) {
				blocks[x][0][z] = COBBLESTONE;
			}
		}

		// A piston held out by a torch on the far side of it, so that reading order arrives at the
		// piston first and at the only thing that answers for it second.
		blocks[1][1][1] = PISTON;
		metadata[1][1][1] = EXTENDED + SIDE_UP;
		blocks[1][2][1] = PISTON_HEAD;
		metadata[1][2][1] = SIDE_UP;
		blocks[1][1][2] = LIT_REDSTONE_TORCH;
		metadata[1][1][2] = TORCH_ON_FLOOR;

		// And one with nothing holding it out at all.
		blocks[3][1][1] = PISTON;
		metadata[3][1][1] = EXTENDED + SIDE_UP;
		blocks[3][2][1] = PISTON_HEAD;
		metadata[3][2][1] = SIDE_UP;

		File file = new File(state.getSchematicDirectory(), "smoketest-piston.schematic");
		try {
			SchematicFormat.write(file, new Schematic(blocks, metadata, new ArrayList<>(),
					PISTON_TEST_WIDTH, PISTON_TEST_HEIGHT, PISTON_TEST_LENGTH));
		} catch (java.io.IOException exception) {
			fail("could not write the piston schematic", exception);
			return;
		}
		if (!state.loadSchematic(file)) {
			fail("loadSchematic returned false for the piston schematic", null);
			return;
		}

		int pasteX = baseX;
		int pasteY = baseY + PASTE_TEST_HEIGHT;
		int pasteZ = baseZ;

		int[][][] savedBlocks = new int[PISTON_TEST_WIDTH][PISTON_TEST_HEIGHT][PISTON_TEST_LENGTH];
		int[][][] savedMetadata = new int[PISTON_TEST_WIDTH][PISTON_TEST_HEIGHT][PISTON_TEST_LENGTH];
		for (int x = 0; x < PISTON_TEST_WIDTH; x++) {
			for (int y = 0; y < PISTON_TEST_HEIGHT; y++) {
				for (int z = 0; z < PISTON_TEST_LENGTH; z++) {
					savedBlocks[x][y][z] = world.getBlockId(pasteX + x, pasteY + y, pasteZ + z);
					savedMetadata[x][y][z] = world.method_1778(pasteX + x, pasteY + y, pasteZ + z);
					world.method_201(pasteX + x, pasteY + y, pasteZ + z, 0, 0);
				}
			}
		}

		try {
			state.getActive().offset.set(pasteX, pasteY, pasteZ);

			SchematicPaste.write(world, state);

			check("the piston whose torch is written after it is still a piston",
					world.getBlockId(pasteX + 1, pasteY + 1, pasteZ + 1), PISTON);
			check("and still out, the way it was saved",
					world.method_1778(pasteX + 1, pasteY + 1, pasteZ + 1), EXTENDED + SIDE_UP);
			check("with its arm on the end of it",
					world.getBlockId(pasteX + 1, pasteY + 2, pasteZ + 1), PISTON_HEAD);
			check("and the torch that holds it out is there to hold it",
					world.getBlockId(pasteX + 1, pasteY + 1, pasteZ + 2), LIT_REDSTONE_TORCH);

			check("a piston with nothing holding it out took its arm in with it",
					world.getBlockId(pasteX + 3, pasteY + 2, pasteZ + 1), 0);
		} finally {
			for (int x = 0; x < PISTON_TEST_WIDTH; x++) {
				for (int y = 0; y < PISTON_TEST_HEIGHT; y++) {
					for (int z = 0; z < PISTON_TEST_LENGTH; z++) {
						world.method_201(pasteX + x, pasteY + y, pasteZ + z,
								savedBlocks[x][y][z], savedMetadata[x][y][z]);
					}
				}
			}
			state.getActive().needsUpdate = true;
		}

		Log.info("SMOKETEST: --- pasting a piston that is out done ---");
	}

	/**
	 * The paste gate with nothing loaded, which is what the button shows at the moment the mod is
	 * first able to open a screen at all. Only ever one answer here, and it is worth saying out loud
	 * that having no world lands on "nothing to paste" rather than on anything about servers.
	 */
	private static void runPasteGateTests(Minecraft mc) {
		Log.info("SMOKETEST: --- paste, with nothing loaded ---");
		checkText("with no schematic there is nothing to paste",
				SchematicPaste.availability(mc).name(), SchematicPaste.Availability.NO_SCHEMATIC.name());
		check("and asking for one anyway is refused", SchematicPaste.paste(mc), SchematicPaste.REFUSED);
	}

	/**
	 * Pasting: what it writes, what it leaves alone, and that the gate really is in front of it.
	 *
	 * <p>The gate is shut here either way - a plain dev run has no creative mode in it at all, and a
	 * run with BHCreative dropped alongside has one and is not in it - so the refusal is checked
	 * through the same call the button makes, and the writing underneath is then driven directly,
	 * which is the only way to reach it at all in the first of those two. What happens with the gate
	 * open is {@link #runCreativePasteTests}, which needs the second.
	 *
	 * <p>The schematic is parked over an emptied box of its own with one block left standing in a
	 * cell it wants nothing in. That block surviving is the whole difference between pasting a build
	 * and pasting a box of air, and it is not something a count of blocks written could show.
	 *
	 * <p>Puts the world and the ghost back where it found them.
	 */
	private static void runPasteTests(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (state.getActive().schematic == null || mc.player == null) {
			Log.info("SMOKETEST: no schematic loaded, skipping the paste checks");
			return;
		}

		Log.info("SMOKETEST: --- paste ---");
		World world = mc.world;
		Schematic schematic = state.getActive().schematic.getSchematic();

		// A plain dev run has no creative mode in it at all, since the mod that adds one is not
		// installed; a run with BHCreative in it has one and is not in it. Both are a greyed-out
		// button with a different line under it, and which one this is decides the rest of the checks.
		boolean creativeMod = CreativeMode.isInstalled();
		Log.info("SMOKETEST: BHCreative is " + (creativeMod ? "installed" : "not installed"));
		checkText("pasting says which of the two it is short of",
				SchematicPaste.availability(mc).name(),
				(creativeMod ? SchematicPaste.Availability.NOT_CREATIVE
						: SchematicPaste.Availability.NO_CREATIVE_MOD).name());

		int width = schematic.getWidth();
		int height = schematic.getHeight();
		int length = schematic.getLength();

		int savedX = state.getActive().offset.x;
		int savedY = state.getActive().offset.y;
		int savedZ = state.getActive().offset.z;
		boolean savedUpdate = state.getActive().needsUpdate;

		// Above the ghost and clear of the structure it was cut from, so nothing in this box belongs
		// to anything else.
		int pasteX = savedX;
		int pasteY = savedY + PASTE_TEST_HEIGHT;
		int pasteZ = savedZ;

		int[][][] savedBlocks = new int[width][height][length];
		int[][][] savedMetadata = new int[width][height][length];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					savedBlocks[x][y][z] = world.getBlockId(pasteX + x, pasteY + y, pasteZ + z);
					savedMetadata[x][y][z] = world.method_1778(pasteX + x, pasteY + y, pasteZ + z);
				}
			}
		}

		try {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					for (int z = 0; z < length; z++) {
						world.method_201(pasteX + x, pasteY + y, pasteZ + z, 0, 0);
					}
				}
			}

			check("the schematic wants nothing in the witness cell", schematic.getBlockId(1, 1, 1), 0);
			world.method_201(pasteX + 1, pasteY + 1, pasteZ + 1, COBBLESTONE, 0);
			state.getActive().offset.set(pasteX, pasteY, pasteZ);

			// The button's own call, which has to refuse: without creative mode there is no paste.
			check("pasting is refused with no creative mode to be in",
					SchematicPaste.paste(mc), SchematicPaste.REFUSED);
			check("and a refusal writes nothing at all",
					world.getBlockId(pasteX, pasteY, pasteZ), 0);

			// The writing on its own, which is what the button would have reached past the gate.
			int placed = SchematicPaste.write(world, state);
			int wanted = 0;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					for (int z = 0; z < length; z++) {
						if (schematic.getBlockId(x, y, z) != 0) {
							wanted++;
						}
					}
				}
			}
			check("the paste wrote every block the schematic asks for", placed, wanted);

			int wrong = 0;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					for (int z = 0; z < length; z++) {
						int id = schematic.getBlockId(x, y, z);
						if (id == 0) {
							continue;
						}
						if (world.getBlockId(pasteX + x, pasteY + y, pasteZ + z) != id
								|| world.method_1778(pasteX + x, pasteY + y, pasteZ + z)
										!= schematic.getMetadata(x, y, z)) {
							wrong++;
						}
					}
				}
			}
			check("and every one of them is standing, turned the way it was", wrong, 0);

			// Named individually as well, so a failure says which part of a schematic stopped coming
			// across rather than only that the count was off.
			check("the floor came across", world.getBlockId(pasteX, pasteY, pasteZ), GOLD);
			check("the stairs came across",
					world.getBlockId(pasteX + 2, pasteY + 1, pasteZ + 2), WOOD_STAIRS);
			check("and the torch is still facing the way it was saved",
					world.method_1778(pasteX + 3, pasteY + 1, pasteZ + 2), worldTorchMetadata);
			check("a cell the schematic leaves empty is left alone",
					world.getBlockId(pasteX + 1, pasteY + 1, pasteZ + 1), COBBLESTONE);

			BlockEntity pasted = world.method_1777(pasteX + 1, pasteY + 1, pasteZ + 4);
			if (pasted instanceof SignBlockEntity) {
				checkText("the sign came across with its text", ((SignBlockEntity) pasted).texts[0], "SMOKE");
				checkText("both lines of it", ((SignBlockEntity) pasted).texts[1], "TEST");
			} else {
				fail("no sign block entity where the schematic has one, got " + pasted, null);
			}

			// The schematic goes on drawing itself out of its own copy: handing a block entity over
			// rather than copying it would empty every sign in the overlay the moment it was pasted.
			BlockEntity ghost = schematic.getBlockEntity(1, 1, 4);
			checkTrue("the schematic kept a sign of its own", ghost instanceof SignBlockEntity && ghost != pasted);
			if (ghost instanceof SignBlockEntity) {
				checkText("with its text still on it", ((SignBlockEntity) ghost).texts[0], "SMOKE");
			}

			if (creativeMod) {
				runCreativePasteTests(mc, pasteX, pasteY, pasteZ, wanted);
			}
		} finally {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					for (int z = 0; z < length; z++) {
						world.method_201(pasteX + x, pasteY + y, pasteZ + z,
								savedBlocks[x][y][z], savedMetadata[x][y][z]);
					}
				}
			}
			state.getActive().offset.set(savedX, savedY, savedZ);
			state.getActive().needsUpdate = savedUpdate;
		}

		runPasteButtonTests(mc, false);
		Log.info("SMOKETEST: --- paste done ---");
	}

	/**
	 * The other side of the gate: what happens when there really is a creative mode and the player
	 * really is in it.
	 *
	 * <p>Only reached in a game with BHCreative installed alongside, which no plain dev run is - this
	 * mod neither ships it nor compiles against it. Install it and this runs, and it is the only
	 * check there is that the bridge to it still reaches: that bridge is a class name and a method
	 * name held in two strings, and nothing about a build would ever notice either going stale.
	 *
	 * <p>Runs with the ghost already parked over the box its caller emptied, and puts creative mode
	 * back off on the way out.
	 */
	private static void runCreativePasteTests(Minecraft mc, int pasteX, int pasteY, int pasteZ, int wanted) {
		SchematicaState state = Schematica.STATE;
		World world = mc.world;
		Schematic schematic = state.getActive().schematic.getSchematic();

		Log.info("SMOKETEST: --- paste, in creative mode ---");
		if (!setCreative(mc, true)) {
			return;
		}

		try {
			checkTrue("BHCreative says the player is in creative mode", CreativeMode.isCreative(mc.player));
			checkText("so pasting is on offer", SchematicPaste.availability(mc).name(),
					SchematicPaste.Availability.READY.name());
			runPasteButtonTests(mc, true);

			// Emptied again, and the witness put back: what the write above left standing would
			// otherwise be answering for this one.
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int y = 0; y < schematic.getHeight(); y++) {
					for (int z = 0; z < schematic.getLength(); z++) {
						world.method_201(pasteX + x, pasteY + y, pasteZ + z, 0, 0);
					}
				}
			}
			world.method_201(pasteX + 1, pasteY + 1, pasteZ + 1, COBBLESTONE, 0);

			// The button's own call again, this time all the way through.
			check("the paste the button makes writes the schematic", SchematicPaste.paste(mc), wanted);
			check("the floor is down", world.getBlockId(pasteX, pasteY, pasteZ), GOLD);
			check("and the cell it wants nothing in is untouched even now",
					world.getBlockId(pasteX + 1, pasteY + 1, pasteZ + 1), COBBLESTONE);

			BlockEntity sign = world.method_1777(pasteX + 1, pasteY + 1, pasteZ + 4);
			if (sign instanceof SignBlockEntity) {
				checkText("with the sign written on", ((SignBlockEntity) sign).texts[0], "SMOKE");
			} else {
				fail("no sign block entity after a creative paste, got " + sign, null);
			}
		} finally {
			setCreative(mc, false);
		}

		checkText("and stepping back out of creative mode closes it again",
				SchematicPaste.availability(mc).name(), SchematicPaste.Availability.NOT_CREATIVE.name());
		Log.info("SMOKETEST: --- paste, in creative mode done ---");
	}

	/**
	 * Steps the player in or out of creative mode through BHCreative's own setter - the same door
	 * the mod itself opens it with. Test-only: reading the flag is the mod's business, setting it is
	 * not, so this lives here rather than in {@code compat/CreativeMode}.
	 */
	private static boolean setCreative(Minecraft mc, boolean creative) {
		try {
			Class<?> creativePlayer = Class.forName("paulevs.bhcreative.interfaces.CreativePlayer");
			creativePlayer.getMethod("creative_setCreative", boolean.class).invoke(mc.player, creative);
			return true;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail("could not step the player " + (creative ? "into" : "out of")
					+ " creative mode through BHCreative", exception);
			return false;
		}
	}

	/**
	 * The button the player actually meets this on. Rendered for real rather than asked, because
	 * whether it is live is settled by the frame rather than by the screen being opened - and because
	 * the tooltip under it is drawing code that only ever runs with a mouse over the button, which no
	 * screenshot can be made to arrange.
	 */
	private static void runPasteButtonTests(Minecraft mc, boolean expected) {
		Screen before = mc.currentScreen;
		SchematicControlScreen screen = new SchematicControlScreen();
		mc.setScreen(screen);

		ButtonWidget paste = buttonNamed(screen, Translations.get("schematic.paste"));
		if (paste == null) {
			fail("the move screen has no paste button on it", null);
			mc.setScreen(before);
			return;
		}

		checkTrue("opening the move screen leaves the paste button alone", paste.active);
		// Over the button, so the tooltip is drawn as well as the button greyed.
		screen.render(paste.x + 1, paste.y + 1, 0.0F);
		checkTrue(expected
				? "and a frame in creative mode leaves it live"
				: "and a frame that cannot paste greys it out", paste.active == expected);

		mc.setScreen(before);
	}

	/** The first button on a screen carrying this label, or null when there is none. */
	private static ButtonWidget buttonNamed(Screen screen, String text) {
		try {
			java.lang.reflect.Field field = Screen.class.getDeclaredField("buttons");
			field.setAccessible(true);
			for (Object button : (List<?>) field.get(screen)) {
				if (button instanceof ButtonWidget && text.equals(((ButtonWidget) button).text)) {
					return (ButtonWidget) button;
				}
			}
			return null;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail("could not reach the buttons on " + screen.getClass().getSimpleName(), exception);
			return null;
		}
	}

	/**
	 * The coordinate rows, driven the way a player drives them. Both ways into a coordinate are
	 * checked: the field, which is new, and the +/- buttons, whose step the row now applies itself.
	 */
	private static void runCoordinateFieldTests(Minecraft mc) {
		Log.info("SMOKETEST: --- coordinate entry ---");

		SchematicaState state = Schematica.STATE;
		Vec3i savedOffset = state.getActive().offset.copy();
		Vec3i savedPointA = state.pointA.copy();
		Vec3i savedPointB = state.pointB.copy();

		// Set before the screen is built: the rows read their starting value as they are laid out.
		state.getActive().offset.set(0, 4, 0);

		SchematicControlScreen control = new SchematicControlScreen();
		mc.setScreen(control);

		control.handleTab();
		type(control, "a12b"); // letters never reach the field
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("typed X offset", state.getActive().offset.x, 12);

		// Backspace rubs out one digit rather than the whole number. The field is showing an applied
		// 12 and nothing is drawn as selected, so taking all of it away would look like it was eaten.
		pressKey(control, '\b', Keyboard.KEY_BACK);
		type(control, "5");
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("backspace takes a digit off rather than the lot", state.getActive().offset.x, 15);

		// And once a digit has gone the next one still types onto the end rather than over it.
		pressKey(control, '\b', Keyboard.KEY_BACK);
		pressKey(control, '\b', Keyboard.KEY_BACK);
		type(control, "40");
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("backspacing the number away leaves an empty field to type into", state.getActive().offset.x, 40);

		// Nothing left to rub out, and Enter on an empty field keeps what was applied.
		pressKey(control, '\b', Keyboard.KEY_BACK);
		pressKey(control, '\b', Keyboard.KEY_BACK);
		pressKey(control, '\b', Keyboard.KEY_BACK);
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("and an empty field applied leaves the value alone", state.getActive().offset.x, 40);

		type(control, "12");
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("typing over an applied value still replaces it", state.getActive().offset.x, 12);

		type(control, "999");
		pressKey(control, (char) 27, Keyboard.KEY_ESCAPE);
		check("escape abandons the edit", state.getActive().offset.x, 12);

		// Escape also gave the field up, so the tab order starts from the top again.
		control.handleTab();
		control.handleTab();
		type(control, "-5");
		type(control, "\t"); // tab applies this row and moves to the next
		check("typed Y offset", state.getActive().offset.y, -5);

		type(control, "7");
		pressKey(control, '\r', Keyboard.KEY_RETURN);
		check("typed Z offset", state.getActive().offset.z, 7);

		ButtonWidget increaseX = buttonAt(control, 2);
		if (increaseX != null) {
			clickButton(control, increaseX);
			check("the + button still steps the coordinate", state.getActive().offset.x, 13);
		}

		// The mouse route, which is how anyone actually gets into a field. The row's [-] button is
		// the anchor rather than a copy of the layout maths: the field ends a few pixels before it.
		ButtonWidget decreaseZ = buttonAt(control, 6);
		if (decreaseZ != null) {
			clickAt(control, decreaseZ.x - 10, decreaseZ.y + 10);
			type(control, "64");
			clickAt(control, 2, 2);
			check("clicking a value types into it, clicking away applies it", state.getActive().offset.z, 64);
		}

		// The same rows on the save screen, bound to the two corners instead.
		state.pointA.set(0, 4, 0);
		state.pointB.set(1, 4, 1);
		state.updatePoints();

		SchematicSaveScreen save = new SchematicSaveScreen();
		mc.setScreen(save);

		// Red X, red Y, red Z, then blue X.
		for (int i = 0; i < 4; i++) {
			save.handleTab();
		}
		type(save, "20");
		pressKey(save, '\r', Keyboard.KEY_RETURN);
		check("typed blue point X", state.pointB.x, 20);
		check("the selection followed the typed corner", state.pointMax.x, 20);

		mc.setScreen(null);
		state.getActive().offset.set(savedOffset.x, savedOffset.y, savedOffset.z);
		state.pointA.set(savedPointA.x, savedPointA.y, savedPointA.z);
		state.pointB.set(savedPointB.x, savedPointB.y, savedPointB.z);
		state.updatePoints();

		Log.info("SMOKETEST: --- coordinate entry done ---");
	}

	/**
	 * Leaves a coordinate half typed on whatever control screen is open, so the shot after this one
	 * shows the field mid-edit: the cursor, and the value in the colour that says it is not applied
	 * yet. Nothing commits it, so the schematic stays where it is.
	 */
	private static void typeDraft(Minecraft mc) {
		if (!(mc.currentScreen instanceof SchematicControlScreen)) {
			fail("expected the control screen to still be open, got " + mc.currentScreen, null);
			return;
		}

		SchematicControlScreen screen = (SchematicControlScreen) mc.currentScreen;
		int before = Schematica.STATE.getActive().offset.x;
		screen.handleTab();
		type(screen, "-120");
		checkTrue("a half-typed coordinate has not moved anything", Schematica.STATE.getActive().offset.x == before);
	}

	private static void type(Screen screen, String text) {
		for (int i = 0; i < text.length(); i++) {
			// Only backspace and enter are read off the key code, so the character is enough here.
			pressKey(screen, text.charAt(i), Keyboard.KEY_NONE);
		}
	}

	/** Everything a screen reacts to is protected, so the harness has to knock rather than call. */
	private static void pressKey(Screen screen, char character, int keyCode) {
		try {
			java.lang.reflect.Method method = Screen.class.getDeclaredMethod("keyPressed", char.class, int.class);
			method.setAccessible(true);
			method.invoke(screen, character, keyCode);
		} catch (ReflectiveOperationException exception) {
			fail("could not send a key to " + screen.getClass().getSimpleName(), exception);
		}
	}

	private static void clickAt(Screen screen, int mouseX, int mouseY) {
		try {
			java.lang.reflect.Method method = Screen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
			method.setAccessible(true);
			method.invoke(screen, mouseX, mouseY, 0);
		} catch (ReflectiveOperationException exception) {
			fail("could not click " + screen.getClass().getSimpleName() + " at " + mouseX + ", " + mouseY, exception);
		}
	}

	private static void clickButton(Screen screen, ButtonWidget button) {
		try {
			java.lang.reflect.Method method = Screen.class.getDeclaredMethod("buttonClicked", ButtonWidget.class);
			method.setAccessible(true);
			method.invoke(screen, button);
		} catch (ReflectiveOperationException exception) {
			fail("could not click a button on " + screen.getClass().getSimpleName(), exception);
		}
	}

	/**
	 * The first widget on a screen of a given class, by name. Which button a screen holds where is
	 * not something a test should have to count, and the picker is the one widget on the move screen
	 * that is not an ordinary button.
	 */
	private static ButtonWidget widgetNamed(Screen screen, String simpleName) {
		try {
			java.lang.reflect.Field field = Screen.class.getDeclaredField("buttons");
			field.setAccessible(true);
			for (Object button : (List<?>) field.get(screen)) {
				if (button.getClass().getSimpleName().equals(simpleName)) {
					return (ButtonWidget) button;
				}
			}
			return null;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail("could not look for a " + simpleName + " on " + screen.getClass().getSimpleName(), exception);
			return null;
		}
	}

	private static ButtonWidget buttonAt(Screen screen, int index) {
		try {
			java.lang.reflect.Field field = Screen.class.getDeclaredField("buttons");
			field.setAccessible(true);
			return (ButtonWidget) ((List<?>) field.get(screen)).get(index);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail("could not reach button " + index + " on " + screen.getClass().getSimpleName(), exception);
			return null;
		}
	}

	private static boolean openModList(Minecraft mc) {
		Class<?> screenClass;
		try {
			screenClass = Class.forName("io.github.prospector.modmenu.gui.ModListScreen");
		} catch (ClassNotFoundException exception) {
			return false;
		}

		try {
			Screen screen = (Screen) screenClass.getConstructor(Screen.class).newInstance(mc.currentScreen);
			mc.setScreen(screen);
			highlightSchematica(screenClass, screen);
			return true;
		} catch (ReflectiveOperationException exception) {
			fail("could not open the mod menu list", exception);
			return false;
		}
	}

	/**
	 * Scrolls the mod list to this mod and selects it, so the screenshot shows the entry and its
	 * Configure button rather than whatever happens to sort first. Presentation only - a failure
	 * here says nothing about the integration, which is checked in {@link #runModMenuTests}.
	 */
	private static void highlightSchematica(Class<?> screenClass, Screen screen) {
		try {
			java.lang.reflect.Field field = screenClass.getDeclaredField("modList");
			field.setAccessible(true);
			Object list = field.get(screen);

			Class<?> entryClass = Class.forName("io.github.prospector.modmenu.gui.ModListEntry");
			for (Object entry : (List<?>) list.getClass().getMethod("children").invoke(list)) {
				ModMetadata metadata = (ModMetadata) entryClass.getMethod("getMetadata").invoke(entry);
				if (Schematica.MOD_ID.equals(metadata.getId())) {
					list.getClass().getMethod("select", entryClass).invoke(list, entry);
					list.getClass().getMethod("setScrollAmount", double.class).invoke(list, 1000.0);
					return;
				}
			}
			Log.warn("SMOKETEST: the mod list has no schematica entry");
		} catch (ReflectiveOperationException | RuntimeException exception) {
			Log.warn("SMOKETEST: could not select the schematica entry", exception);
		}
	}

	private static void screenshot(Minecraft mc, int index) {
		String result = class_260.method_908(Minecraft.getRunDirectory(), mc.displayWidth, mc.displayHeight);
		if (result.startsWith("Failed")) {
			fail("screenshot " + index + ": " + result, null);
		} else {
			Log.info("SMOKETEST: scene " + index + " - " + result);
		}
	}

	private static void finish(Minecraft mc) {
		phase = Phase.DONE;
		Log.info("SMOKETEST: " + (failures == 0 ? "PASS" : "FAIL (" + failures + " problem(s))"));
		mc.scheduleStop();
	}

	private static void check(String what, int actual, int expected) {
		if (actual != expected) {
			fail(what + ": expected " + expected + " but was " + actual, null);
		} else {
			Log.info("SMOKETEST: ok - " + what + " = " + actual);
		}
	}

	private static void checkTrue(String what, boolean actual) {
		if (!actual) {
			fail(what, null);
		} else {
			Log.info("SMOKETEST: ok - " + what);
		}
	}

	private static void checkText(String what, String actual, String expected) {
		if (!expected.equals(actual)) {
			fail(what + ": expected \"" + expected + "\" but was \"" + actual + "\"", null);
		} else {
			Log.info("SMOKETEST: ok - " + what + " = " + actual);
		}
	}

	private static void fail(String message, Throwable throwable) {
		failures++;
		if (throwable != null) {
			Log.error("SMOKETEST: " + message, throwable);
		} else {
			Log.error("SMOKETEST: " + message);
		}
	}

	/**
	 * Placing where there is nothing to build against. Vanilla cannot do this at all - a click has
	 * to land on a block - so all of it is easy place working out for itself what the player is
	 * looking at, and every check goes through the client's own click handler rather than asking the
	 * mod what it would do.
	 *
	 * <p>The player is stood three blocks west of the gap looking straight along it, with everything
	 * between them cleared out, so the only thing on the line of sight is the gap itself. Puts the
	 * world, the schematic and the player back the way it found them.
	 */
	private static void runMidairTests(Minecraft mc, int x, int y, int z) {
		SchematicaState state = Schematica.STATE;
		Schematic schematic = state.getActive().schematic.getSchematic();
		World world = mc.world;
		PlayerInventory inventory = mc.player.inventory;

		// The line of sight runs east into the gap, so the blocks the player stands among and every
		// one between them and the gap have to be out of the way - above and below it as well, since
		// what is around a position decides whether a block can stand in it.
		int fromX = x - 3;
		int toX = x + 1;
		int span = (toX - fromX + 1) * 3 * 3;
		int[] savedBlocks = new int[span];
		int[] savedMetadata = new int[span];
		int index = 0;
		for (int bx = fromX; bx <= toX; bx++) {
			for (int by = y - 1; by <= y + 1; by++) {
				for (int bz = z - 1; bz <= z + 1; bz++) {
					savedBlocks[index] = world.getBlockId(bx, by, bz);
					savedMetadata[index] = world.method_1778(bx, by, bz);
					index++;
				}
			}
		}

		// Local x 0 to 3 is the same run of the schematic; nothing but the gap itself is wanted, or
		// a block on the way in would be built before the one being aimed at.
		int[] savedWanted = new int[4];
		int[] savedWantedMetadata = new int[4];
		for (int local = 0; local < savedWanted.length; local++) {
			savedWanted[local] = schematic.getBlockId(local, 0, 2);
			savedWantedMetadata[local] = schematic.getMetadata(local, 0, 2);
		}

		ItemStack[] savedInventory = new ItemStack[inventory.main.length];
		System.arraycopy(inventory.main, 0, savedInventory, 0, savedInventory.length);
		int savedSlot = inventory.selectedSlot;
		class_27 savedHit = mc.field_2823;
		double savedPlayerX = mc.player.x;
		double savedPlayerY = mc.player.y;
		double savedPlayerZ = mc.player.z;
		float savedYaw = mc.player.yaw;
		float savedPitch = mc.player.pitch;

		try {
			Log.info("SMOKETEST: --- easy place in mid air ---");

			for (int bx = fromX; bx <= toX; bx++) {
				for (int by = y - 1; by <= y + 1; by++) {
					for (int bz = z - 1; bz <= z + 1; bz++) {
						world.method_201(bx, by, bz, 0, 0);
					}
				}
			}
			for (int local = 0; local < savedWanted.length; local++) {
				schematic.setBlockId(local, 0, 2, 0);
				schematic.setMetadata(local, 0, 2, 0);
			}
			schematic.setBlockId(2, 0, 2, GOLD);

			// Eye height is the middle of the gap's own layer, so the line of sight runs flat into it.
			// Yaw -90 is due east, which is the +x the gap lies along.
			// This takes the feet rather than the eye - it adds the player's own eye offset on the way
			// through - and everything about aiming is measured from the eye. So it is asked for the
			// height that is wanted, and then asked again for that height less however far it moved.
			mc.player.method_1341(x - 2.5, y + 0.5, z + 0.5, -90.0F, 0.0F);
			double eyeOffset = mc.player.y - (y + 0.5);
			mc.player.method_1341(x - 2.5, y + 0.5 - eyeOffset, z + 0.5, -90.0F, 0.0F);
			checkTrue("the aim is level with the middle of the gap", Math.abs(mc.player.y - (y + 0.5)) < 0.001);
			mc.field_2823 = null;
			// The one thing here that cannot be read back from a check: what the mod is actually
			// looking along. A wrong eye or a wrong camera would fail every mid-air check at once
			// and look exactly like the feature not working.
			Vec3d aimFrom = mc.player.method_931(1.0F);
			Vec3d aimAlong = mc.player.method_926(1.0F);
			Log.info("SMOKETEST: aiming from " + aimFrom.x + ", " + aimFrom.y + ", " + aimFrom.z
					+ " along " + aimAlong.x + ", " + aimAlong.y + ", " + aimAlong.z
					+ " reaching " + mc.interactionManager.method_1715()
					+ ", camera " + (mc.field_2807 == mc.player ? "is the player" : "is " + mc.field_2807)
					+ ", gap at " + x + ", " + y + ", " + z);

			clearInventory(inventory);
			inventory.main[MIDAIR_GOLD_SLOT] = new ItemStack(GOLD, 64, 0);
			inventory.main[MIDAIR_REDSTONE_SLOT] = new ItemStack(REDSTONE_DUST, 64, 0);
			inventory.main[MIDAIR_SAND_SLOT] = new ItemStack(SAND, 64, 0);
			// Empty handed, and nothing being aimed at: everything below is easy place alone.
			inventory.selectedSlot = HOTBAR_SIZE - 1;
			HotbarRestock.reset();

			checkTrue("nothing is holding the gap up", world.getBlockId(x, y - 1, z) == 0);
			checkTrue("and there is nothing beside it to build against",
					world.getBlockId(x - 1, y, z) == 0 && world.getBlockId(x + 1, y, z) == 0
							&& world.getBlockId(x, y, z - 1) == 0 && world.getBlockId(x, y, z + 1) == 0
							&& world.getBlockId(x, y + 1, z) == 0);
			checkTrue("so vanilla has nothing to aim at", mc.field_2823 == null);

			rightClick(mc);
			check("a block with nothing to build against is placed in mid air", world.getBlockId(x, y, z), GOLD);
			check("out of the slot the schematic asked for", inventory.selectedSlot, MIDAIR_GOLD_SLOT);
			world.method_201(x, y, z, 0, 0);

			// The same click along the same line, with the schematic no longer asking for anything.
			schematic.setBlockId(2, 0, 2, 0);
			rightClick(mc);
			check("and nothing at all where the schematic wants nothing", world.getBlockId(x, y, z), 0);

			// Redstone would drop off the moment it was put down, so the position waits for its floor.
			// The dust is on the bar throughout: what refuses the placement is the block, not the want
			// of something to place it with.
			schematic.setBlockId(2, 0, 2, REDSTONE_WIRE);
			rightClick(mc);
			check("a block that needs something under it is left for its floor", world.getBlockId(x, y, z), 0);

			// Sand would place quite happily and then fall straight back out of the air.
			schematic.setBlockId(2, 0, 2, SAND);
			rightClick(mc);
			check("a block that would fall is not left hanging", world.getBlockId(x, y, z), 0);

			world.method_201(x, y - 1, z, COBBLESTONE, 0);
			rightClick(mc);
			check("but goes down once something is holding it", world.getBlockId(x, y, z), SAND);
			world.method_201(x, y, z, 0, 0);
			world.method_201(x, y - 1, z, 0, 0);

			// A block in the way ends the line of sight, exactly as it does for vanilla's own ray.
			schematic.setBlockId(2, 0, 2, GOLD);
			world.method_201(x - 1, y, z, COBBLESTONE, 0);
			rightClick(mc);
			check("nothing is built through a block in the way", world.getBlockId(x, y, z), 0);

			// And with a face to aim at, the click vanilla can make itself is left to it: the same
			// block ends up in the same place, but by the ordinary path rather than this one.
			mc.field_2823 = hitResult(x - 1, y, z, SIDE_EAST);
			checkTrue("a position an ordinary click can reach is left to it", !EasyPlace.interceptUse(mc));
			check("with the block it wants already in hand", inventory.selectedSlot, MIDAIR_GOLD_SLOT);
			rightClick(mc);
			check("and that click builds it", world.getBlockId(x, y, z), GOLD);
		} finally {
			index = 0;
			for (int bx = fromX; bx <= toX; bx++) {
				for (int by = y - 1; by <= y + 1; by++) {
					for (int bz = z - 1; bz <= z + 1; bz++) {
						world.method_201(bx, by, bz, savedBlocks[index], savedMetadata[index]);
						index++;
					}
				}
			}
			for (int local = 0; local < savedWanted.length; local++) {
				schematic.setBlockId(local, 0, 2, savedWanted[local]);
				schematic.setMetadata(local, 0, 2, savedWantedMetadata[local]);
			}
			System.arraycopy(savedInventory, 0, inventory.main, 0, savedInventory.length);
			inventory.selectedSlot = savedSlot;
			mc.field_2823 = savedHit;
			mc.player.method_1341(savedPlayerX, savedPlayerY, savedPlayerZ, savedYaw, savedPitch);
			HotbarRestock.reset();
			state.getActive().needsUpdate = true;
		}

		Log.info("SMOKETEST: --- easy place in mid air done ---");
	}
}
