package lunatrius.schematica.util;

/**
 * Mutable integer triple. Kept mutable on purpose: the GUIs nudge the schematic offset and the
 * selection corners in place, and the renderer reads them every frame.
 */
public class Vec3i {
	public static final Vec3i ZERO = new Vec3i();

	public int x;
	public int y;
	public int z;

	public Vec3i() {
		this(0, 0, 0);
	}

	public Vec3i(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3i set(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public Vec3i add(int i) {
		return this.add(i, i, i);
	}

	public Vec3i add(int x, int y, int z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	public Vec3i sub(Vec3i vec) {
		return this.sub(vec.x, vec.y, vec.z);
	}

	public Vec3i sub(int x, int y, int z) {
		this.x -= x;
		this.y -= y;
		this.z -= z;
		return this;
	}

	public Vec3i copy() {
		return new Vec3i(this.x, this.y, this.z);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Vec3i)) {
			return false;
		}
		Vec3i other = (Vec3i) obj;
		return this.x == other.x && this.y == other.y && this.z == other.z;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 71 * hash + this.x;
		hash = 71 * hash + this.y;
		return 71 * hash + this.z;
	}

	@Override
	public String toString() {
		return "(" + this.x + ", " + this.y + ", " + this.z + ")";
	}
}
