package lunatrius.schematica;

import java.io.File;
import java.io.IOException;

import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.schematic.Schematic;
import lunatrius.schematica.schematic.SchematicFormat;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Vec3f;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.class_13;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * One schematic the player has open: what it is, where it stands, which way it has been turned
 * since it came off disk, and how much of it is being drawn.
 *
 * <p>All of this used to be the one loaded schematic in {@link SchematicaState}. It is a class of
 * its own because a build is rarely one file: a farm and the roof that goes over it are two, they
 * stand in two places, and either one may be the one being worked on this evening. Everything here
 * is per-schematic for that reason - a layer slice, an orientation and a material count each
 * describe one build and say nothing about the one standing next to it.
 *
 * <p>An instance holding nothing is a slot rather than a schematic: it is a place for one, which is
 * what the load screen fills. That keeps "no schematic" somewhere the controls can point at rather
 * than a null every screen has to think about.
 */
public class OpenSchematic {
	/** Below this volume a freshly loaded schematic is shown in full rather than layer by layer. */
	private static final int LAYER_MODE_VOLUME_THRESHOLD = 125000;

	/** Edge length of the renderer's regions, which is what the watcher below marks. */
	private static final int WATCH_REGION = 16;

	/**
	 * How much of the world the watcher reads. Every second block in each axis, which is plenty to
	 * see a chunk go from empty to a world - the thing it is there to catch - at an eighth of the
	 * cost of reading all of it.
	 */
	private static final int WATCH_STRIDE = 2;

	public SchematicWorld schematic = null;
	/** Name of the file it was read from, so the same one can be found again next session. */
	private String loadedName = null;
	public class_13 blockRenderer = null;

	/** World position the schematic's minimum corner is pinned to. */
	public final Vec3i offset = new Vec3i();

	/**
	 * How the schematic has been turned since it came off disk: quarter turns about Y, and whether
	 * it was flipped before them.
	 *
	 * <p>Any run of rotations and mirrors lands on one of these eight, so this pair is enough to put
	 * a schematic back the way it was left without replaying the clicks that got it there.
	 */
	private int turns = 0;
	private boolean mirrored = false;

	/** -1 renders every layer, otherwise only the given Y slice. */
	public int renderingLayer = -1;
	public boolean isRenderingSchematic = false;

	/** Set whenever this schematic's cached geometry is stale and must be rebuilt. */
	public boolean needsUpdate = true;

	/** What this schematic is built out of, counted on demand and kept until it changes. */
	private MaterialList materials = null;
	/** The schematic {@link #materials} was counted from, so a different one is noticed. */
	private Schematic materialsCountedFrom = null;

	/**
	 * Whether blocks have been swapped in it since it came off disk, so that what is being drawn is
	 * no longer what the file says. Cleared by {@link #save()}, which is what makes the two agree
	 * again.
	 */
	private boolean edited = false;

	/** What the world last read as under each region of this schematic. */
	private int[] watched = null;
	private int watchOriginX;
	private int watchOriginZ;
	private int watchSpanX;
	private int watchSpanZ;
	private int watchRows;
	/** Which chunk column is looked at next, so one tick only ever pays for one of them. */
	private int nextColumn = 0;

	/** Whether this is an empty slot rather than a schematic. */
	public boolean isEmpty() {
		return this.schematic == null;
	}

	/**
	 * The file this came from, or null in an empty slot. Only the name, not the path: it is looked
	 * up in the schematics folder again, so moving the folder keeps working.
	 */
	public String getLoadedName() {
		return this.loadedName;
	}

	public boolean load(World world, File file) {
		try {
			SchematicFormat.LoadResult result = SchematicFormat.read(file);
			this.schematic = new SchematicWorld(this, world, result.schematic);
			this.blockRenderer = new class_13(this.schematic);
			this.loadedName = file.getName();
			this.turns = 0;
			this.mirrored = false;
			this.edited = false;
			this.isRenderingSchematic = true;
			this.needsUpdate = true;
			this.watched = null;
			this.invalidateMaterials();

			Log.info("Loaded " + file.getName() + " ("
					+ result.schematic.getWidth() + "x" + result.schematic.getHeight() + "x" + result.schematic.getLength() + ")");
			if (result.droppedBlocks > 0) {
				Log.warn(result.droppedBlocks + " block(s) in " + file.getName()
						+ " do not exist in Beta 1.7.3 and were left empty.");
			}
			return true;
		} catch (IOException | RuntimeException exception) {
			Log.error("Failed to load schematic " + file.getName(), exception);
			this.close();
			return false;
		}
	}

