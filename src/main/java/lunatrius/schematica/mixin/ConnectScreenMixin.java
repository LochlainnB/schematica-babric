package lunatrius.schematica.mixin;

import lunatrius.schematica.SchematicMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which server is being dialled, which is how a multiplayer world is told apart.
 *
 * <p>Every way into a server goes through this screen - the server list, direct connect, and the
 * address the game can be started with - so it is the one place the address is known before the
 * world turns up. Taken at the end of the constructor rather than the start: opening this screen
 * drops the world the player was in, and that world has to be written down under its own name
 * before this one takes over.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V", at = @At("RETURN"))
	private void schematica$onConnect(Minecraft minecraft, String address, int port, CallbackInfo info) {
		SchematicMemory.onServerConnect(address, port);
	}
}
