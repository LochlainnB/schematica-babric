package lunatrius.schematica.gui;

import lunatrius.schematica.EasyPlace;
import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicPaste;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Moves a schematic around, steps through its layers and applies rotate / mirror.
 *
 * <p>Everything here is pointed at one of the open schematics, and the picker along the top is what
 * chooses which. The rest of the screen is built again whenever that changes, because the
 * coordinate rows edit the offset of the schematic they were laid out against.
 *
 * <p>Draws no background on purpose: the schematics stay visible behind the controls while one of
 * them is being positioned.
 */
public class SchematicControlScreen extends AxisScreen {
	private static final int FIELD_WIDTH = 44;
	private static final int BUTTON_WIDTH = 30;

	/** Left edge of the layers / operations column on the right of the screen. */
	private static final int RIGHT_COLUMN = 95;

	/** The row along the top: which schematic, and the two ways to change what is open. */
	private static final int PICKER_WIDTH = 130;
	private static final int PICKER_Y = 8;
	private static final int TOP_BUTTON_WIDTH = 42;
	private static final int TOP_GAP = 4;
	private static final int TOP_ROW_WIDTH = PICKER_WIDTH + (TOP_BUTTON_WIDTH + TOP_GAP) * 2;

	/** The paste button, kept here because working out whether the mouse is over it needs them. */
	private static final int PASTE_X = 10;
	private static final int PASTE_WIDTH = 100;
	private static final int PASTE_HEIGHT = 20;

	/** The tooltip box: the air inside it, the step between its lines, and its edges. */
	private static final int TOOLTIP_PADDING = 4;
	private static final int TOOLTIP_LINE_HEIGHT = 11;
	private static final int TOOLTIP_MARGIN = 2;
	/** How tall a line of text is, as against how far apart two of them are set. */
	private static final int TOOLTIP_TEXT_HEIGHT = 8;
	private static final int TOOLTIP_BACKGROUND = 0xE0100010;
	private static final int TOOLTIP_TEXT = 0xFFFFFF;
	/** The line saying why a greyed-out button is greyed out, in the grey the button itself is. */
	private static final int TOOLTIP_REASON = 0xA0A0A0;

	private final SchematicaState state = Schematica.STATE;

	/** Left edge of the coordinate rows, and the middle of them. */
	private int blockX;
	private int blockCenterX;
	private int centerY;

	/**
	 * Built in {@link #init()} rather than held for the life of the screen: a row edits one number
	 * inside one schematic's offset, so pointing the screen at another schematic means new rows.
	 */
	private AxisControls axisX;
	private AxisControls axisY;
	private AxisControls axisZ;

	private SchematicPickerWidget picker;
	private ButtonWidget btnOpen;
	private ButtonWidget btnClose;
	private ButtonWidget btnDecLayer;
	private ButtonWidget btnIncLayer;
	private ButtonWidget btnHide;
	private ButtonWidget btnMove;
	private ButtonWidget btnMirror;
	private ButtonWidget btnRotate;
	private ButtonWidget btnEasyPlace;
	private ButtonWidget btnPaste;
	private ButtonWidget btnReplace;
	private ButtonWidget btnMaterials;
	private ButtonWidget btnSettings;

	/** What the last paste did, shown where the coordinate hint is until the screen is closed. */
	private String status = "";

