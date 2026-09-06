package lunatrius.schematica;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicFormat;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3f;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.class_13;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Everything the mod knows about the current session: the loaded schematic, where it sits in the
 * world, the selection used for saving, and the render toggles the GUIs drive.
 */
public class SchematicaState {
	/** Offsets the +/- buttons in the GUIs cycle through. */
	public final int[] increments = { 1, 5, 15, 50, 250 };

	/** Below this volume a freshly loaded schematic is shown in full rather than layer by layer. */
	private static final int LAYER_MODE_VOLUME_THRESHOLD = 125000;

	public SchematicWorld schematic = null;
	public class_13 blockRenderer = null;

	/** Camera position, interpolated for the current frame. */
	public final Vec3f playerPosition = new Vec3f();
	/** Which of the four cardinal directions the player faces, 0-3. */
	public int rotationRender = 0;

	/** World position the schematic's minimum corner is pinned to. */
	public final Vec3i offset = new Vec3i();

	/** The two corners of the save selection. */
	public final Vec3i pointA = new Vec3i();
	public final Vec3i pointB = new Vec3i();
	public final Vec3i pointMin = new Vec3i();
	public final Vec3i pointMax = new Vec3i();

	public int selectedSchematic = 0;

	/** -1 renders every layer, otherwise only the given Y slice. */
	public int renderingLayer = -1;
	public boolean isRenderingSchematic = false;
	public boolean isRenderingGuide = false;

	/** Set whenever the cached geometry is stale and must be rebuilt next frame. */
	public boolean needsUpdate = true;

	public Minecraft getMinecraft() {
		return Schematica.getMinecraft();
	}

	public File getSchematicDirectory() {
		return Schematica.getSchematicDirectory();
	}

	// --- schematic files ---------------------------------------------------------------------

	/**
	 * The entries shown in the load list: index 0 is the "no schematic" placeholder, the rest are
	 * file names.
	 */
	public List<String> getSchematicFiles() {
		List<String> names = new ArrayList<>();
		names.add(Translations.get("schematic.noschematic"));

		File[] files = this.getSchematicDirectory().listFiles(SchematicFileFilter.INSTANCE);
		if (files != null) {
			List<String> fileNames = new ArrayList<>(files.length);
			for (File file : files) {
				fileNames.add(file.getName());
			}
			Collections.sort(fileNames, String.CASE_INSENSITIVE_ORDER);
			names.addAll(fileNames);
		}

		return names;
	}

	public boolean loadSchematic(File file) {
		World world = this.getMinecraft().world;
		if (world == null) {
			return false;
		}

		try {
			SchematicFormat.LoadResult result = SchematicFormat.read(file);
			this.schematic = new SchematicWorld(world, result.schematic);
			this.blockRenderer = new class_13(this.schematic);
			this.isRenderingSchematic = true;
			this.needsUpdate = true;

			Log.info("Loaded " + file.getName() + " ("
					+ result.schematic.getWidth() + "x" + result.schematic.getHeight() + "x" + result.schematic.getLength() + ")");
			if (result.droppedBlocks > 0) {
				Log.warn(result.droppedBlocks + " block(s) in " + file.getName()
						+ " do not exist in Beta 1.7.3 and were left empty.");
			}
			return true;
		} catch (IOException | RuntimeException exception) {
			Log.error("Failed to load schematic " + file.getName(), exception);
			this.clearSchematic();
			return false;
		}
	}

	public void clearSchematic() {
		this.schematic = null;
		this.blockRenderer = null;
		this.isRenderingSchematic = false;
		this.renderingLayer = -1;
		this.needsUpdate = true;
	}

	/**
	 * Captures the world between two corners (inclusive) and writes it out as a schematic.
	 */
	public boolean saveSchematic(File file, Vec3i from, Vec3i to) {
		World world = this.getMinecraft().world;
		if (world == null) {
			return false;
		}

		try {
			int minX = Math.min(from.x, to.x);
			int maxX = Math.max(from.x, to.x);
			int minY = Math.max(0, Math.min(from.y, to.y));
			int maxY = Math.min(127, Math.max(from.y, to.y));
			int minZ = Math.min(from.z, to.z);
			int maxZ = Math.max(from.z, to.z);

			if (maxY < minY) {
				Log.warn("Selection is entirely outside the world's height range.");
				return false;
			}

			int width = maxX - minX + 1;
			int height = maxY - minY + 1;
			int length = maxZ - minZ + 1;

			int[][][] blocks = new int[width][height][length];
			int[][][] metadata = new int[width][height][length];
			List<BlockEntity> blockEntities = new ArrayList<>();

			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					for (int z = minZ; z <= maxZ; z++) {
						blocks[x - minX][y - minY][z - minZ] = world.getBlockId(x, y, z);
						metadata[x - minX][y - minY][z - minZ] = world.method_1778(x, y, z);

						BlockEntity source = world.method_1777(x, y, z);
						if (source == null) {
							continue;
						}

						// Round-trip through NBT so the copy is detached from the live world.
						NbtCompound nbt = new NbtCompound();
						source.writeNbt(nbt);
						BlockEntity copy = BlockEntity.method_1068(nbt);
						if (copy != null) {
							copy.x -= minX;
							copy.y -= minY;
							copy.z -= minZ;
							blockEntities.add(copy);
						}
					}
				}
			}

