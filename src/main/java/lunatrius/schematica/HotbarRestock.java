package lunatrius.schematica;

import java.util.HashSet;
import java.util.Set;

import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import net.minecraft.MultiplayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Moves a block from the rest of the inventory onto the hotbar, so easy place can put it in the
 * player's hand even when it was not to hand to begin with.
 *
 * <p>Beta has no click that swaps a slot straight into the hotbar - that arrives with the number-key
 * swap in a later version - so this is done with shift clicks, which move a stack between the bar
 * and the inventory <em>without it ever passing through the cursor</em>. That is the whole reason
 * for the choice: the obvious pick-up, put-down, put-back sequence leaves a stack on the cursor
 * between clicks, and a cursor left holding something is dropped on the floor the moment any
 * container closes. Shift clicks have no such half-finished state, so a click the server turns down
 * is a click that did nothing.
 *
 * <p>On a server the click is applied here first and sent second, which is a guess about what the
 * server will do with it. Until the server has answered, easy place places nothing: what is in hand
 * is not settled yet. A refusal costs a round trip and a retry, never an item.
 */
public final class HotbarRestock {
	/** The player's own inventory is window 0, whether a screen is open on it or not. */
	private static final int PLAYER_WINDOW = 0;
	/** Where the hotbar starts in the player container; the rest of the inventory is 9 to 35. */
	private static final int HOTBAR_SLOT = 36;
	private static final int HOTBAR_SIZE = 9;

	/** Ticks to wait for the server to answer before giving up on a swap and carrying on. */
	private static final int CONFIRM_TIMEOUT = 40;
	/** Ticks to hold off after a swap the server refused, or one that could not be made at all. */
	private static final int RETRY_DELAY = 20;

	/** Clicks sent and not yet answered. Only ever set on a server. */
	private static int pendingClicks = 0;
	private static int waitTicks = 0;
	/** Swaps the server turned down, counted since the last world change. */
	private static int refusals = 0;

	private HotbarRestock() {
	}

	/** Whether easy place should hold off: a swap is in the air, or one has just failed. */
	public static boolean isBusy() {
		return pendingClicks > 0 || waitTicks > 0;
	}

	public static void tick() {
		if (waitTicks == 0) {
			return;
		}

		if (--waitTicks == 0 && pendingClicks > 0) {
			// The server never answered. Letting go beats wedging the mode for the rest of the session.
			Log.warn("The server did not answer an inventory swap; carrying on without it");
			pendingClicks = 0;
		}
	}

	public static void reset() {
		pendingClicks = 0;
		waitTicks = 0;
		refusals = 0;
	}

	/** How many swaps the server has turned down. Zero is the answer this is asked for. */
	public static int getRefusals() {
		return refusals;
	}

	/** Whether a swap has gone out and is waiting on the server, as opposed to simply holding off. */
	public static boolean isWaitingForServer() {
		return pendingClicks > 0;
	}

	/** The server's word on a slot click, from {@code ClientNetworkHandlerMixin}. */
	public static void onTransaction(int windowId, boolean accepted) {
		if (windowId != PLAYER_WINDOW || pendingClicks == 0) {
			return;
		}

		if (!accepted) {
			// A refused click also shuts the container to us until the reply the client sends back
			// lands, so there is nothing to gain by trying again immediately.
			Log.warn("The server turned down an inventory swap; it will be tried again shortly");
			refusals++;
			pendingClicks = 0;
			waitTicks = RETRY_DELAY;
			return;
		}

		if (--pendingClicks == 0) {
			waitTicks = 0;
		}
	}

