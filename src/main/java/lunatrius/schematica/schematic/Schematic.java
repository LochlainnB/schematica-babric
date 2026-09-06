package lunatrius.schematica.schematic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.entity.BlockEntity;

/**
 * The in-memory contents of a schematic: block ids, metadata and block entities, indexed
 * {@code [x][y][z]} with the origin at the schematic's minimum corner.
 *
 * <p>Deliberately free of any rendering or world state so that future features (material lists,
 * printers, format converters) can work on it directly.
 */
public class Schematic {
	private int[][][] blocks;
	private int[][][] metadata;
	private final List<BlockEntity> blockEntities;

	private int width;
	private int height;
	private int length;

	public Schematic(int width, int height, int length) {
		this(new int[width][height][length], new int[width][height][length], new ArrayList<>(), width, height, length);
	}

	public Schematic(int[][][] blocks, int[][][] metadata, List<BlockEntity> blockEntities, int width, int height, int length) {
		this.blocks = blocks;
		this.metadata = metadata;
		this.blockEntities = blockEntities;
		this.width = width;
		this.height = height;
		this.length = length;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getLength() {
		return this.length;
	}

	public int getVolume() {
		return this.width * this.height * this.length;
	}

	public boolean isInside(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.height && z < this.length;
	}

	public int getBlockId(int x, int y, int z) {
		return this.isInside(x, y, z) ? this.blocks[x][y][z] : 0;
	}

	public void setBlockId(int x, int y, int z, int blockId) {
		if (this.isInside(x, y, z)) {
			this.blocks[x][y][z] = blockId;
		}
	}

	public int getMetadata(int x, int y, int z) {
		return this.isInside(x, y, z) ? this.metadata[x][y][z] : 0;
	}

	public void setMetadata(int x, int y, int z, int value) {
		if (this.isInside(x, y, z)) {
			this.metadata[x][y][z] = value;
		}
	}

	public List<BlockEntity> getBlockEntities() {
		return this.blockEntities;
	}

	public BlockEntity getBlockEntity(int x, int y, int z) {
		for (int i = 0; i < this.blockEntities.size(); i++) {
			BlockEntity blockEntity = this.blockEntities.get(i);
			if (blockEntity.x == x && blockEntity.y == y && blockEntity.z == z) {
				return blockEntity;
			}
		}
		return null;
	}

	/** Mirrors the schematic along the Z axis, in place. */
	public void mirrorZ() {
		for (int x = 0; x < this.width; x++) {
			for (int y = 0; y < this.height; y++) {
				for (int z = 0; z < (this.length + 1) / 2; z++) {
					int far = this.length - 1 - z;

					int nearBlock = this.blocks[x][y][z];
					int farBlock = this.blocks[x][y][far];
					this.blocks[x][y][z] = farBlock;
					this.blocks[x][y][far] = nearBlock;

					int nearMeta = this.metadata[x][y][z];
					int farMeta = this.metadata[x][y][far];
					this.metadata[x][y][z] = BlockTransform.mirrorZ(farBlock, farMeta);
					this.metadata[x][y][far] = BlockTransform.mirrorZ(nearBlock, nearMeta);
				}
			}
		}

		for (BlockEntity blockEntity : this.blockEntities) {
			blockEntity.z = this.length - 1 - blockEntity.z;
		}
	}

	/** Rotates the schematic 90 degrees about the Y axis, in place. Width and length swap. */
	public void rotate() {
		int[][][] rotatedBlocks = new int[this.length][this.height][this.width];
		int[][][] rotatedMetadata = new int[this.length][this.height][this.width];

		for (int x = 0; x < this.width; x++) {
			for (int y = 0; y < this.height; y++) {
				for (int z = 0; z < this.length; z++) {
					int sourceX = this.width - 1 - x;
					int blockId = this.blocks[sourceX][y][z];
					rotatedBlocks[z][y][x] = blockId;
					rotatedMetadata[z][y][x] = BlockTransform.rotate(blockId, this.metadata[sourceX][y][z]);
				}
			}
		}

		this.blocks = rotatedBlocks;
		this.metadata = rotatedMetadata;

		for (BlockEntity blockEntity : this.blockEntities) {
			int oldX = blockEntity.x;
			blockEntity.x = blockEntity.z;
			blockEntity.z = this.width - 1 - oldX;
		}

		int oldWidth = this.width;
		this.width = this.length;
		this.length = oldWidth;
	}
}
