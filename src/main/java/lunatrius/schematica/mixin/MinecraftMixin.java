package lunatrius.schematica.mixin;

import lunatrius.schematica.EasyPlace;
import lunatrius.schematica.Schematica;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
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

	/**
	 * Both mouse buttons in game come through here, and it is the last point before the interaction
	 * manager either applies the click locally or sends it to the server - so a click easy place
	 * drops here never becomes a placement anywhere.
	 */
	@Inject(method = "method_2107(I)V", at = @At("HEAD"), cancellable = true)
	private void schematica$onUse(int button, CallbackInfo info) {
		if (button == 1 && EasyPlace.interceptUse((Minecraft) (Object) this)) {
			info.cancel();
		}
	}

	/**
	 * How long holding the right button waits between placements: vanilla divides the tick rate by
	 * four, giving five ticks. This is the second of the two constants in {@code tick()} - the first
	 * is the same gate for the left button, which easy place has no business speeding up.
	 */
	@ModifyConstant(method = "tick()V", constant = @Constant(floatValue = 4.0F, ordinal = 1))
	private float schematica$useRepeatRate(float divisor) {
		return EasyPlace.useRepeatDivisor(divisor);
	}

	/** Joining or leaving a world invalidates the placement of anything already loaded. */
	@Inject(method = "method_2115(Lnet/minecraft/world/World;Ljava/lang/String;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"))
	private void schematica$onWorldChanged(World world, String message, PlayerEntity player, CallbackInfo info) {
		Schematica.STATE.onWorldChanged();
	}
}
