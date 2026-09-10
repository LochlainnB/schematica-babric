package lunatrius.schematica;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import lunatrius.schematica.util.Log;
import net.minecraft.client.Minecraft;

/**
 * What was open in each world, kept between sessions.
 *
 * <p>A build is not a sitting. You log off with the ghost of a house standing where you left it and
 * you come back to the same house in the same place, whether that is an hour later or after the
 * game has been shut down and started again - so the mod writes down what was open and where it was
 * put, and reads it back when you return. All of it: a world you left holding a farm, the roof for
 * it and a half-finished tower comes back holding the three of them, each in its own place and
 * turned the way you left it, with the controls pointed at the one you were working on.
 *
 * <p>Every world gets its own note, because a schematic pinned to a corner of one world means
 * nothing in another. A world is told apart by where it is: the save folder for a single player
 * world, the address dialled for a server, and the dimension on top of that - the coordinates a
 * build sits at in the overworld are somewhere else entirely in the Nether, so each end of a portal
 * remembers its own.
 *
 * <p>What names a world is said before the world arrives - a save folder as one is started, an
 * address as it is dialled - so it is a statement about where the player was going rather than
 * about the world in hand. A world that turns up from a direction nothing announced would be taken
 * for the last one that was, so the two are held up against each other: a world that is somebody
 * else's server can only be named by an address, and a world that is not can only be named by a
 * save folder. Where they disagree nothing is remembered here and the log says why, that being the
 * only answer that cannot put a build back into a world it was never in.
 *
 * <p>Nothing here is a setting. Closing a schematic before you log off is what forgets it, and that
 * is already how you close a schematic.
 */
public final class SchematicMemory {
	/** Field names, appended to the world id and the schematic's place in it to make one line. */
	private static final String KEY_SCHEMATIC = "schematic";
	private static final String KEY_X = "x";
	private static final String KEY_Y = "y";
	private static final String KEY_Z = "z";
	private static final String KEY_TURNS = "turns";
	private static final String KEY_MIRRORED = "mirrored";
	private static final String KEY_LAYER = "layer";
	private static final String KEY_OVERLAY = "overlay";

	/** The one field about the world rather than about a schematic in it. */
	private static final String KEY_ACTIVE = "active";

	/**
	 * What a world id starts with, which is also what it promises about the world it names. A note
	 * taken in a save folder is no note for a server, so the two are never spelt the same way and
	 * the spelling is something {@link #attach} can hold the world up against.
	 */
	private static final String SINGLEPLAYER = "singleplayer/";
	private static final String SERVER = "server/";

	private static final String[] ENTRY_KEYS = {
		KEY_SCHEMATIC, KEY_X, KEY_Y, KEY_Z, KEY_TURNS, KEY_MIRRORED, KEY_LAYER, KEY_OVERLAY,
	};

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
	private static final Map<String, WorldRecord> records = new LinkedHashMap<>();

	private static File file;

	/**
	 * Where the player is heading, set before the world arrives. Null until something says.
	 *
	 * <p>Volatile because a server is named from the thread that dials it while everything else
	 * here runs on the client tick.
	 */
	private static volatile String base;
	/** The world currently being remembered - {@link #base} plus its dimension - or null. */
	private static String worldId;
	/** What was last written for {@link #worldId}, so an unchanged state is not written again. */
	private static WorldRecord saved;
	private static int ticks;
	/** What is still to be put back into {@link #worldId}, or null once nothing is owed. */
	private static WorldRecord owed;
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
		// startup, and a game started with a server to join is already dialling it by then - from a
		// thread of its own, so the address can arrive on either side of this line. Clearing it here
		// would throw away the one thing that names the world being joined, some of the time.
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

		// Which places in which worlds the file has anything to say about. Sorted, so the schematics
		// of a world come back in the order they were opened in it.
		Map<String, Set<Integer>> places = new TreeMap<>();
		for (String key : properties.stringPropertyNames()) {
			int dot = key.lastIndexOf('.');
			if (dot <= 0) {
				continue;
			}

			String field = key.substring(dot + 1);
			String rest = key.substring(0, dot);
			if (!isEntryKey(field)) {
				// KEY_ACTIVE, or a field written by a later version of the mod. Either way it says
				// nothing about how many schematics there are.
				continue;
			}

			int place = 0;
			int previous = rest.lastIndexOf('.');
			if (previous > 0 && isNumber(rest.substring(previous + 1))) {
				place = Integer.parseInt(rest.substring(previous + 1));
				rest = rest.substring(0, previous);
			}
			// Otherwise a line written before the mod could hold more than one schematic per world,
			// which is the same as the first of several.

			places.computeIfAbsent(rest, ignored -> new TreeSet<>()).add(place);
		}

