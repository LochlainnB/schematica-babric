package lunatrius.schematica;

import lunatrius.schematica.compat.CreativeMode;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.class_505;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Builds the loaded schematic into the world in one go, where the ghost is standing.
 *
 * <p>The rest of the mod draws a schematic and leaves the building to you. This does not: it writes
 * the blocks straight into the world, which is only something a player can be allowed to do when
 * the world is theirs alone and they were already able to have any block they liked - so it is
 * offered in single player, in creative mode, and nowhere else. On a server the client does not own
 * the world it is drawing, and blocks written into its copy would be a lie the next chunk update
 * takes back; in survival it would hand over a building nobody gathered.
 *
 * <p>Creative mode is not something Beta 1.7.3 has an answer for on its own - see
 * {@link CreativeMode} for who is asked instead, and why through a keyhole rather than a door.
 *
 * <p>What gets written is the blocks the schematic asks for. What happens to the cells it asks for
 * nothing in is the {@code pasteAir} setting, and it is a real question rather than an oversight:
 * an empty cell can be read as "no part of this build", which is how the rest of the mod reads it,
 * or as "and there must be air here", which is how a schematic saved out of a world was in fact
 * standing. On - the default - the box is cleared first, so what is left standing inside it is the
 * build and nothing else; a schematic dropped underground gets its rooms rather than the hill they
 * were cut into. Off, whatever is already there is left where it is, so a build saved with room
 * around it can be dropped onto a hillside without that room being carved out of the hill.
 */
public final class SchematicPaste {
	/** What {@link #paste} returns when it would not. */
	public static final int REFUSED = -1;

	/** The world's ceiling. A schematic hanging over it is written up to here and no further. */
	private static final int MAX_Y = 127;

	/**
	 * The two pistons and the arm they push out. A piston is two blocks while it is extended, and
	 * the game only agrees they are one thing when it finds them together - see {@link #writePistons}.
	 */
	private static final int PISTON = Block.field_1874.id;
	private static final int STICKY_PISTON = Block.STICKY_PISTON.id;
	private static final int PISTON_HEAD = Block.field_1875.id;

	/** The part of a piston's metadata that says which way it points. The rest says whether it is out. */
	private static final int FACING = 7;

	/** Whether the schematic may be pasted, and where it may not, what to say about it. */
	public enum Availability {
		READY(null),
		NO_SCHEMATIC("schematic.paste.noschematic"),
		ON_A_SERVER("schematic.paste.singleplayer"),
		NO_CREATIVE_MOD("schematic.paste.nocreativemod"),
		NOT_CREATIVE("schematic.paste.creative");

		private final String key;

		Availability(String key) {
			this.key = key;
		}

		public boolean isReady() {
			return this.key == null;
		}

		/** Why the paste is not on offer, in the player's language, or null while it is. */
		public String reason() {
			return this.key == null ? null : Translations.get(this.key);
		}
	}

	private SchematicPaste() {
	}

	/**
	 * Whether the schematic may be pasted right now, and if not, which of the reasons it is.
	 *
	 * <p>Asked every frame the move screen is open, so the button greys and un-greys itself while it
	 * is being looked at rather than only on the way in.
	 */
	public static Availability availability(Minecraft mc) {
		SchematicaState state = Schematica.STATE;
		if (mc == null || state.getActive().isEmpty()) {
			// Covers having no world as well: a schematic can only be loaded into one, so with no
			// world nothing is loaded either, and "nothing to paste" is the truer of the two answers.
			return Availability.NO_SCHEMATIC;
		}
		if (mc.world == null || mc.player == null || mc.world.isRemote) {
			return Availability.ON_A_SERVER;
		}
		if (!CreativeMode.isInstalled()) {
			return Availability.NO_CREATIVE_MOD;
		}
		if (!CreativeMode.isCreative(mc.player)) {
			return Availability.NOT_CREATIVE;
		}
		return Availability.READY;
	}

