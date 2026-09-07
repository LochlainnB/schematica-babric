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
	 * The damage a stack has to carry to put down a block with this metadata, or -1 when none of
	 * them does.
	 *
	 * <p>Where there is an answer, the metadata is the thing that tells one stack of the item from
	 * another - wool colour, wood type, sapling type - and only that stack will do. Where there is
	 * not, the metadata is coming from somewhere else entirely: which way a piston faces, the bit
	 * leaves get for being placed by hand. Insisting on a match there would refuse to place those
	 * blocks at all, so any stack of the item is taken instead.
	 */
	public static int damageFor(Item item, int metadata) {
		for (int damage = 0; damage < VALUES; damage++) {
			if (item.method_470(damage) == metadata) {
				return damage;
			}
		}
		return -1;
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

	public static Item itemAt(int itemId) {
		return itemId > 0 && itemId < Item.ITEMS.length ? Item.ITEMS[itemId] : null;
	}
}
