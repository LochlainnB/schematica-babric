package lunatrius.schematica.gui;

import lunatrius.schematica.Schematica;
import net.minecraft.class_583;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Base for the two screens that list blocks with their icons: the blocks a schematic is made of,
 * and the blocks one of them could be swapped for.
 *
 * <p>They are one screen's worth of ideas seen twice - the same rows, the same icons, the same
 * scrolling - so what they share lives here and what differs is the handful of methods below. The
 * pattern is {@link AxisScreen}'s: a base that owns the widget and the wiring, and subclasses that
 * only say what goes in it.
 *
 * <p>Where the mouse is is kept for the length of a frame, because a row is drawn and clicked from
 * inside the list's own draw call and both want to know.
 */
public abstract class BlockListScreen extends Screen {
	/** Below the title, with room for a line of column headings under it. */
	protected static final int LIST_TOP = 40;
	protected static final int ROW_HEIGHT = 20;
	protected static final int ICON_SIZE = 16;
	/** Where a row's text starts, past its icon. */
	protected static final int NAME_LEFT = ICON_SIZE + 4;

	protected static final int TITLE_COLOUR = 0xFFFFFF;
	protected static final int HEADING_COLOUR = 0xA0A0A0;
	protected static final int NAME_COLOUR = 0xFFFFFF;
	protected static final int COUNT_COLOUR = 0xA0A0A0;
	protected static final int FOOTER_COLOUR = 0x808080;

	/** Item icons are drawn the way the hotbar draws them, which needs the gui light rig. */
	private static final ItemRenderer ITEMS = new ItemRenderer();

	private BlockListWidget list;

	/** Where the mouse was when this frame began, for the rows drawn part way through it. */
	private int mouseX;
	private int mouseY;

	/** How many rows there are. */
	protected abstract int rowCount();

	/** Draws one row. {@code x} is its left edge and {@code height} the room inside it. */
	protected abstract void renderRow(int index, int x, int y, int height);

	/** A row was clicked. Where in the row is {@link #getMouseX()}. */
	protected abstract void rowClicked(int index);

	/** The line above the list. */
	protected abstract String title();

	/** How much of the bottom of the screen the buttons and the footer line need. */
	protected abstract int footerHeight();

	/** Lays out the screen's own buttons and returns the first id it did not use. */
	protected abstract int initControls();

	/** Handles a click on one of those buttons; false leaves it to the list's scroll buttons. */
	protected abstract boolean controlClicked(ButtonWidget button);

	/** Anything drawn over the list: headings, footers, a line saying the list is empty. */
	protected abstract void renderContents();

	@Override
	public void init() {
		int nextId = this.initControls();
		this.list = new BlockListWidget(this, LIST_TOP, this.listBottom(), ROW_HEIGHT);
		this.list.registerButtons(this.buttons, nextId, nextId + 1);
	}

	protected int listBottom() {
		return this.height - this.footerHeight();
	}

	protected int getMouseX() {
		return this.mouseX;
	}

	protected int getMouseY() {
		return this.mouseY;
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (!button.active) {
			return;
		}
		if (!this.controlClicked(button)) {
			this.list.buttonClicked(button);
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		// Before the list draws, since the list is what calls back into the rows.
		this.mouseX = mouseX;
		this.mouseY = mouseY;

		this.list.render(mouseX, mouseY, delta);
		this.drawCenteredTextWithShadow(this.textRenderer, this.title(), this.width / 2, 12, TITLE_COLOUR);
		this.renderContents();
		super.render(mouseX, mouseY, delta);
	}

	/** Adds a button and hands it back, which vanilla's own list of them does not. */
	protected ButtonWidget addButton(int id, int x, int y, int width, int height, String text) {
		ButtonWidget button = new ButtonWidget(id, x, y, width, height, text);
		this.buttons.add(button);
		return button;
	}

	/** Where a row starts, which is where the column headings line up against. */
	protected int rowLeft() {
		return this.width / 2 - 92 - 16;
	}

	/** Draws text ending at the given x, which is how a column of numbers lines up. */
	protected void drawRight(String text, int right, int y, int colour) {
		this.drawStringWithShadow(this.textRenderer, text, right - this.textRenderer.getWidth(text), y, colour);
	}

	/** One item icon, drawn the size and the way the inventory draws them. */
	protected void drawIcon(ItemStack stack, int x, int y) {
		Minecraft minecraft = Schematica.getMinecraft();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glPushMatrix();
		GL11.glRotatef(120.0F, 1.0F, 0.0F, 0.0F);
		class_583.method_1930();
		GL11.glPopMatrix();
		ITEMS.method_1487(minecraft.textRenderer, minecraft.textureManager, stack, x, y);
		class_583.method_1927();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}
}