	/** Empties the slot. What was drawn for it is dropped by the renderer on the next frame. */
	public void close() {
		this.schematic = null;
		this.loadedName = null;
		this.turns = 0;
		this.mirrored = false;
		this.edited = false;
		this.blockRenderer = null;
		this.isRenderingSchematic = false;
		this.renderingLayer = -1;
		this.needsUpdate = true;
		this.watched = null;
		this.invalidateMaterials();
	}

	// --- materials ---------------------------------------------------------------------------

	/**
	 * What this schematic is built out of.
	 *
	 * <p>Kept rather than counted per call: the count is a walk of every block in the schematic,
	 * and both the material list screen and the info HUD ask for it many times a second. Only
	 * loading, closing or turning a schematic can change the answer, and each of those drops it.
	 */
	public MaterialList getMaterials() {
		Schematic loaded = this.schematic == null ? null : this.schematic.getSchematic();
		if (this.materials == null || this.materialsCountedFrom != loaded) {
			this.materials = MaterialList.of(loaded);
			this.materialsCountedFrom = loaded;
		}
		return this.materials;
	}

	/** Recounts the pack against the list, which is the half of it that moves. */
	public void countMaterials(ItemStack[] inventory) {
		this.getMaterials().countInventory(inventory);
	}

	/**
	 * Drops the counted list so the next look at it counts again.
	 *
	 * <p>Turning a schematic cannot really change what it is built out of - only orientation
	 * metadata moves, and no two stacks of one item differ by orientation - but a rotation already
	 * rebuilds the whole overlay, so counting again costs nothing next to what it is doing anyway
	 * and does not rest on that staying true.
	 */
	private void invalidateMaterials() {
		this.materials = null;
		this.materialsCountedFrom = null;
	}

	// --- editing -----------------------------------------------------------------------------

	/** Whether its blocks have been changed since it was loaded, and not yet written back. */
	public boolean isEdited() {
		return this.edited;
	}

	/**
	 * Called once blocks in it have been swapped for others.
	 *
	 * <p>Everything drawn for it is thrown away rather than picked over: a replacement can reach
	 * every region of a schematic at once - that is rather the point of it - so there is nothing to
	 * be saved by working out which ones it reached.
	 */
	public void onBlocksReplaced() {
		this.needsUpdate = true;
		this.edited = true;
		this.invalidateMaterials();
	}

	/**
	 * Writes it back over the file it came from.
	 *
	 * <p>What is saved is what is on the screen, turns and all, and afterwards the schematic is not
	 * a turned copy of the file any more - it is the file. Saying otherwise would leave the note
	 * this world keeps holding a rotation that has already been applied, and turn the build again on
	 * the way back in.
	 */
	public boolean save() {
		if (this.schematic == null || this.loadedName == null) {
			return false;
		}

		File file = new File(Schematica.getSchematicDirectory(), this.loadedName);
		try {
			SchematicFormat.write(file, this.schematic.getSchematic());
		} catch (IOException | RuntimeException exception) {
			Log.error("Failed to save schematic " + this.loadedName, exception);
			return false;
		}

		this.turns = 0;
		this.mirrored = false;
		this.edited = false;
		Log.info("Saved " + this.loadedName + " (" + this.getWidth() + "x" + this.getHeight()
				+ "x" + this.getLength() + ")");
		return true;
	}

	// --- placement ---------------------------------------------------------------------------

	public int getWidth() {
		return this.schematic == null ? 0 : this.schematic.getWidth();
	}

	public int getHeight() {
		return this.schematic == null ? 0 : this.schematic.getHeight();
	}

	public int getLength() {
		return this.schematic == null ? 0 : this.schematic.getLength();
	}

