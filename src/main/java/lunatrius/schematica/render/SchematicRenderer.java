package lunatrius.schematica.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaConfig;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.class_13;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.world.BlockView;
import org.lwjgl.opengl.GL11;

/**
 * Draws the loaded schematic as a ghost overlay, plus the save-selection guide boxes.
 *
 * <p>The schematic is cut into 16x16x16 regions, each holding two display lists - the ghost blocks
 * and the coloured comparison boxes over them. A region is rebuilt only when something it covers
 * changed, and only regions the camera can actually see are drawn, so the cost of both halves
 * tracks what is on screen rather than the size of the schematic.
 *
 * <p>Rebuilding is also capped at {@link #REBUILD_BUDGET_NANOS} per frame. Whole-schematic
 * invalidations - moving it, rotating it, changing a render setting - are unavoidable, because
 * every block is compared against the world underneath it; spreading one over a handful of frames
 * turns what was a stall proportional to the schematic's volume into a visible refresh.
 *
 * <p>Everything is drawn from {@code renderWeather}, which runs inside the world pass with the
 * camera at the origin - so geometry is submitted in schematic-local coordinates after translating
 * by {@code offset - cameraPosition}.
 */
public class SchematicRenderer {
	/** Face bits used by the cuboid helpers, matching vanilla's side numbering. */
	private static final int DOWN = 1;
	private static final int UP = 2;
	private static final int NORTH = 4;
	private static final int SOUTH = 8;
	private static final int WEST = 16;
	private static final int EAST = 32;
	private static final int ALL_FACES = DOWN | UP | NORTH | SOUTH | WEST | EAST;

	/** Edge length of one cached region, in blocks. */
	private static final int REGION_SIZE = 16;
	private static final int REGION_VOLUME = REGION_SIZE * REGION_SIZE * REGION_SIZE;

	/** How long one frame may spend rebuilding. At least one region is always rebuilt. */
	private static final long REBUILD_BUDGET_NANOS = 8_000_000L;

	/** Sorts regions the camera cannot see behind every region it can. */
	private static final double OFF_SCREEN = 1.0E9;

	/** Slack around a region's bounds when culling, covering the inflated highlight boxes. */
	private static final float CULL_MARGIN = 1.0F;

	private static final Comparator<Region> BY_SORT_KEY = new Comparator<Region>() {
		@Override
		public int compare(Region a, Region b) {
			return Double.compare(a.sortKey, b.sortKey);
		}
	};

	private final SchematicaState state = Schematica.STATE;
	private final SchematicaConfig config = Schematica.CONFIG;
	private final SignRenderer signRenderer = new SignRenderer();
	private final Frustum frustum = new Frustum();

	private Region[] regions = new Region[0];
	private int regionsX;
	private int regionsY;
	private int regionsZ;

	/** The display lists behind {@link #regions}, allocated as one contiguous run. */
	private int listBase;
	private int listCount;

	/** What the region grid was laid out for; a rotation swaps the dimensions in place. */
	private SchematicWorld gridSchematic;
	private int gridWidth;
	private int gridHeight;
	private int gridLength;

	/** Reused between frames so the per-frame rebuild pass allocates nothing. */
	private final List<Region> rebuildQueue = new ArrayList<>();

	/**
	 * Highlight boxes found while a region's blocks are being compiled, packed as
	 * {@code x | y << 4 | z << 8 | faces << 12} with the coordinates region-local. A cell can land
	 * in at most one of the three, so a region can never overflow them.
	 */
	private final int[] wrongBlock = new int[REGION_VOLUME];
	private final int[] wrongMetadata = new int[REGION_VOLUME];
	private final int[] missingBlock = new int[REGION_VOLUME];
	private int wrongBlockCount;
	private int wrongMetadataCount;
	private int missingBlockCount;

	/** Forces every region to be rebuilt. */
	public void invalidate() {
		this.state.needsUpdate = true;
	}

