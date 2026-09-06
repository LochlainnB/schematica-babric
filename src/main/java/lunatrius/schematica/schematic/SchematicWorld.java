package lunatrius.schematica.schematic;

import java.util.HashMap;
import java.util.Map;

import lunatrius.schematica.Schematica;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.class_43;
import net.minecraft.class_50;
import net.minecraft.class_51;
import net.minecraft.class_62;
import net.minecraft.world.World;

/**
 * Presents a {@link Schematic} as a read-only {@link World} so the vanilla block and block-entity
 * renderers can draw it.
 *
 * <p>It is a {@code World} rather than a bare {@code BlockView} because
 * {@link BlockEntity#world} is typed as {@code World}, and the block entity renderers dereference
 * it. Nothing here ever ticks, saves or generates; the chunks {@link #method_197()} hands out are
 * windows onto the schematic rather than storage.
 */
public class SchematicWorld extends World {
	private final Schematic schematic;

	public SchematicWorld(World template, Schematic schematic) {
		// A *fresh* dimension: World's copy constructor calls dimension.method_1768(this), which
		// would otherwise repoint the live world's dimension (and its biome source) at us.
		super(template, class_50.method_1767(0));
		this.schematic = schematic;
		this.isRemote = true;

		for (BlockEntity blockEntity : schematic.getBlockEntities()) {
			blockEntity.world = this;
		}
	}

	public Schematic getSchematic() {
		return this.schematic;
	}

	public int getWidth() {
		return this.schematic.getWidth();
	}

	public int getHeight() {
		return this.schematic.getHeight();
	}

	public int getLength() {
		return this.schematic.getLength();
	}

	public Block getBlock(int x, int y, int z) {
		return Block.BLOCKS[this.getBlockId(x, y, z)];
	}

	/** Re-points block entities at this world after the schematic has been rotated or mirrored. */
	public void refreshBlockEntities() {
		for (BlockEntity blockEntity : this.schematic.getBlockEntities()) {
			blockEntity.world = this;
		}
	}

	// --- BlockView ---------------------------------------------------------------------------

	@Override
	public int getBlockId(int x, int y, int z) {
		return this.schematic.getBlockId(x, y, z);
	}

	@Override
	public int method_1778(int x, int y, int z) {
		return this.schematic.getMetadata(x, y, z);
	}

	@Override
	public BlockEntity method_1777(int x, int y, int z) {
		return this.schematic.getBlockEntity(x, y, z);
	}

	@Override
	public Material method_1779(int x, int y, int z) {
		Block block = this.getBlock(x, y, z);
		return block != null ? block.field_1900 : Material.AIR;
	}

	/** Brightness with a floor - the schematic is always rendered fully lit. */
	@Override
	public float method_1784(int x, int y, int z, int minLight) {
		return 1.0F;
	}

	@Override
	public float method_1782(int x, int y, int z) {
		return 1.0F;
	}

	/** Whether the block at this position is a full opaque cube (used for face culling). */
	@Override
	public boolean method_1783(int x, int y, int z) {
		if (Schematica.STATE.renderingLayer != -1 && Schematica.STATE.renderingLayer != y) {
			return false;
		}
		Block block = this.getBlock(x, y, z);
		return block != null && block.method_1620();
	}

	@Override
	public boolean method_1780(int x, int y, int z) {
		Block block = this.getBlock(x, y, z);
		return this.method_1779(x, y, z).method_897() && block != null && block.method_1623();
	}

	@Override
	public boolean method_234(int x, int y, int z) {
		return this.schematic.getBlockId(x, y, z) == 0;
	}

	// --- chunks ------------------------------------------------------------------------------

	/**
	 * Chunks that read straight through to the schematic, holding no block data of their own.
	 *
	 * <p>Every {@code BlockView} method the vanilla block renderer calls is overridden above, so for
	 * a long time this returned nothing at all. Mods reach for the chunks anyway: StationAPI's
	 * flattening reads a block's state through {@code World.getBlockState}, which goes chunk-first,
	 * and its Arsenic renderer draws from that state rather than from the block id. With no chunk
	 * source both walked into a null and took the game down as soon as an overlay was drawn.
	 */
	@Override
	protected class_51 method_197() {
		// Called from World's constructor, so this cannot capture anything this class has not
		// assigned yet - hence an inner class that reads the schematic when it is asked, not now.
		return new ChunkSource();
	}

	private final class ChunkSource implements class_51 {
		private final Map<Long, class_43> chunks = new HashMap<>();

		@Override
		public boolean method_1802(int chunkX, int chunkZ) {
			return true;
		}

		@Override
		public class_43 method_1806(int chunkX, int chunkZ) {
			long key = (long) chunkX & 0xFFFFFFFFL | (long) chunkZ << 32;
			return this.chunks.computeIfAbsent(key, ignored -> new SchematicChunk(SchematicWorld.this, chunkX, chunkZ));
		}

		@Override
		public class_43 method_1807(int chunkX, int chunkZ) {
			return this.method_1806(chunkX, chunkZ);
		}

		@Override
		public void method_1803(class_51 source, int chunkX, int chunkZ) {
		}

		/** Nothing to write: {@link #method_1805()} already says this world is not saveable. */
		@Override
		public boolean method_1804(boolean flush, class_62 progress) {
			return true;
		}

		@Override
		public boolean method_1801() {
			return false;
		}

		@Override
		public boolean method_1805() {
			return false;
		}

		@Override
		public String method_1808() {
			return "SchematicChunkSource";
		}
	}

	/** One 16x16 column of the schematic, seen as a chunk. */
	private static final class SchematicChunk extends class_43 {
		private final SchematicWorld world;

		SchematicChunk(SchematicWorld world, int chunkX, int chunkZ) {
			super(world, chunkX, chunkZ);
			this.world = world;
		}

		@Override
		public int method_859(int x, int y, int z) {
			return this.world.getBlockId(this.blockX(x), y, this.blockZ(z));
		}

		@Override
		public int method_875(int x, int y, int z) {
			return this.world.method_1778(this.blockX(x), y, this.blockZ(z));
		}

		/** Fully lit, matching the brightness the world itself reports. */
		@Override
		public int method_880(int x, int y, int z, int ambientDarkness) {
			return 15;
		}

		private int blockX(int x) {
			return (this.field_962 << 4) + x;
		}

		private int blockZ(int z) {
			return (this.field_963 << 4) + z;
		}
	}
}
