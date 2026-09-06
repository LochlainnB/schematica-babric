package lunatrius.schematica.schematic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import lunatrius.schematica.util.Log;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

/**
 * Reader and writer for the MCEdit {@code .schematic} format (gzipped NBT).
 *
 * <p>The format stores a block id per byte. Beta 1.7.3 on its own never needs more, so the optional
 * {@code Add} array that extends ids past 255 is parsed but any out-of-range block is dropped to
 * air, and {@link LoadResult#droppedBlocks} reports how many. Writing drops the same way, which only
 * comes up when a mod - StationAPI, say - has widened the id space beyond what the format holds.
 */
public final class SchematicFormat {
	private SchematicFormat() {
	}

	/** A loaded schematic plus whatever this game version could not represent. */
	public static class LoadResult {
		public final Schematic schematic;
		public final int droppedBlocks;

		LoadResult(Schematic schematic, int droppedBlocks) {
			this.schematic = schematic;
			this.droppedBlocks = droppedBlocks;
		}
	}

	public static LoadResult read(File file) throws IOException {
		try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
			NbtCompound nbt = NbtIo.readCompressed(stream);
			if (nbt == null) {
				throw new IOException("Empty or unreadable schematic: " + file.getName());
			}
			return fromNbt(nbt);
		}
	}

	public static LoadResult fromNbt(NbtCompound nbt) {
		int width = nbt.getShort("Width") & 0xFFFF;
		int height = nbt.getShort("Height") & 0xFFFF;
		int length = nbt.getShort("Length") & 0xFFFF;

		if (width <= 0 || height <= 0 || length <= 0) {
			throw new IllegalArgumentException("Schematic has an empty or invalid size: " + width + "x" + height + "x" + length);
		}

		byte[] rawBlocks = nbt.getByteArray("Blocks");
		byte[] rawMetadata = nbt.getByteArray("Data");
		byte[] rawExtra = nbt.contains("Add") ? nbt.getByteArray("Add") : null;

		int expected = width * height * length;
		if (rawBlocks.length < expected || rawMetadata.length < expected) {
			throw new IllegalArgumentException("Schematic block data is truncated (expected " + expected + " entries)");
		}

		int[][][] blocks = new int[width][height][length];
		int[][][] metadata = new int[width][height][length];
		int dropped = 0;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					int index = x + (y * length + z) * width;
					int blockId = rawBlocks[index] & 0xFF;
					if (rawExtra != null && index < rawExtra.length) {
						blockId |= (rawExtra[index] & 0xFF) << 8;
					}

					if (blockId >= Block.BLOCKS.length) {
						// Not representable in b1.7.3 - leave the cell empty rather than crash the renderer.
						dropped++;
						blockId = 0;
					}

					blocks[x][y][z] = blockId;
					metadata[x][y][z] = rawMetadata[index] & 0xFF;
				}
			}
		}

		List<BlockEntity> blockEntities = new ArrayList<>();
		Schematic schematic = new Schematic(blocks, metadata, blockEntities, width, height, length);

		if (nbt.contains("TileEntities")) {
			NbtList list = nbt.getList("TileEntities");
			for (int i = 0; i < list.size(); i++) {
				NbtElement element = list.get(i);
				if (!(element instanceof NbtCompound)) {
					continue;
				}

				BlockEntity blockEntity = BlockEntity.method_1068((NbtCompound) element);
				if (blockEntity == null) {
					// b1.7.3 does not know this block entity type; the block itself still renders.
					continue;
				}
				if (schematic.isInside(blockEntity.x, blockEntity.y, blockEntity.z)) {
					blockEntities.add(blockEntity);
				}
			}
		}

		return new LoadResult(schematic, dropped);
	}

	public static void write(File file, Schematic schematic) throws IOException {
		NbtCompound nbt = toNbt(schematic);
		try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
			NbtIo.writeCompressed(nbt, stream);
		}
	}

	public static NbtCompound toNbt(Schematic schematic) {
		int width = schematic.getWidth();
		int height = schematic.getHeight();
		int length = schematic.getLength();

		NbtCompound nbt = new NbtCompound();
		nbt.putShort("Width", (short) width);
		nbt.putShort("Height", (short) height);
		nbt.putShort("Length", (short) length);
		nbt.putString("Materials", "Alpha");

		byte[] rawBlocks = new byte[width * height * length];
		byte[] rawMetadata = new byte[width * height * length];
		int droppedBlocks = 0;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					int index = x + (y * length + z) * width;
					int blockId = schematic.getBlockId(x, y, z);
					if (blockId < 0 || blockId > 255) {
						// Beta 1.7.3 alone never gets here, but StationAPI hands out ids well past
						// 255. Truncating one into a byte would write a different block entirely, so
						// leave it empty - the same thing the read side does with an out-of-range id.
						blockId = 0;
						droppedBlocks++;
					}
					rawBlocks[index] = (byte) blockId;
					rawMetadata[index] = (byte) schematic.getMetadata(x, y, z);
				}
			}
		}

		if (droppedBlocks > 0) {
			Log.warn(droppedBlocks + " block(s) have ids above 255 and were saved as air:"
					+ " the .schematic format has no room for them.");
		}

		nbt.putByteArray("Blocks", rawBlocks);
		nbt.putByteArray("Data", rawMetadata);
		nbt.put("Entities", new NbtList());

		NbtList blockEntityList = new NbtList();
		for (BlockEntity blockEntity : schematic.getBlockEntities()) {
			try {
				NbtCompound blockEntityNbt = new NbtCompound();
				blockEntity.writeNbt(blockEntityNbt);
				blockEntityList.add(blockEntityNbt);
			} catch (RuntimeException exception) {
				Log.warn("Skipping unsaveable block entity at "
						+ blockEntity.x + ", " + blockEntity.y + ", " + blockEntity.z, exception);
			}
		}
		nbt.put("TileEntities", blockEntityList);

		return nbt;
	}
}