	/**
	 * Pastes the loaded schematic where it stands, if it may be. Returns how many blocks were
	 * written, or {@link #REFUSED} when {@link #availability} says it may not be.
	 *
	 * <p>This is what the button calls, and the check is inside it rather than only in front of it:
	 * writing blocks into a world the client does not own is the one thing in this mod that cannot
	 * be undone by looking away from it.
	 */
	public static int paste(Minecraft mc) {
		Availability availability = availability(mc);
		if (!availability.isReady()) {
			Log.warn("Not pasting: " + availability.reason());
			return REFUSED;
		}

		return write(mc.world, Schematica.STATE);
	}

	/**
	 * The writing itself, with nothing in front of it. {@link #paste} is this behind
	 * {@link #availability}, and is what anything with a player in front of it should call.
	 *
	 * <p>Separate because the two are separate things - what may be pasted, and what pasting is -
	 * and because the second one cannot otherwise be checked at all in a game that has no creative
	 * mode installed in it.
	 *
	 * @return how many blocks were written.
	 */
	public static int write(World world, SchematicaState state) {
		return write(world, state.getActive());
	}

	/**
	 * The same, pointed at one open schematic rather than at whichever is active. Several can be
	 * open at once and only one of them is being pasted - the one the move screen's picker and its
	 * Paste button are both pointed at.
	 */
	public static int write(World world, OpenSchematic open) {
		if (world == null || open.schematic == null) {
			return 0;
		}

		SchematicWorld ghost = open.schematic;
		Schematic schematic = ghost.getSchematic();
		int offsetX = open.offset.x;
		int offsetY = open.offset.y;
		int offsetZ = open.offset.z;
		int width = schematic.getWidth();
		int height = schematic.getHeight();
		int length = schematic.getLength();

		long started = System.currentTimeMillis();

		// Emptied first, where the setting says to empty it: the build then goes into a box that is
		// already clear, rather than being written and then having the ground taken out from between
		// its blocks afterwards. Nothing that lands later has to wonder what it is standing next to.
		int cleared = Schematica.CONFIG.pasteAir
				? clearPass(world, schematic, offsetX, offsetY, offsetZ)
				: 0;

		// Walls first, then everything that hangs on one. A block that has landed can be asked whether
		// it is still standing on anything, and a torch asked that before the wall it hangs on has
		// been written takes itself down, drops on the floor as an item and leaves a hole in the
		// build. Writing in reading order is not enough to avoid it either, because the asking travels
		// further than the neighbours: a redstone wire landing tells the wire beside it, and that one
		// tells all of its own neighbours, so a torch two blocks clear of the wire that arrived gets
		// asked as well. Every block that can be hung on going down before any block that hangs is
		// what makes the wall always there by the time anything asks.
		int placed = writePass(world, ghost, offsetX, offsetY, offsetZ, true)
				+ writePass(world, ghost, offsetX, offsetY, offsetZ, false);

		// And then whatever a block decided for itself on the way in is put back to what the schematic
		// says. A plain torch re-reads its own facing off whichever neighbour is solid every time it is
		// placed, so one with a wall on both sides comes out on the first one found rather than the one
		// it was saved against - which for a torch that powers what it is attached to is a circuit
		// wired differently rather than an ornament turned round.
		Repairs repairs = repair(world, schematic, offsetX, offsetY, offsetZ);

		int entities = writeBlockEntities(world, schematic, offsetX, offsetY, offsetZ);

		// And the pistons last of all, once everything they read is there and saying what it was
		// saved saying. A piston is the one block that starts working the moment it lands: it looks
		// around for power straight away, and one saved extended that finds none throws its arm off
		// and climbs back into its hole. Landing in reading order it looks too early - the torch
		// that powers it is one cell further along and has not been written yet - so a row of
		// identical pistons comes out half extended and half not, decided by nothing but which side
		// each one's torch happened to be on.
		placed += writePistons(world, schematic, offsetX, offsetY, offsetZ);

		// None of that went through the single-block path, so nothing has been told any of it
		// happened. The world's own renderer is handed the whole box in one call, and the overlay -
		// which hears about blocks through that same path - is marked stale outright, since after
		// this every cell of it has changed.
		world.method_202(offsetX, Math.max(0, offsetY), offsetZ,
				offsetX + width - 1, Math.min(MAX_Y, offsetY + height - 1), offsetZ + length - 1);
		open.needsUpdate = true;

		Log.info("Pasted " + open.getLoadedName() + " at " + offsetX + ", " + offsetY + ", " + offsetZ
				+ " - " + placed + " block(s), " + cleared + " cell(s) cleared, "
				+ entities + " block entit" + (entities == 1 ? "y" : "ies")
				+ ", " + repairs.metadata + " turned back the way they were saved, "
				+ (System.currentTimeMillis() - started) + " ms");
		if (repairs.lost > 0) {
			Log.warn(repairs.lost + " block(s) of " + open.getLoadedName() + " would not stay where they"
					+ " were put. A schematic cut through something one of its blocks was attached to"
					+ " has nothing to attach it to unless the world already holds it.");
		}
		return placed;
	}

