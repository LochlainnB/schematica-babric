package lunatrius.schematica.gui;

import java.util.Locale;

import lunatrius.schematica.MaterialList;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaConfig;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Everything the loaded schematic is built out of, and how much of it the player is carrying.
 *
 * <p>The schematic side of the list is counted once and shared with the info HUD, since only
 * loading or transforming a schematic can change it. The inventory side is counted again every
 * tick, which is what makes the numbers fall while the screen is open - a chest emptied into the
 * pack shows up without closing anything.
 *
 * <p>The two buttons at the bottom drive {@link InfoHud}, which is the same list in the corner of
 * the screen while playing. They are here rather than in the settings because this is where a
 * player is already looking at the list and deciding what to do about it.
 */
public class MaterialListScreen extends Screen {
	private static final int LIST_TOP = 40;
	/** Height of the footer: the totals line, the two HUD buttons and Done under them. */
	private static final int FOOTER_HEIGHT = 72;
	/** The two-column grid the settings screen uses, so the mod's buttons all line up. */
	private static final int COLUMN_WIDTH = 150;

	private final SchematicaConfig config = Schematica.CONFIG;
	private final Screen parent;

	private MaterialList materials;
	private MaterialListWidget list;
	private ButtonWidget btnHud;
	private ButtonWidget btnSort;
	private ButtonWidget btnDone;

	public MaterialListScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		// Counted once and kept: init() runs again on every resize, and walking the whole schematic
		// again for a window that changed size would be work for nothing.
		this.materials = Schematica.STATE.getMaterials();
		Schematica.STATE.countMaterials();

		this.btnHud = this.addButton(
				0, this.width / 2 - 155, this.height - 52, COLUMN_WIDTH, 20, this.hudLabel());
		this.btnSort = this.addButton(
				1, this.width / 2 + 5, this.height - 52, COLUMN_WIDTH, 20, this.sortLabel());
		this.btnDone = this.addButton(
				2, this.width / 2 - 100, this.height - 28, 200, 20, Translations.get("schematic.done"));

		this.list = new MaterialListWidget(this, LIST_TOP, this.height - FOOTER_HEIGHT);
		this.list.registerButtons(this.buttons, 3, 4);
	}

	private ButtonWidget addButton(int id, int x, int y, int width, int height, String text) {
		ButtonWidget button = new ButtonWidget(id, x, y, width, height, text);
		this.buttons.add(button);
		return button;
	}

	/** The list this screen is showing, which is the one the whole mod shares. */
	public MaterialList getMaterials() {
		return this.materials;
	}

	@Override
	public void tick() {
		Schematica.STATE.countMaterials();
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnHud) {
			this.config.infoHud = !this.config.infoHud;
			this.btnHud.text = this.hudLabel();
			this.config.save();
		} else if (button == this.btnSort) {
			this.config.infoHudSort = this.config.infoHudSort == MaterialList.Sort.DESCENDING
					? MaterialList.Sort.ASCENDING
					: MaterialList.Sort.DESCENDING;
			this.btnSort.text = this.sortLabel();
			this.config.save();
		} else if (button == this.btnDone) {
			this.minecraft.setScreen(this.parent);
		} else {
			this.list.buttonClicked(button);
		}
	}

	private String hudLabel() {
		return Translations.get("schematic.materials.hud") + ": "
				+ Translations.get(this.config.infoHud ? "options.on" : "options.off");
	}

	private String sortLabel() {
		return Translations.get("schematic.materials.hud.sort") + ": "
				+ Translations.get(this.config.infoHudSort == MaterialList.Sort.ASCENDING
						? "schematic.materials.hud.sort.ascending"
						: "schematic.materials.hud.sort.descending");
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.list.render(mouseX, mouseY, delta);
		this.drawCenteredTextWithShadow(
				this.textRenderer, Translations.get("schematic.materials.title"), this.width / 2, 12, 0xFFFFFF);

		if (this.materials.isEmpty()) {
			this.drawCenteredTextWithShadow(this.textRenderer, this.emptyMessage(), this.width / 2,
					(LIST_TOP + this.height - FOOTER_HEIGHT) / 2 - 4, 0xA0A0A0);
		} else {
			this.drawHeadings();
		}

		this.drawCenteredTextWithShadow(
				this.textRenderer, this.totals(), this.width / 2, this.height - FOOTER_HEIGHT + 8, 0x808080);
		super.render(mouseX, mouseY, delta);
	}

	private void drawHeadings() {
		int left = MaterialListWidget.rowLeft(this.width);
		int y = LIST_TOP - 12;
		this.drawStringWithShadow(this.textRenderer, Translations.get("schematic.materials.item"),
				left + MaterialListWidget.NAME_LEFT, y, 0xA0A0A0);
		this.drawRight(Translations.get("schematic.materials.have"),
				left + MaterialListWidget.HAVE_RIGHT, y, 0xA0A0A0);
		this.drawRight(Translations.get("schematic.materials.missing"),
				left + MaterialListWidget.MISSING_RIGHT, y, 0xA0A0A0);
	}

	/** Draws text ending at the given x, which is how a column of numbers lines up. */
	void drawRight(String text, int right, int y, int colour) {
		this.drawStringWithShadow(this.textRenderer, text, right - this.textRenderer.getWidth(text), y, colour);
	}

	private String emptyMessage() {
		return Translations.get(Schematica.STATE.getActive().isEmpty()
				? "schematic.materials.none"
				: "schematic.materials.empty");
	}

	private String totals() {
		if (this.materials.isEmpty()) {
			return "";
		}

		return Translations.get("schematic.materials.needed") + ": " + count(this.materials.getTotalNeeded())
				+ "    " + Translations.get("schematic.materials.missing") + ": "
				+ count(this.materials.getTotalMissing());
	}

	private static String count(int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
