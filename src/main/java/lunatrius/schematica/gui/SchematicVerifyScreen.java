package lunatrius.schematica.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lunatrius.schematica.OpenSchematic;
import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicVerify;
import lunatrius.schematica.util.Translations;
import lunatrius.schematica.util.Vec3i;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * What is wrong with a build: every block the schematic asks for that is not standing, every one
 * standing in the wrong place or the wrong way round, and everything in the way of the rest.
 *
 * <p>{@link SchematicVerify} is where the answer comes from and where the reasoning about it lives.
 * This is the list of it, on the same rows and icons the replace screen uses, with a second line
 * under each saying what is wrong there and where the nearest one of them is.
 *
 * <p>The check is made when the screen opens and again when <b>Check again</b> is clicked, and not
 * on any frame in between. It is a walk of every cell of the schematic against every cell of the
 * world under it, which for a large one is a million reads - fine once, and not something to be
 * doing sixty times a second behind a list nobody is scrolling.
 *
 * <p>That is also why there is a button for it rather than a number that keeps up on its own: the
 * useful thing about this list is that it holds still while you read it, note what is missing, and
 * go and fix it.
 */
public class SchematicVerifyScreen extends BlockListScreen {
	/** Room for two lines of text beside a 16-pixel icon. */
	private static final int ENTRY_HEIGHT = 24;
	/** How far under the first line of a row the second one sits. */
	private static final int SECOND_LINE = 10;

	/** The footer: the summary, a line for anything that could not be checked, and two buttons. */
	private static final int FOOTER_HEIGHT = 84;

	/** Where a row's count ends, measured from its left edge. */
	private static final int COUNT_RIGHT = 214;

	/** Everything standing where it should be, in the green the material list uses for a done row. */
	private static final int MATCHES_COLOUR = 0x55FF55;

	private final Screen parent;
	private final OpenSchematic target;

	private SchematicVerify report;
	private ButtonWidget btnRecheck;
	private ButtonWidget btnDone;

	public SchematicVerifyScreen(Screen parent, OpenSchematic target) {
		this.parent = parent;
		this.target = target;
		this.check();
	}

	/**
	 * Holds the schematic up against the world again.
	 *
	 * <p>Called when the screen is made and from the button, rather than from {@code init()}, which
	 * runs again on every resize - where nothing about either the schematic or the world has
	 * changed, and the answer would be a million reads to arrive at the list already on the screen.
	 *
	 * <p>Rows measure their nearest cell against the player, so what a row offers to walk to is the
	 * nearest one of it when the check was made. Without a player - which only a test is - the
	 * schematic's own corner stands in, and the row keeps whichever cell of it comes first.
	 */
	private void check() {
		Vec3i near = this.target.offset;
		int nearX = near.x;
		int nearY = near.y;
		int nearZ = near.z;

		if (Schematica.getMinecraft() != null && Schematica.getMinecraft().player != null) {
			nearX = (int) Math.floor(Schematica.getMinecraft().player.x);
			nearY = (int) Math.floor(Schematica.getMinecraft().player.y);
			nearZ = (int) Math.floor(Schematica.getMinecraft().player.z);
		}

		this.report = SchematicVerify.of(
				Schematica.getMinecraft() == null ? null : Schematica.getMinecraft().world,
				this.target, nearX, nearY, nearZ);
	}

	/** The report on the screen, which is what a test asks about rather than the pixels. */
	public SchematicVerify getReport() {
		return this.report;
	}

	@Override
	protected int rowHeight() {
		return ENTRY_HEIGHT;
	}

	@Override
	protected int initControls() {
		this.btnRecheck = this.addButton(0, this.width / 2 - 100, this.height - 52, 200, 20,
				Translations.get("schematic.verify.recheck"));
		this.btnDone = this.addButton(1, this.width / 2 - 100, this.height - 28, 200, 20,
				Translations.get("schematic.done"));
		return 2;
	}

