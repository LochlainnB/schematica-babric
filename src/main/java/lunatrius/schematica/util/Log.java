package lunatrius.schematica.util;

/**
 * Minimal console logger.
 *
 * <p>Babric's Fabric Loader does not put SLF4J or Log4J on the mod classpath, so this writes to the
 * game's own streams with a consistent prefix instead of pulling in a logging framework.
 */
public final class Log {
	private static final String PREFIX = "[Schematica] ";

	private Log() {
	}

	public static void info(String message) {
		System.out.println(PREFIX + message);
	}

	public static void warn(String message) {
		System.out.println(PREFIX + "WARN: " + message);
	}

	public static void warn(String message, Throwable throwable) {
		warn(message);
		throwable.printStackTrace();
	}

	public static void error(String message) {
		System.err.println(PREFIX + "ERROR: " + message);
	}

	public static void error(String message, Throwable throwable) {
		error(message);
		throwable.printStackTrace();
	}
}
