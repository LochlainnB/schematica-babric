package lunatrius.schematica.mixin;

import lunatrius.schematica.Schematica;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the mod's keys to the list vanilla walks, which is all it takes for them to show up in
 * Options &rarr; Controls and to be written to {@code options.txt} - both {@code load} and
 * {@code save} iterate the array rather than the individual fields.
 */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
	@Shadow
	public KeyBinding[] allKeys;

	/**
	 * {@code load()} is the last thing the constructor does, so injecting at its head appends the
	 * bindings while the array still holds the defaults and just before the saved codes are read
	 * back into it.
	 */
	@Inject(method = "load()V", at = @At("HEAD"))
	private void schematica$installKeyBindings(CallbackInfo info) {
		this.allKeys = Schematica.CONFIG.installKeyBindings(this.allKeys);
	}
}
