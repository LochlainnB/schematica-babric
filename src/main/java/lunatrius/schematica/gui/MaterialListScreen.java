package lunatrius.schematica.gui;

import java.util.Locale;

import lunatrius.schematica.MaterialList;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.schematic.SchematicWorld;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Everything the loaded schematic is built out of, and how much of it the player is carrying.
 *
 * <p>The schematic side of the list is counted once, when the screen opens, since only loading or
 * transforming a schematic can change it and neither can happen from here. The inventory side is
 * counted again every tick, which is what makes the numbers fall while the screen is open - a chest
 * emptied into the pack shows up without closing anything.
 */
public class MaterialListScreen extends Screen {
	private static final int LIST_TOP = 40;
	/** Height of the footer: the totals line and the Done button under it. */
	private static final int FOOTER_HEIGHT = 44;

	private final Screen parent;

	private MaterialList materials;
	private MaterialListWidget list;
	private ButtonWidget btnDone;

	public MaterialListScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		if (this.materials == null) {
			// init() runs again on every resize, and walking the whole schematic again for a window
			// that changed size would be work for nothing.
			SchematicWorld schematic = Schematica.STATE.schematic;
			this.materials = MaterialList.of(schematic == null ? null : schematic.getSchematic());
		}
		this.countInventory();

		this.btnDone = new ButtonWidget(
				0, this.width / 2 - 100, this.height - 28, 200, 20, Translations.get("schematic.done"));
		this.buttons.add(this.btnDone);

		this.list = new MaterialListWidget(this, LIST_TOP, this.height - FOOTER_HEIGHT);
		this.list.registerButtons(this.buttons, 1, 2);
	}

	/** The list this screen is showing, counted when it opened. */
	public MaterialList getMaterials() {
		return this.materials;
	}

	@Override
	public void tick() {
		this.countInventory();
	}

	private void countInventory() {
		PlayerEntity player = this.minecraft != null ? this.minecraft.player : null;
		this.materials.countInventory(player != null ? player.inventory.main : null);
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (button == this.btnDone) {
			this.minecraft.setScreen(this.parent);
		} else {
			this.list.buttonClicked(button);
		}
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
		return Translations.get(Schematica.STATE.schematic == null
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
