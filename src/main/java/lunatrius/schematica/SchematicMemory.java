package lunatrius.schematica;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import lunatrius.schematica.util.Log;
import net.minecraft.client.Minecraft;

/**
 * What was open in each world, kept between sessions.
 *
 * <p>A build is not a sitting. You log off with the ghost of a house standing where you left it and
 * you come back to the same house in the same place, whether that is an hour later or after the
 * game has been shut down and started again - so the mod writes down what was open and where it was
 * put, and reads it back when you return.
 *
 * <p>Every world gets its own note, because a schematic pinned to a corner of one world means
 * nothing in another. A world is told apart by where it is: the save folder for a single player
 * world, the address dialled for a server, and the dimension on top of that - the coordinates a
 * build sits at in the overworld are somewhere else entirely in the Nether, so each end of a portal
 * remembers its own.
 *
 * <p>Nothing here is a setting. Closing a schematic before you log off is what forgets it, and that
 * is already how you close a schematic.
 */
public final class SchematicMemory {
	/** Field names, appended to the world id to make one line of the file. */
	private static final String KEY_SCHEMATIC = "schematic";
	private static final String KEY_X = "x";
	private static final String KEY_Y = "y";
	private static final String KEY_Z = "z";
	private static final String KEY_TURNS = "turns";
	private static final String KEY_MIRRORED = "mirrored";
	private static final String KEY_LAYER = "layer";
	private static final String KEY_OVERLAY = "overlay";

	/**
	 * Ticks between two looks at the state. Nothing tells this class when a schematic moves - the
	 * offset is nudged from three screens and two keys - so it compares what is loaded against what
	 * it last wrote rather than asking every one of them to say so, which also means a feature added
	 * later is remembered without being taught to.
	 */
	private static final int POLL_INTERVAL = 20;

	/**
	 * Ticks to wait for a world to say it has been handed over before putting a schematic back into
	 * it regardless. The game says so at the far end of the world swap, but a mod that reaches the
	 * same swap can take that end away - and when it does, waiting for it means waiting forever. The
	 * overlay is compared against the world it is drawn on anyway, so the worst a schematic put back
	 * a moment early costs is one rebuild.
	 */
	private static final int RESTORE_DEADLINE = 60;

	/** Every world there is a note for, in the order they were first written down. */
	private static final Map<String, Record> records = new LinkedHashMap<>();

	private static File file;

	/** Where the player is heading, set before the world arrives. Null until a world says. */
	private static String base;
	/** The world currently being remembered - {@link #base} plus its dimension - or null. */
	private static String worldId;
	/** What was last written for {@link #worldId}, so an unchanged state is not written again. */
	private static Record saved;
	private static int ticks;
	/** What is still to be put back into {@link #worldId}, or null once nothing is owed. */
	private static Record owed;
	/** Ticks spent waiting for the world to say it is ready, counted against the deadline. */
	private static int waited;
	/**
	 * Whether the world has said it has finished being handed over. A single player world is
	 * prepared with the game still running underneath it - ticking, drawing, and reading as air for
	 * the seconds it takes - so there is a window in which a world is there to be seen but is not
	 * there to be read, and a schematic put back in that window would be drawn against nothing.
	 *
	 * <p>This is when to put one back, not whether to. Nothing else waits on it: a world is written
	 * down from the moment it can be named, because the alternative is a game where one mixin going
	 * missing means nothing is ever remembered again and nothing says so.
	 */
	private static boolean worldReady = false;

	private SchematicMemory() {
	}

	/** Reads the file, replacing anything already remembered. Called once as the game starts. */
	public static void load(File target) {
		file = target;
		records.clear();
		// Deliberately not the world we are heading for: this runs at the end of the game's own
		// startup, and a game started with a server to join builds that screen before the end of it.
		// Clearing the address here would throw away the one thing that names the world being joined.
		worldId = null;
		saved = null;
		owed = null;
		ticks = 0;
		waited = 0;

		if (target == null || !target.isFile()) {
			return;
		}

		Properties properties = new Properties();
		try (InputStream stream = new FileInputStream(target)) {
			properties.load(stream);
		} catch (IOException exception) {
			Log.warn("Could not read " + target.getName() + ", starting with nothing remembered", exception);
			return;
		}

		// A world id can hold dots - host names are full of them - but no field name does, so the
		// last dot in a key is always the one in front of the field.
		Set<String> ids = new TreeSet<>();
		for (String key : properties.stringPropertyNames()) {
			int dot = key.lastIndexOf('.');
			if (dot > 0) {
				ids.add(key.substring(0, dot));
			}
		}

		for (String id : ids) {
			String schematic = properties.getProperty(id + "." + KEY_SCHEMATIC);
			if (schematic == null || schematic.trim().isEmpty()) {
				continue;
			}
			records.put(id, new Record(
					schematic.trim(),
					readInt(properties, id, KEY_X, 0),
					readInt(properties, id, KEY_Y, 0),
					readInt(properties, id, KEY_Z, 0),
					readInt(properties, id, KEY_TURNS, 0) & 3,
					readBoolean(properties, id, KEY_MIRRORED, false),
					readInt(properties, id, KEY_LAYER, -1),
					readBoolean(properties, id, KEY_OVERLAY, true)));
		}

		if (!records.isEmpty()) {
			Log.info("Remembering what was open in " + records.size() + " world(s)");
		}
	}

