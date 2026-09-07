package lunatrius.schematica.mixin;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.gui.InfoHud;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hangs the info HUD off the end of the vanilla one.
 *
 * <p>The end of {@code render} rather than a hook of the mod's own: the ortho projection and the
 * gui scale are already set up here, the call is skipped altogether when the player has hidden the
 * hud with F1, and anything drawn this late lands over the hotbar and under whatever screen is
 * open - which is exactly where the HUD belongs.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "render(FZII)V", at = @At("RETURN"))
	private void schematica$renderInfoHud(
			float delta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo info) {
		InfoHud.render(Schematica.getMinecraft());
	}
}
