package lunatrius.schematica.gui;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

/**
 * One coordinate row: {@code X: [value] [-] [step] [+]}. The middle button cycles through
 * {@link lunatrius.schematica.SchematicaState#increments}, and the value can be typed straight in.
 *
 * <p>The row owns the number it edits - one component of a {@link Vec3i} - so a screen built out of
 * these only has to say where each row goes and what to do once one of them changed.
 */
class AxisControls implements NumberFieldWidget.Listener {
	static final int X = 0;
	static final int Y = 1;
	static final int Z = 2;

	private static final String[] LABEL_KEYS = { "schematic.x", "schematic.y", "schematic.z" };

	/** Room for the "X:" label in front of the field. */
	private static final int LABEL_WIDTH = 12;
	/** Gap between the field and the button group. */
	private static final int GAP = 4;
	/** Gap between the buttons. */
	private static final int SPACING = 5;
	private static final int HEIGHT = 20;

	private final int[] increments = Schematica.STATE.increments;
	private final Vec3i vector;
	private final int axis;

	private AxisScreen screen;
	private TextRenderer textRenderer;
	private NumberFieldWidget field;
	private ButtonWidget decrease;
	private ButtonWidget amount;
	private ButtonWidget increase;
	private int labelX;
	private int labelY;
	private int incrementIndex = 0;

	AxisControls(Vec3i vector, int axis) {
		this.vector = vector;
		this.axis = axis;
	}

	/** How wide a row is, so a screen can work out whether two of them fit side by side. */
	static int width(int fieldWidth, int buttonWidth) {
		return LABEL_WIDTH + fieldWidth + GAP + buttonWidth * 3 + SPACING * 2;
	}

	/** Lays the row out with its top left corner at {@code x, y} and returns the next free id. */
	int build(AxisScreen screen, int id, int x, int y, int fieldWidth, int buttonWidth) {
		this.screen = screen;
		this.textRenderer = screen.getTextRenderer();
		this.labelX = x;
		this.labelY = y + (HEIGHT - 8) / 2;

		// Inset by a pixel because the field paints its border outside its own box, so a 18 high
		// field ends up exactly as tall as the buttons beside it.
		int fieldX = x + LABEL_WIDTH;
		this.field = new NumberFieldWidget(screen, this.textRenderer, fieldX, y + 1, fieldWidth, HEIGHT - 2, this);
		this.field.setValue(this.get());

		int buttonX = fieldX + fieldWidth + GAP;
		int step = buttonWidth + SPACING;
		this.decrease = screen.addButton(id++, buttonX, y, buttonWidth, HEIGHT, Translations.get("schematic.decrease"));
		this.amount = screen.addButton(id++, buttonX + step, y, buttonWidth, HEIGHT, this.stepLabel());
		this.increase = screen.addButton(id++, buttonX + step * 2, y, buttonWidth, HEIGHT, Translations.get("schematic.increase"));
		return id;
	}

	boolean owns(ButtonWidget button) {
		return button == this.decrease || button == this.amount || button == this.increase;
	}

	/** Applies a click on one of this row's buttons. */
	void click(ButtonWidget button) {
		if (button == this.amount) {
			this.incrementIndex = (this.incrementIndex + 1) % this.increments.length;
			this.amount.text = this.stepLabel();
			return;
		}

		int increment = this.increments[this.incrementIndex];
		this.set(this.get() + (button == this.increase ? increment : -increment));
		this.field.setValue(this.get());
		this.screen.axisChanged();
	}

	void mouseClicked(int mouseX, int mouseY, int button) {
		this.field.mouseClicked(mouseX, mouseY, button);
	}

	/** Handles a key when this row holds the focus; false leaves it to the screen. */
	boolean keyPressed(char character, int keyCode) {
		if (!this.field.focused) {
			return false;
		}

		if (keyCode == Keyboard.KEY_ESCAPE) {
			// Give up the field rather than the screen: one press abandons the edit, a second closes
			// the screen the way every other screen does.
			this.field.cancel();
			return true;
		}

		this.field.keyPressed(character, keyCode);
		return true;
	}

	void tick() {
		this.field.tick();
	}

	void render() {
		// Anything else that moved the value - "Move here", a rotation, the other point - lands here.
		this.field.setValue(this.get());
		this.screen.drawStringWithShadow(this.textRenderer, Translations.get(LABEL_KEYS[this.axis]), this.labelX, this.labelY, 0xFFFFFF);
		this.field.render();
	}

	boolean isFocused() {
		return this.field != null && this.field.focused;
	}

	void setFocused(boolean focused) {
		if (this.field != null) {
			this.field.setFocused(focused);
		}
	}

	@Override
	public void onNumberEntered(NumberFieldWidget field, int value) {
		this.set(value);
		this.screen.axisChanged();
	}

	private int get() {
		switch (this.axis) {
			case X: return this.vector.x;
			case Y: return this.vector.y;
			default: return this.vector.z;
		}
	}

	private void set(int value) {
		switch (this.axis) {
			case X: this.vector.x = value; break;
			case Y: this.vector.y = value; break;
			default: this.vector.z = value; break;
		}
	}

	private String stepLabel() {
		return Integer.toString(this.increments[this.incrementIndex]);
	}
}
