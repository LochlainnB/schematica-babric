package lunatrius.schematica.gui;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Moves the loaded schematic around, steps through its layers and applies rotate / mirror.
 *
 * <p>Draws no background on purpose: the schematic stays visible behind the controls while it is
 * being positioned.
 */
public class SchematicControlScreen extends AxisScreen {
	private static final int FIELD_WIDTH = 44;
	private static final int BUTTON_WIDTH = 30;

	/** Left edge of the layers / operations column on the right of the screen. */
	private static final int RIGHT_COLUMN = 95;

	private final SchematicaState state = Schematica.STATE;

	/** Left edge of the coordinate rows, and the middle of them. */
	private int blockX;
	private int blockCenterX;
	private int centerY;

	private final AxisControls axisX = new AxisControls(this.state.offset, AxisControls.X);
	private final AxisControls axisY = new AxisControls(this.state.offset, AxisControls.Y);
	private final AxisControls axisZ = new AxisControls(this.state.offset, AxisControls.Z);

	private ButtonWidget btnDecLayer;
	private ButtonWidget btnIncLayer;
	private ButtonWidget btnHide;
	private ButtonWidget btnMove;
	private ButtonWidget btnMirror;
	private ButtonWidget btnRotate;
	private ButtonWidget btnSettings;

	@Override
	public void init() {
		int rowWidth = AxisControls.width(FIELD_WIDTH, BUTTON_WIDTH);
		// Centred where there is room, pushed left when the operations column would be in the way -
		// at the smallest gui scale the two do not both fit around the middle of the screen.
		this.blockX = Math.max(6, Math.min(this.width / 2 - rowWidth / 2, this.width - RIGHT_COLUMN - rowWidth));
		this.blockCenterX = this.blockX + rowWidth / 2;
		this.centerY = this.height / 2;

		int id = 0;
		id = this.addAxis(this.axisX, id, this.blockX, this.centerY - 30, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisY, id, this.blockX, this.centerY - 5, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisZ, id, this.blockX, this.centerY + 20, FIELD_WIDTH, BUTTON_WIDTH);

		this.btnDecLayer = this.addButton(id++, this.width - 90, this.height - 150, 25, 20, Translations.get("schematic.decrease"));
		this.btnIncLayer = this.addButton(id++, this.width - 35, this.height - 150, 25, 20, Translations.get("schematic.increase"));
		this.btnHide = this.addButton(id++, this.width - 90, this.height - 105, 80, 20, this.hideButtonLabel());
		this.btnMove = this.addButton(id++, this.width - 90, this.height - 80, 80, 20, Translations.get("schematic.movehere"));
		this.btnMirror = this.addButton(id++, this.width - 90, this.height - 55, 80, 20, Translations.get("schematic.flip"));
		this.btnRotate = this.addButton(id++, this.width - 90, this.height - 30, 80, 20, Translations.get("schematic.rotate"));

		// The settings otherwise only open through Mod Menu, which is optional.
		this.btnSettings = this.addButton(id, 10, this.height - 30, 80, 20, Translations.get("schematic.settings"));

		boolean hasSchematic = this.state.schematic != null;
		this.btnDecLayer.active = hasSchematic;
		this.btnIncLayer.active = hasSchematic;
		this.btnHide.active = hasSchematic;
		this.btnMirror.active = hasSchematic;
		this.btnRotate.active = hasSchematic;
	}

	private String hideButtonLabel() {
		return Translations.get(this.state.isRenderingSchematic ? "schematic.hide" : "schematic.show");
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (this.axisClicked(button)) {
			return;
		}

		if (button == this.btnDecLayer) {
			this.state.setRenderingLayer(this.state.renderingLayer - 1);
		} else if (button == this.btnIncLayer) {
			this.state.setRenderingLayer(this.state.renderingLayer + 1);
		} else if (button == this.btnHide) {
			this.state.toggleRendering();
			this.btnHide.text = this.hideButtonLabel();
		} else if (button == this.btnMove) {
			this.state.moveHere();
		} else if (button == this.btnMirror) {
			this.state.mirrorSchematic();
		} else if (button == this.btnRotate) {
			this.state.rotateSchematic();
		} else if (button == this.btnSettings) {
			this.minecraft.setScreen(new SchematicaSettingsScreen(this));
		}
	}

	/** The offset the rows edit is what the overlay is drawn against, so it has to be rebuilt. */
	@Override
	protected void axisChanged() {
		this.state.needsUpdate = true;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.moveschematic"), this.blockCenterX, this.centerY - 45, 0xFFFFFF);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.coords.hint"), this.blockCenterX, this.centerY + 46, 0xA0A0A0);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.layers"), this.width - 50, this.height - 165, 0xFFFFFF);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.operations"), this.width - 50, this.height - 120, 0xFFFFFF);
		this.drawCenteredTextWithShadow(
				this.textRenderer,
				this.state.renderingLayer < 0 ? Translations.get("schematic.all") : Integer.toString(this.state.renderingLayer + 1),
				this.width - 50, this.height - 145, 0xFFFFFF);

		this.renderAxes();
		super.render(mouseX, mouseY, delta);
	}
}