	/** A single player world is told apart by the folder it is saved in. */
	public static void onSingleplayerWorld(String saveName) {
		base = "singleplayer/" + saveName;
	}

	/**
	 * A server by the address dialled, lowered because host names do not care about case and two
	 * spellings of one server are still one server.
	 */
	public static void onServerConnect(String address, int port) {
		base = "server/" + address.toLowerCase(Locale.ROOT) + ":" + port;
	}

	/**
	 * Leaving the world - logging off, quitting to the title, stepping through a portal, or closing
	 * the game, which shuts the world down on its way out. Whatever is loaded is written down before
	 * the state is cleared out from under it.
	 */
	public static void onWorldLeaving() {
		worldReady = false;
		if (worldId == null) {
			return;
		}

		// A schematic still owed to this world was never put back into it, so what is loaded now is
		// not what was left here - it is nothing. Writing that down would rub the note out on the way
		// past, and the note is the whole point of it.
		if (owed == null) {
			remember(capture());
		}

		worldId = null;
		saved = null;
		owed = null;
		ticks = 0;
		waited = 0;
	}

	/**
	 * The world has been handed over and can be read. Said at the far end of the world swap, which
	 * in single player is only after the spawn area has been prepared - and that is done with the
	 * game still ticking, so it is not something a tick can work out for itself.
	 *
	 * <p>It may never be said at all: the far end of that swap is a popular place, and a mod that
	 * gets there first can leave this end of it unreached. Nothing here depends on hearing it.
	 */
	public static void onWorldReady() {
		worldReady = true;
	}

	/**
	 * Polled from the client tick. Takes up a world on the first tick there is one, puts back what
	 * it was left holding once it can be read, then watches for the schematic being moved, turned,
	 * sliced, hidden, swapped or closed.
	 */
	public static void tick(Minecraft mc) {
		if (mc == null || mc.world == null || mc.player == null) {
			return;
		}

		if (worldId == null) {
			attach(mc);
			return;
		}

		if (owed != null) {
			// Nothing is written down while a schematic is still owed to this world: what is loaded
			// is not yet what was left here, and saying so would forget the note before it is used.
			if (!worldReady && ++waited < RESTORE_DEADLINE) {
				return;
			}
			putBack();
			return;
		}

		if (++ticks < POLL_INTERVAL) {
			return;
		}
		ticks = 0;
		remember(capture());
	}

	/** The world being remembered, or null if this one cannot be told apart from any other. */
	public static String getWorldId() {
		return worldId;
	}

	/**
	 * Works out which world this is, and takes up whatever was open in it - putting it back now if
	 * the world is already known to be readable, and otherwise owing it until it is.
	 *
	 * <p>The dimension is read off the world rather than off the player: a server sets the player's
	 * dimension a moment after handing over the world, and this can run in between.
	 */
	private static void attach(Minecraft mc) {
		if (base == null) {
			// Nothing said where we are - a world reached by some path the mod does not know about.
			// Remembering nothing is better than remembering it against the wrong world.
			return;
		}

		worldId = base + "/" + mc.world.dimension.id;
		ticks = 0;
		waited = 0;
		owed = records.get(worldId);

		if (owed == null) {
			saved = capture();
		} else if (worldReady) {
			// The usual way round: the world said it was ready before anyone thought to ask.
			putBack();
		}
	}

	/**
	 * Puts back what this world was left holding, and starts watching from whatever came of it -
	 * so a schematic that could not be put back is forgotten on the next poll rather than sitting
	 * in the file for good.
	 */
	private static void putBack() {
		Record record = owed;
		owed = null;

		if (!worldReady) {
			Log.warn("This world never said it had finished loading, which usually means another mod"
					+ " reaches the same place; putting " + record.schematic + " back anyway");
		}

		restore(record);
		saved = capture();
	}