	@Override
	public void init() {
		OpenSchematic active = this.state.getActive();

		int rowWidth = AxisControls.width(FIELD_WIDTH, BUTTON_WIDTH);
		// Centred where there is room, pushed left when the operations column would be in the way -
		// at the smallest gui scale the two do not both fit around the middle of the screen.
		this.blockX = Math.max(6, Math.min(this.width / 2 - rowWidth / 2, this.width - RIGHT_COLUMN - rowWidth));
		this.blockCenterX = this.blockX + rowWidth / 2;
		this.centerY = this.height / 2;

		this.axisX = new AxisControls(active.offset, AxisControls.X);
		this.axisY = new AxisControls(active.offset, AxisControls.Y);
		this.axisZ = new AxisControls(active.offset, AxisControls.Z);

		int id = 0;
		id = this.addAxis(this.axisX, id, this.blockX, this.centerY - 30, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisY, id, this.blockX, this.centerY - 5, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisZ, id, this.blockX, this.centerY + 20, FIELD_WIDTH, BUTTON_WIDTH);

		// Which schematic all of the above is about, with the two buttons that change the set itself
		// on either side of it.
		int pickerX = Math.max(2, this.width / 2 - TOP_ROW_WIDTH / 2);
		this.picker = new SchematicPickerWidget(id++, pickerX, PICKER_Y, PICKER_WIDTH, this.state);
		this.buttons.add(this.picker);
		this.btnOpen = this.addButton(id++, pickerX + PICKER_WIDTH + TOP_GAP, PICKER_Y,
				TOP_BUTTON_WIDTH, 20, Translations.get("schematic.open"));
		this.btnClose = this.addButton(id++, pickerX + PICKER_WIDTH + TOP_BUTTON_WIDTH + TOP_GAP * 2, PICKER_Y,
				TOP_BUTTON_WIDTH, 20, Translations.get("schematic.close"));

		this.btnDecLayer = this.addButton(id++, this.width - 90, this.height - 150, 25, 20, Translations.get("schematic.decrease"));
		this.btnIncLayer = this.addButton(id++, this.width - 35, this.height - 150, 25, 20, Translations.get("schematic.increase"));
		this.btnHide = this.addButton(id++, this.width - 90, this.height - 105, 80, 20, this.hideButtonLabel());
		this.btnMove = this.addButton(id++, this.width - 90, this.height - 80, 80, 20, Translations.get("schematic.movehere"));
		this.btnMirror = this.addButton(id++, this.width - 90, this.height - 55, 80, 20, Translations.get("schematic.flip"));
		this.btnRotate = this.addButton(id++, this.width - 90, this.height - 30, 80, 20, Translations.get("schematic.rotate"));

		// Changing what the schematic is made of, rather than where it stands - the one thing on this
		// screen that is about the file and not about the ghost.
		this.btnReplace = this.addButton(id++, 10, this.height - 130, 100, 20, Translations.get("schematic.replace"));

		// Building the thing rather than a guide to it, which only a creative single player world can
		// be allowed. Greyed out the rest of the time, with a tooltip saying which of the reasons it
		// is - a button that is simply dead says nothing about what would bring it back.
		this.btnPaste = this.addButton(id++, PASTE_X, this.height - 105, PASTE_WIDTH, PASTE_HEIGHT,
				Translations.get("schematic.paste"));

		// What the schematic is built out of, and how much of it is already in the pack.
		this.btnMaterials = this.addButton(id++, 10, this.height - 80, 100, 20, Translations.get("schematic.materials"));

		// Easy place has a key of its own, but nothing on screen otherwise says whether it is on.
		this.btnEasyPlace = this.addButton(id++, 10, this.height - 55, 100, 20, EasyPlace.label());

		// The settings otherwise only open through Mod Menu, which is optional.
		this.btnSettings = this.addButton(id, 10, this.height - 30, 80, 20, Translations.get("schematic.settings"));

		boolean hasSchematic = !active.isEmpty();
		this.btnDecLayer.active = hasSchematic;
		this.btnIncLayer.active = hasSchematic;
		this.btnHide.active = hasSchematic;
		this.btnMirror.active = hasSchematic;
		this.btnRotate.active = hasSchematic;
		this.btnMaterials.active = hasSchematic;
		this.btnReplace.active = hasSchematic;
		// Paste, Open and Close are left to render(), which settles them every frame: what they turn
		// on is a creative mode and a set of open schematics this screen does not own and cannot hear
		// about, so asking once on the way in would leave a button that is wrong until the screen is
		// opened again.
	}

