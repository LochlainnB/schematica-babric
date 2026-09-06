package lunatrius.schematica.schematic;

/**
 * Block metadata fix-ups for the two schematic operations that change orientation.
 *
 * <p>Everything here is keyed on raw numeric block ids rather than {@code Block} constants: a
 * schematic is a stream of numeric ids, and the tables stay readable and mapping-independent
 * that way.
 *
 * <p>{@link #rotate} turns the schematic 90 degrees about the Y axis in the same direction as
 * {@link Schematic#rotate()}; {@link #mirrorZ} mirrors it along the Z axis.
 */
public final class BlockTransform {
	private static final int DISPENSER = 23;
	private static final int BED = 26;
	private static final int POWERED_RAIL = 27;
	private static final int DETECTOR_RAIL = 28;
	private static final int STICKY_PISTON = 29;
	private static final int PISTON = 33;
	private static final int PISTON_HEAD = 34;
	private static final int TORCH = 50;
	private static final int WOOD_STAIRS = 53;
	private static final int CHEST = 54;
	private static final int FURNACE = 61;
	private static final int LIT_FURNACE = 62;
	private static final int STANDING_SIGN = 63;
	private static final int WOODEN_DOOR = 64;
	private static final int LADDER = 65;
	private static final int RAIL = 66;
	private static final int STONE_STAIRS = 67;
	private static final int WALL_SIGN = 68;
	private static final int LEVER = 69;
	private static final int IRON_DOOR = 71;
	private static final int REDSTONE_TORCH_OFF = 75;
	private static final int REDSTONE_TORCH_ON = 76;
	private static final int PUMPKIN = 86;
	private static final int JACK_O_LANTERN = 91;
	private static final int REPEATER_OFF = 93;
	private static final int REPEATER_ON = 94;
	private static final int TRAPDOOR = 96;

	private BlockTransform() {
	}

	public static int rotate(int blockId, int metadata) {
		switch (blockId) {
			case TORCH:
			case REDSTONE_TORCH_OFF:
			case REDSTONE_TORCH_ON:
				switch (metadata) {
					case 1: return 4;
					case 2: return 3;
					case 3: return 1;
					case 4: return 2;
					default: return metadata;
				}

			case RAIL:
				switch (metadata) {
					case 0: return 1;
					case 1: return 0;
					case 2: return 4;
					case 3: return 5;
					case 4: return 3;
					case 5: return 2;
					case 6: return 9;
					case 7: return 6;
					case 8: return 7;
					case 9: return 8;
					default: return metadata;
				}

			case POWERED_RAIL:
			case DETECTOR_RAIL:
				switch (metadata & 7) {
					case 0: return 1 | (metadata & 8);
					case 1: return 0 | (metadata & 8);
					case 2: return 4 | (metadata & 8);
					case 3: return 5 | (metadata & 8);
					case 4: return 3 | (metadata & 8);
					case 5: return 2 | (metadata & 8);
					default: return metadata;
				}

			case WOOD_STAIRS:
			case STONE_STAIRS:
				switch (metadata & 3) {
					case 0: return 3 | (metadata & 4);
					case 1: return 2 | (metadata & 4);
					case 2: return 0 | (metadata & 4);
					case 3: return 1 | (metadata & 4);
					default: return metadata;
				}

			case LEVER:
				switch (metadata & 7) {
					case 1: return 4 | (metadata & 8);
					case 2: return 3 | (metadata & 8);
					case 3: return 1 | (metadata & 8);
					case 4: return 2 | (metadata & 8);
					case 5: return 6 | (metadata & 8);
					case 6: return 5 | (metadata & 8);
					default: return metadata;
				}

			case WOODEN_DOOR:
			case IRON_DOOR:
				// The upper half only stores the hinge, so it rotates with the lower half implicitly.
				if ((metadata & 8) == 8) {
					return metadata;
				}
				switch (metadata & 3) {
					case 0: return 3 | (metadata & 12);
					case 1: return 0 | (metadata & 12);
					case 2: return 1 | (metadata & 12);
					case 3: return 2 | (metadata & 12);
					default: return metadata;
				}

			case STANDING_SIGN:
				return (metadata + 12) % 16;

			case LADDER:
			case WALL_SIGN:
			case LIT_FURNACE:
			case FURNACE:
			case DISPENSER:
			case CHEST:
				switch (metadata) {
					case 2: return 4;
					case 3: return 5;
					case 4: return 3;
					case 5: return 2;
					default: return metadata;
				}

			case PUMPKIN:
			case JACK_O_LANTERN:
				switch (metadata) {
					case 0: return 3;
					case 1: return 0;
					case 2: return 1;
					case 3: return 2;
					default: return metadata;
				}

			case BED:
			case REPEATER_ON:
			case REPEATER_OFF:
				switch (metadata & 3) {
					case 0: return 3 | (metadata & 12);
					case 1: return 0 | (metadata & 12);
					case 2: return 1 | (metadata & 12);
					case 3: return 2 | (metadata & 12);
					default: return metadata;
				}

			case TRAPDOOR:
				switch (metadata) {
					case 0: return 2;
					case 1: return 3;
					case 2: return 1;
					case 3: return 0;
					default: return metadata;
				}

			case PISTON:
			case STICKY_PISTON:
			case PISTON_HEAD:
				switch (metadata & 7) {
					case 2: return 4 | (metadata & 8);
					case 3: return 5 | (metadata & 8);
					case 4: return 3 | (metadata & 8);
					case 5: return 2 | (metadata & 8);
					default: return metadata;
				}

			default:
				return metadata;
		}
	}

