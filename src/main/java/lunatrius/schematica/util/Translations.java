package lunatrius.schematica.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.mixin.TranslationStorageAccessor;
import net.minecraft.client.resource.language.TranslationStorage;

/**
 * Merges the mod's language file into the vanilla {@link TranslationStorage} so every screen can
 * keep using the plain {@code get(key)} lookup, and adding a language later is just another file.
 */
public final class Translations {
	private static final String BASE_PATH = "/assets/" + Schematica.MOD_ID + "/lang/";
	private static final String FALLBACK_LANGUAGE = "en_US";

	private Translations() {
	}

	public static void install() {
		load(FALLBACK_LANGUAGE);
	}

	private static void load(String language) {
		String path = BASE_PATH + language + ".lang";
		try (InputStream stream = Translations.class.getResourceAsStream(path)) {
			if (stream == null) {
				Log.warn("Missing language file " + path);
				return;
			}

			Properties translations = new Properties();
			translations.load(stream);

			Properties vanilla = ((TranslationStorageAccessor) TranslationStorage.getInstance()).getTranslations();
			for (String key : translations.stringPropertyNames()) {
				vanilla.setProperty(key, translations.getProperty(key));
			}
		} catch (IOException exception) {
			Log.error("Could not load language file " + path, exception);
		}
	}

	public static String get(String key) {
		return TranslationStorage.getInstance().get(key);
	}
}
