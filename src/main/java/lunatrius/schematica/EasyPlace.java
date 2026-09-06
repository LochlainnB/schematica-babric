package lunatrius.schematica;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.minecraft.Item;
import net.minecraft.block.Block;
import net.minecraft.class_212;
import net.minecraft.class_27;
import net.minecraft.class_533;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Easy place: while it is on, a right click can only ever put down the block the schematic wants at
 * the position being aimed at - and it puts that block in the player's hand first.
 *
 * <p>Everything is decided before the click reaches the interaction manager, which is what makes it
 * behave the same on a server as in single player: a click dropped here never becomes a placement
 * packet, so there is nothing for the server to place either.
 *
 * <p>A click is only ever taken away from the player when it would have placed a block. An empty
 * hand, a tool or a piece of food is left alone, and so is any block that handles right clicks
 * itself, so chests, doors and buttons still work with a stack of something in hand.
 */
public final class EasyPlace {
	/** Which neighbour of the clicked block a new one lands in, indexed by the face that was hit. */
	private static final int[] OFFSET_X = { 0, 0, 0, 0, -1, 1 };
	private static final int[] OFFSET_Y = { -1, 1, 0, 0, 0, 0 };
	private static final int[] OFFSET_Z = { 0, 0, -1, 1, 0, 0 };

	/** The one block a placement replaces rather than lands next to. */
	private static final int SNOW_LAYER = 78;

	private static final int BED = 26;
	private static final int WOODEN_DOOR = 64;
	private static final int IRON_DOOR = 71;
	/** The metadata bit doors and beds use for their second half, which the first one places. */
	private static final int SECOND_HALF = 8;

	private static final int HOTBAR_SIZE = 9;

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

	/** Vanilla's tick rate, which is also the fastest a placement can be repeated. */
	private static final float TICK_RATE = 20.0F;

	private static final Method BLOCK_ON_USE = findOnUse();
	private static final Map<Class<?>, Boolean> HANDLES_USE = new HashMap<>();

	private EasyPlace() {
	}

	/**
	 * Whether easy place has anything to act on: the mode is on, and there is an overlay being drawn
	 * to build against. Everything the mode does hangs off this, so it cannot place faster while it
	 * is not also holding clicks back - with nothing loaded, or the schematic hidden, it is off in
	 * every way that can be felt.
	 */
	private static boolean isEngaged() {
		SchematicaState state = Schematica.STATE;
		return state.isEasyPlace && state.schematic != null && state.isRenderingSchematic;
	}

	/**
	 * Decides what happens to a right click on a block. Returns true when the click has to be
	 * dropped; when it returns false it may have switched the held item to the one the schematic
	 * asked for first.
	 */
	public static boolean interceptUse(Minecraft mc) {
		if (!isEngaged()) {
			return false;
		}
		if (mc.world == null || mc.player == null) {
			return false;
		}

		class_27 hit = mc.field_2823;
		if (hit == null || hit.field_1983 != class_212.TILE) {
			return false;
		}

		// Only a click that is, or could be, a block going down is ours to take.
		if (!isPlacement(mc.player.inventory.getSelectedItem())) {
			return false;
		}

		Block clicked = blockAt(mc.world, hit.field_1984, hit.field_1985, hit.field_1986);
		if (clicked != null && handlesUse(clicked)) {
			return false;
		}

		return !place(mc, hit);
	}

	/**
	 * Puts the block the schematic wants at the aimed-at position into the player's hand. Returns
	 * false when there is nothing to place there, or nothing to place it with, which is what turns
	 * the click into a no-op.
	 */
	private static boolean place(Minecraft mc, class_27 hit) {
		World world = mc.world;
		SchematicaState state = Schematica.STATE;

		int x = hit.field_1984;
		int y = hit.field_1985;
		int z = hit.field_1986;
		int side = hit.field_1987;

		// Where the block would land, by vanilla's own rule: across the face that was clicked, unless
		// the click was on a snow layer, which a new block replaces where it stands.
		if (world.getBlockId(x, y, z) != SNOW_LAYER) {
			if (side < 0 || side >= OFFSET_X.length) {
				return false;
			}
			x += OFFSET_X[side];
			y += OFFSET_Y[side];
			z += OFFSET_Z[side];
		}

		int localX = x - state.offset.x;
		int localY = y - state.offset.y;
		int localZ = z - state.offset.z;

		// A layer slice is the course being built, so a position outside it is not a target either -
		// there is nothing drawn there to aim at.
		if (state.renderingLayer != -1 && state.renderingLayer != localY) {
			return false;
		}

		int wantedId = state.schematic.getBlockId(localX, localY, localZ);
		Block wanted = blockById(wantedId);
		if (wanted == null) {
			return false;
		}

		// A door or a bed is two blocks put down by one click, and it is the bottom half that is
		// placed; aiming at the top half is aiming at something the other half brings with it.
		int wantedMetadata = state.schematic.method_1778(localX, localY, localZ);
		if ((wantedMetadata & SECOND_HALF) != 0
				&& (wantedId == BED || wantedId == WOODEN_DOOR || wantedId == IRON_DOOR)) {
			return false;
		}

		// Already built, or something else is in the way - vanilla would refuse the placement anyway.
		if (!wanted.method_1567(world, x, y, z)) {
			return false;
		}

		return selectItem(mc.player.inventory, wantedId, wantedMetadata);
	}