	public static int mirrorZ(int blockId, int metadata) {
		switch (blockId) {
			case TORCH:
			case REDSTONE_TORCH_OFF:
			case REDSTONE_TORCH_ON:
				switch (metadata) {
					case 3: return 4;
					case 4: return 3;
					default: return metadata;
				}

			case RAIL:
				switch (metadata) {
					case 4: return 5;
					case 5: return 4;
					case 6: return 9;
					case 7: return 8;
					case 8: return 7;
					case 9: return 6;
					default: return metadata;
				}

			case POWERED_RAIL:
			case DETECTOR_RAIL:
				switch (metadata & 7) {
					case 4: return 5 | (metadata & 8);
					case 5: return 4 | (metadata & 8);
					default: return metadata;
				}

			case WOOD_STAIRS:
			case STONE_STAIRS:
				switch (metadata & 3) {
					case 2: return 3 | (metadata & 4);
					case 3: return 2 | (metadata & 4);
					default: return metadata;
				}

			case LEVER:
				switch (metadata & 7) {
					case 3: return 4 | (metadata & 8);
					case 4: return 3 | (metadata & 8);
					default: return metadata;
				}

			case WOODEN_DOOR:
			case IRON_DOOR:
				if ((metadata & 8) == 8) {
					return metadata ^ 1;
				}
				switch (metadata & 3) {
					case 1: return 3 | (metadata & 12);
					case 3: return 1 | (metadata & 12);
					default: return metadata;
				}

			case STANDING_SIGN:
				return (8 - metadata) & 15;

			case LADDER:
			case WALL_SIGN:
			case LIT_FURNACE:
			case FURNACE:
			case DISPENSER:
			case CHEST:
				switch (metadata) {
					case 2: return 3;
					case 3: return 2;
					default: return metadata;
				}

			case PUMPKIN:
			case JACK_O_LANTERN:
				switch (metadata) {
					case 0: return 2;
					case 2: return 0;
					default: return metadata;
				}

			case BED:
			case REPEATER_ON:
			case REPEATER_OFF:
				switch (metadata & 3) {
					case 0: return 2 | (metadata & 12);
					case 2: return 0 | (metadata & 12);
					default: return metadata;
				}

			case TRAPDOOR:
				switch (metadata) {
					case 0: return 1;
					case 1: return 0;
					default: return metadata;
				}

			case PISTON:
			case STICKY_PISTON:
			case PISTON_HEAD:
				switch (metadata & 7) {
					case 2: return 3 | (metadata & 8);
					case 3: return 2 | (metadata & 8);
					default: return metadata;
				}

			default:
				return metadata;
		}
	}
}