	private String hideButtonLabel() {
		return Translations.get(this.state.getActive().isRenderingSchematic ? "schematic.hide" : "schematic.show");
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (this.axisClicked(button)) {
			return;
		}

		OpenSchematic active = this.state.getActive();
		if (button == this.picker) {
			this.picker.toggle();
		} else if (button == this.btnOpen) {
			this.minecraft.setScreen(new SchematicLoadScreen(this));
		} else if (button == this.btnClose) {
			this.state.closeActive();
			this.rebuild();
		} else if (button == this.btnDecLayer) {
			active.setRenderingLayer(active.renderingLayer - 1);
		} else if (button == this.btnIncLayer) {
			active.setRenderingLayer(active.renderingLayer + 1);
		} else if (button == this.btnHide) {
			active.toggleRendering();
			this.btnHide.text = this.hideButtonLabel();
		} else if (button == this.btnMove) {
			this.state.moveHere();
		} else if (button == this.btnMirror) {
			active.mirrorSchematic();
		} else if (button == this.btnRotate) {
			active.rotateSchematic();
		} else if (button == this.btnPaste) {
			this.paste();
		} else if (button == this.btnReplace) {
			this.minecraft.setScreen(new BlockReplaceScreen(this, active));
		} else if (button == this.btnMaterials) {
			this.minecraft.setScreen(new MaterialListScreen(this));
		} else if (button == this.btnEasyPlace) {
			this.state.toggleEasyPlace();
			this.btnEasyPlace.text = EasyPlace.label();
		} else if (button == this.btnSettings) {
			this.minecraft.setScreen(new SchematicaSettingsScreen(this));
		}
	}

	/**
	 * Clicks are offered to the picker before the buttons: while it is dropped it lies over some of
	 * them, and a click meant for the list would otherwise land on whatever is underneath.
	 */
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (this.picker != null && this.picker.isExpanded()) {
			int row = this.picker.rowAt(mouseX, mouseY);
			if (row >= 0) {
				this.select(row);
				return;
			}
			if (!this.picker.isOverButton(mouseX, mouseY)) {
				// Anywhere else puts the list away, and does nothing else: the click that closes a
				// dropped list belongs to the list.
				this.picker.collapse();
				return;
			}
		}

