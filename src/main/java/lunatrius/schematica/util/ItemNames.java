package lunatrius.schematica.util;

import net.minecraft.item.ItemStack;

/**
 * The stack a row is drawn from, and what the game calls it.
 *
 * <p>Shared because two lists put the same rows on the screen from different directions: the
 * material list counts the items a schematic would take, and the replace screen counts the blocks
 * it is made of. Both want an icon and a name, and both are handed ids out of a file rather than
 * out of the game, so both need the same care with ones the game does not know.
 */
public final class ItemNames {
	private ItemNames() {
	}

	/**
	 * A stack to draw, or the plain item where that damage is more than the game can show.
	 *
	 * <p>A schematic written by a later version can carry metadata this one never made - a slab
	 * laid upside down, say - and the game's own name and icon tables are sized for the metadata it
	 * did, so the stack is tried before it is kept. A row that reads a little vaguely is a better
	 * answer than a list that cannot be opened.
	 */
	public static ItemStack stack(int itemId, int damage) {
		ItemStack stack = new ItemStack(itemId, 1, Math.max(damage, 0));
		try {
			stack.getTranslationKey();
			stack.method_725();
			return stack;
		} catch (RuntimeException exception) {
			return new ItemStack(itemId, 1, 0);
		}
	}

	/** The name the game itself gives a stack, so a wool row says which colour it is. */
	public static String of(ItemStack stack) {
		String key;
		try {
			key = stack.getTranslationKey() + ".name";
		} catch (RuntimeException exception) {
			// A block the game has no item name for at all - a piston head, the block a piston is
			// pushing - which only a schematic would ever ask about.
			return "#" + stack.itemId;
		}

		String name = Translations.get(key);
		if (!name.equals(key)) {
			return name;
		}

		// Nothing in the language file for it. The bare key is no use to anyone, but the part of it
		// that names the block still is.
		int dot = key.lastIndexOf('.', key.length() - ".name".length() - 1);
		return dot < 0 ? key : key.substring(dot + 1, key.length() - ".name".length());
	}
}
