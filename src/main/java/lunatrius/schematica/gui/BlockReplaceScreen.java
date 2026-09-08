package lunatrius.schematica.gui;

import java.util.List;
import java.util.Locale;

import lunatrius.schematica.BlockPalette;
import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Every kind of block one schematic is made of, each with a button that swaps it for another.
 *
 * <p>The list is {@link BlockPalette}'s rather than the material list's, because this screen is
 * about what is in the file and not about what you would carry to build it: a door is one row here
 * and one row there, but here it is the two blocks a door is and there it is the one item that puts
 * them both down, and only the first of those is a thing that can be replaced.
 *
 * <p>Replacing changes the schematic that is open, and the overlay redraws with it - the wall you
 * are stood in front of is cobblestone the moment you pick cobblestone. The file it came from is
 * untouched until Save, which is deliberate: trying colours against a build in the world is the
 * point, and most of what is tried is not kept.
 */
public class BlockReplaceScreen extends BlockListScreen {
	/** The footer: the line that says what happened, then Save, then Done. */
	private static final int FOOTER_HEIGHT = 72;

	/** Where a row's columns sit, measured from its left edge. */
	private static final int COUNT_RIGHT = 148;
	private static final int BUTTON_LEFT = 156;
	private static final int BUTTON_WIDTH = 58;
	private static final int BUTTON_HEIGHT = 16;

	private static final int BUTTON_BORDER = 0xFF000000;
	private static final int BUTTON_FACE = 0xFF6A6A6A;
	private static final int BUTTON_FACE_HOVERED = 0xFF8C8C8C;
	private static final int BUTTON_FACE_DEAD = 0xFF4A4A4A;
	private static final int BUTTON_TEXT = 0xFFFFFF;
	private static final int BUTTON_TEXT_HOVERED = 0xFFFFA0;
	private static final int BUTTON_TEXT_DEAD = 0xA0A0A0;

	private final Screen parent;
	private final OpenSchematic target;

	private BlockPalette palette;
	private ButtonWidget btnSave;
	private ButtonWidget btnDone;

	/** What the last replacement or save did, shown until something else happens. */
	private String status = "";

	public BlockReplaceScreen(Screen parent, OpenSchematic target) {
		this.parent = parent;
		this.target = target;
		this.refresh();
	}

	/**
	 * Counts the schematic again.
	 *
	 * <p>Called when the screen is made and after each replacement, rather than from {@code init()}:
	 * the count is a walk of every block in the schematic, and {@code init()} runs again on every
	 * resize, where nothing about the schematic has changed at all.
	 */
	private void refresh() {
		this.palette = BlockPalette.of(this.target.isEmpty() ? null : this.target.schematic.getSchematic());
	}

	/** Called by the choice screen once it has swapped one block for another. */
	void blocksReplaced(int count) {
		this.refresh();
		this.status = Translations.get("schematic.replace.done") + " " + count(count);
	}

	OpenSchematic getTarget() {
		return this.target;
	}

	@Override
	protected int initControls() {
		this.btnSave = this.addButton(0, this.width / 2 - 100, this.height - 52, 200, 20,
				Translations.get("schematic.replace.save"));
		this.btnDone = this.addButton(1, this.width / 2 - 100, this.height - 28, 200, 20,
				Translations.get("schematic.done"));
		return 2;
	}

	@Override
	protected boolean controlClicked(ButtonWidget button) {
		if (button == this.btnSave) {
			this.status = Translations.get(this.target.save()
					? "schematic.replace.saved"
					: "schematic.replace.savefailed");
			return true;
		}
		if (button == this.btnDone) {
			this.minecraft.setScreen(this.parent);
			return true;
		}
		return false;
	}

	@Override
	protected String title() {
		return Translations.get("schematic.replace.title");
	}

	@Override
	protected int footerHeight() {
		return FOOTER_HEIGHT;
	}

	@Override
	protected int rowCount() {
		return this.palette.getEntries().size();
	}

