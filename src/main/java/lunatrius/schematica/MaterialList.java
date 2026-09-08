package lunatrius.schematica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.util.ItemNames;
import net.minecraft.Item;
import net.minecraft.item.ItemStack;

/**
 * What a schematic is built out of: one row for each kind of item it takes, how many of them it
 * takes, and how many the player is carrying.
 *
 * <p>The rows are what {@link BlockItems} says you would go and fetch, not the blocks the schematic
 * holds - a stack of redstone rather than a pile of wire, a bucket rather than a block of water, one
 * door rather than its two halves. A block that is no material of its own has no row at all: a
 * piston head comes out of a piston, water runs from its source, fire is lit rather than placed.
 *
 * <p>Counting the schematic is a walk of every block in it and is done once, when the list is made.
 * Counting the inventory is 36 slots and is done again whenever the list is looked at, which is what
 * makes the numbers fall as the player picks things up.
 */
public final class MaterialList {
	private static final int BED = 26;
	private static final int WOODEN_DOOR = 64;
	private static final int IRON_DOOR = 71;
	/** The metadata bit doors and beds use for their second half, which the first one places. */
	private static final int SECOND_HALF = 8;

	/** How many values a block's metadata can take, plus one for "the metadata does not matter". */
	private static final int KEY_STRIDE = 17;

	private final List<Entry> entries = new ArrayList<>();
	private int totalNeeded;

	private MaterialList() {
	}

	/** One kind of item: what it is, how many the schematic wants, how many the player has. */
	public static final class Entry {
		private final int itemId;
		/** The block metadata wanted here, which is what a stack has to be able to put down. */
		private final int metadata;
		/** Whether that metadata is the thing that tells one stack of this item from another. */
		private final boolean matchDamage;
		/** One of them, for the icon and the name. */
		private final ItemStack stack;

		private int needed;
		private int have;
		/** Looked up once and kept: the info HUD sorts on it, and does so again every tick. */
		private String name;

		private Entry(int itemId, int damage, int metadata, boolean matchDamage) {
			this.itemId = itemId;
			this.metadata = metadata;
			this.matchDamage = matchDamage;
			this.stack = ItemNames.stack(itemId, damage);
		}

		public ItemStack getStack() {
			return this.stack;
		}

		public int getNeeded() {
			return this.needed;
		}

		public int getHave() {
			return this.have;
		}

		/** How many still have to be found. Never negative: a spare stack is not a debt. */
		public int getMissing() {
			return Math.max(0, this.needed - this.have);
		}

		/** The name the game itself gives this stack, so a wool row says which colour it is. */
		public String getName() {
			if (this.name == null) {
				this.name = ItemNames.of(this.stack);
			}
			return this.name;
		}

		private boolean accepts(ItemStack stack) {
			return BlockItems.matches(stack, this.itemId, this.metadata, this.matchDamage);
		}

		/** Whether this row asks for exactly this stack, rather than being one that would take it. */
		private boolean isExactly(ItemStack stack) {
			return this.matchDamage && stack.getDamage() == this.stack.getDamage();
		}
	}

	/** Counts what a schematic is built out of. Nothing loaded gives an empty list. */
	public static MaterialList of(Schematic schematic) {
		MaterialList list = new MaterialList();
		if (schematic == null) {
			return list;
		}

		Map<Integer, Entry> rows = new HashMap<>();
		for (int x = 0; x < schematic.getWidth(); x++) {
			for (int y = 0; y < schematic.getHeight(); y++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					list.add(rows, schematic.getBlockId(x, y, z), schematic.getMetadata(x, y, z));
				}
			}
		}

