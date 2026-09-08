package lunatrius.schematica.util;

import java.util.Locale;

/**
 * How much of something a number of it comes to, in the stacks you would actually carry it in.
 *
 * <p>A material list counts blocks, and a pack holds stacks, so a build wanting seventy cobblestone
 * wants a stack and six - which is the number a player packing a chest is working in, and the one
 * they would otherwise be doing in their head at every row.
 *
 * <p>The stack size is asked of the item rather than assumed, since not everything holds
 * sixty-four: a snowball stacks to sixteen, and a door, a bed, a sign or a bucket does not stack at
 * all. Anything that does not stack has nothing to say here and says nothing.
 */
public final class Stacks {
	private Stacks() {
	}

	/**
	 * A count written as stacks and what is left over, or an empty string where that would be no
	 * help: an item that does not stack, and a count that does not fill one.
	 */
	public static String describe(int count, int stackSize) {
		if (stackSize <= 1 || count < stackSize) {
			return "";
		}

		int stacks = count / stackSize;
		int rest = count % stackSize;
		String full = String.format(Locale.ROOT, "%,d", stacks) + "x" + stackSize;
		return rest == 0 ? full : full + " + " + rest;
	}
}
