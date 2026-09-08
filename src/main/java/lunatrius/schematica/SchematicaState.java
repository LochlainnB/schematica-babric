package lunatrius.schematica;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicFormat;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3f;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Everything the mod knows about the current session: the schematics that are open, where each one
 * sits in the world, the selection used for saving, and the render toggles the GUIs drive.
 *
 * <p>Several schematics can be open at once and all of them are drawn. One of them is the active
 * one - what the move screen's controls are pointed at, what the material list counts, and what a
 * paste writes - and the picker at the top of that screen is how it is chosen. Everything that is
 * about a single build lives in {@link OpenSchematic}; what is left here is either about the player
 * (where the camera is, which way it faces), about the save selection, or about the set as a whole.
 *
 * <p>There is always at least one slot, empty or not, so "no schematic" is a slot with nothing in
 * it rather than a list with nothing in it. {@link #getActive()} therefore always has something to
 * return, and the screens ask that whether one schematic is open or six.
 */
public class SchematicaState {
	/** Offsets the +/- buttons in the GUIs cycle through. */
	public final int[] increments = { 1, 5, 15, 50, 250 };

	/**
	 * How many schematics may be open at once.
	 *
	 * <p>A cap rather than none of them: each open schematic holds its blocks in memory and a run
	 * of display lists in the driver, and the picker that chooses between them has to fit on the
	 * screen next to everything else the move screen draws. Eight is more than a build has ever
	 * needed and still fits both.
	 */
	public static final int MAX_OPEN = 8;

	/** The open schematics, in the order they were opened. Never empty; entries may be. */
	private final List<OpenSchematic> open = new ArrayList<>();
	/** Handed out by {@link #getOpen()}, which the renderer asks for twice a frame. */
	private final List<OpenSchematic> openView = Collections.unmodifiableList(this.open);

	/** Which of {@link #open} the controls are pointed at. Always a valid index. */
	private int active = 0;

	/** Camera position, interpolated for the current frame. */
	public final Vec3f playerPosition = new Vec3f();
	/** Which of the four cardinal directions the player faces, 0-3. */
	public int rotationRender = 0;

	/** The two corners of the save selection. */
	public final Vec3i pointA = new Vec3i();
	public final Vec3i pointB = new Vec3i();
	public final Vec3i pointMin = new Vec3i();
	public final Vec3i pointMax = new Vec3i();

	public int selectedSchematic = 0;

	public boolean isRenderingGuide = false;

	/**
	 * Easy place: a right click may only put down the block a schematic wants where it wants it.
	 * Session state rather than a setting, like the render toggles - it is a mode the player steps
	 * in and out of while building, not something to come back to a week later.
	 */
	public boolean isEasyPlace = false;

	public SchematicaState() {
		this.open.add(new OpenSchematic());
	}

	public Minecraft getMinecraft() {
		return Schematica.getMinecraft();
	}

	public File getSchematicDirectory() {
		return Schematica.getSchematicDirectory();
	}

	// --- the open schematics -------------------------------------------------------------------

	/** Every open slot, in the order they were opened. Read-only; use the methods below to change. */
	public List<OpenSchematic> getOpen() {
		return this.openView;
	}

	/** The one the controls are pointed at. Never null - an empty slot is still a slot. */
	public OpenSchematic getActive() {
		return this.open.get(this.active);
	}

	public int getActiveIndex() {
		return this.active;
	}

	/** Points the controls at another slot. Out of range values are ignored rather than clamped. */
	public void setActiveIndex(int index) {
		if (index >= 0 && index < this.open.size()) {
			this.active = index;
		}
	}

	/** How many slots there are, empty ones included. */
	public int getOpenCount() {
		return this.open.size();
	}

	/** How many of them actually hold a schematic. */
	public int getLoadedCount() {
		int count = 0;
		for (OpenSchematic schematic : this.open) {
			if (!schematic.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/** Whether another schematic can be opened without closing one first. */
	public boolean canOpenAnother() {
		return this.getActive().isEmpty() || this.open.size() < MAX_OPEN;
	}

	/**
	 * Whether any open schematic is being drawn, which is what the show/hide key asks before
	 * deciding which way to go.
	 */
	public boolean isAnyRendering() {
		for (OpenSchematic schematic : this.open) {
			if (schematic.schematic != null && schematic.isRenderingSchematic) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The schematic that has something to say about a world position: the one covering it, active
	 * first, and only ones being drawn.
	 *
	 * <p>Active first because two schematics may be standing in the same place - the same build
	 * loaded twice while it is being lined up - and the one the controls are pointed at is the one
	 * the player is working on. Hidden ones are skipped for the same reason easy place is off while
	 * the overlay is hidden: what is not drawn is not being built.
	 */
	public OpenSchematic schematicAt(int x, int y, int z) {
		OpenSchematic active = this.getActive();
		if (active.isRenderingSchematic && active.covers(x, y, z)) {
			return active;
		}

		for (OpenSchematic schematic : this.open) {
			if (schematic.isRenderingSchematic && schematic.covers(x, y, z)) {
				return schematic;
			}
		}
		return null;
	}

	/**
	 * Opens a schematic alongside whatever is already open and points the controls at it. The
	 * active slot is used when it is empty, so opening one thing into an empty world does not leave
	 * a spare slot behind it; otherwise a slot is added.
	 *
	 * @return false when the file would not load, or when {@link #MAX_OPEN} are already open.
	 */
	public boolean openSchematic(File file) {
		if (this.getActive().isEmpty()) {
			return this.loadSchematic(file);
		}
		if (this.open.size() >= MAX_OPEN) {
			Log.warn("Already holding " + MAX_OPEN + " schematics; close one before opening " + file.getName());
			return false;
		}

		int previous = this.active;
		this.open.add(new OpenSchematic());
		this.active = this.open.size() - 1;
		if (this.loadSchematic(file)) {
			return true;
		}

		// It never loaded, so the slot it was given is an empty one nobody asked for, and the
		// controls go back to whatever they were on rather than to whatever is left at the end.
		this.open.remove(this.open.size() - 1);
		this.active = previous;
		return false;
	}

	/**
	 * Loads a schematic into the active slot, replacing whatever was in it. This is the one that
	 * moves nothing else around: {@link #openSchematic} is what a player asking for another
	 * schematic gets.
	 */
	public boolean loadSchematic(File file) {
		World world = this.getMinecraft().world;
		if (world == null) {
			return false;
		}
		return this.getActive().load(world, file);
	}

	/** Empties the active slot without taking it away. */
	public void clearSchematic() {
		this.getActive().close();
	}

	/**
	 * Closes the active slot and points the controls at whatever takes its place - the next one
	 * along, or the one before it when what was closed was last in the set. Closing the only one
	 * leaves an empty slot rather than nothing, because the controls have to point somewhere.
	 */
	public void closeActive() {
		if (this.open.size() <= 1) {
			this.getActive().close();
			return;
		}

		this.open.remove(this.active);
		if (this.active >= this.open.size()) {
			this.active = this.open.size() - 1;
		}
	}

	/** Closes every schematic, leaving the one empty slot a fresh session starts with. */
	public void closeAll() {
		for (OpenSchematic schematic : this.open) {
			schematic.close();
		}
		this.open.clear();
		this.open.add(new OpenSchematic());
		this.active = 0;
	}

	/** Marks every open schematic for a full rebuild. */
	public void invalidateAll() {
		for (OpenSchematic schematic : this.open) {
			schematic.needsUpdate = true;
		}
	}

	// --- what the controls do to the active schematic ------------------------------------------

	/** The file the active schematic came from, or null with nothing in the slot. */
	public String getLoadedName() {
		return this.getActive().getLoadedName();
	}

	public MaterialList getMaterials() {
		return this.getActive().getMaterials();
	}

	/** Recounts the pack against the active schematic's list, which is the half of it that moves. */
	public void countMaterials() {
		Minecraft mc = this.getMinecraft();
		PlayerEntity player = mc == null ? null : mc.player;
		this.getActive().countMaterials(player == null ? null : player.inventory.main);
	}

	/** Places the active schematic in front of the player, aligned to the way they are facing. */
	public void moveHere() {
		this.getActive().moveHere(this.playerPosition, this.rotationRender);
	}

	public void toggleRendering() {
		this.getActive().toggleRendering();
	}

	/**
	 * Shows or hides every open schematic at once: hides them all if any one of them is showing,
	 * and otherwise brings all of them back.
	 *
	 * <p>What the key does, as against the move screen's Hide button, which is pointed at one
	 * schematic like the rest of that screen. The key is the one you reach for when the ghosts are
	 * in the way of seeing what you have built, and half of them going is no use then.
	 */
	public void toggleAllRendering() {
		boolean show = !this.isAnyRendering();
		for (OpenSchematic schematic : this.open) {
			schematic.isRenderingSchematic = show && schematic.schematic != null;
		}
	}

	public void mirrorSchematic() {
		this.getActive().mirrorSchematic();
	}

	public void rotateSchematic() {
		this.getActive().rotateSchematic();
	}

	public int getTurns() {
		return this.getActive().getTurns();
	}

	public boolean isMirrored() {
		return this.getActive().isMirrored();
	}

	public void setOrientation(int turns, boolean mirrored) {
		this.getActive().setOrientation(turns, mirrored);
	}

	public void resetRenderingLayer() {
		this.getActive().resetRenderingLayer();
	}

	public int getMaxRenderingLayer() {
		return this.getActive().getMaxRenderingLayer();
	}

	public void setRenderingLayer(int layer) {
		this.getActive().setRenderingLayer(layer);
	}

	// --- schematic files -----------------------------------------------------------------------

	/**
	 * The entries shown in the load list: index 0 is the "no schematic" placeholder, the rest are
	 * file names.
	 */
	public List<String> getSchematicFiles() {
		List<String> names = new ArrayList<>();
		names.add(Translations.get("schematic.noschematic"));

		File[] files = this.getSchematicDirectory().listFiles(SchematicFileFilter.INSTANCE);
		if (files != null) {
			List<String> fileNames = new ArrayList<>(files.length);
			for (File file : files) {
				fileNames.add(file.getName());
			}
			Collections.sort(fileNames, String.CASE_INSENSITIVE_ORDER);
			names.addAll(fileNames);
		}

		return names;
	}

	/**
	 * Captures the world between two corners (inclusive) and writes it out as a schematic.
	 */
	public boolean saveSchematic(File file, Vec3i from, Vec3i to) {
		World world = this.getMinecraft().world;
		if (world == null) {
			return false;
		}

		try {
			int minX = Math.min(from.x, to.x);
			int maxX = Math.max(from.x, to.x);
			int minY = Math.max(0, Math.min(from.y, to.y));
			int maxY = Math.min(127, Math.max(from.y, to.y));
			int minZ = Math.min(from.z, to.z);
			int maxZ = Math.max(from.z, to.z);

			if (maxY < minY) {
				Log.warn("Selection is entirely outside the world's height range.");
				return false;
			}

			int width = maxX - minX + 1;
			int height = maxY - minY + 1;
			int length = maxZ - minZ + 1;

			int[][][] blocks = new int[width][height][length];
			int[][][] metadata = new int[width][height][length];
			List<BlockEntity> blockEntities = new ArrayList<>();

			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					for (int z = minZ; z <= maxZ; z++) {
						blocks[x - minX][y - minY][z - minZ] = world.getBlockId(x, y, z);
						metadata[x - minX][y - minY][z - minZ] = world.method_1778(x, y, z);

						BlockEntity source = world.method_1777(x, y, z);
						if (source == null) {
							continue;
						}

						// Round-trip through NBT so the copy is detached from the live world.
						NbtCompound nbt = new NbtCompound();
						source.writeNbt(nbt);
						BlockEntity copy = BlockEntity.method_1068(nbt);
						if (copy != null) {
							copy.x -= minX;
							copy.y -= minY;
							copy.z -= minZ;
							blockEntities.add(copy);
						}
					}
				}
			}

			Schematic schematic = new Schematic(blocks, metadata, blockEntities, width, height, length);
			SchematicFormat.write(file, schematic);
			Log.info("Saved " + file.getName() + " (" + width + "x" + height + "x" + length + ")");
			return true;
		} catch (IOException | RuntimeException exception) {
			Log.error("Failed to save schematic " + file.getName(), exception);
			return false;
		}
	}

	// --- the save selection --------------------------------------------------------------------

	public void updatePoints() {
		this.pointMin.set(
				Math.min(this.pointA.x, this.pointB.x),
				Math.min(this.pointA.y, this.pointB.y),
				Math.min(this.pointA.z, this.pointB.z));
		this.pointMax.set(
				Math.max(this.pointA.x, this.pointB.x),
				Math.max(this.pointA.y, this.pointB.y),
				Math.max(this.pointA.z, this.pointB.z));
	}

	/** Drops a selection corner at the block the player is standing on, nudged away from them. */
	public void moveHere(Vec3i point) {
		point.set(
				(int) Math.floor(this.playerPosition.x),
				(int) Math.floor(this.playerPosition.y - 1.0F),
				(int) Math.floor(this.playerPosition.z));

		switch (this.rotationRender) {
			case 0: point.x--; point.z++; break;
			case 1: point.x--; point.z--; break;
			case 2: point.x++; point.z--; break;
			default: point.x++; point.z++; break;
		}
	}

	/**
	 * Turns easy place on or off. Allowed with nothing loaded: without a schematic the mode simply
	 * has nothing to hold clicks against, and it is still the mode the player asked for.
	 */
	public void toggleEasyPlace() {
		this.isEasyPlace = !this.isEasyPlace;
	}

	// --- the world underneath ------------------------------------------------------------------

	/** Hands a single block change to every open schematic; each decides whether it covers it. */
	public void onWorldBlockChanged(int x, int y, int z) {
		for (int i = 0; i < this.open.size(); i++) {
			this.open.get(i).onWorldBlockChanged(x, y, z);
		}
	}

	/** The same for a chunk of world arriving from a server. */
	public void onWorldChunkLoaded(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			return;
		}
		for (int i = 0; i < this.open.size(); i++) {
			this.open.get(i).onWorldChunkLoaded(x, y, z, sizeX, sizeY, sizeZ);
		}
	}

	/**
	 * Called for several blocks in one chunk changing at once, which is what a server sends instead
	 * of a packet each whenever more than one block in a chunk moves in a tick. With other people
	 * building alongside you that is most ticks, and none of it comes through
	 * {@link #onWorldBlockChanged}: these go straight into the chunk without the world ever being
	 * told block by block, so without this the overlay goes stale as soon as anyone else builds.
	 *
	 * <p>Positions arrive packed the way the packet packs them - four bits of X, four of Z and eight
	 * of Y, all relative to the chunk.
	 */
	public void onWorldBlocksChanged(int chunkX, int chunkZ, short[] packed, int count) {
		if (packed == null) {
			return;
		}

		int originX = chunkX * 16;
		int originZ = chunkZ * 16;
		for (int i = 0; i < count && i < packed.length; i++) {
			short position = packed[i];
			this.onWorldBlockChanged(
					originX + (position >> 12 & 0xF),
					position & 0xFF,
					originZ + (position >> 8 & 0xF));
		}
	}

	/**
	 * Watches the world underneath every open schematic for changes nothing announced.
	 *
	 * <p>An overlay is drawn by comparing a schematic against the world, so it is only ever as good
	 * as the world was at the moment it was built - and a world does not always say when it changes.
	 * A single player world is handed over while the game is still running: it ticks and draws for
	 * seconds while reading as air, so a schematic put back in that window is drawn against nothing
	 * at all and would stay that way, because a world filling itself in is not a block change and
	 * nothing reports it. Chunks coming and going as the player walks about are the same, and so is
	 * anything else that writes into a chunk without a word.
	 *
	 * <p>So the world is read back and compared against what it said last time, one chunk column per
	 * schematic per tick, and any part that has changed underneath us is rebuilt. That bounds the
	 * work whatever the schematics cost, and puts a ceiling of a second or two on how long an
	 * overlay can be wrong for a reason nobody told it about.
	 */
	public void pollWorldUnderSchematics() {
		Minecraft mc = this.getMinecraft();
		World world = mc == null ? null : mc.world;
		for (int i = 0; i < this.open.size(); i++) {
			this.open.get(i).pollWorldUnder(world);
		}
	}

	/** Drops the render state that is tied to a particular world instance. */
	public void onWorldChanged() {
		this.closeAll();
		this.isRenderingGuide = false;
		// Any swap still waiting on the old server will never be answered now.
		HotbarRestock.reset();
		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.invalidate();
		}
	}

	private static final class SchematicFileFilter implements java.io.FileFilter {
		static final SchematicFileFilter INSTANCE = new SchematicFileFilter();

		@Override
		public boolean accept(File file) {
			return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".schematic");
		}
	}
}