	/**
	 * Whether a world position falls inside this schematic, and inside the slice being drawn.
	 *
	 * <p>The slice counts because it is the course being built: with the overlay cut to one layer
	 * there is nothing drawn above or below it, and a position with nothing drawn at it is not one
	 * this schematic has anything to say about.
	 */
	public boolean covers(int x, int y, int z) {
		if (this.schematic == null) {
			return false;
		}

		int localX = x - this.offset.x;
		int localY = y - this.offset.y;
		int localZ = z - this.offset.z;
		if (localX < 0 || localY < 0 || localZ < 0
				|| localX >= this.schematic.getWidth()
				|| localY >= this.schematic.getHeight()
				|| localZ >= this.schematic.getLength()) {
			return false;
		}

		return this.renderingLayer == -1 || this.renderingLayer == localY;
	}

	/** Places the schematic in front of the player, aligned to the direction they are facing. */
	public void moveHere(Vec3f playerPosition, int rotationRender) {
		this.offset.set(
				(int) Math.floor(playerPosition.x),
				(int) Math.floor(playerPosition.y) - 1,
				(int) Math.floor(playerPosition.z));

		if (this.schematic == null) {
			this.needsUpdate = true;
			return;
		}

		switch (rotationRender) {
			case 0: this.offset.x -= this.schematic.getWidth(); this.offset.z++; break;
			case 1: this.offset.x -= this.schematic.getWidth(); this.offset.z -= this.schematic.getLength(); break;
			case 2: this.offset.x++; this.offset.z -= this.schematic.getLength(); break;
			default: this.offset.x++; this.offset.z++; break;
		}

		this.needsUpdate = true;
	}

	/**
	 * Shows or hides this overlay. Deliberately does not invalidate anything: the renderer keeps its
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
			// Flipping something already turned is the same as turning the flip the other way, which
			// is what keeps the pair down to one flip and a count of turns however long the run gets.
			this.turns = (4 - this.turns) & 3;
			this.mirrored = !this.mirrored;
			this.needsUpdate = true;
			this.invalidateMaterials();
		}
	}

	public void rotateSchematic() {
		if (this.schematic != null) {
			this.schematic.getSchematic().rotate();
			this.schematic.refreshBlockEntities();
			this.turns = (this.turns + 1) & 3;
			this.needsUpdate = true;
			this.invalidateMaterials();
		}
	}

	/** Quarter turns applied since the schematic was loaded, 0-3. */
	public int getTurns() {
		return this.turns;
	}

	/** Whether the schematic was flipped before those turns. */
	public boolean isMirrored() {
		return this.mirrored;
	}

	/**
	 * Turns this schematic to the orientation a {@link #getTurns} and {@link #isMirrored} pair
	 * describes: the flip first, then the turns.
	 *
	 * <p>Meant for a schematic straight off disk, which is the only state the pair is measured
	 * against. Calling it on one that has already been turned turns it again from where it is.
	 */
	public void setOrientation(int turns, boolean mirrored) {
		if (this.schematic == null) {
			return;
		}

		if (mirrored) {
			this.schematic.getSchematic().mirrorZ();
		}
		for (int turn = 0; turn < (turns & 3); turn++) {
			this.schematic.getSchematic().rotate();
		}

		this.schematic.refreshBlockEntities();
		this.turns = turns & 3;
		this.mirrored = mirrored;
		this.needsUpdate = true;
		this.invalidateMaterials();
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
	 * Slices this overlay to a single Y layer, or to all of them at -1. Values outside the schematic
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

	// --- keeping up with the world ------------------------------------------------------------

	/**
	 * Called for every single-block change in the live world. Only changes that can alter what this
	 * overlay draws invalidate it - otherwise flowing water or a redstone clock anywhere in the
	 * world would rebuild the geometry every tick.
	 *
	 * <p>Changes are tracked even while the overlay is hidden, which is what lets the toggle key
	 * bring it back without a rebuild.
	 */
	public void onWorldBlockChanged(int x, int y, int z) {
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
			renderer.invalidateBlock(this, localX, localY, localZ);
		} else {
			this.needsUpdate = true;
		}
	}

