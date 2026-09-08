package lunatrius.schematica.gui;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import lunatrius.schematica.MaterialList;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.util.Translations;
import net.minecraft.class_564;
import net.minecraft.class_583;
import net.minecraft.client.Minecraft;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.item.ItemRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * What is left to gather, drawn in the corner of the screen while playing.
 *
 * <p>The same list the material list screen shows, kept to the part of it that is still a job: a
 * row leaves the HUD the moment the player is carrying enough of it, and comes back if the stack
 * does. That is the whole point of it - the corner of the screen is worth a shopping list, not a
 * receipt.
 *
 * <p>The order is the screen's own, on what the schematic takes, with the option of turning it
 * round. A row being gathered holds its place until it is finished and goes.
 *
 * <p>It draws from the end of the vanilla HUD rather than from a hook of its own, which is what
 * gets it the gui scale, the F1 toggle and the right place in the draw order for nothing.
 *
 * <p>The rows are worked out once a tick and drawn from as they are. Sorting them per frame would
 * be the same answer sixty times over, and the numbers only move on a tick anyway.
 */
public final class InfoHud extends DrawableHelper {
	private static final InfoHud INSTANCE = new InfoHud();

	/** Item icons are drawn the way the hotbar draws them, which needs the gui light rig. */
	private static final ItemRenderer ITEMS = new ItemRenderer();

	private static final int MARGIN = 4;
	private static final int PADDING = 3;
	private static final int ICON_SIZE = 16;
	/** A row is an icon with a little air around it; a plain line of text is shorter. */
	private static final int ROW_HEIGHT = 18;
	private static final int LINE_HEIGHT = 10;
	private static final int ICON_GAP = 3;
	/** Smallest gap between a name and the number to its right. */
	private static final int COUNT_GAP = 8;

	/** Most rows the HUD will show, however much room there is. */
	private static final int MAX_ROWS = 10;

	/** How far up the hotbar, the hearts and the air bubbles reach, and how far either side. */
	private static final int HOTBAR_HEIGHT = 42;
	private static final int HOTBAR_HALF_WIDTH = 91;

	private static final int BACKGROUND = 0x90101010;
	private static final int TITLE_COLOUR = 0xFFFFFF;
	private static final int TOTAL_COLOUR = 0xA0A0A0;
	private static final int NAME_COLOUR = 0xE0E0E0;
	private static final int COUNT_COLOUR = 0xFFFF55;
	private static final int NOTE_COLOUR = 0xA0A0A0;

	/** Whether there is anything to draw at all, which is not the same as having rows. */
	private boolean showing = false;
	private List<MaterialList.Entry> rows = Collections.emptyList();
	private int totalMissing = 0;

	private InfoHud() {
	}

	/**
	 * Works out what the HUD says, once per client tick.
	 *
	 * <p>Called whether or not a screen is open, so the material list screen's own HUD buttons can
	 * be seen taking effect behind it.
	 */
	public static void tick(Minecraft mc) {
		INSTANCE.update(mc);
	}

	/** Draws it. Called from the end of the vanilla HUD, so F1 hides this along with the rest. */
	public static void render(Minecraft mc) {
		INSTANCE.draw(mc);
	}

	/** What the HUD is showing. The smoke test cannot read a screenshot, so it reads this. */
	public static List<MaterialList.Entry> getRows() {
		return Collections.unmodifiableList(INSTANCE.rows);
	}

	public static boolean isShowing() {
		return INSTANCE.showing;
	}

	private void update(Minecraft mc) {
		this.showing = false;
		this.rows = Collections.emptyList();
		this.totalMissing = 0;

		if (!Schematica.CONFIG.infoHud || mc == null || mc.player == null
				|| Schematica.STATE.getActive().isEmpty()) {
			return;
		}

		Schematica.STATE.countMaterials();
		MaterialList materials = Schematica.STATE.getMaterials();
		if (materials.isEmpty()) {
			// Nothing in the schematic that has to be fetched, so there is no list to show.
			return;
		}

		this.rows = materials.getOutstanding(Schematica.CONFIG.infoHudSort);
		this.totalMissing = materials.getTotalMissing();
		this.showing = true;
	}

