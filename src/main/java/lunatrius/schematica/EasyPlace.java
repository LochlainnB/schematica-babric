package lunatrius.schematica;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.minecraft.Item;
import net.minecraft.block.Block;
import net.minecraft.class_123;
import net.minecraft.class_212;
import net.minecraft.class_27;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
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
 *
 * <p>A position with nothing to build against is placed at as well, which vanilla cannot do: a
 * click has to be aimed at a block, so a schematic block hanging in the air is out of reach however
 * carefully it is looked at. Easy place walks the line of sight itself and puts down the first
 * block the schematic wants along it, whether or not anything is holding that block up. Which
 * blocks can stand on their own is not a list kept here - it is the game's own answer, which is
 * what lets cobblestone hang in the air while redstone, which would drop straight off, waits for
 * its floor to be built first.
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

	/** The world's ceiling. */
	private static final int MAX_Y = 127;

	/**
	 * How far a server lets a click reach, squared, measured to the block that was clicked. It is
	 * well outside the four blocks the client itself allows, but a placement worked out from a line
	 * of sight rather than taken from a hit result has nothing else keeping it honest.
	 */
	private static final double SERVER_REACH_SQUARED = 64.0;

	/** Vanilla's tick rate, which is also the fastest a placement can be repeated. */
	private static final float TICK_RATE = 20.0F;

	private static final Method BLOCK_ON_USE = findOnUse();
	private static final Map<Class<?>, Boolean> HANDLES_USE = new HashMap<>();

	private EasyPlace() {
	}

	/**
	 * Whether easy place has anything to act on: the mode is on, and there is an overlay being drawn
	 * to build against. Everything the mode does hangs off this, so it cannot place faster while it
	 * is not also holding clicks back - with nothing open, or every one of them hidden, it is off in
	 * every way that can be felt.
	 */
	private static boolean isEngaged() {
		SchematicaState state = Schematica.STATE;
		return state.isEasyPlace && state.isAnyRendering();
	}

	/**
	 * Decides what happens to a right click. Returns true when the click has to be dropped - which
	 * is also the answer when easy place has made the placement itself, since the click it stands in
	 * for must not go on to make a second one.
	 */
	public static boolean interceptUse(Minecraft mc) {
		// Opening a chest mid-exchange would close the player container and drop its server cursor.
		// This protection lasts until the swap settles, even if easy place has since been turned off.
		if (HotbarRestock.isCursorSwapPending()) {
			return true;
		}
		if (!isEngaged()) {
			return false;
		}
		if (mc.world == null || mc.player == null) {
			return false;
		}

		class_27 hit = mc.field_2823;
		if (hit != null && hit.field_1983 != class_212.TILE) {
			// An entity is being aimed at: nothing to do with building.
			return false;
		}

		// Only a click that is, or could be, a block going down is ours to take.
		if (!isPlacement(mc.player.inventory.getSelectedItem())) {
			return false;
		}

		if (hit != null) {
			Block clicked = blockAt(mc.world, hit.field_1984, hit.field_1985, hit.field_1986);
			if (clicked != null && handlesUse(clicked)) {
				return false;
			}
		}

		// Checked after the block has had its say, so a swap in the air never costs the player a
		// chest: what it holds back is the placement, not the click.
		if (HotbarRestock.isBusy()) {
			return true;
		}

		// A position with nothing to build against is only ever taken when it is nearer than wherever
		// the ordinary click would land, so either way what goes down is the first thing on the line
		// of sight.
		AirTarget air = aimedAtNothing(mc, hit);
		if (air != null) {
			air.place(mc);
			// Built or not, the click is spent here: nothing further off is the one being aimed at.
			return true;
		}

		if (hit == null) {
			// Nothing to aim at and nothing wanted in the air - vanilla can have the click back.
			return false;
		}

		return !placeAgainst(mc, hit);
	}

	/**
	 * Puts the block the schematic wants across the face being aimed at into the player's hand.
	 * Returns false when there is nothing to place there, or nothing to place it with, which is what
	 * turns the click into a no-op.
	 */
	private static boolean placeAgainst(Minecraft mc, class_27 hit) {
		int x = hit.field_1984;
		int y = hit.field_1985;
		int z = hit.field_1986;
		int face = hit.field_1987;

		// Where the block would land, by vanilla's own rule: across the face that was clicked, unless
		// the click was on a snow layer, which a new block replaces where it stands.
		if (mc.world.getBlockId(x, y, z) == SNOW_LAYER) {
			face = 0;
		} else {
			if (face < 0 || face >= OFFSET_X.length) {
				return false;
			}
			x += OFFSET_X[face];
			y += OFFSET_Y[face];
			z += OFFSET_Z[face];
		}

		int wantedId = buildableAt(mc, x, y, z, face);
		if (wantedId == 0) {
			return false;
		}

		return selectItem(mc, wantedId, wantedMetadataAt(x, y, z));
	}

	/**
	 * The block the schematic wants at a world position and vanilla would let stand there, or 0 when
	 * there is nothing to build.
	 *
	 * @param face the side the block would be placed against, which is what vanilla is asked about
	 */
	private static int buildableAt(Minecraft mc, int x, int y, int z, int face) {
		// Whichever of the open schematics is drawing something there, which is also what settles
		// the layer slice: a position outside the course being built is not a target, because there
		// is nothing drawn there to aim at.
		OpenSchematic open = Schematica.STATE.schematicAt(x, y, z);
		if (open == null) {
			return 0;
		}

		int localX = x - open.offset.x;
		int localY = y - open.offset.y;
		int localZ = z - open.offset.z;

		int wantedId = open.schematic.getBlockId(localX, localY, localZ);
		if (blockById(wantedId) == null) {
			return 0;
		}

		// A door or a bed is two blocks put down by one click, and it is the bottom half that is
		// placed; aiming at the top half is aiming at something the other half brings with it.
		int wantedMetadata = open.schematic.method_1778(localX, localY, localZ);
		if ((wantedMetadata & SECOND_HALF) != 0
				&& (wantedId == BED || wantedId == WOODEN_DOOR || wantedId == IRON_DOOR)) {
			return 0;
		}

		// Vanilla's own answer to whether the block may go there: the space is empty or holds
		// something a placement washes away, nobody is standing in it, and the block has whatever it
		// needs to hold on to. Asking the game rather than keeping a list here is what tells
		// cobblestone, which hangs in the air quite happily, from redstone, which would drop off.
		return mc.world.method_156(wantedId, x, y, z, false, face) ? wantedId : 0;
	}

	/**
	 * The metadata wanted at a world position, which is only ever asked about one that
	 * {@link #buildableAt} has already answered for - so the schematic covering it is the same one.
	 */
	private static int wantedMetadataAt(int x, int y, int z) {
		OpenSchematic open = Schematica.STATE.schematicAt(x, y, z);
		if (open == null) {
			return 0;
		}
		return open.schematic.method_1778(x - open.offset.x, y - open.offset.y, z - open.offset.z);
	}

	/**
	 * Looks along the line of sight for a position the schematic wants filled that has nothing to be
	 * built against, and works out how to click a block into it. Returns null when there is none in
	 * front of the player.
	 */
	private static AirTarget aimedAtNothing(Minecraft mc, class_27 hit) {
		LivingEntity camera = mc.field_2807 != null ? mc.field_2807 : mc.player;
		if (camera == null) {
			return null;
		}

		AirTarget target = new AirTarget(mc, hit);
		Sightline.walk(camera, mc.interactionManager.method_1715(), target);
		return target.side < 0 ? null : target;
	}

	/**
	 * The first position on the line of sight that the schematic wants filled and that no ordinary
	 * click can reach, along with the side of it to click a block into place across.
	 *
	 * <p>The search stops at whatever the player is actually aiming at, and at the position an
	 * ordinary click would build in. Those belong to the click that was already on its way, and are
	 * left to the tested path that handles them; this only picks up what is nearer than both.
	 */
	private static final class AirTarget implements Sightline.Visitor {
		private final Minecraft mc;
		/** The block being aimed at, and where an ordinary click would put one. Neither is ours. */
		private final int stopX;
		private final int stopY;
		private final int stopZ;
		private final int skipX;
		private final int skipY;
		private final int skipZ;

		private int x;
		private int y;
		private int z;
		/** Which side of the position the block is clicked into place across, or -1 for nothing found. */
		private int side = -1;
		private int blockId;

		AirTarget(Minecraft mc, class_27 hit) {
			this.mc = mc;
			if (hit == null) {
				// A position no block can be at, so nothing on the line of sight matches it.
				this.stopX = this.skipX = Integer.MIN_VALUE;
				this.stopY = this.skipY = Integer.MIN_VALUE;
				this.stopZ = this.skipZ = Integer.MIN_VALUE;
				return;
			}

			this.stopX = hit.field_1984;
			this.stopY = hit.field_1985;
			this.stopZ = hit.field_1986;

			int face = hit.field_1987;
			// A snow layer is landed in rather than beside, and a face that is not one leaves nowhere to
			// work out at all - either way the click owns the position it was aimed at.
			boolean landsHere = mc.world.getBlockId(this.stopX, this.stopY, this.stopZ) == SNOW_LAYER
					|| face < 0 || face >= OFFSET_X.length;
			this.skipX = landsHere ? this.stopX : this.stopX + OFFSET_X[face];
			this.skipY = landsHere ? this.stopY : this.stopY + OFFSET_Y[face];
			this.skipZ = landsHere ? this.stopZ : this.stopZ + OFFSET_Z[face];
		}

		@Override
		public boolean visit(int x, int y, int z, int entryFace) {
			if ((x == this.stopX && y == this.stopY && z == this.stopZ)
					|| (x == this.skipX && y == this.skipY && z == this.skipZ)) {
				return true;
			}

			if (!isSeeThrough(this.mc.world, x, y, z)) {
				// Something the vanilla ray would have stopped at: the line of sight ends here, and
				// anything past it is behind a wall.
				return true;
			}

			if (entryFace == Sightline.NO_FACE) {
				// The block the eye is inside. There is no side of it the player came in through, so
				// there is nothing to say which neighbour they meant to click.
				return false;
			}

			int side = clickableSide(this.mc, x, y, z, entryFace);
			if (side < 0) {
				return false;
			}

			int id = buildableAt(this.mc, x, y, z, opposite(side));
			if (id == 0 || !stays(this.mc.world, id, x, y, z)) {
				return false;
			}

			this.x = x;
			this.y = y;
			this.z = z;
			this.side = side;
			this.blockId = id;
			return true;
		}

		/** Puts the block in hand and clicks it into place across the chosen side. */
		void place(Minecraft mc) {
			if (!selectItem(mc, this.blockId, wantedMetadataAt(this.x, this.y, this.z))) {
				// Nowhere in the inventory, or a swap has been started and it is not in hand yet.
				return;
			}

			click(mc, this.x + OFFSET_X[this.side], this.y + OFFSET_Y[this.side], this.z + OFFSET_Z[this.side],
					opposite(this.side));
		}
	}

	/**
	 * Which side of a position to click a block into place across. Vanilla places across the face of
	 * a block that was clicked, so one has to be picked even when every neighbour is thin air - which
	 * the server allows, since it only looks at what the clicked position holds to see whether that
	 * block wants the click for itself.
	 *
	 * <p>The side the line of sight came in through is tried first: it is the one the player is
	 * looking at, and the one the ray has already been through, so it is known to be clear.
	 */
	private static int clickableSide(Minecraft mc, int x, int y, int z, int entryFace) {
		if (isClickable(mc, x, y, z, entryFace)) {
			return entryFace;
		}

		for (int side = 0; side < OFFSET_X.length; side++) {
			if (side != entryFace && isClickable(mc, x, y, z, side)) {
				return side;
			}
		}
		return -1;
	}

	private static boolean isClickable(Minecraft mc, int x, int y, int z, int side) {
		int neighbourX = x + OFFSET_X[side];
		int neighbourY = y + OFFSET_Y[side];
		int neighbourZ = z + OFFSET_Z[side];
		if (neighbourY < 0 || neighbourY > MAX_Y) {
			return false;
		}

		// A snow layer is the one block a placement lands in rather than beside, so a click on one
		// would put the block somewhere else entirely.
		int id = mc.world.getBlockId(neighbourX, neighbourY, neighbourZ);
		if (id == SNOW_LAYER) {
			return false;
		}

		// And a block that answers a right click would answer this one instead of passing it on.
		Block block = blockById(id);
		if (block != null && handlesUse(block)) {
			return false;
		}

		// The server measures the reach to what was clicked, not to where the block lands.
		return mc.player.method_1347(neighbourX + 0.5, neighbourY + 0.5, neighbourZ + 0.5) < SERVER_REACH_SQUARED;
	}

	/**
	 * Whether the line of sight carries on through a position. The four blocks that let it through
	 * are also the four a placement washes away, so this is equally the question of whether the
	 * space is free - anything else standing here is both the end of the line and a block already
	 * in the way.
	 */
	private static boolean isSeeThrough(World world, int x, int y, int z) {
		int id = world.getBlockId(x, y, z);
		if (id == 0) {
			return true;
		}

		Block block = blockById(id);
		return block == Block.FLOWING_WATER || block == Block.STILL_WATER
				|| block == Block.FLOWING_LAVA || block == Block.STILL_LAVA
				|| block == Block.field_1892;
	}

	/**
	 * Whether a block put down here would still be here a moment later. Sand and gravel are the two
	 * that would not: nothing in the check vanilla makes for a placement stops them going into the
	 * air, and they fall straight back out of it. The game's own rule for what they fall through is
	 * the one asked, so a schematic that stacks sand on a floor still builds.
	 */
	private static boolean stays(World world, int blockId, int x, int y, int z) {
		return !(blockById(blockId) instanceof class_123) || !class_123.method_435(world, x, y - 1, z);
	}

	/** The face opposite another. Vanilla numbers them in pairs, so this is the low bit flipped. */
	private static int opposite(int face) {
		return face ^ 1;
	}

	/**
	 * Makes the placement the click would have made, at a position the player is not pointing at.
	 * Everything vanilla does around a placement is done here too, because this stands in for the
	 * whole of the click rather than being added to it.
	 */
	private static void click(Minecraft mc, int x, int y, int z, int face) {
		PlayerEntity player = mc.player;
		ItemStack held = player.inventory.getSelectedItem();
		int before = held != null ? held.count : 0;

		if (mc.interactionManager.method_1713(player, mc.world, held, x, y, z, face)) {
			player.method_500();
		}

		if (held == null) {
			return;
		}
		if (held.count == 0) {
			player.inventory.main[player.inventory.selectedSlot] = null;
		} else if (held.count != before && mc.field_2818 != null) {
			mc.field_2818.field_2342.method_1863();
		}
	}

	/**
	 * Puts the block in the player's hand: the hotbar slot holding it, or a swap that brings it onto
	 * the bar from the rest of the inventory.
	 *
	 * <p>A swap costs the click that found it - the block is not in hand until the slots have moved,
	 * and on a server not until it has agreed - so this returns false and the next click places.
	 * Holding the button down, that is one tick later.
	 */
	private static boolean selectItem(Minecraft mc, int blockId, int metadata) {
		PlayerInventory inventory = mc.player.inventory;
		int itemId = BlockItems.itemFor(blockId);
		Item item = BlockItems.itemAt(itemId);
		if (item == null) {
			return false;
		}

		boolean matchDamage = BlockItems.damageFor(item, metadata) >= 0;
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (BlockItems.matches(inventory.main[slot], itemId, metadata, matchDamage)) {
				inventory.selectedSlot = slot;
				return true;
			}
		}

		for (int slot = HOTBAR_SIZE; slot < inventory.main.length; slot++) {
			if (BlockItems.matches(inventory.main[slot], itemId, metadata, matchDamage)) {
				HotbarRestock.moveToHotbar(mc, slot);
				return false;
			}
		}

		return false;
	}

	/**
	 * Whether a click with this in hand is a block being placed. An empty hand counts: a right click
	 * with nothing held does nothing in vanilla, so there is nothing to lose by treating it as the
	 * start of a placement and handing the player the block. A tool, a bucket or a piece of food is
	 * something else entirely and is left alone.
	 */
	private static boolean isPlacement(ItemStack stack) {
		return stack == null || BlockItems.isBlockItem(stack);
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
