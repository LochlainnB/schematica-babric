package lunatrius.schematica;

import java.util.HashSet;
import java.util.Set;

import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import net.minecraft.MultiplayerInteractionManager;
import net.minecraft.class_363;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Moves a block from the rest of the inventory onto the hotbar, so easy place can put it in the
 * player's hand even when it was not to hand to begin with.
 *
 * <p>An empty hotbar slot can be filled with a shift click. A full bar needs a swap instead:
 * shift-clicking its spare block into a full inventory cannot make room. Beta has no number-key
 * swap, so three ordinary clicks exchange the stacks, returning the displaced one to the source
 * slot. All three run together, never leaving a stack on the cursor between ticks.
 *
 * <p>On a server the click is applied here first and sent second, which is a guess about what the
 * server will do with it. Until the server has answered, easy place places nothing: what is in hand
 * is not settled yet. A rejected exchange resynchronizes first, then puts any stranded cursor
 * stack away before another restock can begin.
 */
public final class HotbarRestock {
	/** The player's own inventory is window 0, whether a screen is open on it or not. */
	private static final int PLAYER_WINDOW = 0;
	/** Where the hotbar starts in the player container; the rest of the inventory is 9 to 35. */
	private static final int HOTBAR_SLOT = 36;
	private static final int HOTBAR_SIZE = 9;

	/** Ticks to wait before warning about a missing answer; cursor-bearing swaps remain protected. */
	private static final int CONFIRM_TIMEOUT = 40;
	/** Ticks to hold off after a swap the server refused, or one that could not be made at all. */
	private static final int RETRY_DELAY = 20;

	/** The actual transaction ids of our clicks, not other inventory actions. */
	private static final Set<Short> pendingClicks = new HashSet<>();
	private static boolean sendingClick = false;
	/** A cursor-bearing swap remains ours until it is confirmed or safely put away after resync. */
	private static int swapSource = -1;
	private static boolean awaitingContents = false;
	private static boolean awaitingCursor = false;
	/** Cleanup is predicted only after confirmation, so the old resync cannot overwrite it. */
	private static ItemStack cleanupStack = null;
	private static boolean manualRecovery = false;
	private static int waitTicks = 0;
	/** Swaps the server turned down, counted since the last world change. */
	private static int refusals = 0;

	private HotbarRestock() {
	}

	/** Whether easy place should hold off: a swap is in the air, or one has just failed. */
	public static boolean isBusy() {
		return !pendingClicks.isEmpty() || waitTicks > 0 || swapSource >= 0;
	}

	public static void tick(Minecraft mc) {
		if (manualRecovery && mc.player != null && mc.player.inventory.getCursorStack() == null) {
			swapSource = -1;
			manualRecovery = false;
			waitTicks = RETRY_DELAY;
		}
		if (waitTicks == 0) {
			return;
		}

		if (--waitTicks == 0 && isWaitingForServer()) {
			if (swapSource >= 0) {
				// Silence cannot tell us whether the server is still holding a stack on its cursor.
				Log.warn("Waiting for the server to finish an inventory swap; keeping its cursor protected");
			} else {
				Log.warn("The server did not answer an inventory swap; carrying on without it");
				pendingClicks.clear();
			}
		}
	}

	public static void reset() {
		pendingClicks.clear();
		sendingClick = false;
		swapSource = -1;
		awaitingContents = false;
		awaitingCursor = false;
		cleanupStack = null;
		manualRecovery = false;
		waitTicks = 0;
		refusals = 0;
	}

	/** How many swaps the server has turned down. Zero is the answer this is asked for. */
	public static int getRefusals() {
		return refusals;
	}

	/** Whether a swap has gone out and is waiting on the server, as opposed to simply holding off. */
	public static boolean isWaitingForServer() {
		return !pendingClicks.isEmpty() || awaitingContents || awaitingCursor;
	}

	/** Container transitions and use must not interrupt an exchange on the server's cursor. */
	public static boolean isCursorSwapPending() {
		return swapSource >= 0;
	}

	public static boolean needsManualRecovery() {
		return manualRecovery;
	}

	/** Called as vanilla sends a click, so its revision is recorded without allocating another. */
	public static void onClickSent(class_363 packet) {
		if (sendingClick && packet.field_1362 == PLAYER_WINDOW) {
			pendingClicks.add(packet.field_1365);
			lunatrius.schematica.debug.SmokeTest.onRestockClick(packet);
		}
	}

	/** The server's word on a slot click, from {@code ClientNetworkHandlerMixin}. */
	public static void onTransaction(int windowId, short actionId, boolean accepted) {
		if (windowId != PLAYER_WINDOW || !pendingClicks.remove(actionId)) {
			return;
		}

		if (!accepted) {
			Log.warn("The server turned down an inventory swap; it will be tried again shortly");
			refusals++;
			pendingClicks.clear();
			cleanupStack = null;
			// Vanilla applied the click before refusing it, and will ignore the remaining clicks.
			// Its full inventory update followed by its cursor update tells us what needs putting away.
			awaitingContents = swapSource >= 0;
			awaitingCursor = false;
			waitTicks = awaitingContents ? CONFIRM_TIMEOUT : RETRY_DELAY;
			return;
		}

		if (pendingClicks.isEmpty()) {
			if (cleanupStack != null) {
				Minecraft mc = Schematica.getMinecraft();
				// The server confirmed a deposit into an empty slot. Apply just that confirmed move
				// after all of the pre-cleanup slot updates that arrived before its acknowledgment.
				mc.player.playerContainer.method_2084(swapSource).setStack(cleanupStack);
				mc.player.inventory.setCursorStack(null);
				cleanupStack = null;
			}
			swapSource = -1;
			waitTicks = 0;
		}
	}

