package lunatrius.schematica;

import java.io.File;

import lunatrius.schematica.gui.SchematicControlScreen;
import lunatrius.schematica.gui.SchematicLoadScreen;
import lunatrius.schematica.gui.SchematicSaveScreen;
import lunatrius.schematica.gui.SchematicaSettingsScreen;
import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

/**
 * Entry point. Everything the mod does is client side and lives behind the keybinds in
 * {@link SchematicaConfig}.
 *
 * <p>The renderer is created lazily from {@link #onGameInit} rather than here: it allocates an
 * OpenGL display list, so it cannot exist before the display does.
 */
public class Schematica implements ClientModInitializer {
	public static final String MOD_ID = "schematica";

	public static final SchematicaState STATE = new SchematicaState();
	public static final SchematicaConfig CONFIG = new SchematicaConfig();

	private static Minecraft minecraft;
	private static SchematicRenderer renderer;
	private static File schematicDirectory;

	@Override
	public void onInitializeClient() {
		Log.info("Schematica for Babric loading");
	}

	/** Called from {@code MinecraftMixin} once the display and texture manager exist. */
	public static void onGameInit(Minecraft mc) {
		minecraft = mc;

		schematicDirectory = new File(Minecraft.getRunDirectory(), "schematics");
		if (!schematicDirectory.exists() && !schematicDirectory.mkdirs()) {
			Log.warn("Could not create the schematics directory at " + schematicDirectory.getAbsolutePath());
		}

		Translations.install();
		CONFIG.load(new File(Minecraft.getRunDirectory(), "config/schematica.properties"));

		if (CONFIG.migratedKeybinds) {
			// The bindings were already added to mc.options by GameOptionsMixin, but the codes only
			// arrived just now; write them out so options.txt is the single source from here on.
			mc.options.save();
		}

		renderer = new SchematicRenderer();
		Log.info("Schematica ready - schematics folder: " + schematicDirectory.getAbsolutePath());
	}

	public static Minecraft getMinecraft() {
		return minecraft;
	}

	public static SchematicRenderer getRenderer() {
		return renderer;
	}

	public static File getSchematicDirectory() {
		return schematicDirectory;
	}

	/**
	 * Polled once per client tick from {@code MinecraftMixin}. Only fires while no screen is open,
	 * which mirrors how the original mod gated its keybinds.
	 */
	public static void onClientTick(Minecraft mc) {
		if (lunatrius.schematica.debug.SmokeTest.isEnabled()) {
			lunatrius.schematica.debug.SmokeTest.tick(mc);
			return;
		}

		if (mc.currentScreen != null || mc.world == null || mc.player == null) {
			// Still polled so a key held down over a screen is not seen as a fresh press afterwards.
			for (SchematicaConfig.Keybind keybind : CONFIG.getKeybinds()) {
				keybind.poll();
			}
			return;
		}

		boolean load = CONFIG.keyLoad.poll();
		boolean save = CONFIG.keySave.poll();
		boolean control = CONFIG.keyControl.poll();
		boolean toggle = CONFIG.keyToggle.poll();
		boolean settings = CONFIG.keySettings.poll();
		boolean layerUp = CONFIG.keyLayerUp.poll();
		boolean layerDown = CONFIG.keyLayerDown.poll();
		boolean easyPlace = CONFIG.keyEasyPlace.poll();

		if (load) {
			mc.setScreen(new SchematicLoadScreen(mc.currentScreen));
		} else if (save) {
			mc.setScreen(new SchematicSaveScreen());
		} else if (control) {
			mc.setScreen(new SchematicControlScreen());
		} else if (toggle) {
			// Nothing loaded means nothing to show, and toggleRendering already refuses that.
			STATE.toggleRendering();
		} else if (settings) {
			// The same screen Mod Menu's Configure button opens, so the key works either way.
			mc.setScreen(new SchematicaSettingsScreen(mc.currentScreen));
		} else if (layerUp) {
			// The same step the move screen's layer buttons take, so -1 (all layers) sits below layer 1
			// from the keys as well.
			STATE.setRenderingLayer(STATE.renderingLayer + 1);
		} else if (layerDown) {
			STATE.setRenderingLayer(STATE.renderingLayer - 1);
		} else if (easyPlace) {
			STATE.toggleEasyPlace();
			// The one thing the mod says out loud. Every other key changes something you can see;
			// this one changes what a click does, which is invisible until a block refuses to go down.
			mc.inGameHud.addChatMessage(EasyPlace.label());
		}
	}
}