	/** What is open now, as a line of the file. */
	private static Record capture() {
		SchematicaState state = Schematica.STATE;
		String name = state.getLoadedName();
		if (state.schematic == null || name == null) {
			return Record.NOTHING;
		}

		return new Record(name, state.offset.x, state.offset.y, state.offset.z,
				state.getTurns(), state.isMirrored(), state.renderingLayer, state.isRenderingSchematic);
	}

	/** Writes a record against the current world, if it says anything new. */
	private static void remember(Record record) {
		if (worldId == null || record.equals(saved)) {
			return;
		}

		saved = record;
		if (record.isEmpty()) {
			records.remove(worldId);
		} else {
			records.put(worldId, record);
		}
		write();
	}

	/** Puts a schematic back where it was left. */
	private static void restore(Record record) {
		SchematicaState state = Schematica.STATE;
		File schematic = new File(Schematica.getSchematicDirectory(), record.schematic);
		if (!schematic.isFile()) {
			Log.warn("The schematic left open here, " + record.schematic
					+ ", is no longer in the schematics folder");
			return;
		}

		if (!state.loadSchematic(schematic)) {
			return;
		}

		state.setOrientation(record.turns, record.mirrored);
		state.offset.set(record.x, record.y, record.z);
		state.setRenderingLayer(record.layer);
		state.isRenderingSchematic = record.overlay;
		state.needsUpdate = true;

		// So the load screen opens with the loaded file picked out rather than the placeholder.
		List<String> files = state.getSchematicFiles();
		state.selectedSchematic = Math.max(files.indexOf(record.schematic), 0);

		Log.info("Put " + record.schematic + " back at " + record.x + ", " + record.y + ", " + record.z);
	}

	private static void write() {
		if (file == null) {
			return;
		}

		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			Log.warn("Could not create the config directory at " + parent.getAbsolutePath());
			return;
		}

		Properties properties = new Properties();
		for (Map.Entry<String, Record> entry : records.entrySet()) {
			String id = entry.getKey();
			Record record = entry.getValue();
			properties.setProperty(id + "." + KEY_SCHEMATIC, record.schematic);
			properties.setProperty(id + "." + KEY_X, Integer.toString(record.x));
			properties.setProperty(id + "." + KEY_Y, Integer.toString(record.y));
			properties.setProperty(id + "." + KEY_Z, Integer.toString(record.z));
			properties.setProperty(id + "." + KEY_TURNS, Integer.toString(record.turns));
			properties.setProperty(id + "." + KEY_MIRRORED, Boolean.toString(record.mirrored));
			properties.setProperty(id + "." + KEY_LAYER, Integer.toString(record.layer));
			properties.setProperty(id + "." + KEY_OVERLAY, Boolean.toString(record.overlay));
		}

		try (OutputStream stream = new FileOutputStream(file)) {
			properties.store(stream, "Schematica - what was open in each world. Delete a world to forget it.");
		} catch (IOException exception) {
			Log.warn("Could not write " + file.getName(), exception);
		}
	}

	private static int readInt(Properties properties, String id, String key, int fallback) {
		String value = properties.getProperty(id + "." + key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String id, String key, boolean fallback) {
		String value = properties.getProperty(id + "." + key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	/**
	 * What was open in one world. Compared whole, so any change to any part of it is one that gets
	 * written, and none of them has to be noticed on its own.
	 */
	private static final class Record {
		/** Nothing was open. Never written out - the world is dropped from the file instead. */
		static final Record NOTHING = new Record(null, 0, 0, 0, 0, false, -1, true);

		final String schematic;
		final int x;
		final int y;
		final int z;
		final int turns;
		final boolean mirrored;
		final int layer;
		final boolean overlay;

		Record(String schematic, int x, int y, int z, int turns, boolean mirrored, int layer, boolean overlay) {
			this.schematic = schematic;
			this.x = x;
			this.y = y;
			this.z = z;
			this.turns = turns;
			this.mirrored = mirrored;
			this.layer = layer;
			this.overlay = overlay;
		}

		boolean isEmpty() {
			return this.schematic == null;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Record)) {
				return false;
			}

			Record that = (Record) other;
			return this.x == that.x && this.y == that.y && this.z == that.z
					&& this.turns == that.turns && this.mirrored == that.mirrored
					&& this.layer == that.layer && this.overlay == that.overlay
					&& (this.schematic == null ? that.schematic == null : this.schematic.equals(that.schematic));
		}

		@Override
		public int hashCode() {
			int hash = this.schematic == null ? 0 : this.schematic.hashCode();
			hash = hash * 31 + this.x;
			hash = hash * 31 + this.y;
			hash = hash * 31 + this.z;
			hash = hash * 31 + this.turns;
			hash = hash * 31 + this.layer;
			return hash * 31 + (this.mirrored ? 2 : 0) + (this.overlay ? 1 : 0);
		}
	}
}
