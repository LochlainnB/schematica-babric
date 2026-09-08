package lunatrius.schematica.schematic;

import java.util.ArrayList;
import java.util.List;

/**
 * Which blocks can stand in for which, and what happens to their metadata when one does.
 *
 * <p>Replacing a block in a schematic is not a matter of writing a different id into the cell. A
 * schematic is a shape, and most of what makes it that shape is in the metadata: which way a stair
 * turns, which wall a torch hangs on, which half of a door this is. Cobblestone stairs put where
 * wooden stairs were have to face the same way or the roof they were holding up is gone.
 *
 * <p>So every block the mod knows about belongs to a family, and a block may only be swapped for
 * another in the same family. A family is not "looks similar" - it is a promise that the metadata
 * means the same thing to every block in it, which is what lets the old value simply be carried
 * over. Stairs turn the way stairs turn; a torch hangs where a torch hangs. Everything below is
 * keyed on raw numeric ids for the same reason {@link BlockTransform} is: a schematic is a stream
 * of numbers, and the tables stay readable and mapping-independent that way.
 *
 * <p>Two masks per block do the work. {@code carry} is the metadata a block understands and will
 * take from another - the bits copied across a swap. {@code orient} is the part of that the build
 * would be broken without, and it is checked rather than copied: a curved rail cannot become a
 * powered rail, because a powered rail has no curve to be, and the answer is to not offer the swap
 * rather than to quietly straighten the track. That check is made against the metadata the
 * schematic actually holds, so a line of straight rails may become powered ones and a line with a
 * bend in it may not.
 *
 * <p>What is missing is as deliberate as what is here. A block that carries a block entity - a
 * chest, a furnace, a sign, a note block - is in no family at all, because the entity would be left
 * behind at a position that is no longer its block: a chest's contents hanging in a wall of stone.
 * Water, fire, portals and piston heads are in no family either, being placed by something other
 * than a player putting a block down.
 */
public final class BlockSwap {
	/** How many block ids there are to have an opinion about. */
	private static final int BLOCK_IDS = 256;

	/** A block in no family: nothing may replace it, and it may replace nothing. */
	public static final int NONE = 0;
	/** Full cubes. The largest family by far, and the one most swaps are within. */
	public static final int CUBE = 1;
	private static final int STAIRS = 2;
	private static final int SLAB = 3;
	/** Cross-shaped, no collision, broken by a touch: flowers, mushrooms, saplings, grass. */
	private static final int PLANT = 4;
	private static final int TORCH = 5;
	private static final int RAIL = 6;
	private static final int DOOR = 7;
	private static final int PRESSURE_PLATE = 8;

	/**
	 * A block that may be replaced but never offered as the replacement. Every one of them is the
	 * lit or powered half of a pair the game switches between on its own, and both halves carry the
	 * same name - so offering it would put two rows reading "Redstone Torch" in a list where only
	 * one of them is the one anybody means.
	 */
	private static final int NOT_OFFERED = 1;

	/** Where each column of a table row is, once the block id in front of it has been read off. */
	private static final int FAMILY = 0;
	private static final int VARIANT_MASK = 1;
	private static final int VARIANTS = 2;
	private static final int CARRY = 3;
	private static final int ORIENT = 4;
	private static final int STATES = 5;
	private static final int FLAGS = 6;

	/** Every value a mask allows is a legal one, which is true of all but a handful of blocks. */
	private static final int ANY = 0xFFFF;

	/**
	 * Full cubes whose metadata says nothing: any one of them can stand in for any other, and none
	 * of them has anything to hand over when it does.
	 *
	 * <p>Glass and ice are here with stone and cobblestone. They are the same shape, which is what
	 * the family is about - the light that comes through them afterwards is the point of making the
	 * swap. Sand and gravel are here for the same reason, and will fall when the build goes up
	 * exactly as they would have if they had been drawn there in the first place.
	 */
	private static final int[] PLAIN_CUBES = {
			1,   // stone
			2,   // grass
			3,   // dirt
			4,   // cobblestone
			5,   // planks
			7,   // bedrock
			12,  // sand
			13,  // gravel
			14,  // gold ore
			15,  // iron ore
			16,  // coal ore
			19,  // sponge
			20,  // glass
			21,  // lapis ore
			22,  // lapis block
			24,  // sandstone
			41,  // block of gold
			42,  // block of iron
			45,  // bricks
			46,  // tnt
			47,  // bookshelf
			48,  // moss stone
			49,  // obsidian
			56,  // diamond ore
			57,  // block of diamond
			58,  // crafting table
			73,  // redstone ore
			79,  // ice
			80,  // snow block
			82,  // clay
			87,  // netherrack
			88,  // soul sand
			89,  // glowstone
	};

