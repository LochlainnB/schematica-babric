package lunatrius.schematica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lunatrius.schematica.schematic.BlockSwap;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Holds a schematic up against the world it is standing in and says where the two disagree.
 *
 * <p>The overlay has been drawing this all along - the coloured boxes are the same comparison, made
 * one cell at a time as the geometry is built. What it cannot do is count, and a box you have to
 * find before you can see it is no help at the end of a build, when what is left is a handful of
 * blocks somewhere in a hundred thousand. So this is the same question asked of every cell at once,
 * with the answer written down as a list rather than painted into the world.
 *
 * <p>Four ways a cell can be wrong, and the fourth is one the overlay has no way of showing. Three
 * of them are about a block the schematic asks for - it is not there, something else is there, or
 * the right block is there turned the wrong way - and each of those has a box drawn on the block
 * that would be. The fourth is a cell the schematic asks for <em>nothing</em> in that has something
 * standing in it, and there is nothing to hang a box on, because what is wrong there is that the
 * cell is full. That is the one you cannot see coming, and the one that stops a build being the
 * build: a house dropped into a hillside with its rooms still full of stone.
 *
 * <p>Read-only from end to end, which is what makes it the one thing next to Paste on the move
 * screen that works on a server as well as in single player. Nothing here writes a block, asks for
 * one, or tells the world anything: it reads what is already in front of the player and counts.
 *
 * <p>What is compared is what somebody chose. A block the game switches between two ids on its own
 * is compared as the one that was put down ({@link BlockItems#placedForm}), and the parts of a
 * block's metadata the game keeps for itself are masked away ({@link BlockItems#placedMetadata})
 * before the two are held against each other. Without that a finished build reports itself broken
 * the moment a door is opened, a lever is thrown or a wire carries power - which is a list nobody
 * would read twice.
 *
 * <p>By the same rule a block nobody puts down is read as an empty cell wherever it turns up, so
 * that an extended piston is one thing to go and place rather than two - see {@link #settled}.
 */
public final class SchematicVerify {
	/** The world's ceiling. A schematic hanging over it has nothing to be checked against up there. */
	private static final int MAX_Y = 127;

	/** How many variants a block id can have, which is what makes a row's key out of the pair. */
	private static final int VARIANTS = 16;

	/** The two liquids, each of which the game keeps a still id and a flowing one for. */
	private static final int FLOWING_WATER = 8;
	private static final int STILL_WATER = 9;
	private static final int FLOWING_LAVA = 10;
	private static final int STILL_LAVA = 11;

	/**
	 * The ways a cell can be wrong, in the order the list puts them.
	 *
	 * <p>What is still to be built leads, because that is what the question is usually about. Then
	 * what was built wrong, and last what is in the way - last because it is the only one of the
	 * four that is sometimes not a fault at all: a schematic saved with room around it is standing
	 * in a world that was never asked to be empty there.
	 *
	 * <p>The colours are the overlay's own, so a row is the colour of the box you will be looking
	 * for once you go and find it. {@link #EXTRA} has no box to match, and takes the colour left
	 * over.
	 */
	public enum Issue {
		MISSING("schematic.verify.missing", 0x55CCFF),
		WRONG_BLOCK("schematic.verify.wrongblock", 0xFF5555),
		WRONG_METADATA("schematic.verify.wrongmeta", 0xFFAA33),
		EXTRA("schematic.verify.extra", 0xFFFF55);

		private final String key;
		private final int colour;

		Issue(String key, int colour) {
			this.key = key;
			this.colour = colour;
		}

		/** What to call it on screen, in the player's language. */
		public String label() {
			return Translations.get(this.key);
		}

		public int colour() {
			return this.colour;
		}
	}

	private final List<Entry> entries = new ArrayList<>();

	private int checked;
	private int faults;
	private int unloaded;
	private int offWorld;
	private int unknown;

	private SchematicVerify() {
	}

	/**
	 * One kind of block that is wrong in one way, however many cells it is wrong in.
	 *
	 * <p>Grouped rather than listed cell by cell, for the reason every other list in the mod is
	 * grouped: forty missing cobblestone is one thing to go and do, and forty rows each saying
	 * cobblestone is a list you have to count yourself. What a row keeps of the cells behind it is
	 * the nearest one, which is where to start.
	 */
	public static final class Entry {
		private final Issue issue;
		/**
		 * The block the row is about: the one the schematic asks for, except in an
		 * {@link Issue#EXTRA} row, where the schematic asks for nothing and the block in the way is
		 * the whole of what there is to say.
		 */
		private final int blockId;
		private final int variant;
		private final ItemStack stack;

		private int count;
		/** The nearest cell of this kind to wherever the player was standing when it was counted. */
		private final Vec3i example = new Vec3i();
		private long exampleDistance = Long.MAX_VALUE;
		/** What was found in that cell instead, for the one issue where that is worth saying. */
		private int foundId;
		private int foundVariant;

		/** Looked up once and kept, since the list is sorted on it. */
		private String name;
		private String foundName;

		private Entry(Issue issue, int blockId, int variant) {
			this.issue = issue;
			this.blockId = blockId;
			this.variant = variant;
			this.stack = BlockPalette.stackFor(blockId, variant);
		}

		public Issue getIssue() {
			return this.issue;
		}

		public int getBlockId() {
			return this.blockId;
		}

		public int getVariant() {
			return this.variant;
		}

		public ItemStack getStack() {
			return this.stack;
		}

		public int getCount() {
			return this.count;
		}

		/** Where the nearest one of them is, in world coordinates. */
		public Vec3i getExample() {
			return this.example;
		}

		public String getName() {
			if (this.name == null) {
				this.name = BlockPalette.nameFor(this.blockId, this.variant, this.stack);
			}
			return this.name;
		}

		/**
		 * What is standing in the example cell instead of the block this row wants, or null where
		 * that would say nothing: a block that is not there has nothing standing in its place, a
		 * block in the way is already named by the row it is on, and a block turned the wrong way is
		 * the right block.
		 */
		public String getFoundName() {
			if (this.issue != Issue.WRONG_BLOCK) {
				return null;
			}
			if (this.foundName == null) {
				this.foundName = BlockPalette.nameFor(this.foundId, this.foundVariant,
						BlockPalette.stackFor(this.foundId, this.foundVariant));
			}
			return this.foundName;
		}
	}

	/**
	 * Walks a schematic against the world under it. Nothing loaded, or no world to check it in,
	 * gives an empty report.
	 *
	 * <p>The whole schematic, not the layer being drawn. The slice is a way of looking at a build
	 * while it goes up; what is missing from it is a question about all of it, and an answer that
	 * quietly left out everything above the course being worked on would be the wrong answer at
	 * exactly the moment it was being asked.
	 *
	 * <p>One walk of the whole box, which for a large schematic is a million cells - so this is
	 * called when the button is clicked and not while the screen is being drawn.
	 *
	 * @param nearX the position each row measures its nearest cell against, normally the player's
	 */
	public static SchematicVerify of(World world, OpenSchematic open, int nearX, int nearY, int nearZ) {
		SchematicVerify report = new SchematicVerify();
		if (world == null || open == null || open.schematic == null) {
			return report;
		}

		Schematic schematic = open.schematic.getSchematic();
		int offsetX = open.offset.x;
		int offsetY = open.offset.y;
		int offsetZ = open.offset.z;
		int width = schematic.getWidth();
		int height = schematic.getHeight();
		int length = schematic.getLength();

		Map<Integer, Entry> rows = new HashMap<>();
		for (int y = 0; y < height; y++) {
			int worldY = y + offsetY;
			if (worldY < 0 || worldY > MAX_Y) {
				// A whole layer hanging over the ceiling or under the floor. There is nowhere for it
				// to be, so there is nothing to say about it beyond how much of it there is.
				report.offWorld += width * length;
				continue;
			}

			for (int x = 0; x < width; x++) {
				for (int z = 0; z < length; z++) {
					int wanted = schematic.getBlockId(x, y, z);
					if (wanted != 0 && (wanted >= Block.BLOCKS.length || Block.BLOCKS[wanted] == null)) {
						// A schematic written by a later game. Nothing in this one could be put here,
						// and nothing standing here could be the right answer, so the cell is not
						// judged at all - the overlay skips it for the same reason.
						report.unknown++;
						continue;
					}

					int worldX = x + offsetX;
					int worldZ = z + offsetZ;
					if (!world.method_239(worldX, worldY, worldZ)) {
						// The chunk is not loaded, so the client does not know what is in it. Reading
						// it anyway gives air, and air read out of a chunk nobody has been sent would
						// report a finished build as one that had never been started.
						report.unloaded++;
						continue;
					}

					report.checked++;
					report.compare(rows, schematic, x, y, z, world, worldX, worldY, worldZ,
							nearX, nearY, nearZ);
				}
			}
		}

		// By kind of fault first, so the list reads as sections rather than as a shuffle, and inside
		// each of them the order the material list uses: most of it first, alphabetical between rows
		// of the same size, so a list looked at twice reads the same way twice.
		report.entries.sort((left, right) -> {
			if (left.issue != right.issue) {
				return left.issue.ordinal() - right.issue.ordinal();
			}
			return left.count != right.count
					? right.count - left.count
					: left.getName().compareToIgnoreCase(right.getName());
		});
		return report;
	}

	/** Holds one cell of the schematic against the one cell of the world it is standing in. */
	private void compare(Map<Integer, Entry> rows, Schematic schematic, int x, int y, int z,
			World world, int worldX, int worldY, int worldZ, int nearX, int nearY, int nearZ) {
		int wanted = settled(schematic.getBlockId(x, y, z));
		int found = settled(world.getBlockId(worldX, worldY, worldZ));

		if (wanted == found) {
			if (wanted == 0) {
				// Nothing wanted and nothing there, which is most of most schematics.
				return;
			}

			int wantedMetadata = BlockItems.placedMetadata(wanted, schematic.getMetadata(x, y, z));
			int foundMetadata = BlockItems.placedMetadata(found, world.method_1778(worldX, worldY, worldZ));
			if (wantedMetadata != foundMetadata) {
				this.add(rows, Issue.WRONG_METADATA, wanted, wantedMetadata, 0, 0,
						worldX, worldY, worldZ, nearX, nearY, nearZ);
			}
			return;
		}

		if (wanted == 0) {
			this.add(rows, Issue.EXTRA, found,
					BlockItems.placedMetadata(found, world.method_1778(worldX, worldY, worldZ)), 0, 0,
					worldX, worldY, worldZ, nearX, nearY, nearZ);
			return;
		}

		int wantedMetadata = BlockItems.placedMetadata(wanted, schematic.getMetadata(x, y, z));
		if (found == 0) {
			this.add(rows, Issue.MISSING, wanted, wantedMetadata, 0, 0,
					worldX, worldY, worldZ, nearX, nearY, nearZ);
			return;
		}

		this.add(rows, Issue.WRONG_BLOCK, wanted, wantedMetadata,
				found, BlockItems.placedMetadata(found, world.method_1778(worldX, worldY, worldZ)),
				worldX, worldY, worldZ, nearX, nearY, nearZ);
	}

	/**
	 * Counts one wrong cell onto its row, making the row if this is the first of them.
	 *
	 * <p>The row keeps the nearest cell rather than the first, which is what makes the position on
	 * it worth walking to. Nearest is settled once, here, against where the player was standing when
	 * the check was made: a row that re-measured itself as the player moved would be a line of text
	 * that never held still long enough to be read.
	 */
	private void add(Map<Integer, Entry> rows, Issue issue, int blockId, int metadata,
			int foundId, int foundMetadata,
			int worldX, int worldY, int worldZ, int nearX, int nearY, int nearZ) {
		int variant = BlockSwap.variantOf(blockId, metadata);
		int key = (issue.ordinal() * Block.BLOCKS.length + blockId) * VARIANTS + variant;

		Entry entry = rows.get(key);
		if (entry == null) {
			entry = new Entry(issue, blockId, variant);
			rows.put(key, entry);
			this.entries.add(entry);
		}

		entry.count++;
		this.faults++;

		// Measured in longs. A schematic can stand thirty million blocks from the origin, and the
		// square of that is a good deal more than an int has room for.
		long dx = worldX - nearX;
		long dy = worldY - nearY;
		long dz = worldZ - nearZ;
		long distance = dx * dx + dy * dy + dz * dz;
		if (distance < entry.exampleDistance) {
			entry.exampleDistance = distance;
			entry.example.set(worldX, worldY, worldZ);
			entry.foundId = foundId;
			entry.foundVariant = BlockSwap.variantOf(foundId, foundMetadata);
		}
	}

	/**
	 * The form of a block to compare as: the one somebody would have put down, or nothing at all
	 * where nobody would have put anything.
	 *
	 * <p>A block no player places is read as an empty cell on whichever side of the comparison it
	 * turns up. The head of an extended piston comes out of the piston, fire is lit, a portal is
	 * struck, and the block a piston is halfway through pushing is a moment rather than a thing -
	 * so none of them is anything to do or to undo, which is the same answer {@link BlockPalette}
	 * gives when it leaves them off the list of what a schematic is made of.
	 *
	 * <p>That is what stops an extended piston being reported as two things missing. A schematic
	 * holding one holds a piston and an arm, and the arm is not a second block to go and find: place
	 * the piston, power it, and the arm is there. Read as empty it also answers the other three
	 * ways round. An arm standing where the schematic wants nothing is the circuit having pushed it
	 * out, which is no more a fault than the piston being out is - and both are already tolerated,
	 * the piston by {@link BlockItems#placedMetadata} masking the bit that says so. A block standing
	 * where the arm should be is still reported, as a block in the way, which is exactly what it is.
	 *
	 * <p>Otherwise {@link BlockItems#placedForm} for the pairs the game switches between - a lit
	 * furnace is a furnace - and the still form for the two liquids, which are less a pair the game
	 * switches between than one substance it keeps two ids for. A pool saved into a schematic and
	 * the same pool in the world will not agree about which cells of it are running, and a report
	 * saying "wrong block: water, found water" would be telling the truth uselessly.
	 */
	private static int settled(int blockId) {
		if (blockId == 0 || !BlockItems.isPlaceable(blockId)) {
			return 0;
		}
		if (blockId == FLOWING_WATER) {
			return STILL_WATER;
		}
		if (blockId == FLOWING_LAVA) {
			return STILL_LAVA;
		}
		return BlockItems.placedForm(blockId);
	}

	public List<Entry> getEntries() {
		return Collections.unmodifiableList(this.entries);
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	/** How many cells were actually held against the world. */
	public int getChecked() {
		return this.checked;
	}

	/** How many of them were wrong, which is the sum of every row's count. */
	public int getFaults() {
		return this.faults;
	}

	/** Cells whose chunk the client has not been sent, and so can have no opinion about. */
	public int getUnloaded() {
		return this.unloaded;
	}

	/** Cells above the world's ceiling or below its floor. */
	public int getOffWorld() {
		return this.offWorld;
	}

	/** Cells wanting a block this version of the game does not have. */
	public int getUnknown() {
		return this.unknown;
	}

	/** How many cells are wrong in one particular way. */
	public int getCount(Issue issue) {
		int count = 0;
		for (Entry entry : this.entries) {
			if (entry.issue == issue) {
				count += entry.count;
			}
		}
		return count;
	}
}
