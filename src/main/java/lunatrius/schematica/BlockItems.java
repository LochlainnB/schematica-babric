package lunatrius.schematica;

import net.minecraft.Item;
import net.minecraft.class_533;
import net.minecraft.item.ItemStack;

/**
 * Which item puts a block down, and which stack of it.
 *
 * <p>It lives on its own because two features have to agree about it: easy place, which goes
 * looking for the item before it can build with it, and the material list, which counts the same
 * items before any of them have been found. A block the list says you need is by construction the
 * one easy place would go and fetch.
 */
public final class BlockItems {
	/**
	 * Blocks whose item is not the block itself. Everything else is put down by a block item, which
	 * is always registered under the id of the block it places - see {@link #itemFor}.
	 */
	private static final int[][] BLOCK_ITEMS = {
			{ 26, 355 },  // bed             <- bed
			{ 55, 331 },  // redstone wire   <- redstone
			{ 59, 295 },  // wheat           <- seeds
			{ 63, 323 },  // sign post       <- sign
			{ 64, 324 },  // wooden door     <- wooden door
			{ 68, 323 },  // wall sign       <- sign
			{ 71, 330 },  // iron door       <- iron door
			{ 83, 338 },  // sugar cane      <- reeds
			{ 92, 354 },  // cake            <- cake
			{ 93, 356 },  // repeater, off   <- redstone repeater
			{ 94, 356 },  // repeater, on    <- redstone repeater
	};

	/** How many values a block's metadata, and an item's damage, can each take. */
	private static final int VALUES = 16;

	private BlockItems() {
	}

	/** The item that places a block, or -1 if nothing does. */
	public static int itemFor(int blockId) {
		for (int[] pair : BLOCK_ITEMS) {
			if (pair[0] == blockId) {
				return pair[1];
			}
		}

		// A block item is registered under the id of its own block, so for everything else the block
		// id is the item id - as long as there really is a block item there.
		return itemAt(blockId) instanceof class_533 ? blockId : -1;
	}

