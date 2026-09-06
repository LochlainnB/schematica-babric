package lunatrius.schematica.gui;

import java.util.List;
import java.util.Locale;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaConfig;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * The render settings, laid out like the vanilla options screen.
 *
 * <p>This is what Mod Menu opens for the "Configure" button, so it has to work from the title
 * screen as well as in game: nothing here touches the world.
 */
public class SchematicaSettingsScreen extends Screen implements SliderWidget.Listener {
	/** Width of one keybind column, matching the sliders and buttons above them. */
	private static final int COLUMN_WIDTH = 150;
	private static final int ROW_HEIGHT = 12;

	private final SchematicaConfig config = Schematica.CONFIG;
	private final Screen parent;

	private SliderWidget sldAlpha;
	private SliderWidget sldBlockDelta;
	private ButtonWidget btnHighlight;
	private ButtonWidget btnControls;
	private ButtonWidget btnOpenDir;
	private ButtonWidget btnDone;

	public SchematicaSettingsScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int top = this.height / 6;

		this.sldAlpha = new SliderWidget(0, left, top, 150, this.config.alpha, this);
		this.sldAlpha.text = this.alphaLabel();
		this.buttons.add(this.sldAlpha);

		this.btnHighlight = this.addButton(1, right, top, 150, 20, this.highlightLabel());

		this.sldBlockDelta = new SliderWidget(
				2, left, top + 24, 150, this.config.blockDelta / SchematicaConfig.MAX_BLOCK_DELTA, this);
		this.sldBlockDelta.text = this.blockDeltaLabel();
		this.buttons.add(this.sldBlockDelta);

		this.btnControls = this.addButton(3, right, top + 24, 150, 20, Translations.get("schematic.settings.controls"));
		this.btnOpenDir = this.addButton(4, this.width / 2 - 100, top + 60, 200, 20, Translations.get("schematic.openFolder"));
		this.btnDone = this.addButton(5, this.width / 2 - 100, top + 168, 200, 20, Translations.get("schematic.done"));
	}

	private ButtonWidget addButton(int id, int x, int y, int width, int height, String text) {
		ButtonWidget button = new ButtonWidget(id, x, y, width, height, text);
		this.buttons.add(button);
		return button;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnHighlight) {
			this.config.highlight = !this.config.highlight;
			this.btnHighlight.text = this.highlightLabel();
			this.applied();
		} else if (button == this.btnControls) {
			this.minecraft.setScreen(new SchematicaKeysScreen(this));
		} else if (button == this.btnOpenDir) {
			SchematicLoadScreen.openSchematicDirectory();
		} else if (button == this.btnDone) {
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public void onSliderChanged(SliderWidget slider) {
		if (slider == this.sldAlpha) {
			this.config.alpha = slider.getValue();
			slider.text = this.alphaLabel();
		} else if (slider == this.sldBlockDelta) {
			this.config.blockDelta = slider.getValue() * SchematicaConfig.MAX_BLOCK_DELTA;
			slider.text = this.blockDeltaLabel();
		}
		this.applied();
	}

	/**
	 * Every setting here feeds the cached geometry - the highlight lists are built with the boxes
	 * already inflated - so a change has to invalidate it.
	 */
	private void applied() {
		Schematica.STATE.needsUpdate = true;
		this.config.save();
	}

	private String alphaLabel() {
		return Translations.get("schematic.settings.alpha") + ": " + Math.round(this.config.alpha * 100.0F) + "%";
	}

	private String blockDeltaLabel() {
		return Translations.get("schematic.settings.blockdelta") + ": "
				+ String.format(Locale.ROOT, "%.3f", this.config.blockDelta);
	}

	private String highlightLabel() {
		return Translations.get("schematic.settings.highlight") + ": "
				+ Translations.get(this.config.highlight ? "options.on" : "options.off");
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.settings.title"), this.width / 2, 20, 0xFFFFFF);

		int top = this.height / 6;
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.settings.keybinds"), this.width / 2, top + 92, 0x808080);

		// Two columns, over the same grid the sliders and buttons above use. A single column ran into
		// the Done button as soon as there were more than five keys.
		List<SchematicaConfig.Keybind> keybinds = this.config.getKeybinds();
		for (int i = 0; i < keybinds.size(); i++) {
			int x = i % 2 == 0 ? this.width / 2 - 155 : this.width / 2 + 5;
			this.drawKeybind(keybinds.get(i), x, top + 106 + i / 2 * ROW_HEIGHT);
		}

		super.render(mouseX, mouseY, delta);
	}

	private void drawKeybind(SchematicaConfig.Keybind keybind, int x, int y) {
		this.drawStringWithShadow(this.textRenderer, Translations.get(keybind.binding.translationKey), x, y, 0xE0E0E0);
		String key = keybind.getKeyName();
		this.drawStringWithShadow(this.textRenderer, key, x + COLUMN_WIDTH - this.textRenderer.getWidth(key), y, 0xE0E0E0);
	}
}