	/**
	 * Selects the hotbar slot holding the block. Beta has no click that moves an item into the
	 * hotbar in one go, so what is not on the bar is out of reach, and easy place treats that the
	 * same as not having the block at all.
	 */
	private static boolean selectItem(PlayerInventory inventory, int blockId, int metadata) {
		int itemId = itemFor(blockId);
		Item item = itemAt(itemId);
		if (item == null) {
			return false;
		}

		boolean matchDamage = damageCanGive(item, metadata);
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.main[slot];
			if (stack == null || stack.itemId != itemId) {
				continue;
			}
			if (matchDamage && item.method_470(stack.getDamage()) != metadata) {
				continue;
			}

			inventory.selectedSlot = slot;
			return true;
		}

		return false;
	}

	/**
	 * Whether the metadata the schematic wants is one this item can put down by which stack is held
	 * - wool colour, wood type, sapling type. Where it is, the wrong stack really is the wrong
	 * block and only an exact match will do.
	 *
	 * <p>Where it is not, the metadata is coming from somewhere else entirely: which way a piston
	 * faces, the bit leaves get for being placed by hand. Insisting on a match there would refuse to
	 * place those blocks at all, so the damage is ignored and any stack of the item will do.
	 */
	private static boolean damageCanGive(Item item, int metadata) {
		for (int damage = 0; damage < 16; damage++) {
			if (item.method_470(damage) == metadata) {
				return true;
			}
		}
		return false;
	}

	/** The item that places a block, or -1 if nothing does. */
	private static int itemFor(int blockId) {
		for (int[] pair : BLOCK_ITEMS) {
			if (pair[0] == blockId) {
				return pair[1];
			}
		}

		// A block item is registered under the id of its own block, so for everything else the block
		// id is the item id - as long as there really is a block item there.
		return itemAt(blockId) instanceof class_533 ? blockId : -1;
	}

	/**
	 * Whether a click with this in hand is a block being placed. An empty hand counts: a right click
	 * with nothing held does nothing in vanilla, so there is nothing to lose by treating it as the
	 * start of a placement and handing the player the block. A tool, a bucket or a piece of food is
	 * something else entirely and is left alone.
	 */
	private static boolean isPlacement(ItemStack stack) {
		if (stack == null) {
			return true;
		}
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

	/** Whether the block does something of its own with a right click, and so keeps it. */
	private static boolean handlesUse(Block block) {
		if (BLOCK_ON_USE == null) {
			// Without the answer, never take a click away from a block: opening a chest matters more
			// than holding a misplaced block back.
			return true;
		}

		return HANDLES_USE.computeIfAbsent(block.getClass(), type -> {
			try {
				return type.getMethod(BLOCK_ON_USE.getName(), BLOCK_ON_USE.getParameterTypes())
						.getDeclaringClass() != Block.class;
			} catch (NoSuchMethodException exception) {
				return true;
			}
		});
	}

	/**
	 * The right-click handler {@code Block} declares, found by its signature rather than by name.
	 * What a block does with a right click is the one thing easy place cannot work out for itself,
	 * and calling the method to find out would open the chest it is asking about.
	 */
	private static Method findOnUse() {
		Method found = null;
		for (Method method : Block.class.getDeclaredMethods()) {
			Class<?>[] parameters = method.getParameterTypes();
			if (method.getReturnType() != boolean.class || parameters.length != 5
					|| parameters[0] != World.class || parameters[1] != int.class
					|| parameters[2] != int.class || parameters[3] != int.class
					|| parameters[4] != PlayerEntity.class) {
				continue;
			}

			if (found != null) {
				Log.warn("Block has more than one right-click handler - easy place will not hold clicks back");
				return null;
			}
			found = method;
		}

		if (found == null) {
			Log.warn("Could not find the right-click handler on Block - easy place will not hold clicks back");
		}
		return found;
	}

	private static Item itemAt(int itemId) {
		return itemId > 0 && itemId < Item.ITEMS.length ? Item.ITEMS[itemId] : null;
	}

	private static Block blockAt(World world, int x, int y, int z) {
		return blockById(world.getBlockId(x, y, z));
	}

	private static Block blockById(int blockId) {
		return blockId > 0 && blockId < Block.BLOCKS.length ? Block.BLOCKS[blockId] : null;
	}

	/**
	 * Divides the tick rate to give the gap between repeats while the right button is held down.
	 * Vanilla divides by four - five ticks, four blocks a second. Easy place asks for one tick,
	 * which is as fast as placements can be sent, and is the faster-placing half of the feature.
	 */
	public static float useRepeatDivisor(float vanilla) {
		return isEngaged() ? TICK_RATE : vanilla;
	}

	/** "Easy Place: On", shared by the move screen's button and the message the key prints. */
	public static String label() {
		return Translations.get("schematic.easyplace") + ": "
				+ Translations.get(Schematica.STATE.isEasyPlace ? "options.on" : "options.off");
	}
}