	/**
	 * Moves the stack in the given inventory slot onto the hotbar. Returns true when the swap was
	 * made, which is when easy place waits for the server before it places anything else.
	 */
	public static boolean moveToHotbar(Minecraft mc, int inventorySlot) {
		// The slot numbers below are the player's own container. Easy place only ever runs with no
		// screen open, so that is what is in front of the player - but clicking these numbers into
		// somebody's chest would move the wrong things, so it is worth being sure rather than sorry.
		if (mc.player.container != mc.player.playerContainer) {
			return false;
		}

		PlayerInventory inventory = mc.player.inventory;
		ItemStack source = inventory.main[inventorySlot];
		if (source == null) {
			return false;
		}

		int itemId = source.itemId;
		int damage = source.getDamage();
		int clicks = 0;

		if (emptyHotbarSlot(inventory) < 0) {
			int victim = victimSlot(inventory);
			if (victim < 0) {
				// Nothing on the bar can be given up: it is all tools, or all blocks this build still
				// needs. Leaving the hotbar as the player arranged it beats shuffling something off it.
				waitTicks = RETRY_DELAY;
				return false;
			}

			clickSlot(mc, HOTBAR_SLOT + victim);
			clicks++;
		}

		clickSlot(mc, inventorySlot);
		clicks++;

		// The clicks have already been applied here, so whether they worked is a question that can be
		// answered now: a full inventory leaves the evicted stack nowhere to go and nothing moves.
		if (hotbarSlotOf(inventory, itemId, damage) < 0) {
			waitTicks = RETRY_DELAY;
			return false;
		}

		// In single player the clicks are the real thing rather than a guess - there is nobody to
		// disagree with, and no transaction will ever come back.
		if (mc.interactionManager instanceof MultiplayerInteractionManager) {
			pendingClicks = clicks;
			waitTicks = CONFIRM_TIMEOUT;
		}
		return true;
	}

	/**
	 * Shift, so the stack moves between the bar and the inventory on its own. Which slot it lands in
	 * is the container's choice, not ours - it fills the first empty one - which is why the bar is
	 * given a free slot before the block is sent to it.
	 */
	private static void clickSlot(Minecraft mc, int slot) {
		mc.interactionManager.clickSlot(PLAYER_WINDOW, slot, 0, true, mc.player);
	}

	private static int emptyHotbarSlot(PlayerInventory inventory) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (inventory.main[slot] == null) {
				return slot;
			}
		}
		return -1;
	}

	private static int hotbarSlotOf(PlayerInventory inventory, int itemId, int damage) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.main[slot];
			if (stack != null && stack.itemId == itemId && stack.getDamage() == damage) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * Which hotbar slot to give up when the bar is full. Only ever a block: a tool, a bucket or
	 * anything else the player is carrying for its own sake is never shuffled away, and neither is
	 * whatever is in hand. A block this schematic uses nowhere goes first, since it is left over from
	 * something else; failing that, the first block that can be spared.
	 */
	private static int victimSlot(PlayerInventory inventory) {
		Set<Integer> used = itemsUsed();
		int spare = -1;

		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.main[slot];
			if (stack == null || slot == inventory.selectedSlot || !BlockItems.isBlockItem(stack)) {
				continue;
			}

			if (!used.contains(stack.itemId)) {
				return slot;
			}
			if (spare < 0) {
				spare = slot;
			}
		}

		return spare;
	}

	/**
	 * Every item the loaded schematic would be built out of. Walked rather than kept up to date,
	 * because it is only ever asked for on the rare click that finds the hotbar full.
	 */
	private static Set<Integer> itemsUsed() {
		Set<Integer> items = new HashSet<>();
		SchematicWorld world = Schematica.STATE.schematic;
		if (world == null) {
			return items;
		}

		Schematic schematic = world.getSchematic();
		for (int x = 0; x < schematic.getWidth(); x++) {
			for (int y = 0; y < schematic.getHeight(); y++) {
				for (int z = 0; z < schematic.getLength(); z++) {
					int item = BlockItems.itemFor(schematic.getBlockId(x, y, z));
					if (item > 0) {
						items.add(item);
					}
				}
			}
		}

		return items;
	}
}
