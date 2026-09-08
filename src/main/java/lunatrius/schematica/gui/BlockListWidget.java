package lunatrius.schematica.gui;

import lunatrius.schematica.Schematica;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.Tessellator;

/**
 * The scrolling list both of the replace screens are built out of.
 *
 * <p>It holds no rows of its own: what a row is and what is in it belongs to the screen, and this
 * is the scrolling, the background and the hit testing that neither screen should have to write
 * twice.
 */
class BlockListWidget extends EntryListWidget {
	private final BlockListScreen parent;

	BlockListWidget(BlockListScreen parent, int top, int bottom, int rowHeight) {
		super(Schematica.getMinecraft(), parent.width, parent.height, top, bottom, rowHeight);
		this.parent = parent;
		// Nothing here stays picked: a click on a row is an action rather than a selection.
		this.method_1260(false);
	}

	@Override
	protected int getEntryCount() {
		return this.parent.rowCount();
	}

	@Override
	protected void entryClicked(int index, boolean doubleClick) {
		this.parent.rowClicked(index);
	}

	@Override
	protected boolean isSelectedEntry(int index) {
		return false;
	}

	@Override
	protected void renderBackground() {
		this.parent.renderBackground();
	}

	@Override
	protected void renderEntry(int index, int x, int y, int entryHeight, Tessellator tessellator) {
		this.parent.renderRow(index, x, y, entryHeight);
	}
}
