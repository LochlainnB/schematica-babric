package lunatrius.schematica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lunatrius.schematica.schematic.BlockSwap;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.util.ItemNames;
import lunatrius.schematica.util.Translations;
import net.minecraft.item.ItemStack;

/**
 * What a schematic is made of, block by block.
 *
 * <p>Next to {@link MaterialList}, which counts the same schematic in items: a stack of redstone
 * rather than a pile of wire, one door rather than its two halves, nothing at all for a piston head.
 * That is the right list for going and fetching things, and the wrong one for changing them - a
 * block has to be named as the block it is before it can be swapped for another. So this list is
 * one row per kind of block actually in the file, and the two disagree about a schematic in all the
 * places where what you carry is not what ends up standing there.
 *
 * <p>A row is a block and its variant rather than a block: red wool and white wool are two things a
 * build is made of and two things to point at, while a stair facing north and a stair facing south
 * are one thing put down twice. {@link BlockSwap} draws that line.
 *
 * <p>The half of a pair the game switches between on its own is not a kind of block of its own. A
 * redstone torch is counted as a redstone torch whether the file caught it lit or not, and a
 * furnace as a furnace whether or not it was smelting at the time. That is {@link BlockItems}'
 * answer, so it is the same answer the material list gets, and a schematic saved a second later
 * reads as the same schematic.
 *
 * <p>Counting is a walk of every block in the schematic, so the screen holding this counts once and
 * again after each replacement, rather than on every frame or resize.
 */
public final class BlockPalette {
	/** How many variants a block id can have, which is what makes a row's key out of the pair. */
	private static final int VARIANTS = 16;

	private final List<Entry> entries = new ArrayList<>();

	private BlockPalette() {
	}

	/**
	 * One kind of block: what it is, how much of it there is, and every metadata value it is holding.
	 *
	 * <p>Also what a replacement is offered as, in which case the count is zero and the metadata is
	 * empty - it is a block that could be here rather than one that is. The same class because the
	 * screens want the same things of both: an icon, a name, and enough to swap one for the other.
	 */
	public static final class Entry {
		private final int blockId;
		private final int variant;
		private final ItemStack stack;

		private int count;
		/** Which metadata values this block carries, as a mask over the values themselves. */
		private int metadataSeen;
		/** Looked up once and kept, since the list is sorted on it. */
		private String name;
		/** Whether anything could stand in for it, worked out on demand and kept for the same reason. */
		private Boolean replaceable;

		private Entry(int blockId, int variant) {
			this.blockId = blockId;
			this.variant = variant;
			this.stack = stackFor(blockId, variant);
		}

		public int getBlockId() {
			return this.blockId;
		}

		public int getVariant() {
			return this.variant;
		}

		/** How many of it the schematic holds, or zero for a block only being offered. */
		public int getCount() {
			return this.count;
		}

		public ItemStack getStack() {
			return this.stack;
		}

		public String getName() {
			if (this.name == null) {
				this.name = nameFor(this.blockId, this.variant, this.stack);
			}
			return this.name;
		}

		/** Every block that could stand in for this one, in the order they should be listed. */
		public List<Entry> getReplacements() {
			List<Entry> replacements = new ArrayList<>();
			for (int[] pair : BlockSwap.replacementsFor(this.blockId, this.variant, this.metadataSeen)) {
				replacements.add(new Entry(pair[0], pair[1]));
			}
			return replacements;
		}

		/**
		 * Whether anything at all could take its place, which is what greys the row's button out.
		 *
		 * <p>Worked out once and kept. It is asked of every row on the screen on every frame, and
		 * what it is asking is a walk of the whole block table - and the answer cannot change, since
		 * a row is counted from a schematic that is not being changed while it is on the screen.
		 */
		public boolean hasReplacements() {
			if (this.replaceable == null) {
				this.replaceable = BlockSwap.hasReplacement(this.blockId, this.variant, this.metadataSeen);
			}
			return this.replaceable;
		}
	}