	/**
	 * Empties every cell the schematic asks for nothing in, which is what leaves a pasted build
	 * standing in its own space rather than mixed through whatever it was dropped into.
	 *
	 * <p>The raw setter, for the same reason the writing uses it: a cell going empty is not news a
	 * house being built around it should be hearing. The block that was there is still told it has
	 * been taken out, which is the chunk's own doing rather than something asked of it here, and is
	 * what tips a chest's contents onto the floor instead of leaving a block entity sitting in a
	 * cell that is now air.
	 *
	 * <p>A cell holding a block this version has no answer for is left alone rather than cleared.
	 * The schematic asking for one is a schematic from a later game, and it is not asking for
	 * nothing there - which is the only thing that clears a cell.
	 *
	 * <p>Counts the cells that actually changed rather than the cells looked at. The setter refuses
	 * a write that would change nothing, and a schematic is mostly empty over ground that above
	 * anything built on is mostly empty too, so the two numbers are nowhere near each other.
	 */
	private static int clearPass(World world, Schematic schematic, int offsetX, int offsetY, int offsetZ) {
		int cleared = 0;

		for (int y = 0; y < schematic.getHeight(); y++) {
			int worldY = y + offsetY;
			if (worldY < 0 || worldY > MAX_Y) {
				continue;
			}
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					if (schematic.getBlockId(x, y, z) != 0) {
						continue;
					}
					if (world.method_154(x + offsetX, worldY, z + offsetZ, 0, 0)) {
						cleared++;
					}
				}
			}
		}

		return cleared;
	}

	/**
	 * One sweep of the schematic, writing either the blocks that can be hung on or the blocks that
	 * hang - {@code supports} says which. The question of which is which is the game's own, asked of
	 * the schematic rather than of the world it is going into: it is exactly the question a torch
	 * asks of a neighbour before deciding it has nothing to hold on to.
	 */
	private static int writePass(World world, SchematicWorld ghost,
			int offsetX, int offsetY, int offsetZ, boolean supports) {
		Schematic schematic = ghost.getSchematic();
		int placed = 0;

		for (int y = 0; y < schematic.getHeight(); y++) {
			int worldY = y + offsetY;
			if (worldY < 0 || worldY > MAX_Y) {
				continue;
			}
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					int id = schematic.getBlockId(x, y, z);
					if (id == 0 || Block.BLOCKS[id] == null) {
						// Empty, or an id this version has no block for - which the reader already
						// warned about on the way in. The overlay skips both for the same reason.
						continue;
					}
					if (isPistonPart(id)) {
						// Not yet. A piston and its arm go in together, at the end - see writePistons.
						continue;
					}
					if (ghost.method_1780(x, y, z) != supports) {
						continue;
					}

					// The raw setter rather than the notifying one. Every neighbour of a block in the
					// middle of a schematic is another block in the same schematic, so notifying as
					// each one landed would be telling a half-built house about itself: sand falls
					// through floors not laid yet, and a redstone circuit runs before it is finished
					// being wired. Each block's own onBlockAdded still runs, so water still flows and
					// gravel still looks at the ground under it - once all of it is down.
					world.method_154(x + offsetX, worldY, z + offsetZ, id, schematic.getMetadata(x, y, z));
					placed++;
				}
			}
		}

		return placed;
	}

	/**
	 * Puts back any metadata a block changed for itself as it landed, and counts anything that is not
	 * where it was put at all.
	 *
	 * <p>The metadata is written raw, without telling the block it has been placed again: handing it
	 * back to the same code that changed it in the first place would only get the same answer twice.
	 *
	 * <p>Derived metadata is forced along with the rest - a redstone wire comes back at the strength
	 * it was saved carrying rather than the one it has worked out so far. That is the resting state
	 * of the circuit that was saved, and the first thing to touch the wire recomputes it anyway.
	 */
	private static Repairs repair(World world, Schematic schematic, int offsetX, int offsetY, int offsetZ) {
		Repairs repairs = new Repairs();

		for (int y = 0; y < schematic.getHeight(); y++) {
			int worldY = y + offsetY;
			if (worldY < 0 || worldY > MAX_Y) {
				continue;
			}
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					int id = schematic.getBlockId(x, y, z);
					if (id == 0 || Block.BLOCKS[id] == null) {
						continue;
					}

					if (isPistonPart(id)) {
						// Nothing of a piston is written yet, and none of it would be ours to correct
						// if it were: what an extended one does with itself as it lands is the answer
						// the circuit gave it, not something it got wrong.
						continue;
					}

					int worldX = x + offsetX;
					int worldZ = z + offsetZ;
					int standing = world.getBlockId(worldX, worldY, worldZ);
					if (standing != id) {
						// Air means the block took itself straight back out again, and putting it back
						// would only be refused a second time and drop a second item. Anything else is
						// a block part way through turning into something else, which finishes on its
						// own over the next few ticks and is better not interrupted.
						if (standing == 0) {
							repairs.lost++;
						}
						continue;
					}

					int metadata = schematic.getMetadata(x, y, z);
					if (world.method_1778(worldX, worldY, worldZ) != metadata) {
						world.method_223(worldX, worldY, worldZ, metadata);
						repairs.metadata++;
					}
				}
			}
		}

		return repairs;
	}

	/**
	 * Puts the schematic's signs, chests and the rest into the blocks just written.
	 *
	 * <p>Each one is copied through NBT rather than handed over. The schematic goes on drawing itself
	 * out of these, and a block entity belongs to one world at a time - moving them across would
	 * empty every sign in the overlay the moment it was pasted.
	 */
	private static int writeBlockEntities(World world, Schematic schematic,
			int offsetX, int offsetY, int offsetZ) {
		int written = 0;

		for (BlockEntity source : schematic.getBlockEntities()) {
			int x = source.x + offsetX;
			int y = source.y + offsetY;
			int z = source.z + offsetZ;
			if (y < 0 || y > MAX_Y) {
				continue;
			}

			// Asking is also what makes one: the block written there a moment ago is a chest or a
			// sign, so the world answers with the empty one it built for itself. A null means whatever
			// ended up there takes no block entity at all, and this one has nowhere to go.
			if (world.method_1777(x, y, z) == null) {
				continue;
			}

			NbtCompound nbt = new NbtCompound();
			source.writeNbt(nbt);
			BlockEntity copy = BlockEntity.method_1068(nbt);
			if (copy == null) {
				Log.warn("Could not copy a " + source.getClass().getSimpleName() + " out of the schematic");
				continue;
			}

			// The empty one goes before the real one arrives. Left where it is it would keep its seat
			// in the world's list of things to tick while the chunk answered with ours instead, which
			// is a block entity ticking somewhere nothing thinks it is.
			world.method_260(x, y, z);
			world.method_157(x, y, z, copy);
			written++;
		}

		return written;
	}

	/**
	 * Writes the pistons, each one's arm going in just before the piston that owns it.
	 *
	 * <p>Last of the blocks, because a piston is the one thing in a schematic that reads its
	 * surroundings the instant it lands rather than waiting to be told about them. An extended one
	 * that finds no power throws its arm off and climbs back into its hole, and it only finds power
	 * if whatever supplies it has already been written - so pasting a row of pistons in reading
	 * order retracts exactly those whose torch sits on the far side of them.
	 *
	 * <p>Arm before piston, and each pair together rather than every arm and then every piston,
	 * because a piston that does decide to retract clears the cell in front of it as it goes. Write
	 * the arm after that and it is left standing on nothing, with no piston behind it that will ever
	 * take it away again and no way for a player to pick it up; write every arm first and a
	 * retracting piston can knock its neighbour's arm off in passing, since an arm asked whether its
	 * piston is still there while that piston is still waiting to be written says no.
	 *
	 * <p>An arm with no piston of its own in the schematic - a build cut off through a piston
	 * mid-push - has nothing to pair with and goes in on its own at the end.
	 */
	private static int writePistons(World world, Schematic schematic,
			int offsetX, int offsetY, int offsetZ) {
		int placed = 0;

		for (int y = 0; y < schematic.getHeight(); y++) {
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					if (!isPiston(schematic.getBlockId(x, y, z))) {
						continue;
					}

					int facing = schematic.getMetadata(x, y, z) & FACING;
					if (facing < class_505.field_2107.length) {
						int armX = x + class_505.field_2107[facing];
						int armY = y + class_505.field_2108[facing];
						int armZ = z + class_505.field_2109[facing];
						if (inside(schematic, armX, armY, armZ)
								&& schematic.getBlockId(armX, armY, armZ) == PISTON_HEAD
								&& writeCell(world, schematic, armX, armY, armZ, offsetX, offsetY, offsetZ)) {
							placed++;
						}
					}

					if (writeCell(world, schematic, x, y, z, offsetX, offsetY, offsetZ)) {
						placed++;
					}
				}
			}
		}

		for (int y = 0; y < schematic.getHeight(); y++) {
			for (int x = 0; x < schematic.getWidth(); x++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					if (schematic.getBlockId(x, y, z) != PISTON_HEAD
							|| hasPistonBehind(schematic, x, y, z)) {
						continue;
					}
					if (writeCell(world, schematic, x, y, z, offsetX, offsetY, offsetZ)) {
						placed++;
					}
				}
			}
		}

		return placed;
	}

	/** Whether the piston an arm belongs to is in the schematic, and so has already written it. */
	private static boolean hasPistonBehind(Schematic schematic, int x, int y, int z) {
		int facing = schematic.getMetadata(x, y, z) & FACING;
		if (facing >= class_505.field_2107.length) {
			return false;
		}

		int pistonX = x - class_505.field_2107[facing];
		int pistonY = y - class_505.field_2108[facing];
		int pistonZ = z - class_505.field_2109[facing];
		return inside(schematic, pistonX, pistonY, pistonZ)
				&& isPiston(schematic.getBlockId(pistonX, pistonY, pistonZ));
	}

	/** Writes one cell of the schematic into the world. False when it falls outside the world. */
	private static boolean writeCell(World world, Schematic schematic, int x, int y, int z,
			int offsetX, int offsetY, int offsetZ) {
		int worldY = y + offsetY;
		if (worldY < 0 || worldY > MAX_Y) {
			return false;
		}

		world.method_154(x + offsetX, worldY, z + offsetZ,
				schematic.getBlockId(x, y, z), schematic.getMetadata(x, y, z));
		return true;
	}

	/** Whether a cell is one the schematic has at all. Its own reader answers for anything else. */
	private static boolean inside(Schematic schematic, int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0
				&& x < schematic.getWidth() && y < schematic.getHeight() && z < schematic.getLength();
	}

	private static boolean isPiston(int id) {
		return id == PISTON || id == STICKY_PISTON;
	}

	private static boolean isPistonPart(int id) {
		return isPiston(id) || id == PISTON_HEAD;
	}

	/** What a paste had to put right after the blocks were down, and what it could not. */
	private static final class Repairs {
		/** Cells whose metadata the block itself changed on the way in, and which were set back. */
		int metadata;
		/** Cells the block refused to stay in, which are air now and an item on the floor. */
		int lost;
	}
}