	/**
	 * Called for a chunk of world arriving from a server, which single block changes do not cover:
	 * a chunk lands in one go and never reports the thousands of blocks in it one at a time.
	 */
	public void onWorldChunkLoaded(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
		if (this.schematic == null) {
			return;
		}

		int localX = x - this.offset.x;
		int localY = y - this.offset.y;
		int localZ = z - this.offset.z;

		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.invalidateBox(this,
					localX, localY, localZ,
					localX + sizeX - 1, localY + sizeY - 1, localZ + sizeZ - 1);
		} else {
			this.needsUpdate = true;
		}
	}

	/**
	 * Watches the world underneath this schematic for changes nothing announced, one chunk column
	 * per call. {@link SchematicaState#pollWorldUnderSchematics} says why the world has to be read
	 * back at all; the cost of it is why only one column is read per tick, per schematic.
	 */
	public void pollWorldUnder(World world) {
		if (this.schematic == null || world == null) {
			this.watched = null;
			return;
		}

		int originX = this.offset.x >> 4;
		int originZ = this.offset.z >> 4;
		int spanX = ((this.offset.x + this.schematic.getWidth() - 1) >> 4) - originX + 1;
		int spanZ = ((this.offset.z + this.schematic.getLength() - 1) >> 4) - originZ + 1;
		int rows = (this.schematic.getHeight() + WATCH_REGION - 1) / WATCH_REGION;

		if (this.watched == null || this.watchOriginX != originX || this.watchOriginZ != originZ
				|| this.watchSpanX != spanX || this.watchSpanZ != spanZ || this.watchRows != rows) {
			// A different schematic, or the same one somewhere else. Start the record again rather
			// than compare against a world that was under something else - whatever moved it has
			// already marked the whole overlay for rebuilding anyway.
			this.watchOriginX = originX;
			this.watchOriginZ = originZ;
			this.watchSpanX = spanX;
			this.watchSpanZ = spanZ;
			this.watchRows = rows;
			this.watched = new int[Math.max(1, spanX * spanZ * rows)];
			this.nextColumn = 0;
			for (int column = 0; column < spanX * spanZ; column++) {
				this.readColumn(world, column, null);
			}
			return;
		}

		int columns = this.watchSpanX * this.watchSpanZ;
		if (columns > 0) {
			int column = this.nextColumn % columns;
			this.nextColumn = column + 1;
			this.readColumn(world, column, Schematica.getRenderer());
		}
	}

	/**
	 * Reads one chunk column of the world under this schematic, a region-tall row at a time, and
	 * marks any row that no longer reads the way it did. With no renderer to mark, the readings are
	 * simply recorded - which is how the first pass over a schematic gets its starting point.
	 */
	private void readColumn(World world, int column, SchematicRenderer renderer) {
		int chunkX = this.watchOriginX + column / this.watchSpanZ;
		int chunkZ = this.watchOriginZ + column % this.watchSpanZ;

		int fromX = Math.max(chunkX << 4, this.offset.x) - this.offset.x;
		int toX = Math.min((chunkX << 4) + 15, this.offset.x + this.schematic.getWidth() - 1) - this.offset.x;
		int fromZ = Math.max(chunkZ << 4, this.offset.z) - this.offset.z;
		int toZ = Math.min((chunkZ << 4) + 15, this.offset.z + this.schematic.getLength() - 1) - this.offset.z;
		int height = this.schematic.getHeight();

		for (int row = 0; row < this.watchRows; row++) {
			int fromY = row * WATCH_REGION;
			int toY = Math.min(fromY + WATCH_REGION - 1, height - 1);

			int contents = 1;
			for (int x = fromX; x <= toX; x += WATCH_STRIDE) {
				for (int z = fromZ; z <= toZ; z += WATCH_STRIDE) {
					for (int y = fromY; y <= toY; y += WATCH_STRIDE) {
						contents = contents * 31 + world.getBlockId(
								x + this.offset.x, y + this.offset.y, z + this.offset.z);
					}
				}
			}

			int index = column * this.watchRows + row;
			if (this.watched[index] == contents) {
				continue;
			}
			this.watched[index] = contents;

			if (renderer == null) {
				continue;
			}
			renderer.invalidateBox(this, fromX, fromY, fromZ, toX, toY, toZ);
		}
	}
}