	@Override
	protected boolean controlClicked(ButtonWidget button) {
		if (button == this.btnRecheck) {
			this.check();
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
		return Translations.get("schematic.verify.title");
	}

	@Override
	protected int footerHeight() {
		return FOOTER_HEIGHT;
	}

	@Override
	protected int rowCount() {
		return this.report.getEntries().size();
	}

	@Override
	protected void renderRow(int index, int x, int y, int height) {
		List<SchematicVerify.Entry> entries = this.report.getEntries();
		if (index < 0 || index >= entries.size()) {
			return;
		}

		SchematicVerify.Entry entry = entries.get(index);
		int text = y + 2;

		this.drawIcon(entry.getStack(), x, y + (height - ICON_SIZE) / 2);
		this.drawStringWithShadow(this.textRenderer, entry.getName(), x + NAME_LEFT, text, NAME_COLOUR);
		this.drawRight(count(entry.getCount()), x + COUNT_RIGHT, text, COUNT_COLOUR);

		// What is wrong here and where to go and see it, in the colour of the box the overlay draws
		// on it - so the row and the thing it is about are the same colour before you have read
		// either of them.
		this.drawStringWithShadow(this.textRenderer, describe(entry), x + NAME_LEFT, text + SECOND_LINE,
				entry.getIssue().colour());
	}

	/**
	 * A row's second line: what is wrong, what is there instead where that is worth saying, and the
	 * nearest cell it is wrong in.
	 */
	private static String describe(SchematicVerify.Entry entry) {
		StringBuilder line = new StringBuilder(entry.getIssue().label());

		String found = entry.getFoundName();
		if (found != null) {
			line.append(" - ").append(found);
		}

		Vec3i at = entry.getExample();
		return line.append(' ').append(Translations.get("schematic.verify.at")).append(' ')
				.append(at.x).append(", ").append(at.y).append(", ").append(at.z)
				.toString();
	}

	/** Nothing is clicked on this screen: every row is something to go and look at in the world. */
	@Override
	protected void rowClicked(int index) {
	}

	@Override
	protected void renderContents() {
		if (this.report.isEmpty()) {
			this.drawCenteredTextWithShadow(this.textRenderer, this.emptyMessage(), this.width / 2,
					(LIST_TOP + this.listBottom()) / 2 - 4,
					this.report.getChecked() > 0 ? MATCHES_COLOUR : HEADING_COLOUR);
		} else {
			int left = this.rowLeft();
			int y = LIST_TOP - 12;
			this.drawStringWithShadow(this.textRenderer, Translations.get("schematic.replace.block"),
					left + NAME_LEFT, y, HEADING_COLOUR);
			this.drawRight(Translations.get("schematic.replace.count"), left + COUNT_RIGHT, y, HEADING_COLOUR);
		}

		this.drawCenteredTextWithShadow(this.textRenderer, this.summary(), this.width / 2,
				this.listBottom() + 8, FOOTER_COLOUR);

		String skipped = this.skipped();
		if (!skipped.isEmpty()) {
			this.drawCenteredTextWithShadow(this.textRenderer, skipped, this.width / 2,
					this.listBottom() + 19, FOOTER_COLOUR);
		}
	}

	/** How much was looked at, and how much of it was wrong. */
	private String summary() {
		if (this.target.isEmpty()) {
			return "";
		}

		int faults = this.report.getFaults();
		return Translations.get("schematic.verify.checked") + " " + count(this.report.getChecked())
				+ " - " + (faults == 0
						? Translations.get("schematic.verify.matches")
						: count(faults) + " " + Translations.get("schematic.verify.faults"));
	}

	/**
	 * What was left out, and why - normally nothing at all.
	 *
	 * <p>Worth a line of its own rather than being folded into the count above it, because a cell
	 * that was not checked is not a cell that was found to be right. A build being verified from
	 * across the map is mostly chunks the client has never been sent, and a report that quietly
	 * called all of that a match would be the most confident wrong answer this screen could give.
	 */
	private String skipped() {
		List<String> parts = new ArrayList<>();
		if (this.report.getUnloaded() > 0) {
			parts.add(count(this.report.getUnloaded()) + " " + Translations.get("schematic.verify.unloaded"));
		}
		if (this.report.getOffWorld() > 0) {
			parts.add(count(this.report.getOffWorld()) + " " + Translations.get("schematic.verify.offworld"));
		}
		if (this.report.getUnknown() > 0) {
			parts.add(count(this.report.getUnknown()) + " " + Translations.get("schematic.verify.unknown"));
		}
		return String.join("; ", parts);
	}

	/**
	 * The line in the middle of an empty list. Three things make one, and they are not the same
	 * news: nothing is open, what is open is empty, or the build is finished.
	 */
	private String emptyMessage() {
		if (this.target.isEmpty()) {
			return Translations.get("schematic.materials.none");
		}
		if (this.report.getChecked() == 0) {
			return Translations.get("schematic.verify.nothing");
		}
		return Translations.get("schematic.verify.ok");
	}

	static String count(int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
