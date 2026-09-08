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
 * Browses the {@code schematics} folder, loads the selected file, and is one of the two ways in to
 * the replace screen - the other being the move screen, which is pointed at a schematic already.
 */
public class SchematicLoadScreen extends Screen {
	private final SchematicaState state = Schematica.STATE;
	private final Screen parent;

	private SchematicLoadListWidget list;
	private ButtonWidget btnReplace;
	private ButtonWidget btnOpenDir;
	private ButtonWidget btnDone;

	/** Snapshot of the folder taken when the screen opens, so rendering never touches the disk. */
	private List<String> entries;

	public SchematicLoadScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		this.entries = this.state.getSchematicFiles();
		if (this.state.selectedSchematic >= this.entries.size()) {
			this.state.selectedSchematic = 0;
		}

		// A row of its own above the other two: this one changes a file rather than choosing one, and
		// putting it beside Done is asking for it to be pressed on the way past.
		this.btnReplace = new OptionButtonWidget(0, this.width / 2 - 75, this.height - 72, Translations.get("schematic.replace"));
		this.buttons.add(this.btnReplace);
		this.btnOpenDir = new OptionButtonWidget(1, this.width / 2 - 154, this.height - 48, Translations.get("schematic.openFolder"));
		this.buttons.add(this.btnOpenDir);
		this.btnDone = new OptionButtonWidget(2, this.width / 2 + 4, this.height - 48, Translations.get("schematic.done"));
		this.buttons.add(this.btnDone);

		this.list = new SchematicLoadListWidget(this);
		this.list.registerButtons(this.buttons, 3, 4);
	}

	List<String> getEntries() {
		return this.entries;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnReplace) {
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
		// Settled every frame rather than once: what it turns on is which file is highlighted in the
		// list, and the list is on this same screen.
		this.btnReplace.active = this.canReplace();

		this.list.render(mouseX, mouseY, delta);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.title"), this.width / 2, 16, 0xFFFFFF);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.folderInfo"), this.width / 2 - 77, this.height - 26, 0x808080);
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

	/** The open schematic the highlighted file is, or null if it is not one and cannot become one. */
	private OpenSchematic selectedOpen() {
		int selected = this.state.selectedSchematic;
		if (selected <= 0 || selected >= this.entries.size()) {
			return null;
		}

		String name = this.entries.get(selected);
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
		int selected = this.state.selectedSchematic;
		if (selected <= 0 || selected >= this.entries.size()) {
			return false;
		}

		String name = this.entries.get(selected);
		for (OpenSchematic open : this.state.getOpen()) {
			if (name.equals(open.getLoadedName())) {
				return true;
			}
		}
		return this.state.canOpenAnother();
	}
}
