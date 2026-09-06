package lunatrius.schematica.mixin;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.render.SchematicRenderer;
import net.minecraft.class_555;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the weather pass of the world renderer.
 *
 * <p>It runs inside {@code renderWorld} once the terrain, entities and block outline are drawn,
 * with the world camera still set up and the origin at the viewer - exactly the state the overlay
 * needs, and the same point the original ModLoader version hooked.
 */
@Mixin(class_555.class)
public abstract class GameRendererMixin {
	@Inject(method = "method_1847(F)V", at = @At("HEAD"))
	private void schematica$renderOverlay(float delta, CallbackInfo info) {
		SchematicRenderer renderer = Schematica.getRenderer();
		if (renderer != null) {
			renderer.render(delta);
		}
	}
}
