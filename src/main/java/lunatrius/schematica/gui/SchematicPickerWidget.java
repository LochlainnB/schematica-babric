package lunatrius.schematica.gui;

import java.util.List;
import java.util.Locale;

import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.SchematicaState;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * The picker at the top of the move screen: which of the open schematics everything else on that
 * screen is pointed at.
 *
 * <p>A button that drops a list under itself, because Beta has no such widget and the alternative -
 * a button that steps to the next schematic - makes you click through the others to reach the one
 * you want and never shows you what you have open. The list does both jobs at once: it is the only
 * place the whole set is written down, so it says what is open, which one the controls are on, and
 * which of them are hidden, in the same glance that changes it.
 *
 * <p>Drawn after the rest of the screen so it lies over the controls it covers, and clicks are
 * offered to it before them for the same reason.
 */
class SchematicPickerWidget extends ButtonWidget {
	/** A row of the dropped list, and where the text sits inside one. */
	private static final int ROW_HEIGHT = 12;
	private static final int ROW_TEXT_OFFSET = 2;
	private static final int TEXT_INSET = 4;

	private static final int LIST_BACKGROUND = 0xE0100010;
	private static final int LIST_BORDER = 0xFF505070;
	private static final int ROW_HOVERED = 0x60FFFFFF;
	private static final int TEXT = 0xFFFFFF;
	/** The one the controls are on, told apart by colour rather than by a mark taking up a column. */
	private static final int TEXT_ACTIVE = 0xFFFF66;
	/** A schematic that is open but not being drawn, in the grey a dead button is drawn in. */
	private static final int TEXT_HIDDEN = 0xA0A0A0;

	private final SchematicaState state;
	private boolean expanded = false;

	SchematicPickerWidget(int id, int x, int y, int width, SchematicaState state) {
		super(id, x, y, width, 20, "");
		this.state = state;
	}

	boolean isExpanded() {
		return this.expanded;
	}

	void toggle() {
		this.expanded = !this.expanded;
	}

	void collapse() {
		this.expanded = false;
	}

	/** Whether a click landed on the button itself rather than on the list under it. */
	boolean isOverButton(int mouseX, int mouseY) {
		return this.visible
				&& mouseX >= this.x && mouseX < this.x + this.width
				&& mouseY >= this.y && mouseY < this.y + this.height;
	}

	/** Which schematic a click on the dropped list picked, or -1 for anywhere else. */
	int rowAt(int mouseX, int mouseY) {
		if (!this.expanded || !this.visible) {
			return -1;
		}

		int rows = this.state.getOpenCount();
		int top = this.listTop();
		if (mouseX < this.x || mouseX >= this.x + this.width
				|| mouseY < top || mouseY >= top + rows * ROW_HEIGHT) {
			return -1;
		}

		return (mouseY - top) / ROW_HEIGHT;
	}

	/** Keeps the button's own face saying what the controls are pointed at. */
	void updateLabel(TextRenderer font) {
		String name = label(this.state.getActive());
		if (this.state.getOpenCount() > 1) {
			name = name + " (" + (this.state.getActiveIndex() + 1) + "/" + this.state.getOpenCount() + ")";
		}
		this.text = fit(font, name, this.width - TEXT_INSET * 2);
	}

	/**
	 * Draws the list under the button. Called by the screen after everything else, since a list that
	 * lies under the buttons it covers is worse than no list at all.
	 */
	void renderList(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.expanded || !this.visible) {
			return;
		}

		List<OpenSchematic> open = this.state.getOpen();
		int top = this.listTop();
		int bottom = top + open.size() * ROW_HEIGHT;

		this.fill(this.x - 1, top - 1, this.x + this.width + 1, bottom + 1, LIST_BORDER);
		this.fill(this.x, top, this.x + this.width, bottom, LIST_BACKGROUND);

		TextRenderer font = minecraft.textRenderer;
		int hovered = this.rowAt(mouseX, mouseY);
		for (int i = 0; i < open.size(); i++) {
			OpenSchematic schematic = open.get(i);
			int rowTop = top + i * ROW_HEIGHT;
			if (i == hovered) {
				this.fill(this.x, rowTop, this.x + this.width, rowTop + ROW_HEIGHT, ROW_HOVERED);
			}

			int colour = TEXT;
			if (i == this.state.getActiveIndex()) {
				colour = TEXT_ACTIVE;
			} else if (schematic.isEmpty() || !schematic.isRenderingSchematic) {
				colour = TEXT_HIDDEN;
			}

			// Numbered, because the same file can be open twice - a build being lined up against a
			// copy of itself - and two rows reading the same name are otherwise nothing to choose
			// between. The number is the one the button counts on its own face.
			String row = (i + 1) + ". " + label(schematic);
			this.drawStringWithShadow(font, fit(font, row, this.width - TEXT_INSET * 2),
					this.x + TEXT_INSET, rowTop + ROW_TEXT_OFFSET, colour);
		}
	}

	private int listTop() {
		return this.y + this.height;
	}

	/** What one schematic is called on screen: its file name without the part every file shares. */
	private static String label(OpenSchematic schematic) {
		String name = schematic.getLoadedName();
		if (name == null) {
			return Translations.get("schematic.noschematic");
		}
		if (name.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
			return name.substring(0, name.length() - ".schematic".length());
		}
		return name;
	}

	/**
	 * Cuts a name down to what will fit, from the end. Beta has no ellipsis in its font, so three
	 * full stops stand in for one.
	 */
	private static String fit(TextRenderer font, String text, int width) {
		if (font.getWidth(text) <= width) {
			return text;
		}

		String ellipsis = "...";
		int room = width - font.getWidth(ellipsis);
		if (room <= 0) {
			return ellipsis;
		}

		int length = text.length();
		while (length > 0 && font.getWidth(text.substring(0, length)) > room) {
			length--;
		}
		return text.substring(0, length) + ellipsis;
	}
}