	private void draw(Minecraft mc) {
		if (!this.showing || mc == null) {
			return;
		}

		TextRenderer text = mc.textRenderer;
		class_564 scaled = new class_564(mc.options, mc.displayWidth, mc.displayHeight);
		int screenWidth = scaled.method_1857();
		int screenHeight = scaled.method_1858();

		List<MaterialList.Entry> shown =
				this.rows.subList(0, Math.min(this.rows.size(), maxRows(screenHeight)));
		String title = Translations.get("schematic.materials.hud.title");
		String total = count(this.totalMissing);
		// Either the rows that did not fit, or a word to say the list is finished - never both,
		// since a finished list has no rows to leave out.
		String note = null;
		if (this.rows.isEmpty()) {
			note = Translations.get("schematic.materials.hud.done");
		} else if (shown.size() < this.rows.size()) {
			note = "+" + (this.rows.size() - shown.size()) + " "
					+ Translations.get("schematic.materials.hud.more");
		}

		int width = this.contentWidth(text, shown, title, total, note);
		int height = LINE_HEIGHT + shown.size() * ROW_HEIGHT + (note == null ? 0 : LINE_HEIGHT);

		int right = screenWidth - MARGIN;
		int left = Math.max(MARGIN, right - width - PADDING * 2);
		int bottom = screenHeight - MARGIN;
		if (left < screenWidth / 2 + HOTBAR_HALF_WIDTH + MARGIN) {
			// Wide enough to reach back over the hotbar and the hearts, so it sits above them
			// instead. Narrow lists stay in the very corner, which is where they are least in the way.
			bottom = screenHeight - HOTBAR_HEIGHT;
		}
		int top = bottom - height - PADDING * 2;

		this.fill(left, top, right, bottom, BACKGROUND);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		int contentLeft = left + PADDING;
		int contentRight = right - PADDING;
		int firstRow = top + PADDING + LINE_HEIGHT;

		// Every icon in one pass: the light rig they are drawn under would colour the text as well,
		// which is why the vanilla hotbar draws its stack counts outside it too.
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glPushMatrix();
		GL11.glRotatef(120.0F, 1.0F, 0.0F, 0.0F);
		class_583.method_1930();
		GL11.glPopMatrix();
		for (int i = 0; i < shown.size(); i++) {
			ITEMS.method_1487(text, mc.textureManager, shown.get(i).getStack(),
					contentLeft, firstRow + i * ROW_HEIGHT + (ROW_HEIGHT - ICON_SIZE) / 2);
		}
		class_583.method_1927();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		this.drawStringWithShadow(text, title, contentLeft, top + PADDING, TITLE_COLOUR);
		this.drawRight(text, total, contentRight, top + PADDING, TOTAL_COLOUR);

		for (int i = 0; i < shown.size(); i++) {
			MaterialList.Entry entry = shown.get(i);
			int line = firstRow + i * ROW_HEIGHT + (ROW_HEIGHT - 8) / 2;
			this.drawStringWithShadow(
					text, entry.getName(), contentLeft + ICON_SIZE + ICON_GAP, line, NAME_COLOUR);
			this.drawRight(text, count(entry.getMissing()), contentRight, line, COUNT_COLOUR);
		}

		if (note != null) {
			this.drawStringWithShadow(
					text, note, contentLeft, firstRow + shown.size() * ROW_HEIGHT, NOTE_COLOUR);
		}
	}

	/** How wide the widest thing in the box is, before the padding either side of it. */
	private int contentWidth(TextRenderer text, List<MaterialList.Entry> shown,
			String title, String total, String note) {
		int names = 0;
		int counts = 0;
		for (MaterialList.Entry entry : shown) {
			names = Math.max(names, text.getWidth(entry.getName()));
			counts = Math.max(counts, text.getWidth(count(entry.getMissing())));
		}

		int width = Math.max(
				ICON_SIZE + ICON_GAP + names + COUNT_GAP + counts,
				text.getWidth(title) + COUNT_GAP + text.getWidth(total));
		return note == null ? width : Math.max(width, text.getWidth(note));
	}

	/** How many rows the window has room for: never more than half of it, never more than ten. */
	private static int maxRows(int screenHeight) {
		int room = (screenHeight / 2 - LINE_HEIGHT - PADDING * 2) / ROW_HEIGHT;
		return Math.max(1, Math.min(MAX_ROWS, room));
	}

	/** Draws text ending at the given x, which is how a column of numbers lines up. */
	private void drawRight(TextRenderer text, String string, int right, int y, int colour) {
		this.drawStringWithShadow(text, string, right - text.getWidth(string), y, colour);
	}

	private static String count(int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