		super.mouseClicked(mouseX, mouseY, button);
	}

	/** Points the screen at another of the open schematics. */
	private void select(int index) {
		this.picker.collapse();
		if (index == this.state.getActiveIndex()) {
			return;
		}

		this.state.setActiveIndex(index);
		this.rebuild();
	}

	/**
	 * Lays the screen out again against whatever is now the active schematic. The rows hold the
	 * offset they were built with, so this is what makes them the new one's rows.
	 */
	private void rebuild() {
		this.status = "";
		this.init(this.minecraft, this.width, this.height);
	}

	/**
	 * Writes the schematic into the world, and says how much of it went down. A refusal is left
	 * unsaid: the button is only clickable while the paste is on offer, so the only way to reach one
	 * is for the answer to have changed between the frame that drew the button and the click on it.
	 */
	private void paste() {
		int placed = SchematicPaste.paste(this.minecraft);
		if (placed != SchematicPaste.REFUSED) {
			this.status = Translations.get("schematic.paste.done") + " " + placed;
		}
	}

	/** The offset the rows edit is what an overlay is drawn against, so it has to be rebuilt. */
	@Override
	protected void axisChanged() {
		this.state.getActive().needsUpdate = true;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		OpenSchematic active = this.state.getActive();

		this.picker.updateLabel(this.textRenderer);
		this.btnOpen.active = this.state.canOpenAnother();
		// Closing the last empty slot would leave the controls pointing at nothing, and there is
		// nothing in it to close.
		this.btnClose.active = !active.isEmpty() || this.state.getOpenCount() > 1;

		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.moveschematic"), this.blockCenterX, this.centerY - 45, 0xFFFFFF);
		// The hint has said its piece once something has been pasted.
		String footer = this.status.isEmpty() ? Translations.get("schematic.coords.hint") : this.status;
		this.drawCenteredTextWithShadow(this.textRenderer, footer, this.blockCenterX, this.centerY + 46, 0xA0A0A0);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.layers"), this.width - 50, this.height - 165, 0xFFFFFF);
		this.drawCenteredTextWithShadow(this.textRenderer, Translations.get("schematic.operations"), this.width - 50, this.height - 120, 0xFFFFFF);
		this.drawCenteredTextWithShadow(
				this.textRenderer,
				active.renderingLayer < 0 ? Translations.get("schematic.all") : Integer.toString(active.renderingLayer + 1),
				this.width - 50, this.height - 145, 0xFFFFFF);

		this.renderAxes();

		SchematicPaste.Availability paste = SchematicPaste.availability(this.minecraft);
		this.btnPaste.active = paste.isReady();

		super.render(mouseX, mouseY, delta);

		// After the buttons, so each sits over the ones it belongs over rather than under them.
		if (this.isOverPaste(mouseX, mouseY)) {
			this.renderPasteTooltip(paste);
		}
		this.picker.renderList(this.minecraft, mouseX, mouseY);
	}

	/**
	 * What the paste button is for, and - while it is greyed out - why it is.
	 *
	 * <p>Anchored over the button rather than following the mouse, so it cannot cover the thing it
	 * is describing and lands in the same place every time. Beta has nothing that wraps a line of
	 * text, so the lines behind these keys are short enough to fit across a narrow window and the
	 * box is nudged back on screen if one of them still does not.
	 */
	private void renderPasteTooltip(SchematicPaste.Availability availability) {
		String what = Translations.get("schematic.paste.hint");
		String why = availability.reason();

		int width = this.textRenderer.getWidth(what);
		if (why != null) {
			width = Math.max(width, this.textRenderer.getWidth(why));
		}

		int boxWidth = width + TOOLTIP_PADDING * 2;
		// The gap below the last line is the padding, not a whole line of it: measuring in line
		// heights throughout would leave the text sitting high in its own box.
		int boxHeight = (why == null ? 0 : TOOLTIP_LINE_HEIGHT)
				+ TOOLTIP_TEXT_HEIGHT + TOOLTIP_PADDING * 2;
		int left = Math.max(TOOLTIP_MARGIN, Math.min(this.btnPaste.x, this.width - boxWidth - TOOLTIP_MARGIN));
		int top = Math.max(TOOLTIP_MARGIN, this.btnPaste.y - boxHeight - TOOLTIP_MARGIN);

		this.fill(left, top, left + boxWidth, top + boxHeight, TOOLTIP_BACKGROUND);

		int line = top + TOOLTIP_PADDING;
		this.drawStringWithShadow(this.textRenderer, what, left + TOOLTIP_PADDING, line, TOOLTIP_TEXT);
		if (why != null) {
			this.drawStringWithShadow(this.textRenderer, why,
					left + TOOLTIP_PADDING, line + TOOLTIP_LINE_HEIGHT, TOOLTIP_REASON);
		}
	}

	/**
	 * Whether the mouse is over the paste button. Worked out here rather than asked of the button:
	 * vanilla keeps a button's width and height to itself, and its own answer is about whether a
	 * click would land - which for a button that spends most of its life greyed out is the opposite
	 * of the question. A tooltip is most wanted on the one that cannot be pressed.
	 */
	private boolean isOverPaste(int mouseX, int mouseY) {
		return this.btnPaste != null && this.btnPaste.visible
				&& mouseX >= PASTE_X && mouseX < PASTE_X + PASTE_WIDTH
				&& mouseY >= this.btnPaste.y && mouseY < this.btnPaste.y + PASTE_HEIGHT;
	}
}
