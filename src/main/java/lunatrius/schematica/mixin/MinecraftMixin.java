package lunatrius.schematica.mixin;

import lunatrius.schematica.Schematica;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/**
	 * The display, texture manager and font all exist by the end of {@code init()}, which the
	 * renderer needs before it can allocate its display list.
	 */
	@Inject(method = "init()V", at = @At("RETURN"))
	private void schematica$onInit(CallbackInfo info) {
		Schematica.onGameInit((Minecraft) (Object) this);
	}

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void schematica$onTick(CallbackInfo info) {
		Schematica.onClientTick((Minecraft) (Object) this);
	}

	/** Joining or leaving a world invalidates the placement of anything already loaded. */
	@Inject(method = "method_2115(Lnet/minecraft/world/World;Ljava/lang/String;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"))
	private void schematica$onWorldChanged(World world, String message, PlayerEntity player, CallbackInfo info) {
		Schematica.STATE.onWorldChanged();
	}
}
