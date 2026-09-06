package lunatrius.schematica.compat;

import java.util.function.Function;

import io.github.prospector.modmenu.api.ModMenuApi;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.gui.SchematicaSettingsScreen;
import net.minecraft.client.gui.screen.Screen;

/**
 * Hangs {@link SchematicaSettingsScreen} off Mod Menu's "Configure" button.
 *
 * <p>Mod Menu is an optional dependency and is not shipped with the mod. Nothing references this
 * class directly - Fabric only loads it when Mod Menu asks for the {@code modmenu} entrypoints - so
 * with Mod Menu absent it is never touched and the missing interface never matters.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public String getModId() {
		return Schematica.MOD_ID;
	}

	@Override
	public Function<Screen, ? extends Screen> getConfigScreenFactory() {
		return SchematicaSettingsScreen::new;
	}
}