	/**
	 * Everything whose metadata is worth something: a kind, an orientation, or both.
	 *
	 * <p>{@code variants} and {@code states} are masks over the values themselves rather than over
	 * the bits - the nth bit says whether n is a value that block really has. Logs have a two bit
	 * variant field but only three kinds of wood, and rails have a four bit shape but only ten
	 * shapes, and both of those gaps matter: without them a schematic could be handed birch-plus-one
	 * or a rail bent in a direction that does not exist.
	 */
	private static final int[][] SPECIAL = {
			//  id  family          variant  variants  carry  orient  states   flags
			{    6, PLANT,              0x3,   0x0007,   0x0,    0x0,    ANY,      0 },  // sapling: oak, spruce, birch
			{   17, CUBE,               0x3,   0x0007,   0x0,    0x0,    ANY,      0 },  // log
			{   18, CUBE,               0x3,   0x0007,   0xC,    0x0,    ANY,      0 },  // leaves, keeping the decay flags
			{   27, RAIL,               0x0,   0x0001,   0x7,    0x7, 0x003F,      0 },  // powered rail: straight and sloped only
			{   28, RAIL,               0x0,   0x0001,   0x7,    0x7, 0x003F,      0 },  // detector rail
			{   31, PLANT,              0x3,   0x0007,   0x0,    0x0,    ANY,      0 },  // tall grass: shrub, grass, fern
			{   32, PLANT,              0x0,   0x0001,   0x0,    0x0,    ANY,      0 },  // dead bush
			{   35, CUBE,               0xF,   0xFFFF,   0x0,    0x0,    ANY,      0 },  // wool, all sixteen colours
			{   37, PLANT,              0x0,   0x0001,   0x0,    0x0,    ANY,      0 },  // dandelion
			{   38, PLANT,              0x0,   0x0001,   0x0,    0x0,    ANY,      0 },  // rose
			{   39, PLANT,              0x0,   0x0001,   0x0,    0x0,    ANY,      0 },  // brown mushroom
			{   40, PLANT,              0x0,   0x0001,   0x0,    0x0,    ANY,      0 },  // red mushroom
			{   43, CUBE,               0x3,   0x000F,   0x0,    0x0,    ANY,      0 },  // double slab
			{   44, SLAB,               0x3,   0x000F,   0x8,    0x8,    ANY,      0 },  // slab
			{   50, TORCH,              0x0,   0x0001,   0x7,    0x7, 0x003E,      0 },  // torch, hanging or standing
			{   53, STAIRS,             0x0,   0x0001,   0x7,    0x7,    ANY,      0 },  // wooden stairs
			{   64, DOOR,               0x0,   0x0001,   0xF,    0xF,    ANY,      0 },  // wooden door
			{   66, RAIL,               0x0,   0x0001,   0xF,    0xF, 0x03FF,      0 },  // rail: 6 to 9 are the curves
			{   67, STAIRS,             0x0,   0x0001,   0x7,    0x7,    ANY,      0 },  // cobblestone stairs
			{   70, PRESSURE_PLATE,     0x0,   0x0001,   0x1,    0x0,    ANY,      0 },  // stone pressure plate
			{   71, DOOR,               0x0,   0x0001,   0xF,    0xF,    ANY,      0 },  // iron door
			{   72, PRESSURE_PLATE,     0x0,   0x0001,   0x1,    0x0,    ANY,      0 },  // wooden pressure plate
			{   74, CUBE,               0x0,   0x0001,   0x0,    0x0,    ANY, NOT_OFFERED },  // redstone ore, lit
			{   75, TORCH,              0x0,   0x0001,   0x7,    0x7, 0x003E, NOT_OFFERED },  // redstone torch, off
			{   76, TORCH,              0x0,   0x0001,   0x7,    0x7, 0x003E,      0 },  // redstone torch
			{   86, CUBE,               0x0,   0x0001,   0x3,    0x0,    ANY,      0 },  // pumpkin
			{   91, CUBE,               0x0,   0x0001,   0x3,    0x0,    ANY,      0 },  // jack o'lantern
	};

	/** One row per block id, or null for a block in no family. Built once from the tables above. */
	private static final int[][] ROWS = new int[BLOCK_IDS][];

	static {
		for (int blockId : PLAIN_CUBES) {
			ROWS[blockId] = new int[] { CUBE, 0x0, 0x0001, 0x0, 0x0, ANY, 0 };
		}
		for (int[] row : SPECIAL) {
			ROWS[row[0]] = new int[] { row[1], row[2], row[3], row[4], row[5], row[6], row[7] };
		}
	}

	private BlockSwap() {
	}

