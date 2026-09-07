package lunatrius.schematica;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Walks the blocks along a player's line of sight, nearest first.
 *
 * <p>Vanilla's own ray stops at the first block solid enough to be clicked, which is all it ever
 * needs: everything a click can do is done to that block or across a face of it. Easy place has to
 * see further, because the position it wants to build at usually holds nothing at all - a block
 * hanging in the air has no face to aim for. This walks the same line the vanilla ray does and
 * hands back every block position it crosses, so the mod can pick out the one the schematic wants.
 *
 * <p>The step is the standard grid traversal: at each block, whichever axis has its next boundary
 * nearest is the one crossed. That visits every block the line passes through, in order, and no
 * others.
 */
final class Sightline {
	/** Faces are vanilla's: the side a block sits on, so a neighbour is at {@code + OFFSET[face]}. */
	private static final int DOWN = 0;
	private static final int UP = 1;
	private static final int NORTH = 2;
	private static final int SOUTH = 3;
	private static final int WEST = 4;
	private static final int EAST = 5;

	/** The block the eye is already inside was not entered through any face. */
	static final int NO_FACE = -1;

	/** The world's floor and ceiling; nothing can be built outside them. */
	private static final int MIN_Y = 0;
	private static final int MAX_Y = 127;

	interface Visitor {
		/**
		 * @param entryFace the side of this block the line came in through, or {@link #NO_FACE}
		 * @return true to stop the walk here
		 */
		boolean visit(int x, int y, int z, int entryFace);
	}

	private Sightline() {
	}

	/**
	 * Hands every block position on the line of sight to the visitor, nearest first, out to the
	 * given distance in blocks.
	 */
	static void walk(LivingEntity from, double distance, Visitor visitor) {
		Vec3d eye = from.method_931(1.0F);
		// Read the vectors out straight away: both come from a pool that is handed round every frame.
		double originX = eye.x;
		double originY = eye.y;
		double originZ = eye.z;

		Vec3d look = from.method_926(1.0F);
		double lookX = look.x;
		double lookY = look.y;
		double lookZ = look.z;

		int x = floor(originX);
		int y = floor(originY);
		int z = floor(originZ);

		int stepX = lookX < 0.0 ? -1 : 1;
		int stepY = lookY < 0.0 ? -1 : 1;
		int stepZ = lookZ < 0.0 ? -1 : 1;

		// How far along the line the next boundary on each axis is, and how far apart the ones after
		// it are. An axis the line does not move along never comes up, which infinity says exactly.
		double nextX = boundary(originX, lookX, x);
		double nextY = boundary(originY, lookY, y);
		double nextZ = boundary(originZ, lookZ, z);
		double spanX = span(lookX);
		double spanY = span(lookY);
		double spanZ = span(lookZ);

		int entryFace = NO_FACE;
		while (true) {
			if (y < MIN_Y || y > MAX_Y || visitor.visit(x, y, z, entryFace)) {
				return;
			}

			if (nextX <= nextY && nextX <= nextZ) {
				if (nextX > distance) {
					return;
				}
				x += stepX;
				nextX += spanX;
				// Stepping east crosses into the new block through its western side, and so on.
				entryFace = stepX > 0 ? WEST : EAST;
			} else if (nextY <= nextZ) {
				if (nextY > distance) {
					return;
				}
				y += stepY;
				nextY += spanY;
				entryFace = stepY > 0 ? DOWN : UP;
			} else {
				if (nextZ > distance) {
					return;
				}
				z += stepZ;
				nextZ += spanZ;
				entryFace = stepZ > 0 ? NORTH : SOUTH;
			}
		}
	}

	/** How far along the line the first boundary on one axis is. */
	private static double boundary(double origin, double direction, int block) {
		if (direction == 0.0) {
			return Double.POSITIVE_INFINITY;
		}
		double toEdge = direction > 0.0 ? (double) (block + 1) - origin : origin - (double) block;
		return toEdge / Math.abs(direction);
	}

	/** How far along the line one whole block on an axis is. */
	private static double span(double direction) {
		return direction == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(direction);
	}

	private static int floor(double value) {
		int truncated = (int) value;
		return value < (double) truncated ? truncated - 1 : truncated;
	}
}
