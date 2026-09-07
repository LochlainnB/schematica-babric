package lunatrius.schematica.gui;

import java.util.List;

import lunatrius.schematica.MaterialList;
import lunatrius.schematica.Schematica;
import net.minecraft.class_583;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.item.ItemRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** The scrolling list of materials inside {@link MaterialListScreen}. */
class MaterialListWidget extends EntryListWidget {
	private static final int ENTRY_HEIGHT = 20;
	private static final int ICON_SIZE = 16;

	/** Right edge of each column, measured from the left of a row. */
	static final int NAME_LEFT = ICON_SIZE + 4;
	static final int HAVE_RIGHT = 160;
	static final int MISSING_RIGHT = 214;

	/** Item icons are drawn the way the hotbar draws them, which needs the gui light rig. */
	private static final ItemRenderer ITEMS = new ItemRenderer();

	private static final int NAME_COLOUR = 0xFFFFFF;
	private static final int DONE_COLOUR = 0x808080;
	private static final int COUNT_COLOUR = 0xA0A0A0;
	private static final int MISSING_COLOUR = 0xFFFFFF;
	private static final int NOTHING_MISSING_COLOUR = 0x55FF55;

	private final MaterialListScreen parent;

	MaterialListWidget(MaterialListScreen parent, int top, int bottom) {
		super(Schematica.getMinecraft(), parent.width, parent.height, top, bottom, ENTRY_HEIGHT);
		this.parent = parent;
		// Nothing here is picked, so nothing is drawn as picked either.
		this.method_1260(false);
	}

	/** Where a row starts, which is also where the screen lines its column headings up. */
	static int rowLeft(int screenWidth) {
		return screenWidth / 2 - 92 - 16;
	}

	private List<MaterialList.Entry> entries() {
		return this.parent.getMaterials().getEntries();
	}

	@Override
	protected int getEntryCount() {
		return this.entries().size();
	}

	@Override
	protected void entryClicked(int index, boolean doubleClick) {
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
		List<MaterialList.Entry> entries = this.entries();
		if (index < 0 || index >= entries.size()) {
			return;
		}

		MaterialList.Entry entry = entries.get(index);
		Minecraft mc = Schematica.getMinecraft();
		int missing = entry.getMissing();

		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glPushMatrix();
		GL11.glRotatef(120.0F, 1.0F, 0.0F, 0.0F);
		class_583.method_1930();
		GL11.glPopMatrix();
		ITEMS.method_1487(mc.textRenderer, mc.textureManager, entry.getStack(), x, y);
		class_583.method_1927();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		int text = y + (entryHeight - 8) / 2;
		this.parent.drawStringWithShadow(mc.textRenderer, entry.getName(), x + NAME_LEFT, text,
				missing == 0 ? DONE_COLOUR : NAME_COLOUR);
		this.parent.drawRight(entry.getHave() + " / " + entry.getNeeded(), x + HAVE_RIGHT, text, COUNT_COLOUR);
		this.parent.drawRight(Integer.toString(missing), x + MISSING_RIGHT, text,
				missing == 0 ? NOTHING_MISSING_COLOUR : MISSING_COLOUR);
	}
}