	private static int[] row(int blockId) {
		return blockId > 0 && blockId < BLOCK_IDS ? ROWS[blockId] : null;
	}

	private static int column(int blockId, int column) {
		int[] row = row(blockId);
		return row == null ? 0 : row[column];
	}

	/** Whether this block is one anything can be swapped for, in either direction. */
	public static boolean isKnown(int blockId) {
		return row(blockId) != null;
	}

	/**
	 * The part of a block's metadata that says which kind of it this is, rather than how it sits.
	 *
	 * <p>Red wool and white wool are two things a schematic can be built out of and two rows on the
	 * replace screen; a stair facing north and a stair facing south are one thing put down twice.
	 */
	public static int variantMask(int blockId) {
		return column(blockId, VARIANT_MASK);
	}

	public static int variantOf(int blockId, int metadata) {
		return metadata & variantMask(blockId);
	}

	/** Whether a block may be offered as a replacement, as against merely being replaceable. */
	public static boolean isOffered(int blockId) {
		int[] row = row(blockId);
		return row != null && (row[FLAGS] & NOT_OFFERED) == 0;
	}

	/**
	 * The metadata a block should carry once another has been swapped in for it: the new kind, plus
	 * whatever of the old value the new block understands.
	 *
	 * <p>Bits the new block has no use for are dropped rather than carried, which is what stops a
	 * leaf's decay flag from turning up in a stair's facing. Whether dropping them is acceptable is
	 * {@link #accepts}'s question, not this one's.
	 */
	public static int metadataFor(int fromId, int metadata, int toId, int toVariant) {
		int carried = metadata & column(fromId, CARRY) & column(toId, CARRY);
		return (toVariant & variantMask(toId)) | carried;
	}

	/**
	 * Whether a block can take another's place everywhere it stands in one schematic.
	 *
	 * @param metadataSeen the metadata values that block is holding in the schematic, as a mask over
	 *                     the values themselves - bit n set meaning some block of it carries n
	 */
	public static boolean accepts(int fromId, int metadataSeen, int toId, int toVariant) {
		int[] from = row(fromId);
		int[] to = row(toId);
		if (from == null || to == null || from[FAMILY] != to[FAMILY]) {
			return false;
		}
		if ((to[VARIANTS] >> (toVariant & 0xF) & 1) == 0) {
			return false;
		}

		for (int metadata = 0; metadata < 16; metadata++) {
			if ((metadataSeen >> metadata & 1) == 0) {
				continue;
			}

			// Everything the old block calls orientation has to be something the new one keeps.
			if ((metadata & from[ORIENT] & ~to[CARRY]) != 0) {
				return false;
			}

			int replacement = metadataFor(fromId, metadata, toId, toVariant);
			if ((to[STATES] >> (replacement & to[CARRY]) & 1) == 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Every block that could stand in for this one, as {@code { blockId, variant }} pairs.
	 *
	 * <p>In block id order, which puts the ores together and the wools in colour order, and reads
	 * as a list somebody laid out rather than one that fell out of a map. The block being replaced
	 * is left out of its own list.
	 */
	public static List<int[]> replacementsFor(int fromId, int fromVariant, int metadataSeen) {
		List<int[]> replacements = new ArrayList<>();
		walk(fromId, fromVariant, metadataSeen, replacements);
		return replacements;
	}

	/**
	 * Whether anything at all could stand in for this block.
	 *
	 * <p>The same walk as above, stopped at the first answer and given nothing to put it in. It is
	 * what greys a row's button out, so it is asked of every row on the screen on every frame -
	 * which is no place to be building a list of sixty blocks and throwing it away again.
	 */
	public static boolean hasReplacement(int fromId, int fromVariant, int metadataSeen) {
		return walk(fromId, fromVariant, metadataSeen, null);
	}

	/**
	 * Walks the block table for replacements, collecting them where there is somewhere to put them
	 * and stopping at the first otherwise. True when there was at least one.
	 */
	private static boolean walk(int fromId, int fromVariant, int metadataSeen, List<int[]> into) {
		if (!isKnown(fromId)) {
			return false;
		}

		boolean found = false;
		for (int blockId = 1; blockId < BLOCK_IDS; blockId++) {
			if (!isOffered(blockId)) {
				continue;
			}

			int variants = column(blockId, VARIANTS);
			for (int variant = 0; variant < 16; variant++) {
				if ((variants >> variant & 1) == 0) {
					continue;
				}
				if (blockId == fromId && variant == fromVariant) {
					continue;
				}
				if (!accepts(fromId, metadataSeen, blockId, variant)) {
					continue;
				}

				if (into == null) {
					return true;
				}
				into.add(new int[] { blockId, variant });
				found = true;
			}
		}
		return found;
	}
}
