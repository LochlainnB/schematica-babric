package lunatrius.schematica.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import lunatrius.schematica.OpenSchematic;
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
 * Draws every open schematic as a ghost overlay, plus the save-selection guide boxes.
 *
 * <p>Each schematic is cut into 16x16x16 regions, each holding two display lists - the ghost blocks
 * and the coloured comparison boxes over them. A region is rebuilt only when something it covers
 * changed, and only regions the camera can actually see are drawn, so the cost of both halves
 * tracks what is on screen rather than the size of the schematic.
 *
 * <p>Rebuilding is capped at {@link #REBUILD_BUDGET_NANOS} per frame - for all of them together,
 * not each. Whole-schematic invalidations are unavoidable, because every block is compared against
 * the world underneath it; spreading one over a handful of frames turns what was a stall
 * proportional to the schematic's volume into a visible refresh. Sharing one budget is what keeps
 * that true with six schematics open: the dirty regions of all of them go into one queue sorted by
 * distance from the camera, so the nearest work is done first whichever schematic it belongs to,
 * and a frame costs the same whether the work in front of the player came from one build or four.
 *
 * <p>Everything is drawn from {@code renderWeather}, which runs inside the world pass with the
 * camera at the origin. The modelview is translated by {@code -cameraPosition} once, which puts it
 * in world coordinates - the space the culling planes and the guide boxes are then in - and each
 * schematic is drawn inside a further translation by its own offset, so its geometry can go on
 * being compiled and culled in schematic-local coordinates.
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

	/** One per open schematic that has something to draw, in the order they were opened. */
	private final List<Overlay> overlays = new ArrayList<>();

	/** Reused between frames so the per-frame rebuild pass allocates nothing. */
	private final List<Region> rebuildQueue = new ArrayList<>();

	/**
	 * Highlight boxes found while a region's blocks are being compiled, packed as
	 * {@code x | y << 4 | z << 8 | faces << 12} with the coordinates region-local. A cell can land
	 * in at most one of the three, so a region can never overflow them. Shared across schematics:
	 * they hold what one region compile found, and only one is ever in flight.
	 */
	private final int[] wrongBlock = new int[REGION_VOLUME];
	private final int[] wrongMetadata = new int[REGION_VOLUME];
	private final int[] missingBlock = new int[REGION_VOLUME];
	private int wrongBlockCount;
	private int wrongMetadataCount;
	private int missingBlockCount;

	/** Forces every region of every open schematic to be rebuilt. */
	public void invalidate() {
		this.state.invalidateAll();
	}

	/** Whether any of the overlays is still waiting to be rebuilt. */
	public boolean isRebuilding() {
		for (OpenSchematic open : this.state.getOpen()) {
			if (open.schematic == null || !open.isRenderingSchematic) {
				continue;
			}
			if (open.needsUpdate) {
				return true;
			}

			Overlay overlay = this.findOverlay(open);
			if (overlay == null) {
				continue;
			}
			for (Region region : overlay.regions) {
				if (region.dirty) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * How many blocks the overlays are currently drawing as still to be placed, across every open
	 * schematic. Read off the compiled geometry rather than worked out again, so it answers what is
	 * really on screen - which is the only way to tell an overlay built against a world that had not
	 * arrived yet from one that is right.
	 */
	public int getGhostBlockCount() {
		int total = 0;
		for (Overlay overlay : this.overlays) {
			total += overlay.ghostBlocks();
		}
		return total;
	}

	/** The same for one schematic, which is what a screen pointed at one of them asks. */
	public int getGhostBlockCount(OpenSchematic open) {
		Overlay overlay = this.findOverlay(open);
		return overlay == null ? 0 : overlay.ghostBlocks();
	}

	/**
	 * Marks only the region holding one schematic-local position, which is all a block changing in
	 * the world costs: the overlay compares that one cell against the world and nothing else reads
	 * it, so a neighbouring region cannot be affected.
	 */
	public void invalidateBlock(OpenSchematic open, int x, int y, int z) {
		Overlay overlay = this.findOverlay(open);
		if (overlay == null || !overlay.gridMatches()) {
			// No grid to mark yet. The next frame lays one out and builds all of it regardless.
			open.needsUpdate = true;
			return;
		}

		int regionX = x / REGION_SIZE;
		int regionY = y / REGION_SIZE;
		int regionZ = z / REGION_SIZE;
		if (regionX < 0 || regionY < 0 || regionZ < 0
				|| regionX >= overlay.regionsX || regionY >= overlay.regionsY || regionZ >= overlay.regionsZ) {
			return;
		}

		overlay.regions[(regionX * overlay.regionsY + regionY) * overlay.regionsZ + regionZ].dirty = true;
	}

	/**
	 * Marks every region a box of schematic-local positions touches. A chunk arriving from a server
	 * lands here: it brings a whole column of world at once, and the overlay is drawn by comparing
	 * itself against that world, so the part of it standing in the new chunk is now out of date.
	 */
	public void invalidateBox(OpenSchematic open, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		Overlay overlay = this.findOverlay(open);
		if (overlay == null || !overlay.gridMatches()) {
			// No grid to mark yet. The next frame lays one out and builds all of it regardless.
			open.needsUpdate = true;
			return;
		}

		if (maxX < 0 || maxY < 0 || maxZ < 0
				|| minX >= overlay.gridWidth || minY >= overlay.gridHeight || minZ >= overlay.gridLength) {
			// Somewhere else in the world entirely, which is where most chunks are.
			return;
		}

		int fromX = Math.max(minX, 0) / REGION_SIZE;
		int fromY = Math.max(minY, 0) / REGION_SIZE;
		int fromZ = Math.max(minZ, 0) / REGION_SIZE;
		int toX = Math.min(maxX, overlay.gridWidth - 1) / REGION_SIZE;
		int toY = Math.min(maxY, overlay.gridHeight - 1) / REGION_SIZE;
		int toZ = Math.min(maxZ, overlay.gridLength - 1) / REGION_SIZE;

		for (int x = fromX; x <= toX; x++) {
			for (int y = fromY; y <= toY; y++) {
				for (int z = fromZ; z <= toZ; z++) {
					overlay.regions[(x * overlay.regionsY + y) * overlay.regionsZ + z].dirty = true;
				}
			}
		}
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

		// Tracked every frame even when nothing is drawn: the GUIs read it to drop a schematic and
		// the selection corners at the player's feet.
		this.state.playerPosition.set(
				(float) (player.field_1637 + (player.x - player.field_1637) * delta),
				(float) (player.field_1638 + (player.y - player.field_1638) * delta),
				(float) (player.field_1639 + (player.z - player.field_1639) * delta));
		this.state.rotationRender = (int) ((player.yaw / 90.0F % 4.0F + 4.0F) % 4.0F);

		// Before the early return: a schematic closed while nothing is being drawn still has display
		// lists out on loan from the driver, and this is what hands them back.
		this.syncOverlays();

		boolean drawSchematics = false;
		for (Overlay overlay : this.overlays) {
			if (overlay.isDrawn()) {
				drawSchematics = true;
				break;
			}
		}
		if (!drawSchematics && !this.state.isRenderingGuide) {
			// The cached regions are deliberately left alone: hiding an overlay and bringing it back
			// is then free, and world changes keep marking regions dirty while it is hidden.
			return;
		}

		GL11.glPushMatrix();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);

		// Into world coordinates: each schematic is then drawn inside one more translation by its
		// own offset, and one set of culling planes serves all of them.
		GL11.glTranslatef(
				-this.state.playerPosition.x, -this.state.playerPosition.y, -this.state.playerPosition.z);
		this.frustum.update();

		if (drawSchematics) {
			this.rebuildQueue.clear();
			for (Overlay overlay : this.overlays) {
				if (!overlay.isDrawn()) {
					continue;
				}
				this.syncGrid(overlay);
				if (overlay.open.needsUpdate) {
					overlay.markAllDirty();
					overlay.open.needsUpdate = false;
				}
				this.updateVisibility(overlay);
				for (Region region : overlay.regions) {
					if (region.dirty) {
						this.rebuildQueue.add(region);
					}
				}
			}
			this.rebuildDirtyRegions(minecraft);

			for (Overlay overlay : this.overlays) {
				if (!overlay.isDrawn()) {
					continue;
				}
				GL11.glPushMatrix();
				this.translateTo(overlay);
				for (Region region : overlay.regions) {
					if (region.visible && region.ghostBlocks > 0) {
						GL11.glCallList(region.blockList);
					}
				}
				this.renderBlockEntities(minecraft, overlay);
				GL11.glPopMatrix();
			}
		}

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glLineWidth(1.5F);

		if (drawSchematics) {
			for (Overlay overlay : this.overlays) {
				if (!overlay.isDrawn()) {
					continue;
				}
				GL11.glPushMatrix();
				this.translateTo(overlay);
				for (Region region : overlay.regions) {
					if (region.visible && region.hasOverlay) {
						GL11.glCallList(region.overlayList);
					}
				}
				this.drawBoundingBox(overlay.open.schematic);
				GL11.glPopMatrix();
			}
		}
		if (this.state.isRenderingGuide) {
			// Drawn in world coordinates, where its corners already are: the selection is a place in
			// the world rather than a part of any one schematic.
			this.drawGuide();
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glPopMatrix();
	}

	private void translateTo(Overlay overlay) {
		GL11.glTranslatef(overlay.open.offset.x, overlay.open.offset.y, overlay.open.offset.z);
	}

	// --- one overlay per open schematic ---------------------------------------------------------

	private Overlay findOverlay(OpenSchematic open) {
		for (Overlay overlay : this.overlays) {
			if (overlay.open == open) {
				return overlay;
			}
		}
		return null;
	}

	/**
	 * Brings the overlays into line with what is open: one for each loaded schematic, and none for
	 * a slot that has been closed or emptied.
	 *
	 * <p>Where the display lists of a closed schematic are handed back. That has to happen on the
	 * thread holding the context, which is this one, and it is why closing a schematic anywhere else
	 * in the mod is only ever a matter of dropping it from the list.
	 */
	private void syncOverlays() {
		List<OpenSchematic> open = this.state.getOpen();

		for (Iterator<Overlay> iterator = this.overlays.iterator(); iterator.hasNext();) {
			Overlay overlay = iterator.next();
			if (overlay.open.schematic == null || !open.contains(overlay.open)) {
				overlay.releaseLists();
				iterator.remove();
			}
		}

		for (OpenSchematic schematic : open) {
			if (schematic.schematic != null && this.findOverlay(schematic) == null) {
				this.overlays.add(new Overlay(schematic));
			}
		}
	}

	// --- the region grid ---------------------------------------------------------------------

	/** Lays out a fresh grid whenever a schematic is loaded, or a rotation changes its shape. */
	private void syncGrid(Overlay overlay) {
		if (overlay.gridMatches()) {
			return;
		}

		overlay.releaseLists();

		SchematicWorld schematic = overlay.open.schematic;
		overlay.gridSchematic = schematic;
		overlay.gridWidth = schematic.getWidth();
		overlay.gridHeight = schematic.getHeight();
		overlay.gridLength = schematic.getLength();

		overlay.regionsX = regionCount(overlay.gridWidth);
		overlay.regionsY = regionCount(overlay.gridHeight);
		overlay.regionsZ = regionCount(overlay.gridLength);

		int count = overlay.regionsX * overlay.regionsY * overlay.regionsZ;
		int base = GL11.glGenLists(count * 2);
		if (base == 0) {
			Log.error("The driver would not hand out " + (count * 2) + " display lists;"
					+ " the schematic cannot be drawn.");
			overlay.regions = new Region[0];
			overlay.regionsX = 0;
			overlay.regionsY = 0;
			overlay.regionsZ = 0;
			return;
		}

		overlay.listBase = base;
		overlay.listCount = count * 2;
		overlay.regions = new Region[count];

		int index = 0;
		for (int x = 0; x < overlay.regionsX; x++) {
			for (int y = 0; y < overlay.regionsY; y++) {
				for (int z = 0; z < overlay.regionsZ; z++) {
					overlay.regions[index] = new Region(overlay,
							x * REGION_SIZE, y * REGION_SIZE, z * REGION_SIZE,
							Math.min((x + 1) * REGION_SIZE, overlay.gridWidth),
							Math.min((y + 1) * REGION_SIZE, overlay.gridHeight),
							Math.min((z + 1) * REGION_SIZE, overlay.gridLength),
							base + index * 2, base + index * 2 + 1);
					index++;
				}
			}
		}
	}

	/**
	 * Culls every region against the view, and records how far each is from the camera.
	 *
	 * <p>Both in world coordinates, so that the distances of two schematics standing in different
	 * places can be compared against each other in the one rebuild queue.
	 */
	private void updateVisibility(Overlay overlay) {
		float originX = overlay.open.offset.x;
		float originY = overlay.open.offset.y;
		float originZ = overlay.open.offset.z;
		double cameraX = this.state.playerPosition.x;
		double cameraY = this.state.playerPosition.y;
		double cameraZ = this.state.playerPosition.z;

		for (Region region : overlay.regions) {
			float minX = originX + region.minX;
			float minY = originY + region.minY;
			float minZ = originZ + region.minZ;
			float maxX = originX + region.maxX;
			float maxY = originY + region.maxY;
			float maxZ = originZ + region.maxZ;

			region.visible = this.frustum.isBoxVisible(
					minX - CULL_MARGIN, minY - CULL_MARGIN, minZ - CULL_MARGIN,
					maxX + CULL_MARGIN, maxY + CULL_MARGIN, maxZ + CULL_MARGIN);

			double dx = (minX + maxX) * 0.5 - cameraX;
			double dy = (minY + maxY) * 0.5 - cameraY;
			double dz = (minZ + maxZ) * 0.5 - cameraZ;
			region.sortKey = dx * dx + dy * dy + dz * dz + (region.visible ? 0.0 : OFF_SCREEN);
		}
	}

	// --- rebuilding --------------------------------------------------------------------------

	/**
	 * Rebuilds dirty regions nearest-first until the frame's budget runs out, so whatever the player
	 * is looking at catches up before anything behind them does - whichever schematic it belongs to.
	 */
	private void rebuildDirtyRegions(Minecraft minecraft) {
		if (this.rebuildQueue.isEmpty()) {
			return;
		}
		this.rebuildQueue.sort(BY_SORT_KEY);

		// Smooth lighting samples neighbours through the BlockView; a schematic reports a flat
		// brightness, so it is turned off for the duration of the sweep.
		boolean ambientOcclusion = minecraft.options.ao;
		minecraft.options.ao = false;

		try {
			long deadline = System.nanoTime() + REBUILD_BUDGET_NANOS;
			for (int i = 0; i < this.rebuildQueue.size(); i++) {
				Region region = this.rebuildQueue.get(i);
				if (region.overlay.open.blockRenderer == null) {
					continue;
				}
				this.rebuildRegion(minecraft, region);
				if (System.nanoTime() >= deadline) {
					break;
				}
			}
		} finally {
			minecraft.options.ao = ambientOcclusion;
		}
	}

	private void rebuildRegion(Minecraft minecraft, Region region) {
		region.dirty = false;
		this.wrongBlockCount = 0;
		this.wrongMetadataCount = 0;
		this.missingBlockCount = 0;

		OpenSchematic open = region.overlay.open;
		SchematicWorld schematic = open.schematic;
		BlockView world = minecraft.world;
		class_13 blockRenderer = open.blockRenderer;

		int minY = region.minY;
		int maxY = region.maxY;
		if (open.renderingLayer >= 0) {
			// A region outside the slice ends up with minY >= maxY, which compiles two empty lists -
			// the old geometry has to go somewhere, and leaving it behind would draw it.
			minY = Math.max(minY, open.renderingLayer);
			maxY = Math.min(maxY, open.renderingLayer + 1);
		}

		int offsetX = open.offset.x;
		int offsetY = open.offset.y;
		int offsetZ = open.offset.z;
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

		region.ghostBlocks = ghostBlocks;
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
		float delta = this.config.blockDelta;

		GL11.glColor4f(0.0F, 0.75F, 0.0F, 0.25F);
		GL11.glBegin(GL11.GL_LINES);
		boxLines(this.state.pointMin.x - delta, this.state.pointMin.y - delta, this.state.pointMin.z - delta,
				this.state.pointMax.x + 1 + delta, this.state.pointMax.y + 1 + delta, this.state.pointMax.z + 1 + delta,
				ALL_FACES);
		GL11.glEnd();

		this.drawGuideCorner(this.state.pointA, 0.75F, 0.0F, 0.0F);
		this.drawGuideCorner(this.state.pointB, 0.0F, 0.0F, 0.75F);
	}

	private void drawGuideCorner(Vec3i point, float red, float green, float blue) {
		float delta = this.config.blockDelta;
		float x = point.x - delta;
		float y = point.y - delta;
		float z = point.z - delta;
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

	private void renderBlockEntities(Minecraft minecraft, Overlay overlay) {
		OpenSchematic open = overlay.open;
		SchematicWorld schematic = open.schematic;
		BlockView world = minecraft.world;

		int minY = 0;
		int maxY = schematic.getHeight();
		if (open.renderingLayer >= 0) {
			minY = open.renderingLayer;
			maxY = Math.min(maxY, open.renderingLayer + 1);
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, this.config.alpha);

		for (BlockEntity blockEntity : schematic.getSchematic().getBlockEntities()) {
			int x = blockEntity.x;
			int y = blockEntity.y;
			int z = blockEntity.z;

			if (y < minY || y >= maxY) {
				continue;
			}
			// A big build can carry thousands of chests and signs, and each one is a draw call. The
			// culling planes are in world coordinates, so the position is put back into them first.
			if (!this.frustum.isBoxVisible(
					x + open.offset.x - CULL_MARGIN, y + open.offset.y - CULL_MARGIN, z + open.offset.z - CULL_MARGIN,
					x + open.offset.x + 1 + CULL_MARGIN, y + open.offset.y + 1 + CULL_MARGIN, z + open.offset.z + 1 + CULL_MARGIN)) {
				continue;
			}
			// Only draw the ghost where the real world is still empty.
			if (world.getBlockId(x + open.offset.x, y + open.offset.y, z + open.offset.z) != 0) {
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

	/** The cached geometry of one open schematic: its region grid and the display lists behind it. */
	private static final class Overlay {
		final OpenSchematic open;

		Region[] regions = new Region[0];
		int regionsX;
		int regionsY;
		int regionsZ;

		/** The display lists behind {@link #regions}, allocated as one contiguous run. */
		int listBase;
		int listCount;

		/** What the region grid was laid out for; a rotation swaps the dimensions in place. */
		SchematicWorld gridSchematic;
		int gridWidth;
		int gridHeight;
		int gridLength;

		Overlay(OpenSchematic open) {
			this.open = open;
		}

		/** Whether this schematic is both loaded and being shown. */
		boolean isDrawn() {
			return this.open.schematic != null && this.open.isRenderingSchematic;
		}

		boolean gridMatches() {
			SchematicWorld schematic = this.open.schematic;
			return schematic != null
					&& this.gridSchematic == schematic
					&& this.gridWidth == schematic.getWidth()
					&& this.gridHeight == schematic.getHeight()
					&& this.gridLength == schematic.getLength();
		}

		void markAllDirty() {
			for (Region region : this.regions) {
				region.dirty = true;
			}
		}

		int ghostBlocks() {
			int total = 0;
			for (Region region : this.regions) {
				total += region.ghostBlocks;
			}
			return total;
		}

		void releaseLists() {
			if (this.listCount > 0) {
				GL11.glDeleteLists(this.listBase, this.listCount);
				this.listBase = 0;
				this.listCount = 0;
			}
			this.gridSchematic = null;
			this.regions = new Region[0];
		}
	}

	/** One 16x16x16 slice of a schematic and the two display lists caching it. */
	private static final class Region {
		/** Whose slice this is, which is how the rebuild queue can hold regions of several. */
		final Overlay overlay;

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
		/** Blocks in this region the world does not have, counted as the geometry was compiled. */
		int ghostBlocks;
		boolean hasOverlay;
		boolean visible = true;

		/** Distance from the camera, with off-screen regions pushed to the back of the queue. */
		double sortKey;

		Region(Overlay overlay, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
				int blockList, int overlayList) {
			this.overlay = overlay;
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