		for (Map.Entry<String, Set<Integer>> world : places.entrySet()) {
			String id = world.getKey();
			List<Entry> entries = new ArrayList<>();
			for (int place : world.getValue()) {
				Entry entry = readEntry(properties, id, place);
				if (entry != null) {
					entries.add(entry);
				}
			}
			if (entries.isEmpty()) {
				continue;
			}

			int active = readInt(properties, id + "." + KEY_ACTIVE, 0);
			records.put(id, new WorldRecord(entries, Math.max(0, Math.min(active, entries.size() - 1))));
		}

		if (!records.isEmpty()) {
			Log.info("Remembering what was open in " + records.size() + " world(s)");
		}
	}

	/**
	 * One schematic's line of the file, or null where there is nothing named to put back. A place
	 * with no name is a hole rather than an entry - the ones after it keep their own numbers, and
	 * the list simply comes back one shorter.
	 */
	private static Entry readEntry(Properties properties, String id, int place) {
		// The place is left out of the key for the first one only when nothing else was ever written
		// under that world, which is what a file from before this could hold more than one looks like.
		String prefix = properties.getProperty(id + "." + place + "." + KEY_SCHEMATIC) == null && place == 0
				? id + "."
				: id + "." + place + ".";

		String schematic = properties.getProperty(prefix + KEY_SCHEMATIC);
		if (schematic == null || schematic.trim().isEmpty()) {
			return null;
		}

		return new Entry(
				schematic.trim(),
				readInt(properties, prefix + KEY_X, 0),
				readInt(properties, prefix + KEY_Y, 0),
				readInt(properties, prefix + KEY_Z, 0),
				readInt(properties, prefix + KEY_TURNS, 0) & 3,
				readBoolean(properties, prefix + KEY_MIRRORED, false),
				readInt(properties, prefix + KEY_LAYER, -1),
				readBoolean(properties, prefix + KEY_OVERLAY, true));
	}

	/** A single player world is told apart by the folder it is saved in. */
	public static void onSingleplayerWorld(String saveName) {
		base = SINGLEPLAYER + saveName;
	}

	/**
	 * A server by the address dialled, lowered because host names do not care about case and two
	 * spellings of one server are still one server.
	 */
	public static void onServerConnect(String address, int port) {
		base = SERVER + address.toLowerCase(Locale.ROOT) + ":" + port;
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

		// Schematics still owed to this world were never put back into it, so what is open now is
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
	 * it was left holding once it can be read, then watches for a schematic being moved, turned,
	 * sliced, hidden, opened, swapped or closed.
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
			// Nothing is written down while schematics are still owed to this world: what is open
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
		String where = base;
		if (where == null) {
			// Nothing said where we are - a world reached by some path the mod does not know about.
			// Remembering nothing is better than remembering it against the wrong world.
			return;
		}

		// What was said last is about the world that was being headed for, and a world can turn up
		// that nothing said anything about: a screen the mod has never heard of, a connection made
		// from somewhere else, a mod that reaches a swap first. The world in hand knows one thing
		// about itself for certain, which is whether it is somebody else's, so the last thing said
		// is held up against that before it is believed. A note left in a save folder naming a
		// server's world is how a house comes back standing in the middle of somebody's spawn.
		boolean remote = mc.isWorldRemote();
		if (remote != where.startsWith(SERVER)) {
			Log.warn("This world is " + (remote ? "on a server" : "single player")
					+ " and the last word on where we were going was " + where
					+ "; nothing will be remembered here rather than remembered against that");
			base = null;
			return;
		}

		worldId = where + "/" + mc.world.dimension.id;
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
		WorldRecord record = owed;
		owed = null;

		if (!worldReady) {
			Log.warn("This world never said it had finished loading, which usually means another mod"
					+ " reaches the same place; putting " + record.entries.size()
					+ " schematic(s) back anyway");
		}

		restore(record);
		saved = capture();
	}

	/** What is open now, as the lines of the file. */
	private static WorldRecord capture() {
		SchematicaState state = Schematica.STATE;
		List<Entry> entries = new ArrayList<>();
		int active = 0;

		List<OpenSchematic> open = state.getOpen();
		for (int i = 0; i < open.size(); i++) {
			OpenSchematic schematic = open.get(i);
			String name = schematic.getLoadedName();
			if (schematic.isEmpty() || name == null) {
				// An empty slot is a place for a schematic rather than one, and there is nothing to
				// put back for it. The active one being empty leaves the mark where it fell.
				continue;
			}
			if (i == state.getActiveIndex()) {
				active = entries.size();
			}
			entries.add(new Entry(name, schematic.offset.x, schematic.offset.y, schematic.offset.z,
					schematic.getTurns(), schematic.isMirrored(),
					schematic.renderingLayer, schematic.isRenderingSchematic));
		}

		return entries.isEmpty() ? WorldRecord.NOTHING : new WorldRecord(entries, active);
	}

	/** Writes a record against the current world, if it says anything new. */
	private static void remember(WorldRecord record) {
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

	/** Puts every schematic this world was left holding back where it was left. */
	private static void restore(WorldRecord record) {
		SchematicaState state = Schematica.STATE;
		state.closeAll();

		int active = 0;
		int restored = 0;
		for (int i = 0; i < record.entries.size(); i++) {
			Entry entry = record.entries.get(i);
			File schematic = new File(Schematica.getSchematicDirectory(), entry.schematic);
			if (!schematic.isFile()) {
				Log.warn("The schematic left open here, " + entry.schematic
						+ ", is no longer in the schematics folder");
				continue;
			}

			if (!state.openSchematic(schematic)) {
				continue;
			}

			OpenSchematic open = state.getActive();
			open.setOrientation(entry.turns, entry.mirrored);
			open.offset.set(entry.x, entry.y, entry.z);
			open.setRenderingLayer(entry.layer);
			open.isRenderingSchematic = entry.overlay;
			open.needsUpdate = true;

			if (i == record.active) {
				// Counted among the ones that made it back, since the ones that did not are not in
				// the list to be pointed at.
				active = restored;
			}
			restored++;

			Log.info("Put " + entry.schematic + " back at " + entry.x + ", " + entry.y + ", " + entry.z);
		}

		state.setActiveIndex(active);

		// So the load screen opens with the active file picked out rather than the placeholder.
		String name = state.getLoadedName();
		List<String> files = state.getSchematicFiles();
		state.selectedSchematic = name == null ? 0 : Math.max(files.indexOf(name), 0);
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
		for (Map.Entry<String, WorldRecord> world : records.entrySet()) {
			String id = world.getKey();
			WorldRecord record = world.getValue();

			for (int place = 0; place < record.entries.size(); place++) {
				Entry entry = record.entries.get(place);
				String prefix = id + "." + place + ".";
				properties.setProperty(prefix + KEY_SCHEMATIC, entry.schematic);
				properties.setProperty(prefix + KEY_X, Integer.toString(entry.x));
				properties.setProperty(prefix + KEY_Y, Integer.toString(entry.y));
				properties.setProperty(prefix + KEY_Z, Integer.toString(entry.z));
				properties.setProperty(prefix + KEY_TURNS, Integer.toString(entry.turns));
				properties.setProperty(prefix + KEY_MIRRORED, Boolean.toString(entry.mirrored));
				properties.setProperty(prefix + KEY_LAYER, Integer.toString(entry.layer));
				properties.setProperty(prefix + KEY_OVERLAY, Boolean.toString(entry.overlay));
			}
			properties.setProperty(id + "." + KEY_ACTIVE, Integer.toString(record.active));
		}

		try (OutputStream stream = new FileOutputStream(file)) {
			properties.store(stream, "Schematica - what was open in each world, numbered in the order"
					+ " they were opened. Delete a world to forget it.");
		} catch (IOException exception) {
			Log.warn("Could not write " + file.getName(), exception);
		}
	}

	private static boolean isEntryKey(String field) {
		for (String key : ENTRY_KEYS) {
			if (key.equals(field)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a key's last part is a schematic's place in its world rather than part of the world's
	 * own name. Host names are full of dots, but a world id ends with a dimension behind a slash, so
	 * the only thing that can read as a number here is a number this class put there.
	 */
	private static boolean isNumber(String text) {
		if (text.isEmpty() || text.length() > 9) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) < '0' || text.charAt(i) > '9') {
				return false;
			}
		}
		return true;
	}

	private static int readInt(Properties properties, String key, int fallback) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	/**
	 * What was open in one world. Compared whole, so any change to any part of it is one that gets
	 * written, and none of them has to be noticed on its own.
	 */
	private static final class WorldRecord {
		/** Nothing was open. Never written out - the world is dropped from the file instead. */
		static final WorldRecord NOTHING = new WorldRecord(new ArrayList<>(), 0);

		final List<Entry> entries;
		/** Which of them the controls were pointed at. */
		final int active;

		WorldRecord(List<Entry> entries, int active) {
			this.entries = entries;
			this.active = active;
		}

		boolean isEmpty() {
			return this.entries.isEmpty();
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof WorldRecord)) {
				return false;
			}

			WorldRecord that = (WorldRecord) other;
			return this.active == that.active && this.entries.equals(that.entries);
		}

		@Override
		public int hashCode() {
			return this.entries.hashCode() * 31 + this.active;
		}
	}

	/** One schematic in one world: which file, where it stood, and how it was being drawn. */
	private static final class Entry {
		final String schematic;
		final int x;
		final int y;
		final int z;
		final int turns;
		final boolean mirrored;
		final int layer;
		final boolean overlay;

		Entry(String schematic, int x, int y, int z, int turns, boolean mirrored, int layer, boolean overlay) {
			this.schematic = schematic;
			this.x = x;
			this.y = y;
			this.z = z;
			this.turns = turns;
			this.mirrored = mirrored;
			this.layer = layer;
			this.overlay = overlay;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Entry)) {
				return false;
			}

			Entry that = (Entry) other;
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
