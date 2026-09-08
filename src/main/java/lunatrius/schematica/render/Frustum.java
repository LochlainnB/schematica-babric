package lunatrius.schematica.render;

import java.nio.Buffer;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The six planes of the current view volume, read back out of the fixed-function matrix stack.
 *
 * <p>Taken from the matrices rather than from the game's own frustum so that it keeps working
 * whatever a renderer mod has done to the world pass, and so that it needs no camera state of its
 * own: {@link #update()} is called with the modelview already translated out of the camera's
 * coordinates and into the world's, which is then the space the boxes handed to
 * {@link #isBoxVisible} are in. One set of planes covers every open schematic that way, whatever
 * corner of the world each of them is standing in.
 */
final class Frustum {
	private static final int RIGHT = 0;
	private static final int LEFT = 1;
	private static final int BOTTOM = 2;
	private static final int TOP = 3;
	private static final int FAR = 4;
	private static final int NEAR = 5;

	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private final float[] projection = new float[16];
	private final float[] modelview = new float[16];
	private final float[] clip = new float[16];

	/** Six {@code (a, b, c, d)} planes, normalised, pointing inwards. */
	private final float[][] planes = new float[6][4];

	void update() {
		read(GL11.GL_PROJECTION_MATRIX, this.projection);
		read(GL11.GL_MODELVIEW_MATRIX, this.modelview);
		multiply(this.modelview, this.projection, this.clip);

		float[] c = this.clip;
		this.plane(RIGHT, c[3] - c[0], c[7] - c[4], c[11] - c[8], c[15] - c[12]);
		this.plane(LEFT, c[3] + c[0], c[7] + c[4], c[11] + c[8], c[15] + c[12]);
		this.plane(BOTTOM, c[3] + c[1], c[7] + c[5], c[11] + c[9], c[15] + c[13]);
		this.plane(TOP, c[3] - c[1], c[7] - c[5], c[11] - c[9], c[15] - c[13]);
		this.plane(FAR, c[3] - c[2], c[7] - c[6], c[11] - c[10], c[15] - c[14]);
		this.plane(NEAR, c[3] + c[2], c[7] + c[6], c[11] + c[10], c[15] + c[14]);
	}

	/** Whether any part of an axis-aligned box could be on screen. */
	boolean isBoxVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		for (int i = 0; i < this.planes.length; i++) {
			float[] plane = this.planes[i];
			// Only the box corner furthest along the plane normal has to be tested: if even that one
			// is behind the plane, all eight are.
			float x = plane[0] > 0.0F ? maxX : minX;
			float y = plane[1] > 0.0F ? maxY : minY;
			float z = plane[2] > 0.0F ? maxZ : minZ;
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] < 0.0F) {
				return false;
			}
		}
		return true;
	}

	private void read(int matrix, float[] out) {
		((Buffer) this.buffer).clear();
		GL11.glGetFloat(matrix, this.buffer);
		this.buffer.get(out);
	}

	private void plane(int index, float a, float b, float c, float d) {
		float length = (float) Math.sqrt(a * a + b * b + c * c);
		float[] plane = this.planes[index];
		if (length == 0.0F) {
			// A degenerate matrix - a mod mid-way through setting up its own pass, say. A plane that
			// accepts everything is the safe answer; culling nothing only costs frames.
			plane[0] = 0.0F;
			plane[1] = 0.0F;
			plane[2] = 0.0F;
			plane[3] = 1.0F;
			return;
		}

		plane[0] = a / length;
		plane[1] = b / length;
		plane[2] = c / length;
		plane[3] = d / length;
	}

	/** {@code out = a * b}, both in the column-major order {@code glGetFloat} hands back. */
	private static void multiply(float[] a, float[] b, float[] out) {
		for (int i = 0; i < 4; i++) {
			int row = i * 4;
			out[row] = a[row] * b[0] + a[row + 1] * b[4] + a[row + 2] * b[8] + a[row + 3] * b[12];
			out[row + 1] = a[row] * b[1] + a[row + 1] * b[5] + a[row + 2] * b[9] + a[row + 3] * b[13];
			out[row + 2] = a[row] * b[2] + a[row + 1] * b[6] + a[row + 2] * b[10] + a[row + 3] * b[14];
			out[row + 3] = a[row] * b[3] + a[row + 1] * b[7] + a[row + 2] * b[11] + a[row + 3] * b[15];
		}
	}
}