	/** Counts what a schematic is made of. Nothing loaded gives an empty list. */
	public static BlockPalette of(Schematic schematic) {
		BlockPalette palette = new BlockPalette();
		if (schematic == null) {
			return palette;
		}

		Map<Integer, Entry> rows = new HashMap<>();
		for (int x = 0; x < schematic.getWidth(); x++) {
			for (int y = 0; y < schematic.getHeight(); y++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					palette.add(rows, schematic.getBlockId(x, y, z), schematic.getMetadata(x, y, z));
				}
			}
		}

		// The same order the material list uses: most of it first, and alphabetical between rows
		// holding the same amount, so a list looked at twice reads the same way twice.
		palette.entries.sort((left, right) -> left.count != right.count
				? right.count - left.count
				: left.getName().compareToIgnoreCase(right.getName()));
		return palette;
	}

	private void add(Map<Integer, Entry> rows, int blockId, int metadata) {
		if (blockId <= 0) {
			// Air is not something a schematic is made of, and not something to replace: a schematic
			// is mostly air, and a row saying so would be the biggest one on every screen.
			return;
		}

		// A redstone torch the schematic caught with the power on is a redstone torch, and belongs
		// on the row with the rest of them rather than on one of its own reading the same name.
		blockId = BlockItems.placedForm(blockId);

		int variant = BlockSwap.variantOf(blockId, metadata);
		int key = blockId * VARIANTS + variant;
		Entry entry = rows.get(key);
		if (entry == null) {
			entry = new Entry(blockId, variant);
			rows.put(key, entry);
			this.entries.add(entry);
		}

		entry.count++;
		// Metadata comes off disk as a whole byte and only the low nibble is a block's own, so a
		// schematic written by something that used the rest cannot set a bit that is not there.
		entry.metadataSeen |= 1 << (metadata & 0xF);
	}

	public List<Entry> getEntries() {
		return Collections.unmodifiableList(this.entries);
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	/**
	 * Swaps every block of one kind in a schematic for another, and says how many changed.
	 *
	 * <p>Each block's metadata is worked out from its own, so a stair that was facing north still
	 * faces north and the one beside it facing east still faces east. The row being replaced was
	 * counted from this same schematic, so what is found here is what the row said would be -
	 * including the blocks that were counted under another id than the one they are stored as, a
	 * row of redstone torches being every redstone torch whichever way the circuit had them.
	 */
	public static int replace(Schematic schematic, Entry from, Entry to) {
		if (schematic == null || from == null || to == null) {
			return 0;
		}

		int replaced = 0;
		for (int x = 0; x < schematic.getWidth(); x++) {
			for (int y = 0; y < schematic.getHeight(); y++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					int blockId = schematic.getBlockId(x, y, z);
					if (BlockItems.placedForm(blockId) != from.blockId) {
						continue;
					}

					int metadata = schematic.getMetadata(x, y, z);
					if (BlockSwap.variantOf(from.blockId, metadata) != from.variant) {
						continue;
					}

					schematic.setBlockId(x, y, z, to.blockId);
					schematic.setMetadata(x, y, z,
							BlockSwap.metadataFor(from.blockId, metadata, to.blockId, to.variant));
					replaced++;
				}
			}
		}
		return replaced;
	}

	/**
	 * The stack a row is drawn from: the item that puts this block down, holding the damage that
	 * makes it this variant of it.
	 *
	 * <p>The item rather than the block, so a door row shows a door and not the half-drawn face a
	 * door block wears, which is the same choice - and the same icon - the material list makes.
	 */
	private static ItemStack stackFor(int blockId, int variant) {
		int itemId = BlockItems.itemFor(blockId);
		return ItemNames.stack(itemId > 0 ? itemId : blockId, variant);
	}

	/**
	 * What to call a block on screen.
	 *
	 * <p>The game's own name for it wherever that names it, which for wool and slabs is a good one
	 * that already says which. Where it does not - three kinds of wood all called Wood, two
	 * mushrooms both called Mushroom, a double slab the language file never had a line for - the
	 * mod's own file has the name instead, since a list where two rows read the same is a list you
	 * cannot choose from.
	 */
	private static String nameFor(int blockId, int variant, ItemStack stack) {
		String key = "schematic.block." + blockId + "." + variant;
		String name = Translations.get(key);
		return name.equals(key) ? ItemNames.of(stack) : name;
	}
}
