package lunatrius.schematica.util;

/** Mutable float triple, used for the interpolated camera position. */
public class Vec3f {
	public float x;
	public float y;
	public float z;

	public Vec3f() {
		this(0.0F, 0.0F, 0.0F);
	}

	public Vec3f(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3f set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public Vec3f add(float f) {
		this.x += f;
		this.y += f;
		this.z += f;
		return this;
	}

	public Vec3f sub(float f) {
		this.x -= f;
		this.y -= f;
		this.z -= f;
		return this;
	}

	@Override
	public String toString() {
		return "(" + this.x + ", " + this.y + ", " + this.z + ")";
	}
}