	@Override
	protected void renderRow(int index, int x, int y, int height) {
		List<BlockPalette.Entry> entries = this.palette.getEntries();
		if (index < 0 || index >= entries.size()) {
			return;
		}

		BlockPalette.Entry entry = entries.get(index);
		int text = y + (height - 8) / 2;

		this.drawIcon(entry.getStack(), x, y);
		this.drawStringWithShadow(this.textRenderer, entry.getName(), x + NAME_LEFT, text, NAME_COLOUR);
		this.drawRight(count(entry.getCount()), x + COUNT_RIGHT, text, COUNT_COLOUR);

		// A row nothing can stand in for keeps its button, greyed out. A row with no button at all
		// would read as a row that had not finished loading.
		this.renderReplaceButton(x + BUTTON_LEFT, y, entry.hasReplacements(), this.isOverButton(x, y, height));
	}

	/**
	 * The button on the end of a row, drawn rather than added.
	 *
	 * <p>Vanilla's buttons are laid out once and live at a fixed place on the screen, which a row
	 * that scrolls is not. There is one of these per row and they come and go with the list, so they
	 * are painted the way the row's text is: from the row's own coordinates, each time it is drawn.
	 */
	private void renderReplaceButton(int left, int top, boolean enabled, boolean hovered) {
		int face = enabled ? (hovered ? BUTTON_FACE_HOVERED : BUTTON_FACE) : BUTTON_FACE_DEAD;
		this.fill(left - 1, top - 1, left + BUTTON_WIDTH + 1, top + BUTTON_HEIGHT + 1, BUTTON_BORDER);
		this.fill(left, top, left + BUTTON_WIDTH, top + BUTTON_HEIGHT, face);

		String label = Translations.get("schematic.replace.button");
		int colour = enabled ? (hovered ? BUTTON_TEXT_HOVERED : BUTTON_TEXT) : BUTTON_TEXT_DEAD;
		this.drawCenteredTextWithShadow(this.textRenderer, label,
				left + BUTTON_WIDTH / 2, top + (BUTTON_HEIGHT - 8) / 2, colour);
	}

	/** Whether the mouse is over the button of the row drawn at this height. */
	private boolean isOverButton(int x, int y, int height) {
		int left = x + BUTTON_LEFT;
		return this.getMouseX() >= left && this.getMouseX() < left + BUTTON_WIDTH
				&& this.getMouseY() >= y && this.getMouseY() < y + height;
	}

	@Override
	protected void rowClicked(int index) {
		List<BlockPalette.Entry> entries = this.palette.getEntries();
		if (index < 0 || index >= entries.size()) {
			return;
		}

		// The row is the block; the button is what asks to change it. Clicking the name does nothing
		// on purpose - there is no other thing a row does, so a click that lands anywhere is a click
		// that changes a build by accident.
		int left = this.rowLeft() + BUTTON_LEFT;
		if (this.getMouseX() < left || this.getMouseX() >= left + BUTTON_WIDTH) {
			return;
		}

		BlockPalette.Entry entry = entries.get(index);
		if (entry.hasReplacements()) {
			this.minecraft.setScreen(new BlockChoiceScreen(this, entry));
		}
	}

	@Override
	protected void renderContents() {
		if (this.palette.isEmpty()) {
			this.drawCenteredTextWithShadow(this.textRenderer, this.emptyMessage(), this.width / 2,
					(LIST_TOP + this.listBottom()) / 2 - 4, HEADING_COLOUR);
		} else {
			int left = this.rowLeft();
			int y = LIST_TOP - 12;
			this.drawStringWithShadow(this.textRenderer, Translations.get("schematic.replace.block"),
					left + NAME_LEFT, y, HEADING_COLOUR);
			this.drawRight(Translations.get("schematic.replace.count"), left + COUNT_RIGHT, y, HEADING_COLOUR);
		}

		this.btnSave.active = this.target.isEdited();
		this.drawCenteredTextWithShadow(this.textRenderer, this.footer(), this.width / 2,
				this.listBottom() + 8, FOOTER_COLOUR);
	}

	/**
	 * What is going on under the list: what the last click did, failing that a word about the
	 * changes not being on disk yet, and failing that which file this is.
	 */
	private String footer() {
		if (!this.status.isEmpty()) {
			return this.status;
		}
		if (this.target.isEdited()) {
			return Translations.get("schematic.replace.unsaved");
		}
		String name = this.target.getLoadedName();
		return name == null ? "" : name;
	}

	private String emptyMessage() {
		return Translations.get(this.target.isEmpty()
				? "schematic.materials.none"
				: "schematic.materials.empty");
	}

	static String count(int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
