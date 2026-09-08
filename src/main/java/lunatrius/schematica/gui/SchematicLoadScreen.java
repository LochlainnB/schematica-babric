package lunatrius.schematica.gui;

import java.io.File;
import java.net.URI;
import java.util.List;

import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;

/**
 * Browses the {@code schematics} folder, loads the selected file, and is the only place in the mod
 * that takes one away. It is also one of the two ways in to the replace screen - the other being
 * the move screen, which is pointed at a schematic already.
 */
public class SchematicLoadScreen extends Screen {
	/**
	 * Ticks a delete stays armed. A warning nobody came back to has been left on the screen rather
	 * than read, and it should not still be waiting there to be answered by accident.
	 */
	private static final int CONFIRM_TIMEOUT = 100;
	/**
	 * Ticks a freshly armed delete refuses to go through, so that the second half of a double click
	 * cannot answer a question that was not on the screen when the first half was pressed.
	 */
	private static final int CONFIRM_DELAY = 10;

	private static final int HINT_COLOUR = 0x808080;
	private static final int WARNING_COLOUR = 0xFF5555;

	private final SchematicaState state = Schematica.STATE;
	private final Screen parent;

	private SchematicLoadListWidget list;
	private ButtonWidget btnDelete;
	private ButtonWidget btnReplace;
	private ButtonWidget btnOpenDir;
	private ButtonWidget btnDone;

	/** Snapshot of the folder taken when the screen opens, so rendering never touches the disk. */
	private List<String> entries;

	/** The file a second press of Delete would take away, or null while it is an ordinary button. */
	private String pendingDelete;
	/** How long it has been armed: both the guard against a double click and the timeout. */
	private int pendingTicks;
	/** What became of the last file deleted, said under the list until something else happens. */
	private String status;

	public SchematicLoadScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		this.entries = this.state.getSchematicFiles();
		if (this.state.selectedSchematic >= this.entries.size()) {
			this.state.selectedSchematic = 0;
		}

		// A row of its own above the other two: these change a file rather than choosing one, and
		// putting either beside Done is asking for it to be pressed on the way past. Delete is the
		// one furthest from it, being the one there is no way back from.
		this.btnDelete = new OptionButtonWidget(0, this.width / 2 - 154, this.height - 72, Translations.get("schematic.delete"));
		this.buttons.add(this.btnDelete);
		this.btnReplace = new OptionButtonWidget(1, this.width / 2 + 4, this.height - 72, Translations.get("schematic.replace"));
		this.buttons.add(this.btnReplace);
		this.btnOpenDir = new OptionButtonWidget(2, this.width / 2 - 154, this.height - 48, Translations.get("schematic.openFolder"));
		this.buttons.add(this.btnOpenDir);
		this.btnDone = new OptionButtonWidget(3, this.width / 2 + 4, this.height - 48, Translations.get("schematic.done"));
		this.buttons.add(this.btnDone);

		this.list = new SchematicLoadListWidget(this);
		this.list.registerButtons(this.buttons, 4, 5);