	/** Whether this stack is something that puts a block down, rather than a tool or a meal. */
	public static boolean isBlockItem(ItemStack stack) {
		if (itemAt(stack.itemId) instanceof class_533) {
			return true;
		}

		for (int[] pair : BLOCK_ITEMS) {
			if (pair[1] == stack.itemId) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The damage a stack has to carry to put down a block with this metadata, or -1 when the
	 * metadata is not something a stack carries at all.
	 *
	 * <p>Where there is an answer, the metadata is the thing that tells one stack of the item from
	 * another - wool colour, wood type, sapling type - and only that stack will do. Where there is
	 * not, the metadata is coming from somewhere else entirely: which way a piston faces, which way
	 * a stair turns, the bit leaves get for being placed by hand. Insisting on a match there would
	 * refuse to place those blocks at all, so any stack of the item is taken instead.
	 *
	 * <p>An item whose damage always places the same metadata cannot tell one of its stacks from
	 * another whatever the metadata is, so it is never an answer - not even for the metadata it
	 * does place. Stairs facing north would otherwise be a different material from stairs facing
	 * south, which they are not.
	 */
	public static int damageFor(Item item, int metadata) {
		int first = item.method_470(0);
		boolean carried = false;
		int found = -1;

		for (int damage = 0; damage < VALUES; damage++) {
			int placed = item.method_470(damage);
			carried |= placed != first;
			if (found < 0 && placed == metadata) {
				found = damage;
			}
		}

		return carried ? found : -1;
	}

	/**
	 * Whether a stack would put down the block that is wanted.
	 *
	 * @param matchDamage whether the metadata is one the stack carries, from {@link #damageFor}
	 */
	public static boolean matches(ItemStack stack, int itemId, int metadata, boolean matchDamage) {
		if (stack == null || stack.itemId != itemId) {
			return false;
		}

		Item item = itemAt(itemId);
		return !matchDamage || (item != null && item.method_470(stack.getDamage()) == metadata);
	}


	/**
	 * Blocks that are not their own material. Each row is the block, the item you would go and
	 * fetch for it and how many of that item one block takes, with -1 for a block that is no
	 * material at all: one that comes with another block, or that is lit rather than placed.
	 *
	 * <p>Separate from {@link #BLOCK_ITEMS} because it answers a different question. That table is
	 * what easy place puts in your hand, and it can only hold things easy place can build with -
	 * it does not click with buckets, and nothing at all puts a piston head down. This one is what
	 * you have to be carrying, which for water is a bucket whether or not anything will ever pour
	 * it for you.
	 */
	private static final int[][] MATERIALS = {
			{  8, 326, 1 },  // water              <- water bucket
			{  9, 326, 1 },
			{ 10, 327, 1 },  // lava               <- lava bucket
			{ 11, 327, 1 },
			{ 34,  -1, 0 },  // piston head        - comes out of the piston
			{ 36,  -1, 0 },  // piston moving      - the block a piston is pushing, mid push
			{ 43,  44, 2 },  // double slab        <- two slabs, one on top of the other
			{ 51,  -1, 0 },  // fire               - lit, not placed
			{ 60,   3, 1 },  // farmland           <- dirt, then a hoe
			{ 62,  61, 1 },  // furnace, lit       <- furnace
			{ 74,  73, 1 },  // redstone ore, lit  <- redstone ore
			{ 75,  76, 1 },  // redstone torch off <- redstone torch
			{ 90,  -1, 0 },  // portal             - lit, not placed
	};

	/** Water and lava, whose metadata says whether the block is a source or what flowed out of it. */
	private static final int FLOWING_WATER = 8;
	private static final int STILL_LAVA = 11;

	/**
	 * The item you would go and fetch to build a block, or -1 when the block is no material of its
	 * own - a piston head, water that flowed out of a source, the top half of a door.
	 *
	 * <p>This is the material list's question rather than easy place's: it asks what has to be in
	 * the pack, not what a click can be made with.
	 */
	public static int materialFor(int blockId, int metadata) {
		// A pool is one bucket per source block. Everything else in it is water that ran there on
		// its own and would run there again, so it is nothing to carry.
		if (blockId >= FLOWING_WATER && blockId <= STILL_LAVA && metadata != 0) {
			return -1;
		}

		for (int[] row : MATERIALS) {
			if (row[0] == blockId) {
				return row[1];
			}
		}
		return itemFor(blockId);
	}

	/** How many of that item one block takes - one, unless a block is built out of a pair. */
	public static int materialCount(int blockId) {
		for (int[] row : MATERIALS) {
			if (row[0] == blockId) {
				return row[2];
			}
		}
		return 1;
	}

	/**
	 * Blocks the game turns on and off by itself, each paired with the half of it a player actually
	 * puts down. A redstone torch that happens to be off is a redstone torch, and a furnace that
	 * happens to be smelting is a furnace.
	 *
	 * <p>Which half a schematic holds is a circuit caught mid-tick rather than anything anybody
	 * chose, so a list of what a build is made of has no business holding both. Kept apart from
	 * {@link #MATERIALS} because that table answers what to carry rather than what a block is - and
	 * for three of these the item and the block share a number, which is a coincidence of how block
	 * items are registered and not a rule to lean on.
	 */
	private static final int[][] SWITCHED = {
			{ 62, 61 },  // furnace, lit         -> furnace
			{ 74, 73 },  // redstone ore, lit    -> redstone ore
			{ 75, 76 },  // redstone torch, off  -> redstone torch
			{ 94, 93 },  // repeater, powered    -> repeater
	};

	/** The block a player puts down to get this one, which for all but a handful is itself. */
	public static int placedForm(int blockId) {
		for (int[] pair : SWITCHED) {
			if (pair[0] == blockId) {
				return pair[1];
			}
		}
		return blockId;
	}

	public static Item itemAt(int itemId) {
		return itemId > 0 && itemId < Item.ITEMS.length ? Item.ITEMS[itemId] : null;
	}
}