	public static void onInventoryContents(int windowId) {
		if (windowId == PLAYER_WINDOW && awaitingContents) {
			awaitingContents = false;
			awaitingCursor = true;
		}
	}

	/** The rejection acknowledgment is queued before this resync's cursor update arrives. */
	public static void onCursorUpdate(Minecraft mc) {
		if (!awaitingCursor) {
			return;
		}
		awaitingCursor = false;
		PlayerInventory inventory = mc.player.inventory;
		if (inventory.getCursorStack() == null) {
			swapSource = -1;
			waitTicks = RETRY_DELAY;
			return;
		}

		if (inventory.main[swapSource] != null) {
			// An item picked up during the round trip can fill the hole the exchange made.
			int empty = -1;
			for (int slot = HOTBAR_SIZE; slot < inventory.main.length; slot++) {
				if (inventory.main[slot] == null) {
					empty = slot;
					break;
				}
			}
			if (empty < 0) {
				manualRecovery = true;
				waitTicks = 0;
				mc.inGameHud.addChatMessage("Schematica: open your inventory and put away the cursor stack to finish the swap.");
				return;
			}
			swapSource = empty;
		}

		// Never swap again here: just fill an authoritative empty slot and confirm that one click.
		cleanupStack = inventory.getCursorStack().clone();
		clickSlot(mc, swapSource, false);
		waitTicks = CONFIRM_TIMEOUT;
	}

	/**
	 * Moves the stack in the given inventory slot onto the hotbar. Returns true when the swap was
	 * made, which is when easy place waits for the server before it places anything else.
	 */
	public static boolean moveToHotbar(Minecraft mc, int inventorySlot) {
		// The slot numbers below are the player's own container. Easy place only ever runs with no
		// screen open, so that is what is in front of the player - but clicking these numbers into
		// somebody's chest would move the wrong things, so it is worth being sure rather than sorry.
		if (isBusy() || mc.player.container != mc.player.playerContainer) {
			return false;
		}

		PlayerInventory inventory = mc.player.inventory;
		ItemStack source = inventory.main[inventorySlot];
		if (source == null || inventory.getCursorStack() != null) {
			return false;
		}

		int itemId = source.itemId;
		int damage = source.getDamage();
		boolean multiplayer = mc.interactionManager instanceof MultiplayerInteractionManager;

		if (emptyHotbarSlot(inventory) < 0) {
			int victim = victimSlot(inventory);
			if (victim < 0) {
				// Nothing on the bar can be given up: it is all tools, or all blocks this build still
				// needs. Leaving the hotbar as the player arranged it beats shuffling something off it.
				waitTicks = RETRY_DELAY;
				return false;
			}

			if (multiplayer) {
				swapSource = inventorySlot;
			}
			clickSlot(mc, inventorySlot, false);
			clickSlot(mc, HOTBAR_SLOT + victim, false);
			clickSlot(mc, inventorySlot, false);
		} else {
			clickSlot(mc, inventorySlot, true);
		}

		// The clicks have already been applied here, so whether they worked is a question that can be
		// answered now, before waiting on a server to confirm them.
		if (hotbarSlotOf(inventory, itemId, damage) < 0) {
			waitTicks = RETRY_DELAY;
			return false;
		}

		// In single player the clicks are the real thing rather than a guess - there is nobody to
		// disagree with, and no transaction will ever come back.
		if (multiplayer) {
			waitTicks = CONFIRM_TIMEOUT;
		}
		return true;
	}

	private static void clickSlot(Minecraft mc, int slot, boolean shift) {
		sendingClick = true;
		try {
			if (cleanupStack != null) {
				// Report the empty destination without predicting the deposit yet: an older slot
				// update from the rejection can still be in flight and would erase that prediction.
				short actionId = mc.player.playerContainer.nextRevision(mc.player.inventory);
				mc.getNetworkHandler().sendPacket(new class_363(PLAYER_WINDOW, slot, 0, false, null, actionId));
			} else {
				mc.interactionManager.clickSlot(PLAYER_WINDOW, slot, 0, shift, mc.player);
			}
		} finally {
			sendingClick = false;
		}
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
	 * Every item any open schematic would be built out of. Walked rather than kept up to date,
	 * because it is only ever asked for on the rare click that finds the hotbar full.
	 *
	 * <p>All of them rather than the one the move screen is pointed at, because easy place builds
	 * whichever one is being aimed at: a slot holding something from the schematic next door is
	 * still a slot the next click may want back.
	 */
	private static Set<Integer> itemsUsed() {
		Set<Integer> items = new HashSet<>();
		for (OpenSchematic open : Schematica.STATE.getOpen()) {
			SchematicWorld world = open.schematic;
			if (world == null) {
				continue;
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
		}

		return items;
	}
}
