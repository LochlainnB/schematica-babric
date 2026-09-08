package lunatrius.schematica.gui;

import java.io.File;
import java.util.Locale;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

/**
 * Picks the two corners of a region and writes it out as a {@code .schematic}.
 *
 * <p>Like the control screen this draws no background, so the green selection box stays visible
 * while the corners are nudged.
 */
public class SchematicSaveScreen extends AxisScreen {
	private static final int MAX_FILENAME_LENGTH = 32;

	/** Narrower than the move screen: two of these rows have to fit side by side. */
	private static final int FIELD_WIDTH = 40;
	private static final int BUTTON_WIDTH = 25;
	private static final int COLUMN_GAP = 18;

	private final SchematicaState state = Schematica.STATE;

	private int centerX;
	private int centerY;

	private final AxisControls axisAX = new AxisControls(this.state.pointA, AxisControls.X);
	private final AxisControls axisAY = new AxisControls(this.state.pointA, AxisControls.Y);
	private final AxisControls axisAZ = new AxisControls(this.state.pointA, AxisControls.Z);
	private final AxisControls axisBX = new AxisControls(this.state.pointB, AxisControls.X);
	private final AxisControls axisBY = new AxisControls(this.state.pointB, AxisControls.Y);
	private final AxisControls axisBZ = new AxisControls(this.state.pointB, AxisControls.Z);

	private ButtonWidget btnPointA;
	private ButtonWidget btnPointB;
	private ButtonWidget btnEnable;
	private ButtonWidget btnSave;
	private TextFieldWidget tfFilename;

	private String filename = "";
	private String status = "";

	@Override
	public void init() {
		this.centerX = this.width / 2;
		this.centerY = this.height / 2;

		int rowWidth = AxisControls.width(FIELD_WIDTH, BUTTON_WIDTH);
		int left = this.centerX - rowWidth - COLUMN_GAP / 2;
		int right = this.centerX + COLUMN_GAP / 2;

		int id = 0;
		this.btnPointA = this.addButton(id++, left, this.centerY - 55, rowWidth, 20, Translations.get("schematic.point.red"));
		id = this.addAxis(this.axisAX, id, left, this.centerY - 30, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisAY, id, left, this.centerY - 5, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisAZ, id, left, this.centerY + 20, FIELD_WIDTH, BUTTON_WIDTH);

		this.btnPointB = this.addButton(id++, right, this.centerY - 55, rowWidth, 20, Translations.get("schematic.point.blue"));
		id = this.addAxis(this.axisBX, id, right, this.centerY - 30, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisBY, id, right, this.centerY - 5, FIELD_WIDTH, BUTTON_WIDTH);
		id = this.addAxis(this.axisBZ, id, right, this.centerY + 20, FIELD_WIDTH, BUTTON_WIDTH);

		this.btnEnable = this.addButton(id++, this.width - 210, this.height - 30, 50, 20, this.enableButtonLabel());
		this.btnSave = this.addButton(id, this.width - 50, this.height - 30, 40, 20, Translations.get("schematic.save"));
		this.btnSave.active = this.state.isRenderingGuide;

		this.tfFilename = new TextFieldWidget(this, this.textRenderer, this.width - 155, this.height - 29, 100, 18, "");
		this.tfFilename.setMaxLength(MAX_FILENAME_LENGTH);
		this.tfFilename.setText(this.filename);
	}

	private String enableButtonLabel() {
		return Translations.get(this.state.isRenderingGuide ? "schematic.disable" : "schematic.enable");
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}

		if (this.axisClicked(button)) {
			return;
		}

		if (button == this.btnPointA) {
			this.state.moveHere(this.state.pointA);
			this.state.updatePoints();
		} else if (button == this.btnPointB) {
			this.state.moveHere(this.state.pointB);
			this.state.updatePoints();
		} else if (button == this.btnEnable) {
			this.state.isRenderingGuide = !this.state.isRenderingGuide;
			this.btnEnable.text = this.enableButtonLabel();
			this.btnSave.active = this.state.isRenderingGuide;
			// Nothing to invalidate: the guide is a dozen edges drawn straight every frame, and the
			// cached geometry it used to be lumped in with belongs to the schematics instead.
		} else if (button == this.btnSave) {
			this.save();
		}
	}

	/** Both corners feed the green guide box, so any change to one has to be folded back in. */
	@Override
	protected void axisChanged() {
		this.state.updatePoints();
	}

	private void save() {
		String name = sanitize(this.tfFilename.getText());
		if (name.isEmpty()) {
			this.status = Translations.get("schematic.save.noname");
			return;
		}

		File file = new File(this.state.getSchematicDirectory(), name + ".schematic");
		if (this.state.saveSchematic(file, this.state.pointMin, this.state.pointMax)) {
			this.filename = "";
			this.tfFilename.setText(this.filename);
			this.status = Translations.get("schematic.save.done") + " " + file.getName();
		} else {
			this.status = Translations.get("schematic.save.failed");
		}
	}

	/** Strips anything that would let a file name escape the schematics folder. */
	private static String sanitize(String name) {
		StringBuilder builder = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') {
				builder.append(c);
			}
		}

		String result = builder.toString().trim();
		if (result.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
			result = result.substring(0, result.length() - ".schematic".length()).trim();
		}
		return result;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		this.tfFilename.mouseClicked(mouseX, mouseY, button);
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		// Only one field can hold the focus, so this never competes with the coordinate rows - bar
		// escape, which closes the screen from either.
		if (this.tfFilename.focused && keyCode != Keyboard.KEY_ESCAPE) {
			this.tfFilename.keyPressed(character, keyCode);
			this.filename = this.tfFilename.getText();
			return;
		}
		super.keyPressed(character, keyCode);
	}

	/** The file name is part of the tab order too, and has to let go when a row takes over. */
	@Override
	protected void clearFocus() {
		super.clearFocus();
		this.tfFilename.setFocused(false);
	}

	@Override
	public void tick() {
		this.tfFilename.tick();
		super.tick();
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.drawStringWithShadow(this.textRenderer, Translations.get("schematic.saveselection"), this.width - 205, this.height - 45, 0xFFFFFF);

		this.drawCenteredTextWithShadow(this.textRenderer, this.selectionSizeLabel(), this.centerX, this.centerY + 48, 0xA0A0A0);
		// The hint has said its piece once a save has been attempted.
		String footer = this.status.isEmpty() ? Translations.get("schematic.coords.hint") : this.status;
		this.drawCenteredTextWithShadow(this.textRenderer, footer, this.centerX, this.centerY + 60, 0xA0A0A0);

		this.renderAxes();
		this.tfFilename.render();
		super.render(mouseX, mouseY, delta);
	}

	private String selectionSizeLabel() {
		Vec3i min = this.state.pointMin;
		Vec3i max = this.state.pointMax;
		return (max.x - min.x + 1) + " x " + (max.y - min.y + 1) + " x " + (max.z - min.z + 1);
	}
}
