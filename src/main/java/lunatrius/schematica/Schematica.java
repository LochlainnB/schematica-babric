package lunatrius.schematica;

import java.io.File;
import java.lang.ref.WeakReference;

import lunatrius.schematica.gui.InfoHud;
import lunatrius.schematica.gui.MaterialListScreen;
import lunatrius.schematica.gui.SchematicControlScreen;
import lunatrius.schematica.gui.SchematicLoadScreen;
import lunatrius.schematica.gui.SchematicSaveScreen;
import lunatrius.schematica.gui.SchematicaSettingsScreen;
import lunatrius.schematica.render.SchematicRenderer;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

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

	/**
	 * The world the mod last saw, so a swap nobody announced can still be noticed.
	 *
	 * <p>Weak, because a world let go of is a large thing to be the last one holding: the reference
	 * is only ever compared against the world the game is holding, and a world the game is holding
	 * cannot be collected out from under it. One that has been collected is by that fact not the
	 * world any more, which is the answer the comparison wanted anyway.
	 */
	private static WeakReference<World> lastWorld = new WeakReference<>(null);

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
		SchematicMemory.load(new File(Minecraft.getRunDirectory(), "config/schematica-worlds.properties"));

		if (CONFIG.migratedKeybinds) {
			// The bindings were already added to mc.options by GameOptionsMixin, but the codes only
			// arrived just now; write them out so options.txt is the single source from here on.
			mc.options.save();
		}

		renderer = new SchematicRenderer();
		Log.info("Schematica ready - schematics folder: " + schematicDirectory.getAbsolutePath());
	}

	/**
	 * Joining a world, leaving one, or stepping between dimensions. What was open here is written
	 * down before the state holding it is cleared out.
	 *
	 * @param incoming the world being handed over, kept so that {@link #onClientTick} can tell a
	 *                 swap that came through here from one that never did
	 */
	public static void onWorldChanged(World incoming) {
		lastWorld = new WeakReference<>(incoming);
		SchematicMemory.onWorldLeaving();
		STATE.onWorldChanged();
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
		// Ahead of everything else, including the screen check further down: the info HUD keeps
		// counting behind an open screen, which is what shows the material list screen's own HUD
		// buttons taking effect while they are being clicked.
		InfoHud.tick(mc);

		// A world can be swapped without the hook that says so ever being reached - this is a game
		// played under a stack of mods, and one of them getting to the same method first is a thing
		// that happens. Left unnoticed, the mod would go on believing it was in the world it was
		// last told about: writing one world's note under another world's name, and standing a
		// house from a save folder in the middle of a server. So the world in hand is compared with
		// the one last seen, and a change nobody announced is taken up here instead, a tick late.
		if (mc.world != null && lastWorld.get() != mc.world) {
			Log.warn("The world changed without anything saying so, which usually means another mod"
					+ " reaches the same swap; taking it up a tick late");
			onWorldChanged(mc.world);
		}

		// Ahead of the smoke test as well as the screen check: a world is joined with a screen still
		// up, and putting the last schematic back is the first thing that should happen in it.
		SchematicMemory.tick(mc);

		// The overlay is only as good as the world it was compared against, and a world does not
		// always say when it changes underneath one.
		STATE.pollWorldUnderSchematics();

		// Swaps must settle behind screens and during the smoke test's network waits too.
		HotbarRestock.tick(mc);

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
		boolean materials = CONFIG.keyMaterials.poll();

		if (load) {
			mc.setScreen(new SchematicLoadScreen(mc.currentScreen));
		} else if (save) {
			mc.setScreen(new SchematicSaveScreen());
		} else if (control) {
			mc.setScreen(new SchematicControlScreen());
		} else if (toggle) {
			// Every open schematic at once, rather than the one the move screen is pointed at: this
			// is the key you reach for when the ghosts are in the way of what you have built, and
			// half of them going is no use then. Nothing loaded means nothing to show, and
			// toggleAllRendering already refuses that.
			STATE.toggleAllRendering();
		} else if (settings) {
			// The same screen Mod Menu's Configure button opens, so the key works either way.
			mc.setScreen(new SchematicaSettingsScreen(mc.currentScreen));
		} else if (layerUp) {
			// The same step the move screen's layer buttons take, so -1 (all layers) sits below layer 1
			// from the keys as well.
			STATE.setRenderingLayer(STATE.getActive().renderingLayer + 1);
		} else if (layerDown) {
			STATE.setRenderingLayer(STATE.getActive().renderingLayer - 1);
		} else if (easyPlace) {
			STATE.toggleEasyPlace();
			// The one thing the mod says out loud. Every other key changes something you can see;
			// this one changes what a click does, which is invisible until a block refuses to go down.
			mc.inGameHud.addChatMessage(EasyPlace.label());
		} else if (materials) {
			mc.setScreen(new MaterialListScreen(mc.currentScreen));
		}
	}
}
