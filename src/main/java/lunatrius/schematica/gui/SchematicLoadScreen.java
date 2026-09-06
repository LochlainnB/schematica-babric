package lunatrius.schematica.gui;

import java.io.File;
import java.net.URI;
import java.util.List;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Log;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;

/** Browses the {@code schematics} folder and loads the selected file. */
public class SchematicLoadScreen extends Screen {
	private final SchematicaState state = Schematica.STATE;
	private final Screen parent;

	private SchematicLoadListWidget list;
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

		this.btnOpenDir = new OptionButtonWidget(0, this.width / 2 - 154, this.height - 48, Translations.get("schematic.openFolder"));
		this.buttons.add(this.btnOpenDir);
		this.btnDone = new OptionButtonWidget(1, this.width / 2 + 4, this.height - 48, Translations.get("schematic.done"));
		this.buttons.add(this.btnDone);

		this.list = new SchematicLoadListWidget(this);
		this.list.registerButtons(this.buttons, 2, 3);
	}

	List<String> getEntries() {
		return this.entries;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnOpenDir) {
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
		this.list.render(mouseX, mouseY, delta);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.title"), this.width / 2, 16, 0xFFFFFF);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.folderInfo"), this.width / 2 - 77, this.height - 26, 0x808080);
		super.render(mouseX, mouseY, delta);
	}

	private void loadSelected() {
		int selected = this.state.selectedSchematic;

		// Index 0 is the "-- No schematic --" placeholder.
		if (selected <= 0 || selected >= this.entries.size()) {
			this.state.selectedSchematic = 0;
			this.state.clearSchematic();
			return;
		}

		File file = new File(Schematica.getSchematicDirectory(), this.entries.get(selected));
		if (this.state.loadSchematic(file)) {
			this.state.resetRenderingLayer();
			this.state.moveHere();
		} else {
			this.state.selectedSchematic = 0;
		}
	}
}
