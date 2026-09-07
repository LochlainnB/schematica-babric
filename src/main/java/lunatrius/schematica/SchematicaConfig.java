package lunatrius.schematica;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import lunatrius.schematica.util.Log;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Persisted settings. Written back out whenever a value changes so a future options screen only has
 * to set the field and call {@link #save()}.
 *
 * <p>Key codes are deliberately <em>not</em> in this file: the bindings are handed to vanilla in
 * {@link #installKeyBindings} so they appear in Options &rarr; Controls and are stored in
 * {@code options.txt} next to the vanilla ones. Older configs that still carry the key entries are
 * migrated across once and then dropped.
 */
public class SchematicaConfig {
	/** Numpad / */
	public final Keybind keyLoad = new Keybind("key.load", "key.schematica.load", Keyboard.KEY_DIVIDE);
	/** Numpad * */
	public final Keybind keySave = new Keybind("key.save", "key.schematica.save", Keyboard.KEY_MULTIPLY);
	/** Numpad - */
	public final Keybind keyControl = new Keybind("key.control", "key.schematica.control", Keyboard.KEY_SUBTRACT);
	/** Numpad + */
	public final Keybind keyToggle = new Keybind("key.toggle", "key.schematica.toggle", Keyboard.KEY_ADD);
	/** Numpad . */
	public final Keybind keySettings = new Keybind("key.settings", "key.schematica.settings", Keyboard.KEY_DECIMAL);
	/** Page Up */
	public final Keybind keyLayerUp = new Keybind("key.layerup", "key.schematica.layerup", Keyboard.KEY_PRIOR);
	/** Page Down */
	public final Keybind keyLayerDown = new Keybind("key.layerdown", "key.schematica.layerdown", Keyboard.KEY_NEXT);
	/** Numpad 0 */
	public final Keybind keyEasyPlace = new Keybind("key.easyplace", "key.schematica.easyplace", Keyboard.KEY_NUMPAD0);
	/** Numpad 5 */
	public final Keybind keyMaterials = new Keybind("key.materials", "key.schematica.materials", Keyboard.KEY_NUMPAD5);

	private final List<Keybind> keybinds = Collections.unmodifiableList(Arrays.asList(
			this.keyLoad, this.keySave, this.keyControl, this.keyToggle, this.keySettings,
			this.keyLayerUp, this.keyLayerDown, this.keyEasyPlace, this.keyMaterials));

	/** Opacity used for schematic block entities and the highlight boxes. */
	public float alpha = 1.0F;
	/** Draw the coloured comparison boxes (wrong block / wrong metadata / missing block). */
	public boolean highlight = true;
	/** How far the highlight boxes are inflated so they do not z-fight with the block faces. */
	public float blockDelta = 0.005F;

	/**
	 * Draw what is left to gather in the corner of the screen while playing.
	 *
	 * <p>A setting rather than session state, unlike the render toggles: it is how the player wants
	 * to be shown the list, not something they step in and out of over one build.
	 */
	public boolean infoHud = false;
	/** Which end of the material list the info HUD puts at the top. */
	public MaterialList.Sort infoHudSort = MaterialList.Sort.DESCENDING;

	/** Largest value the settings screen offers for {@link #blockDelta}. */
	public static final float MAX_BLOCK_DELTA = 0.05F;

	/**
	 * Set when {@link #load} found key codes left over from a config written before the bindings
	 * moved into {@code options.txt}. The caller saves the game options once to complete the move.
	 */
	public boolean migratedKeybinds = false;

	private File file;

	public void load(File file) {
		this.file = file;

		if (!file.isFile()) {
			this.save();
			return;
		}

		Properties properties = new Properties();
		try (InputStream stream = new FileInputStream(file)) {
			properties.load(stream);
		} catch (IOException exception) {
			Log.warn("Could not read " + file.getName() + ", using defaults", exception);
			return;
		}

		this.alpha = clamp(readFloat(properties, "alpha", this.alpha), 0.0F, 1.0F);
		this.highlight = readBoolean(properties, "highlight", this.highlight);
		this.blockDelta = clamp(readFloat(properties, "blockDelta", this.blockDelta), 0.0F, 0.5F);
		this.infoHud = readBoolean(properties, "infoHud", this.infoHud);
		this.infoHudSort = readSort(properties, "infoHudSort", this.infoHudSort);

		this.migrateKeybinds(properties);
	}

	/**
	 * Carries key codes from a pre-{@code options.txt} config over to the vanilla bindings, then
	 * rewrites the file without them so this only ever happens once.
	 */
	private void migrateKeybinds(Properties properties) {
		boolean found = false;
		for (Keybind keybind : this.keybinds) {
			String value = properties.getProperty(keybind.name);
			if (value == null) {
				continue;
			}

			try {
				keybind.setCode(Integer.parseInt(value.trim()));
				found = true;
			} catch (NumberFormatException exception) {
				Log.warn("Ignoring unreadable key code for " + keybind.name + ": " + value);
			}
		}

		if (found) {
			Log.info("Moved the Schematica keybinds into the game's controls list");
			this.migratedKeybinds = true;
			this.save();
		}
	}

	public void save() {
		if (this.file == null) {
			return;
		}

		File parent = this.file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			Log.warn("Could not create the config directory at " + parent.getAbsolutePath());
			return;
		}

		Properties properties = new Properties();
		properties.setProperty("alpha", Float.toString(this.alpha));
		properties.setProperty("highlight", Boolean.toString(this.highlight));
		properties.setProperty("blockDelta", Float.toString(this.blockDelta));
		properties.setProperty("infoHud", Boolean.toString(this.infoHud));
		properties.setProperty("infoHudSort", this.infoHudSort.name().toLowerCase(Locale.ROOT));

		try (OutputStream stream = new FileOutputStream(this.file)) {
			properties.store(stream, "Schematica - keybinds live in Options > Controls");
		} catch (IOException exception) {
			Log.warn("Could not write " + this.file.getName(), exception);
		}
	}

	/**
	 * Every binding the mod owns, in the order the settings screen lists them. Anything that wants
	 * to show or walk the whole set goes through this rather than naming each field.
	 */
	public List<Keybind> getKeybinds() {
		return this.keybinds;
	}

	/**
	 * Appends the mod's bindings to the array the vanilla options screen and {@code options.txt}
	 * both walk. Idempotent, because {@code GameOptions.load()} is public and nothing stops it
	 * being called more than once.
	 */
	public KeyBinding[] installKeyBindings(KeyBinding[] existing) {
		List<KeyBinding> keys = new ArrayList<>(Arrays.asList(existing));
		for (Keybind keybind : this.keybinds) {
			if (!keys.contains(keybind.binding)) {
				keys.add(keybind.binding);
			}
		}
		return keys.toArray(new KeyBinding[0]);
	}

	private static float readFloat(Properties properties, String key, float fallback) {
		try {
			return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)).trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)).trim());
	}

	/** Reads a sort order by name, ignoring case, so a hand-edited config is forgiving. */
	private static MaterialList.Sort readSort(Properties properties, String key, MaterialList.Sort fallback) {
		String value = properties.getProperty(key, fallback.name()).trim();
		for (MaterialList.Sort sort : MaterialList.Sort.values()) {
			if (sort.name().equalsIgnoreCase(value)) {
				return sort;
			}
		}
		return fallback;
	}

	private static float clamp(float value, float min, float max) {
		return value < min ? min : (value > max ? max : value);
	}

	/** A single hotkey, polled from the client tick with rising-edge detection. */
	public static class Keybind {
		/** Key this binding used to be stored under in {@code schematica.properties}. */
		public final String name;
		/**
		 * The vanilla binding. The key code lives here rather than in this class so that editing it
		 * in Options &rarr; Controls takes effect with no plumbing of our own.
		 */
		public final KeyBinding binding;

		private boolean wasDown = false;

		Keybind(String name, String translationKey, int code) {
			this.name = name;
			this.binding = new KeyBinding(translationKey, code);
		}

		public int getCode() {
			return this.binding.code;
		}

		public void setCode(int code) {
			this.binding.code = code;
		}

		/** The name shown on the controls screen, e.g. {@code NUMPAD/}. */
		public String getKeyName() {
			return Keyboard.getKeyName(this.binding.code);
		}

		/** Returns true exactly once per press. Must be called every tick to stay in sync. */
		public boolean poll() {
			int code = this.binding.code;
			boolean down = code != Keyboard.KEY_NONE && Keyboard.isKeyDown(code);
			boolean pressed = down && !this.wasDown;
			this.wasDown = down;
			return pressed;
		}
	}
}
