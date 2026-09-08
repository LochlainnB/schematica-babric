package lunatrius.schematica.compat;

import java.lang.reflect.Method;

import lunatrius.schematica.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Whether the player is in creative mode.
 *
 * <p>Beta 1.7.3 has no such thing. Creative mode arrived in Beta 1.8, so in this version it is
 * something a mod adds - and the one nearly every b1.7.3 pack adds it with is
 * <a href="https://github.com/paulevsGitch/BHCreative">BHCreative</a>, which hangs the flag off the
 * player through an interface of its own.
 *
 * <p>It is reached by reflection rather than compiled against, so it is a dependency of the one
 * feature that needs an answer here and of nothing else: with BHCreative absent this says no and
 * everything else in the mod carries on exactly as it did. Compiling against it would have pulled
 * StationAPI in behind it - BHCreative is built on it - and made a build of this mod need both.
 *
 * <p>Reflection is also the only way in even with the jar present. The interface is added to the
 * player class by BHCreative's own mixin, so it does not exist on any class this mod can see at
 * compile time; the name it is added under is the mod's own and is not remapped, which is what
 * makes it safe to look up by string.
 */
public final class CreativeMode {
	/** The mod that answers the question, if it is installed. */
	public static final String MOD_ID = "bhcreative";

	private static final String PLAYER_INTERFACE = "paulevs.bhcreative.interfaces.CreativePlayer";
	private static final String IS_CREATIVE = "creative_isCreative";

	/** BHCreative's own {@code creative_isCreative}, or null with the mod absent. */
	private static Method hook = findHook();

	private CreativeMode() {
	}

	/** Whether anything here can answer at all - which is to say, whether BHCreative is installed. */
	public static boolean isInstalled() {
		return hook != null;
	}

	/**
	 * Whether this player is in creative mode. False with BHCreative absent, which is the honest
	 * answer: without it there is no creative mode in the game to be in.
	 */
	public static boolean isCreative(PlayerEntity player) {
		Method method = hook;
		if (method == null || player == null || !method.getDeclaringClass().isInstance(player)) {
			return false;
		}

		try {
			return Boolean.TRUE.equals(method.invoke(player));
		} catch (ReflectiveOperationException | RuntimeException exception) {
			// Whatever went wrong here will go wrong every frame the move screen is open, so the hook
			// is dropped rather than retried: one warning, and creative mode reads as off from here on.
			hook = null;
			Log.warn("BHCreative would not say whether the player is in creative mode; giving up on it",
					exception);
			return false;
		}
	}

	/**
	 * Everything is caught below, the errors included. This runs in a class initialiser, and a throw
	 * out of one of those does not fail a feature - it makes the class itself unusable for the rest
	 * of the game, so a screen with a paste button on it would stop opening at all.
	 */
	private static Method findHook() {
		try {
			Class<?> creativePlayer =
					Class.forName(PLAYER_INTERFACE, false, CreativeMode.class.getClassLoader());
			return creativePlayer.getMethod(IS_CREATIVE);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			// Absent is the ordinary case and says nothing; installed but unreachable means a version
			// of it this was not written against, which is worth a line in the log rather than a
			// button that is greyed out for no reason anyone can see.
			if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
				Log.warn("BHCreative is installed but " + PLAYER_INTERFACE + "." + IS_CREATIVE
						+ "() could not be found, so creative mode will read as off", exception);
			}
			return null;
		}
	}
}
