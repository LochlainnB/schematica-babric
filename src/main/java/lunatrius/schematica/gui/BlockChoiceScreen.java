package lunatrius.schematica.gui;

import java.util.List;

import lunatrius.schematica.BlockPalette;
import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.util.Translations;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Everything one block could be swapped for without the schematic ceasing to be what it is.
 *
 * <p>The list is not "every block in the game" but the ones that would leave the build standing:
 * the same shape, and metadata that means the same thing to both, so what is in the file survives
 * the swap. Stairs may become other stairs, a full cube may become any other full cube, and a rail
 * with a bend in it may not become a powered rail, because a powered rail has no bend to be. What
 * that comes to is worked out by {@link lunatrius.schematica.schematic.BlockSwap}, and it is worked
 * out against the metadata this schematic is actually holding rather than against the block in the
 * abstract - so the same block can offer more here than it does in a schematic built differently.
 *
 * <p>Picking one applies it everywhere at once and goes back. There is no confirming and no undo:
 * the schematic on disk has not been touched, so the way back from a colour that looked better in
 * the list is to pick the old one again, or to leave without saving.
 */
public class BlockChoiceScreen extends BlockListScreen {
	/** The footer: Cancel, and nothing else to say. */
	private static final int FOOTER_HEIGHT = 48;

	private final BlockReplaceScreen parent;
	private final BlockPalette.Entry source;
	private final List<BlockPalette.Entry> choices;

	private ButtonWidget btnCancel;

	public BlockChoiceScreen(BlockReplaceScreen parent, BlockPalette.Entry source) {
		this.parent = parent;
		this.source = source;
		// Worked out once. Every row of it is a walk of the block table, and the answer cannot change
		// while this screen is up - nothing but this screen changes the schematic.
		this.choices = source.getReplacements();
	}

	@Override
	protected int initControls() {
		this.btnCancel = this.addButton(0, this.width / 2 - 100, this.height - 28, 200, 20,
				Translations.get("schematic.replace.cancel"));
		return 1;
	}

	@Override
	protected boolean controlClicked(ButtonWidget button) {
		if (button == this.btnCancel) {
			this.minecraft.setScreen(this.parent);
			return true;
		}
		return false;
	}

	@Override
	protected String title() {
		return Translations.get("schematic.replace.pick") + " " + this.source.getName();
	}

	@Override
	protected int footerHeight() {
		return FOOTER_HEIGHT;
	}

	@Override
	protected int rowCount() {
		return this.choices.size();
	}

	@Override
	protected void renderRow(int index, int x, int y, int height) {
		if (index < 0 || index >= this.choices.size()) {
			return;
		}

		BlockPalette.Entry choice = this.choices.get(index);
		this.drawIcon(choice.getStack(), x, y);
		this.drawStringWithShadow(this.textRenderer, choice.getName(),
				x + NAME_LEFT, y + (height - 8) / 2, NAME_COLOUR);
	}

	@Override
	protected void rowClicked(int index) {
		if (index < 0 || index >= this.choices.size()) {
			return;
		}

		OpenSchematic target = this.parent.getTarget();
		int replaced = target.isEmpty()
				? 0
				: BlockPalette.replace(target.schematic.getSchematic(), this.source, this.choices.get(index));
		if (replaced > 0) {
			target.onBlocksReplaced();
		}

		this.parent.blocksReplaced(replaced);
		this.minecraft.setScreen(this.parent);
	}

	@Override
	protected void renderContents() {
		String hint = this.choices.isEmpty()
				? Translations.get("schematic.replace.nochoices")
				: Translations.get("schematic.replace.hint");
		this.drawCenteredTextWithShadow(this.textRenderer, hint, this.width / 2, 26, HEADING_COLOUR);

		this.drawCenteredTextWithShadow(this.textRenderer,
				Translations.get("schematic.replace.affected") + " "
						+ BlockReplaceScreen.count(this.source.getCount()),
				this.width / 2, this.listBottom() + 8, FOOTER_COLOUR);
	}
}
