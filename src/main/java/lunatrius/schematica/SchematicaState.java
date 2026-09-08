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
import net.minecraft.entity.player.PlayerEntity;
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
	/** Name of the file it was read from, so the same one can be found again next session. */
	private String loadedName = null;
	public class_13 blockRenderer = null;

	/** Camera position, interpolated for the current frame. */
	public final Vec3f playerPosition = new Vec3f();
	/** Which of the four cardinal directions the player faces, 0-3. */
	public int rotationRender = 0;

	/** World position the schematic's minimum corner is pinned to. */
	public final Vec3i offset = new Vec3i();

	/**
	 * How the loaded schematic has been turned since it came off disk: quarter turns about Y, and
	 * whether it was flipped before them.
	 *
	 * <p>Any run of rotations and mirrors lands on one of these eight, so this pair is enough to put
	 * a schematic back the way it was left without replaying the clicks that got it there.
	 */
	private int turns = 0;
	private boolean mirrored = false;

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

	/**
	 * Easy place: a right click may only put down the block the schematic wants where it wants it.
	 * Session state rather than a setting, like the render toggles above - it is a mode the player
	 * steps in and out of while building, not something to come back to a week later.
	 */
	public boolean isEasyPlace = false;

	/** Set whenever the cached geometry is stale and must be rebuilt next frame. */
	public boolean needsUpdate = true;

	/** What the loaded schematic is built out of, counted on demand and kept until it changes. */
	private MaterialList materials = null;
	/** The schematic {@link #materials} was counted from, so a different one is noticed. */
	private Schematic materialsCountedFrom = null;

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
			this.loadedName = file.getName();
			this.turns = 0;
			this.mirrored = false;
			this.isRenderingSchematic = true;
			this.needsUpdate = true;
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
			this.clearSchematic();
			return false;
		}
	}

	public void clearSchematic() {
		this.schematic = null;
		this.loadedName = null;
		this.turns = 0;
		this.mirrored = false;
		this.blockRenderer = null;
		this.isRenderingSchematic = false;
		this.renderingLayer = -1;
		this.needsUpdate = true;
		this.invalidateMaterials();
	}

	/**
	 * The file the loaded schematic came from, or null with nothing loaded. Only the name, not the
	 * path: it is looked up in the schematics folder again, so moving the folder keeps working.
	 */
	public String getLoadedName() {
		return this.loadedName;
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

	// --- materials ---------------------------------------------------------------------------

	/**
	 * What the loaded schematic is built out of.
	 *
	 * <p>Kept rather than counted per call: the count is a walk of every block in the schematic,
	 * and both the material list screen and the info HUD ask for it many times a second. Only
	 * loading, clearing or turning a schematic can change the answer, and each of those drops it.
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
	public void countMaterials() {
		Minecraft mc = this.getMinecraft();
		PlayerEntity player = mc == null ? null : mc.player;
		this.getMaterials().countInventory(player == null ? null : player.inventory.main);
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

	/**
	 * Turns easy place on or off. Allowed with nothing loaded: without a schematic the mode simply
	 * has nothing to hold clicks against, and it is still the mode the player asked for.
	 */
	public void toggleEasyPlace() {
		this.isEasyPlace = !this.isEasyPlace;
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
	 * Turns a schematic to the orientation a {@link #getTurns} and {@link #isMirrored} pair
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

	/**
	 * Called for a chunk of world arriving from a server, which single block changes do not cover:
	 * a chunk lands in one go and never reports the thousands of blocks in it one at a time.
	 *
	 * <p>Without this a schematic put back the moment a server hands over the world would be drawn
	 * against a world that is not there yet, and every block of it would read as missing until
	 * something else happened to touch it.
	 */
	public void onWorldChunkLoaded(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
		if (this.schematic == null || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			return;
		}

		int localX = x - this.offset.x;
		int localY = y - this.offset.y;
		int localZ = z - this.offset.z;

		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.invalidateBox(localX, localY, localZ,
					localX + sizeX - 1, localY + sizeY - 1, localZ + sizeZ - 1);
		} else {
			this.needsUpdate = true;
		}
	}

	/**
	 * Called for several blocks in one chunk changing at once, which is what a server sends instead
	 * of a packet each whenever more than one block in a chunk moves in a tick. With other people
	 * building alongside you that is most ticks, and none of it comes through
	 * {@link #onWorldBlockChanged}: these go straight into the chunk without the world ever being
	 * told block by block, so without this the overlay goes stale as soon as anyone else builds.
	 *
	 * <p>Positions arrive packed the way the packet packs them - four bits of X, four of Z and eight
	 * of Y, all relative to the chunk.
	 */
	public void onWorldBlocksChanged(int chunkX, int chunkZ, short[] packed, int count) {
		if (this.schematic == null || packed == null) {
			return;
		}

		int originX = chunkX * 16;
		int originZ = chunkZ * 16;
		for (int i = 0; i < count && i < packed.length; i++) {
			short position = packed[i];
			this.onWorldBlockChanged(
					originX + (position >> 12 & 0xF),
					position & 0xFF,
					originZ + (position >> 8 & 0xF));
		}
	}

	// --- the world underneath ------------------------------------------------------------------

	/** Edge length of the renderer's regions, which is what the watcher below marks. */
	private static final int WATCH_REGION = 16;

	/**
	 * How much of the world the watcher reads. Every second block in each axis, which is plenty to
	 * see a chunk go from empty to a world - the thing it is there to catch - at an eighth of the
	 * cost of reading all of it.
	 */
	private static final int WATCH_STRIDE = 2;

	/** What the world last read as under each region of the schematic. */
	private int[] watched = null;
	private int watchOriginX;
	private int watchOriginZ;
	private int watchSpanX;
	private int watchSpanZ;
	private int watchRows;
	/** Which chunk column is looked at next, so one tick only ever pays for one of them. */
	private int nextColumn = 0;

	/**
	 * Watches the world underneath the schematic for changes nothing announced.
	 *
	 * <p>The overlay is drawn by comparing the schematic against the world, so it is only ever as
	 * good as the world was at the moment it was built - and a world does not always say when it
	 * changes. A single player world is handed over while the game is still running: it ticks and
	 * draws for seconds while reading as air, so a schematic put back in that window is drawn
	 * against nothing at all and would stay that way, because a world filling itself in is not a
	 * block change and nothing reports it. Chunks coming and going as the player walks about are the
	 * same, and so is anything else that writes into a chunk without a word.
	 *
	 * <p>So the world is read back and compared against what it said last time, one chunk column per
	 * tick, and any part that has changed underneath us is rebuilt. That bounds the work whatever
	 * the schematic costs, and puts a ceiling of a second or two on how long the overlay can be
	 * wrong for a reason nobody told it about.
	 */
	public void pollWorldUnderSchematic() {
		Minecraft mc = this.getMinecraft();
		World world = mc == null ? null : mc.world;
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
	 * Reads one chunk column of the world under the schematic, a region-tall row at a time, and
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
			renderer.invalidateBox(fromX, fromY, fromZ, toX, toY, toZ);
		}
	}

	/** Drops the render state that is tied to a particular world instance. */
	public void onWorldChanged() {
		this.clearSchematic();
		this.isRenderingGuide = false;
		// Any swap still waiting on the old server will never be answered now.
		HotbarRestock.reset();
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