		// Ordered by what the schematic takes most of, which is an order that holds still while the
		// player gathers: sorting by what is missing would shuffle the rows about under them.
		list.entries.sort((left, right) -> left.needed != right.needed
				? right.needed - left.needed
				: left.getName().compareToIgnoreCase(right.getName()));
		return list;
	}

	private void add(Map<Integer, Entry> rows, int blockId, int metadata) {
		if (blockId <= 0) {
			return;
		}

		// A door or a bed is two blocks put down by one item, and it is the bottom half that is
		// placed - counting the top half as well would ask for twice as many as it takes.
		if ((metadata & SECOND_HALF) != 0
				&& (blockId == BED || blockId == WOODEN_DOOR || blockId == IRON_DOOR)) {
			return;
		}

		int itemId = BlockItems.materialFor(blockId, metadata);
		Item item = BlockItems.itemAt(itemId);
		if (item == null) {
			// Nothing to go and fetch for it: a piston head comes out of a piston, water runs from a
			// source of its own, fire is lit rather than placed.
			return;
		}

		int damage = BlockItems.damageFor(item, metadata);
		boolean matchDamage = damage >= 0;
		// One row per stack you would go and fetch: where the metadata is what tells two stacks
		// apart it is part of the key, and where it is not - which way a piston ends up facing -
		// every one of them is the same row.
		int key = itemId * KEY_STRIDE + (matchDamage ? metadata + 1 : 0);

		Entry entry = rows.get(key);
		if (entry == null) {
			entry = new Entry(itemId, damage, metadata, matchDamage);
			rows.put(key, entry);
			this.entries.add(entry);
		}

		int count = BlockItems.materialCount(blockId);
		entry.needed += count;
		this.totalNeeded += count;
	}

	/**
	 * Recounts what the player is carrying, which is the half of the list that moves.
	 *
	 * <p>A stack is only ever counted once. Where two rows would both take it - a schematic wanting
	 * leaves both as they grow and as they are placed by hand - the row asking for exactly this
	 * stack gets it, and failing that the first row that would have it.
	 */
	public void countInventory(ItemStack[] stacks) {
		for (Entry entry : this.entries) {
			entry.have = 0;
		}
		if (stacks == null) {
			return;
		}

		for (ItemStack stack : stacks) {
			if (stack == null || stack.count <= 0) {
				continue;
			}

			Entry entry = this.rowFor(stack);
			if (entry != null) {
				entry.have += stack.count;
			}
		}
	}

	private Entry rowFor(ItemStack stack) {
		Entry loose = null;
		for (Entry entry : this.entries) {
			if (!entry.accepts(stack)) {
				continue;
			}
			if (entry.isExactly(stack)) {
				return entry;
			}
			if (loose == null) {
				loose = entry;
			}
		}
		return loose;
	}

	public List<Entry> getEntries() {
		return Collections.unmodifiableList(this.entries);
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public int getTotalNeeded() {
		return this.totalNeeded;
	}

	/** Everything still to be found, across every row. */
	public int getTotalMissing() {
		int missing = 0;
		for (Entry entry : this.entries) {
			missing += entry.getMissing();
		}
		return missing;
	}

	/** Which end of the list the info HUD puts first. */
	public enum Sort {
		/** Most to gather at the top, so the long jobs lead. */
		DESCENDING,
		/** Fewest to gather at the top, so the short jobs lead. */
		ASCENDING
	}

	/**
	 * The rows that are still short, in the order the info HUD wants them.
	 *
	 * <p>A finished row is left out rather than greyed out: the HUD is the list of what to go and
	 * find, and a row that is done is not that. Nothing is remembered between calls, so the row
	 * comes straight back if the stack leaves the pack again.
	 *
	 * <p>Ordered by what the schematic takes and not by what is left of it, which is the same order
	 * the screen uses and for the same reason: a row has to hold its place while it is being worked
	 * on. Sorting on what is missing would push a row down the moment a stack of it went in the
	 * pack - eleven of the thirty redstone a build wants would drop it below twenty cobblestone
	 * nobody had started on - and a row is meant to leave this list by being finished, not by being
	 * begun.
	 */
	public List<Entry> getOutstanding(Sort order) {
		List<Entry> outstanding = new ArrayList<>();
		for (Entry entry : this.entries) {
			if (entry.getMissing() > 0) {
				outstanding.add(entry);
			}
		}

		outstanding.sort((left, right) -> {
			int difference = order == Sort.ASCENDING
					? left.needed - right.needed
					: right.needed - left.needed;
			return difference != 0 ? difference : left.getName().compareToIgnoreCase(right.getName());
		});
		return outstanding;
	}
}
