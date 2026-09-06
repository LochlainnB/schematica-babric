package lunatrius.schematica.mixin;

import java.util.Properties;

import net.minecraft.client.resource.language.TranslationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla translation table so the mod's language file can be merged into it. Screens
 * then keep using the ordinary {@code TranslationStorage.get(key)} lookup.
 */
@Mixin(TranslationStorage.class)
public interface TranslationStorageAccessor {
	@Accessor("translations")
	Properties getTranslations();
}