	/** Whether any of the overlay is still waiting to be rebuilt. */
	public boolean isRebuilding() {
		if (this.state.schematic == null || !this.state.isRenderingSchematic) {
			return false;
		}
		if (this.state.needsUpdate) {
			return true;
		}
		for (Region region : this.regions) {
			if (region.dirty) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Marks only the region holding one schematic-local position, which is all a block changing in
	 * the world costs: the overlay compares that one cell against the world and nothing else reads
	 * it, so a neighbouring region cannot be affected.
	 */
	public void invalidateBlock(int x, int y, int z) {
		if (!this.gridMatches(this.state.schematic)) {
			// No grid to mark yet. The next frame lays one out and builds all of it regardless.
			this.state.needsUpdate = true;
			return;
		}

		int regionX = x / REGION_SIZE;
		int regionY = y / REGION_SIZE;
		int regionZ = z / REGION_SIZE;
		if (regionX < 0 || regionY < 0 || regionZ < 0
				|| regionX >= this.regionsX || regionY >= this.regionsY || regionZ >= this.regionsZ) {
			return;
		}

		this.regions[(regionX * this.regionsY + regionY) * this.regionsZ + regionZ].dirty = true;
	}

	/**
	 * Render entry point, called from {@code GameRendererMixin} with the frame's partial tick.
	 */
	public void render(float delta) {
		Minecraft minecraft = Schematica.getMinecraft();
		if (minecraft == null || minecraft.world == null) {
			return;
		}

		ClientPlayerEntity player = minecraft.player;
		if (player == null) {
			return;
		}

		// Tracked every frame even when nothing is drawn: the GUIs read it to drop the schematic and
		// the selection corners at the player's feet.
		this.state.playerPosition.set(
				(float) (player.field_1637 + (player.x - player.field_1637) * delta),
				(float) (player.field_1638 + (player.y - player.field_1638) * delta),
				(float) (player.field_1639 + (player.z - player.field_1639) * delta));
		this.state.rotationRender = (int) ((player.yaw / 90.0F % 4.0F + 4.0F) % 4.0F);

		SchematicWorld schematic = this.state.schematic;
		boolean drawSchematic = this.state.isRenderingSchematic && schematic != null;
		if (!drawSchematic && !this.state.isRenderingGuide) {
			// The cached regions are deliberately left alone: hiding the overlay and bringing it back
			// is then free, and world changes keep marking regions dirty while it is hidden.
			return;
		}

		GL11.glPushMatrix();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);

		GL11.glTranslatef(-this.state.getTranslationX(), -this.state.getTranslationY(), -this.state.getTranslationZ());
		// From here on the modelview maps schematic-local coordinates, so the culling planes are in
		// the same space as the region bounds.
		this.frustum.update();

		if (drawSchematic) {
			this.syncGrid(schematic);
			if (this.state.needsUpdate) {
				this.markAllDirty();
				this.state.needsUpdate = false;
			}
			this.updateVisibility();
			this.rebuildDirtyRegions(minecraft, schematic);

			for (Region region : this.regions) {
				if (region.visible && region.hasBlocks) {
					GL11.glCallList(region.blockList);
				}
			}

			this.renderBlockEntities(minecraft, schematic);
		}

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glLineWidth(1.5F);

		if (drawSchematic) {
			for (Region region : this.regions) {
				if (region.visible && region.hasOverlay) {
					GL11.glCallList(region.overlayList);
				}
			}
			this.drawBoundingBox(schematic);
		}
		if (this.state.isRenderingGuide) {
			this.drawGuide();
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glTranslatef(this.state.getTranslationX(), this.state.getTranslationY(), this.state.getTranslationZ());
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glPopMatrix();
	}

	// --- the region grid ---------------------------------------------------------------------

	private boolean gridMatches(SchematicWorld schematic) {
		return schematic != null
				&& this.gridSchematic == schematic
				&& this.gridWidth == schematic.getWidth()
				&& this.gridHeight == schematic.getHeight()
				&& this.gridLength == schematic.getLength();
	}

	/** Lays out a fresh grid whenever a schematic is loaded, or a rotation changes its shape. */
	private void syncGrid(SchematicWorld schematic) {
		if (this.gridMatches(schematic)) {
			return;
		}

		this.releaseLists();

		this.gridSchematic = schematic;
		this.gridWidth = schematic.getWidth();
		this.gridHeight = schematic.getHeight();
		this.gridLength = schematic.getLength();

		this.regionsX = regionCount(this.gridWidth);
		this.regionsY = regionCount(this.gridHeight);
		this.regionsZ = regionCount(this.gridLength);

		int count = this.regionsX * this.regionsY * this.regionsZ;
		int base = GL11.glGenLists(count * 2);
		if (base == 0) {
			Log.error("The driver would not hand out " + (count * 2) + " display lists;"
					+ " the schematic cannot be drawn.");
			this.regions = new Region[0];
			this.regionsX = 0;
			this.regionsY = 0;
			this.regionsZ = 0;
			return;
		}

		this.listBase = base;
		this.listCount = count * 2;
		this.regions = new Region[count];

		int index = 0;
		for (int x = 0; x < this.regionsX; x++) {
			for (int y = 0; y < this.regionsY; y++) {
				for (int z = 0; z < this.regionsZ; z++) {
					this.regions[index] = new Region(
							x * REGION_SIZE, y * REGION_SIZE, z * REGION_SIZE,
							Math.min((x + 1) * REGION_SIZE, this.gridWidth),
							Math.min((y + 1) * REGION_SIZE, this.gridHeight),
							Math.min((z + 1) * REGION_SIZE, this.gridLength),
							base + index * 2, base + index * 2 + 1);
					index++;
				}
			}
		}
	}

	private void releaseLists() {
		if (this.listCount > 0) {
			GL11.glDeleteLists(this.listBase, this.listCount);
			this.listBase = 0;
			this.listCount = 0;
		}
	}

	private void markAllDirty() {
		for (Region region : this.regions) {
			region.dirty = true;
		}
	}

	/** Culls every region against the view, and records how far each is from the camera. */
	private void updateVisibility() {
		float cameraX = this.state.getTranslationX();
		float cameraY = this.state.getTranslationY();
		float cameraZ = this.state.getTranslationZ();

		for (Region region : this.regions) {
			region.visible = this.frustum.isBoxVisible(
					region.minX - CULL_MARGIN, region.minY - CULL_MARGIN, region.minZ - CULL_MARGIN,
					region.maxX + CULL_MARGIN, region.maxY + CULL_MARGIN, region.maxZ + CULL_MARGIN);

			double dx = (region.minX + region.maxX) * 0.5 - cameraX;
			double dy = (region.minY + region.maxY) * 0.5 - cameraY;
			double dz = (region.minZ + region.maxZ) * 0.5 - cameraZ;
			region.sortKey = dx * dx + dy * dy + dz * dz + (region.visible ? 0.0 : OFF_SCREEN);
		}
	}

	// --- rebuilding --------------------------------------------------------------------------

	/**
	 * Rebuilds dirty regions nearest-first until the frame's budget runs out, so whatever the player
	 * is looking at catches up before anything behind them does.
	 */
	private void rebuildDirtyRegions(Minecraft minecraft, SchematicWorld schematic) {
		if (this.state.blockRenderer == null) {
			return;
		}

		this.rebuildQueue.clear();
		for (Region region : this.regions) {
			if (region.dirty) {
				this.rebuildQueue.add(region);
			}
		}
		if (this.rebuildQueue.isEmpty()) {
			return;
		}
		this.rebuildQueue.sort(BY_SORT_KEY);

		// Smooth lighting samples neighbours through the BlockView; the schematic reports a flat
		// brightness, so it is turned off for the duration of the sweep.
		boolean ambientOcclusion = minecraft.options.ao;
		minecraft.options.ao = false;

		try {
			long deadline = System.nanoTime() + REBUILD_BUDGET_NANOS;
			for (int i = 0; i < this.rebuildQueue.size(); i++) {
				this.rebuildRegion(minecraft, schematic, this.rebuildQueue.get(i));
				if (System.nanoTime() >= deadline) {
					break;
				}
			}
		} finally {
			minecraft.options.ao = ambientOcclusion;
		}
	}

	private void rebuildRegion(Minecraft minecraft, SchematicWorld schematic, Region region) {
		region.dirty = false;
		this.wrongBlockCount = 0;
		this.wrongMetadataCount = 0;
		this.missingBlockCount = 0;

		BlockView world = minecraft.world;
		class_13 blockRenderer = this.state.blockRenderer;

		int minY = region.minY;
		int maxY = region.maxY;
		if (this.state.renderingLayer >= 0) {
			// A region outside the slice ends up with minY >= maxY, which compiles two empty lists -
			// the old geometry has to go somewhere, and leaving it behind would draw it.
			minY = Math.max(minY, this.state.renderingLayer);
			maxY = Math.min(maxY, this.state.renderingLayer + 1);
		}

		int offsetX = this.state.offset.x;
		int offsetY = this.state.offset.y;
		int offsetZ = this.state.offset.z;
		int ghostBlocks = 0;

		GL11.glNewList(region.blockList, GL11.GL_COMPILE);
		Tessellator tessellator = Tessellator.INSTANCE;
		tessellator.startQuads();
		try {
			for (int x = region.minX; x < region.maxX; x++) {
				for (int y = minY; y < maxY; y++) {
					for (int z = region.minZ; z < region.maxZ; z++) {
						int schematicId = schematic.getBlockId(x, y, z);
						Block block = Block.BLOCKS[schematicId];
						if (block == null) {
							// Air, or an id this version has no block for. There is no geometry to draw
							// and no box to put over it either - the highlight boxes take their faces
							// from the block, so a missing one produces a box with no sides.
							continue;
						}

						int worldId = world.getBlockId(x + offsetX, y + offsetY, z + offsetZ);
						if (worldId != 0) {
							if (!this.config.highlight) {
								continue;
							}
							if (schematicId != worldId) {
								this.wrongBlock[this.wrongBlockCount++] =
										pack(region, x, y, z, visibleFaces(block, schematic, x, y, z));
							} else if (schematic.method_1778(x, y, z)
									!= world.method_1778(x + offsetX, y + offsetY, z + offsetZ)) {
								this.wrongMetadata[this.wrongMetadataCount++] =
										pack(region, x, y, z, visibleFaces(block, schematic, x, y, z));
							}
						} else {
							if (this.config.highlight) {
								this.missingBlock[this.missingBlockCount++] =
										pack(region, x, y, z, visibleFaces(block, schematic, x, y, z));
							}
							blockRenderer.method_57(block, x, y, z);
							ghostBlocks++;
						}
					}
				}
			}
		} finally {
			tessellator.draw();
			GL11.glEndList();
		}

		region.hasBlocks = ghostBlocks > 0;
		this.compileOverlay(region);
	}

	/** Compiles the boxes the sweep collected into the region's second list. */
	private void compileOverlay(Region region) {
		GL11.glNewList(region.overlayList, GL11.GL_COMPILE);
		try {
			// Quads first, then lines, which is the order the single global batch used to draw in.
			this.overlayQuads(region, this.wrongBlock, this.wrongBlockCount, 1.0F, 0.0F, 0.0F, 0.25F);
			this.overlayQuads(region, this.wrongMetadata, this.wrongMetadataCount, 0.75F, 0.35F, 0.0F, 0.45F);
			this.overlayQuads(region, this.missingBlock, this.missingBlockCount, 0.0F, 0.75F, 1.0F, 0.25F);

			this.overlayLines(region, this.wrongBlock, this.wrongBlockCount, 1.0F, 0.0F, 0.0F, 0.25F);
			this.overlayLines(region, this.wrongMetadata, this.wrongMetadataCount, 0.75F, 0.35F, 0.0F, 0.45F);
			this.overlayLines(region, this.missingBlock, this.missingBlockCount, 0.0F, 0.75F, 1.0F, 0.25F);
		} finally {
			GL11.glEndList();
		}

		region.hasOverlay = this.wrongBlockCount + this.wrongMetadataCount + this.missingBlockCount > 0;
	}

	private void overlayQuads(Region region, int[] packed, int count, float red, float green, float blue, float alpha) {
		if (count == 0) {
			return;
		}

		float delta = this.config.blockDelta;
		GL11.glColor4f(red, green, blue, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		for (int i = 0; i < count; i++) {
			int entry = packed[i];
			float x = region.minX + (entry & 0xF);
			float y = region.minY + (entry >> 4 & 0xF);
			float z = region.minZ + (entry >> 8 & 0xF);
			boxQuads(x - delta, y - delta, z - delta, x + 1 + delta, y + 1 + delta, z + 1 + delta, entry >>> 12);
		}
		GL11.glEnd();
	}

	private void overlayLines(Region region, int[] packed, int count, float red, float green, float blue, float alpha) {
		if (count == 0) {
			return;
		}

		float delta = this.config.blockDelta;
		GL11.glColor4f(red, green, blue, alpha);
		GL11.glBegin(GL11.GL_LINES);
		for (int i = 0; i < count; i++) {
			int entry = packed[i];
			float x = region.minX + (entry & 0xF);
			float y = region.minY + (entry >> 4 & 0xF);
			float z = region.minZ + (entry >> 8 & 0xF);
			boxLines(x - delta, y - delta, z - delta, x + 1 + delta, y + 1 + delta, z + 1 + delta, entry >>> 12);
		}
		GL11.glEnd();
	}

	private static int pack(Region region, int x, int y, int z, int faces) {
		return (x - region.minX) | (y - region.minY) << 4 | (z - region.minZ) << 8 | faces << 12;
	}

	private static int visibleFaces(Block block, SchematicWorld schematic, int x, int y, int z) {
		int faces = 0;
		if (block.method_1618(schematic, x, y - 1, z, 0)) faces |= DOWN;
		if (block.method_1618(schematic, x, y + 1, z, 1)) faces |= UP;
		if (block.method_1618(schematic, x, y, z - 1, 2)) faces |= NORTH;
		if (block.method_1618(schematic, x, y, z + 1, 3)) faces |= SOUTH;
		if (block.method_1618(schematic, x - 1, y, z, 4)) faces |= WEST;
		if (block.method_1618(schematic, x + 1, y, z, 5)) faces |= EAST;
		return faces;
	}

	// --- boxes drawn straight, without a cache ------------------------------------------------

	/** The schematic's outline. Twelve edges, so there is nothing to gain by caching it. */
	private void drawBoundingBox(SchematicWorld schematic) {
		float delta = this.config.blockDelta;
		GL11.glColor4f(0.75F, 0.0F, 0.75F, 0.25F);
		GL11.glBegin(GL11.GL_LINES);
		boxLines(-delta, -delta, -delta,
				schematic.getWidth() + delta, schematic.getHeight() + delta, schematic.getLength() + delta,
				ALL_FACES);
		GL11.glEnd();
	}

	private void drawGuide() {
		Vec3i min = this.state.pointMin.copy().sub(this.state.offset);
		Vec3i max = this.state.pointMax.copy().sub(this.state.offset).add(1);
		float delta = this.config.blockDelta;

		GL11.glColor4f(0.0F, 0.75F, 0.0F, 0.25F);
		GL11.glBegin(GL11.GL_LINES);
		boxLines(min.x - delta, min.y - delta, min.z - delta, max.x + delta, max.y + delta, max.z + delta, ALL_FACES);
		GL11.glEnd();

		this.drawGuideCorner(this.state.pointA, 0.75F, 0.0F, 0.0F);
		this.drawGuideCorner(this.state.pointB, 0.0F, 0.0F, 0.75F);
	}

	private void drawGuideCorner(Vec3i point, float red, float green, float blue) {
		float delta = this.config.blockDelta;
		float x = point.x - this.state.offset.x - delta;
		float y = point.y - this.state.offset.y - delta;
		float z = point.z - this.state.offset.z - delta;
		float maxX = x + 1 + delta * 2;
		float maxY = y + 1 + delta * 2;
		float maxZ = z + 1 + delta * 2;

		GL11.glColor4f(red, green, blue, 0.25F);
		GL11.glBegin(GL11.GL_LINES);
		boxLines(x, y, z, maxX, maxY, maxZ, ALL_FACES);
		GL11.glEnd();
		GL11.glBegin(GL11.GL_QUADS);
		boxQuads(x, y, z, maxX, maxY, maxZ, ALL_FACES);
		GL11.glEnd();
	}

	// --- block entities ----------------------------------------------------------------------

	private void renderBlockEntities(Minecraft minecraft, SchematicWorld schematic) {
		BlockView world = minecraft.world;

		int minY = 0;
		int maxY = schematic.getHeight();
		if (this.state.renderingLayer >= 0) {
			minY = this.state.renderingLayer;
			maxY = Math.min(maxY, this.state.renderingLayer + 1);
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, this.config.alpha);

		for (BlockEntity blockEntity : schematic.getSchematic().getBlockEntities()) {
			int x = blockEntity.x;
			int y = blockEntity.y;
			int z = blockEntity.z;

			if (y < minY || y >= maxY) {
				continue;
			}
			// A big build can carry thousands of chests and signs, and each one is a draw call.
			if (!this.frustum.isBoxVisible(x - CULL_MARGIN, y - CULL_MARGIN, z - CULL_MARGIN,
					x + 1 + CULL_MARGIN, y + 1 + CULL_MARGIN, z + 1 + CULL_MARGIN)) {
				continue;
			}
			// Only draw the ghost where the real world is still empty.
			if (world.getBlockId(x + this.state.offset.x, y + this.state.offset.y, z + this.state.offset.z) != 0) {
				continue;
			}

			try {
				if (blockEntity instanceof SignBlockEntity) {
					// Vanilla's sign renderer draws opaque text; ours honours the ghost alpha.
					this.signRenderer.render(schematic, (SignBlockEntity) blockEntity, this.config.alpha);
				} else {
					BlockEntityRenderer renderer = BlockEntityRenderDispatcher.INSTANCE.method_1279(blockEntity);
					if (renderer != null) {
						renderer.render(blockEntity, x, y, z, 0.0F);
					}
				}
			} catch (RuntimeException exception) {
				Log.warn("Block entity at " + x + ", " + y + ", " + z + " failed to render", exception);
			}

			GL11.glColor4f(1.0F, 1.0F, 1.0F, this.config.alpha);
		}
	}

	// --- cuboids -------------------------------------------------------------------------------

	/** Emits one box's faces; the caller has an open {@code GL_QUADS} block. */
	private static void boxQuads(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int faces) {
		if ((faces & WEST) != 0) {
			GL11.glVertex3f(minX, minY, minZ);
			GL11.glVertex3f(minX, minY, maxZ);
			GL11.glVertex3f(minX, maxY, maxZ);
			GL11.glVertex3f(minX, maxY, minZ);
		}
		if ((faces & EAST) != 0) {
			GL11.glVertex3f(maxX, minY, maxZ);
			GL11.glVertex3f(maxX, minY, minZ);
			GL11.glVertex3f(maxX, maxY, minZ);
			GL11.glVertex3f(maxX, maxY, maxZ);
		}
		if ((faces & NORTH) != 0) {
			GL11.glVertex3f(maxX, minY, minZ);
			GL11.glVertex3f(minX, minY, minZ);
			GL11.glVertex3f(minX, maxY, minZ);
			GL11.glVertex3f(maxX, maxY, minZ);
		}
		if ((faces & SOUTH) != 0) {
			GL11.glVertex3f(minX, minY, maxZ);
			GL11.glVertex3f(maxX, minY, maxZ);
			GL11.glVertex3f(maxX, maxY, maxZ);
			GL11.glVertex3f(minX, maxY, maxZ);
		}
		if ((faces & DOWN) != 0) {
			GL11.glVertex3f(maxX, minY, minZ);
			GL11.glVertex3f(maxX, minY, maxZ);
			GL11.glVertex3f(minX, minY, maxZ);
			GL11.glVertex3f(minX, minY, minZ);
		}
		if ((faces & UP) != 0) {
			GL11.glVertex3f(maxX, maxY, minZ);
			GL11.glVertex3f(minX, maxY, minZ);
			GL11.glVertex3f(minX, maxY, maxZ);
			GL11.glVertex3f(maxX, maxY, maxZ);
		}
	}

	/** Emits one box's edges; the caller has an open {@code GL_LINES} block. */
	private static void boxLines(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int faces) {
		// An edge is drawn when either of the two faces meeting at it is visible.
		edge(faces, WEST | DOWN, minX, minY, minZ, minX, minY, maxZ);
		edge(faces, WEST | UP, minX, maxY, minZ, minX, maxY, maxZ);
		edge(faces, EAST | DOWN, maxX, minY, minZ, maxX, minY, maxZ);
		edge(faces, EAST | UP, maxX, maxY, minZ, maxX, maxY, maxZ);
		edge(faces, NORTH | DOWN, minX, minY, minZ, maxX, minY, minZ);
		edge(faces, NORTH | UP, minX, maxY, minZ, maxX, maxY, minZ);
		edge(faces, SOUTH | DOWN, minX, minY, maxZ, maxX, minY, maxZ);
		edge(faces, SOUTH | UP, minX, maxY, maxZ, maxX, maxY, maxZ);
		edge(faces, NORTH | WEST, minX, minY, minZ, minX, maxY, minZ);
		edge(faces, NORTH | EAST, maxX, minY, minZ, maxX, maxY, minZ);
		edge(faces, SOUTH | WEST, minX, minY, maxZ, minX, maxY, maxZ);
		edge(faces, SOUTH | EAST, maxX, minY, maxZ, maxX, maxY, maxZ);
	}

	private static void edge(int faces, int mask, float x1, float y1, float z1, float x2, float y2, float z2) {
		if ((faces & mask) == 0) {
			return;
		}
		GL11.glVertex3f(x1, y1, z1);
		GL11.glVertex3f(x2, y2, z2);
	}

	private static int regionCount(int blocks) {
		return Math.max(1, (blocks + REGION_SIZE - 1) / REGION_SIZE);
	}

	/** One 16x16x16 slice of the schematic and the two display lists caching it. */
	private static final class Region {
		final int minX;
		final int minY;
		final int minZ;
		/** Exclusive, and clamped to the schematic at the far edges. */
		final int maxX;
		final int maxY;
		final int maxZ;

		final int blockList;
		final int overlayList;

		boolean dirty = true;
		boolean hasBlocks;
		boolean hasOverlay;
		boolean visible = true;

		/** Distance from the camera, with off-screen regions pushed to the back of the queue. */
		double sortKey;

		Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int blockList, int overlayList) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
			this.blockList = blockList;
			this.overlayList = overlayList;
		}
	}
}
