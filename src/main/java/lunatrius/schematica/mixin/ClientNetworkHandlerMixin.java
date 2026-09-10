package lunatrius.schematica.mixin;

import lunatrius.schematica.HotbarRestock;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicMemory;
import net.minecraft.class_325;
import net.minecraft.class_441;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.play.ChunkDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientNetworkHandler.class)
public abstract class ClientNetworkHandlerMixin {
	/**
	 * A connection to a server being opened, which is where the address is known and where a
	 * multiplayer world is told apart by it.
	 *
	 * <p>Taken off the connection rather than off the screen that dialled it. Every world that
	 * arrives from a server arrives through one of these - the handler is what builds it - so this
	 * holds whatever screen is in front of it, and a pack is free to bring its own server list. At
	 * the end of the constructor, so a connection that could not be made never names a world; the
	 * world the player was in was let go of before this, on the way to the connecting screen.
	 */
	@Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V", at = @At("RETURN"))
	private void schematica$onConnect(Minecraft minecraft, String address, int port, CallbackInfo info) {
		SchematicMemory.onServerConnect(address, port);
	}

	/**
	 * The server's answer to a slot click. Easy place moves blocks onto the hotbar itself, and this
	 * is the only word back on whether the server took them - the client keeps no note of its own,
	 * so without this there would be no way to tell a swap that landed from one that was refused.
	 */
	@Inject(method = "method_1429(Lnet/minecraft/class_325;)V", at = @At("HEAD"))
	private void schematica$onTransaction(class_325 packet, CallbackInfo info) {
		HotbarRestock.onTransaction(packet.field_1222, packet.field_1224);
	}

	/**
	 * A chunk of world arriving. The schematic overlay draws itself by comparing the schematic
	 * against the world, and a chunk brings a slab of that world in one packet without ever
	 * reporting the blocks in it - so the part of the overlay standing in it has to be told.
	 */
	@Inject(method = "handleChunkData(Lnet/minecraft/network/packet/play/ChunkDataPacket;)V", at = @At("RETURN"))
	private void schematica$onChunkData(ChunkDataPacket packet, CallbackInfo info) {
		Schematica.STATE.onWorldChunkLoaded(
				packet.x, packet.y, packet.z, packet.sizeX, packet.sizeY, packet.sizeZ);
	}

	/**
	 * Several blocks in one chunk changing at once. A server sends one of these rather than a packet
	 * each whenever more than one block in a chunk moves in a tick, which with other people building
	 * alongside you is most of them - and they are written straight into the chunk, so the single
	 * block hook in {@code WorldMixin} never sees any of it.
	 */
	@Inject(method = "method_1468(Lnet/minecraft/class_441;)V", at = @At("RETURN"))
	private void schematica$onMultiBlockChange(class_441 packet, CallbackInfo info) {
		Schematica.STATE.onWorldBlocksChanged(
				packet.field_2153, packet.field_2154, packet.field_2155, packet.field_2158);
	}
}
