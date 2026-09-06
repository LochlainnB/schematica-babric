package lunatrius.schematica.mixin;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.schematic.SchematicWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the schematic overlay stale whenever a block in the real world changes.
 *
 * <p>Every single-block change on the client funnels through here, so this covers the player
 * placing and breaking blocks as well as updates that arrive from a server - broader than the
 * original mod, which only saw the local player's own interactions. The state object filters out
 * changes outside the schematic so this stays cheap in a busy world.
 */
@Mixin(World.class)
public abstract class WorldMixin {
	@Inject(method = "method_243(III)V", at = @At("HEAD"))
	private void schematica$onBlockChanged(int x, int y, int z, CallbackInfo info) {
		if ((Object) this instanceof SchematicWorld) {
			return;
		}
		Schematica.STATE.onWorldBlockChanged(x, y, z);
	}
}