			Schematic schematic = new Schematic(blocks, metadata, blockEntities, width, height, length);
			SchematicFormat.write(file, schematic);
			Log.info("Saved " + file.getName() + " (" + width + "x" + height + "x" + length + ")");
			return true;
		} catch (IOException | RuntimeException exception) {
			Log.error("Failed to save schematic " + file.getName(), exception);
			return false;
		}
	}

	// --- placement ---------------------------------------------------------------------------

	public float getTranslationX() {
		return this.playerPosition.x - this.offset.x;
	}

	public float getTranslationY() {
		return this.playerPosition.y - this.offset.y;
	}

	public float getTranslationZ() {
		return this.playerPosition.z - this.offset.z;
	}

	public void updatePoints() {
		this.pointMin.set(
				Math.min(this.pointA.x, this.pointB.x),
				Math.min(this.pointA.y, this.pointB.y),
				Math.min(this.pointA.z, this.pointB.z));
		this.pointMax.set(
				Math.max(this.pointA.x, this.pointB.x),
				Math.max(this.pointA.y, this.pointB.y),
				Math.max(this.pointA.z, this.pointB.z));
		this.needsUpdate = true;
	}

	/** Drops a selection corner at the block the player is standing on, nudged away from them. */
	public void moveHere(Vec3i point) {
		point.set(
				(int) Math.floor(this.playerPosition.x),
				(int) Math.floor(this.playerPosition.y - 1.0F),
				(int) Math.floor(this.playerPosition.z));

		switch (this.rotationRender) {
			case 0: point.x--; point.z++; break;
			case 1: point.x--; point.z--; break;
			case 2: point.x++; point.z--; break;
			default: point.x++; point.z++; break;
		}
	}

	/** Places the schematic in front of the player, aligned to the direction they are facing. */
	public void moveHere() {
		this.offset.set(
				(int) Math.floor(this.playerPosition.x),
				(int) Math.floor(this.playerPosition.y) - 1,
				(int) Math.floor(this.playerPosition.z));

		if (this.schematic == null) {
			this.needsUpdate = true;
			return;
		}

		switch (this.rotationRender) {
			case 0: this.offset.x -= this.schematic.getWidth(); this.offset.z++; break;
			case 1: this.offset.x -= this.schematic.getWidth(); this.offset.z -= this.schematic.getLength(); break;
			case 2: this.offset.x++; this.offset.z -= this.schematic.getLength(); break;
			default: this.offset.x++; this.offset.z++; break;
		}

		this.needsUpdate = true;
	}

	/**
	 * Shows or hides the overlay. Deliberately does not invalidate anything: the renderer keeps its
	 * cached geometry while the schematic is hidden and carries on marking it stale as the world
	 * changes, so this is free in both directions however large the schematic is.
	 */
	public void toggleRendering() {
		this.isRenderingSchematic = !this.isRenderingSchematic && this.schematic != null;
	}

	public void mirrorSchematic() {
		if (this.schematic != null) {
			this.schematic.getSchematic().mirrorZ();
			this.schematic.refreshBlockEntities();
			this.needsUpdate = true;
		}
	}

	public void rotateSchematic() {
		if (this.schematic != null) {
			this.schematic.getSchematic().rotate();
			this.schematic.refreshBlockEntities();
			this.needsUpdate = true;
		}
	}

	/** Picks a sensible initial layer mode for a schematic that has just been loaded. */
	public void resetRenderingLayer() {
		this.renderingLayer = -1;
		if (this.schematic != null && this.schematic.getSchematic().getVolume() > LAYER_MODE_VOLUME_THRESHOLD) {
			this.renderingLayer = 0;
		}
	}

	public int getMaxRenderingLayer() {
		return this.schematic != null ? this.schematic.getHeight() - 1 : -1;
	}

	/**
	 * Slices the overlay to a single Y layer, or to all of them at -1. Values outside the schematic
	 * are clamped rather than rejected, so a caller can just step the current value by one and let
	 * this stop at either end.
	 */
	public void setRenderingLayer(int layer) {
		int max = this.getMaxRenderingLayer();
		int clamped = max < 0 ? -1 : Math.max(-1, Math.min(layer, max));
		if (clamped == this.renderingLayer) {
			// Held at one end of the range, or nothing is loaded: rebuilding would draw the same thing.
			return;
		}

		this.renderingLayer = clamped;
		this.needsUpdate = true;
	}

	/**
	 * Called for every single-block change in the live world. Only changes that can alter what the
	 * overlay draws invalidate it - otherwise flowing water or a redstone clock anywhere in the
	 * world would rebuild the geometry every tick.
	 *
	 * <p>Changes are tracked even while the overlay is hidden, which is what lets the toggle key
	 * bring it back without a rebuild.
	 */
	public void onWorldBlockChanged(int x, int y, int z) {
		// The guide boxes are derived purely from pointA/pointB, so only the schematic overlay -
		// which compares itself against the world - can be invalidated by a block change.
		if (this.schematic == null) {
			return;
		}

		int localX = x - this.offset.x;
		int localY = y - this.offset.y;
		int localZ = z - this.offset.z;
		if (localX < 0 || localY < 0 || localZ < 0
				|| localX >= this.schematic.getWidth()
				|| localY >= this.schematic.getHeight()
				|| localZ >= this.schematic.getLength()) {
			return;
		}

		// One block placed or broken only changes the cell it landed in, so hand the position to the
		// renderer rather than marking the whole schematic stale.
		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.invalidateBlock(localX, localY, localZ);
		} else {
			this.needsUpdate = true;
		}
	}

	/** Drops the render state that is tied to a particular world instance. */
	public void onWorldChanged() {
		this.clearSchematic();
		this.isRenderingGuide = false;
		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.invalidate();
		}
	}

	private static final class SchematicFileFilter implements java.io.FileFilter {
		static final SchematicFileFilter INSTANCE = new SchematicFileFilter();

		@Override
		public boolean accept(File file) {
			return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".schematic");
		}
	}
}
