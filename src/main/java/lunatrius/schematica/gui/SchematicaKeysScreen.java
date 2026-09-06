package lunatrius.schematica.gui;

import java.util.List;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaConfig;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

/**
 * Rebinds the mod's keys, laid out like the vanilla controls screen and writing to the same place:
 * the bindings here are the ones in {@code mc.options.allKeys}, so a change lands in
 * {@code options.txt} exactly as vanilla's screen would write it.
 *
 * <p>It exists because vanilla's screen has room for fourteen keys and no more - it puts Done at the
 * eighth row, so with this many mods keys the last of them end up behind it or off the bottom of the
 * screen. The mod's keys are still in that list, they are just not all reachable there.
 */
public class SchematicaKeysScreen extends Screen {
	private static final int BUTTON_WIDTH = 70;
	private static final int BUTTON_HEIGHT = 20;
	/** Row pitch where there is room for it. Rows are packed tighter when there is not. */
	private static final int MAX_ROW_HEIGHT = 22;
	/** Top of the first row, clear of the title. */
	private static final int ROWS_TOP = 36;
	/** Gap between a key and the name of what it does. */
	private static final int LABEL_GAP = 6;
	private static final int DONE_ID = 200;

	private final List<SchematicaConfig.Keybind> keybinds = Schematica.CONFIG.getKeybinds();
	private final Screen parent;

	private ButtonWidget btnDone;
	/** Index of the row waiting for a key, or -1 when nothing is being rebound. */
	private int selected = -1;

	public SchematicaKeysScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		this.selected = -1;

		for (int i = 0; i < this.keybinds.size(); i++) {
			this.buttons.add(new ButtonWidget(
					i, this.rowX(), this.rowY(i), BUTTON_WIDTH, BUTTON_HEIGHT, this.keybinds.get(i).getKeyName()));
		}

		this.btnDone = new ButtonWidget(
				DONE_ID, this.width / 2 - 100, this.doneY(), 200, BUTTON_HEIGHT, Translations.get("schematic.done"));
		this.buttons.add(this.btnDone);
	}

	/**
	 * One column, lined up with the Done button. Vanilla fits two columns by giving each name 84
	 * pixels, which is not enough for names as long as these.
	 */
	private int rowX() {
		return this.width / 2 - 100;
	}

	private int rowY(int index) {
		return ROWS_TOP + this.rowHeight() * index;
	}

	/**
	 * Pitch of the rows, taken from the space there actually is between the title and the hint
	 * rather than fixed. Another key added later then still lands on the screen at the smallest gui
	 * scale, where the whole screen is 240 pixels tall.
	 */
	private int rowHeight() {
		int rows = this.keybinds.size();
		if (rows < 2) {
			return MAX_ROW_HEIGHT;
		}

		int available = this.hintY() - 4 - ROWS_TOP - BUTTON_HEIGHT;
		return Math.max(BUTTON_HEIGHT, Math.min(MAX_ROW_HEIGHT, available / (rows - 1)));
	}

	private int hintY() {
		return this.doneY() - 12;
	}

	private int doneY() {
		return this.height - 28;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnDone) {
			this.minecraft.setScreen(this.parent);
			return;
		}

		this.selected = button.id;
		this.refreshLabels();
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		if (this.selected < 0) {
			super.keyPressed(character, keyCode);
			return;
		}

		// Escape gives up the rebind instead of binding escape, which would otherwise take the pause
		// menu with it and leave no way back out.
		if (keyCode != Keyboard.KEY_ESCAPE) {
			this.keybinds.get(this.selected).setCode(keyCode);
			// The same write vanilla's controls screen does, and for the same reason: the binding
			// itself is only in memory until options.txt has it.
			this.minecraft.options.save();
		}

		this.selected = -1;
		this.refreshLabels();
	}

	private void refreshLabels() {
		for (int i = 0; i < this.keybinds.size(); i++) {
			ButtonWidget button = (ButtonWidget) this.buttons.get(i);
			String key = this.keybinds.get(i).getKeyName();
			button.text = i == this.selected ? "> " + key + " <" : key;
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.keys.title"), this.width / 2, 20, 0xFFFFFF);

		for (int i = 0; i < this.keybinds.size(); i++) {
			this.drawStringWithShadow(
					this.textRenderer,
					Translations.get(this.keybinds.get(i).binding.translationKey),
					this.rowX() + BUTTON_WIDTH + LABEL_GAP, this.rowY(i) + 7, 0xE0E0E0);
		}

		this.drawCenteredTextWithShadow(
				this.textRenderer, Translations.get("schematic.keys.hint"), this.width / 2, this.hintY(), 0xA0A0A0);

		super.render(mouseX, mouseY, delta);
	}
}
