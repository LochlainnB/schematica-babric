package lunatrius.schematica.mixin;

import lunatrius.schematica.HotbarRestock;
import net.minecraft.class_325;
import net.minecraft.client.network.ClientNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientNetworkHandler.class)
public abstract class ClientNetworkHandlerMixin {
	/**
	 * The server's answer to a slot click. Easy place moves blocks onto the hotbar itself, and this
	 * is the only word back on whether the server took them - the client keeps no note of its own,
	 * so without this there would be no way to tell a swap that landed from one that was refused.
	 */
	@Inject(method = "method_1429(Lnet/minecraft/class_325;)V", at = @At("HEAD"))
	private void schematica$onTransaction(class_325 packet, CallbackInfo info) {
		HotbarRestock.onTransaction(packet.field_1222, packet.field_1224);
	}
}