		// The button has just been made afresh, so whatever it was warning about it is not warning
		// about now.
		this.disarm();
	}

	List<String> getEntries() {
		return this.entries;
	}

	@Override
	public void tick() {
		if (this.pendingDelete == null) {
			return;
		}

		// Armed on a file rather than on the button, so highlighting another one is as good an
		// answer as pressing anything: the question was about that file, and it is no longer asked.
		this.pendingTicks++;
		if (this.pendingTicks > CONFIRM_TIMEOUT || !this.pendingDelete.equals(this.selectedName())) {
			this.disarm();
		}
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnDelete) {
			this.deleteSelected();
		} else if (button == this.btnReplace) {
			this.replaceBlocks();
		} else if (button == this.btnOpenDir) {
			openSchematicDirectory();
		} else if (button == this.btnDone) {
			this.loadSelected();
			this.minecraft.setScreen(this.parent);
		} else {
			this.list.buttonClicked(button);
		}
	}

	/** Also used by the settings screen, which offers the same shortcut. */
	static void openSchematicDirectory() {
		File directory = Schematica.getSchematicDirectory();
		try {
			// Reflective so the mod still links on a headless / trimmed JRE without java.awt.Desktop.
			Class<?> desktop = Class.forName("java.awt.Desktop");
			Object instance = desktop.getMethod("getDesktop").invoke(null);
			desktop.getMethod("browse", URI.class).invoke(instance, directory.toURI());
			return;
		} catch (Throwable throwable) {
			Log.warn("java.awt.Desktop is unavailable, falling back to LWJGL", throwable);
		}

		try {
			org.lwjgl.Sys.openURL("file://" + directory.getAbsolutePath());
		} catch (Throwable throwable) {
			Log.error("Could not open " + directory.getAbsolutePath(), throwable);
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		// Settled every frame rather than once: what they turn on is which file is highlighted in
		// the list, and the list is on this same screen.
		this.btnDelete.active = this.selectedName() != null;
		this.btnReplace.active = this.canReplace();

		this.list.render(mouseX, mouseY, delta);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.title"), this.width / 2, 16, 0xFFFFFF);

		if (this.pendingDelete != null) {
			this.drawCenteredTextWithShadow(this.textRenderer,
					Translations.get("schematic.delete.pending") + " " + this.pendingDelete,
					this.width / 2, this.height - 26, WARNING_COLOUR);
		} else if (this.status != null) {
			this.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, this.height - 26, HINT_COLOUR);
		} else {
			this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.folderInfo"), this.width / 2 - 77, this.height - 26, HINT_COLOUR);
		}

		super.render(mouseX, mouseY, delta);
	}

	/**
	 * Opens the highlighted file alongside whatever is already open, and puts it in front of the
	 * player - which is where a schematic nobody has said anything about yet belongs, and is the
	 * only place it will not be standing inside one of the others.
	 *
	 * <p>Adding rather than replacing, since several can be open at once. The placeholder at the top
	 * of the list still means nothing, and closes the schematic the controls are pointed at.
	 */
	private void loadSelected() {
		int selected = this.state.selectedSchematic;

		// Index 0 is the "-- No schematic --" placeholder.
		if (selected <= 0 || selected >= this.entries.size()) {
			this.state.selectedSchematic = 0;
			this.state.closeActive();
			return;
		}

		File file = new File(Schematica.getSchematicDirectory(), this.entries.get(selected));
		if (this.state.openSchematic(file)) {
			this.state.resetRenderingLayer();
			this.state.moveHere();
		} else {
			this.state.selectedSchematic = 0;
		}
	}

	/**
	 * Takes the highlighted file away, on the second press rather than the first.
	 *
	 * <p>The first press arms the button and names the file it is holding, and the second carries
	 * it out - so the warning says what is about to go, and reading it is what happens between the
	 * two. A press while another file is highlighted arms that one instead of taking this one,
	 * which is the same answer as walking away from it.
	 *
	 * <p>Whatever is already open stays open. The blocks are in memory and the ghost is standing in
	 * the world; deleting the file they came from is not an instruction to take the build off the
	 * screen, and there would be nothing to gain by making it one.
	 */
	private void deleteSelected() {
		String name = this.selectedName();
		if (name == null) {
			return;
		}

		if (!name.equals(this.pendingDelete) || this.pendingTicks < CONFIRM_DELAY) {
			this.arm(name);
			return;
		}

		boolean deleted = delete(new File(Schematica.getSchematicDirectory(), name));
		this.disarm();
		this.status = deleted
				? Translations.get("schematic.delete.done") + " " + name
				: Translations.get("schematic.delete.failed");
		if (!deleted) {
			return;
		}

		// The list is a snapshot, so it is taken again rather than having a row picked out of it,
		// and nothing is left highlighted afterwards: what was chosen is gone, and the file that
		// slid up into its place is not one anybody chose.
		this.entries = this.state.getSchematicFiles();
		this.state.selectedSchematic = 0;
	}

	/**
	 * Takes a file away, into whatever the platform keeps deleted files in where it has one.
	 *
	 * <p>A schematic is hours of somebody's building and this is the one button in the mod that
	 * ends one, so it is worth some reflection to leave a way back from it. Desktop is asked the
	 * same way the folder button asks it - it is not on every runtime, and moving a file to the
	 * wastebasket is not on every desktop - and where the answer is no the file is simply deleted.
	 */
	private static boolean delete(File file) {
		try {
			Class<?> desktop = Class.forName("java.awt.Desktop");
			Object instance = desktop.getMethod("getDesktop").invoke(null);
			if (Boolean.TRUE.equals(desktop.getMethod("moveToTrash", File.class).invoke(instance, file))) {
				Log.info("Moved " + file.getName() + " to the wastebasket");
				return true;
			}
		} catch (Throwable throwable) {
			Log.warn("Could not move " + file.getName() + " to the wastebasket, deleting it instead", throwable);
		}

		if (file.delete()) {
			Log.info("Deleted " + file.getName());
			return true;
		}

		Log.error("Could not delete " + file.getAbsolutePath());
		return false;
	}

	/** Puts the button on its second press, holding the file that press would take away. */
	private void arm(String name) {
		this.pendingDelete = name;
		this.pendingTicks = 0;
		this.status = null;
		this.btnDelete.text = Translations.get("schematic.delete.confirm");
	}

	/** Back to an ordinary button, holding nothing. */
	private void disarm() {
		this.pendingDelete = null;
		this.pendingTicks = 0;
		if (this.btnDelete != null) {
			this.btnDelete.text = Translations.get("schematic.delete");
		}
	}

	/**
	 * Opens the replace screen on the highlighted file.
	 *
	 * <p>Editing a schematic means having it open, so one that is not gets opened here - the same
	 * thing Done would have done with it, since a build being changed is one you want to be looking
	 * at while you change it. One that is already open is not opened twice; the controls are pointed
	 * at the copy that is already standing somewhere instead.
	 */
	private void replaceBlocks() {
		OpenSchematic target = this.selectedOpen();
		if (target != null) {
			this.minecraft.setScreen(new BlockReplaceScreen(this, target));
		}
	}

	/** The file highlighted in the list, or null for the placeholder at the top and for nothing. */
	private String selectedName() {
		int selected = this.state.selectedSchematic;
		return selected > 0 && selected < this.entries.size() ? this.entries.get(selected) : null;
	}

	/** The open schematic the highlighted file is, or null if it is not one and cannot become one. */
	private OpenSchematic selectedOpen() {
		String name = this.selectedName();
		if (name == null) {
			return null;
		}

		List<OpenSchematic> open = this.state.getOpen();
		for (int i = 0; i < open.size(); i++) {
			if (name.equals(open.get(i).getLoadedName())) {
				this.state.setActiveIndex(i);
				return open.get(i);
			}
		}

		if (!this.state.openSchematic(new File(Schematica.getSchematicDirectory(), name))) {
			return null;
		}
		this.state.resetRenderingLayer();
		this.state.moveHere();
		return this.state.getActive();
	}

	/** Whether there is a file highlighted that could be opened for editing, or already is. */
	private boolean canReplace() {
		String name = this.selectedName();
		if (name == null) {
			return false;
		}

		for (OpenSchematic open : this.state.getOpen()) {
			if (name.equals(open.getLoadedName())) {
				return true;
			}
		}
		return this.state.canOpenAnother();
	}
}
