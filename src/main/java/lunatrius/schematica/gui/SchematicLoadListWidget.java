package lunatrius.schematica.gui;

import java.util.List;
import java.util.Locale;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.SchematicaState;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.Tessellator;
import org.lwjgl.opengl.GL11;

/** The scrolling file list inside {@link SchematicLoadScreen}, ending above its two rows of buttons. */
class SchematicLoadListWidget extends EntryListWidget {
	private static final int ENTRY_HEIGHT = 36;
	private static final int ICON_SIZE = 32;
	private static final String ICON_TEXTURE = "/gui/unknown_pack.png";

	private final SchematicaState state = Schematica.STATE;
	private final SchematicLoadScreen parent;

	SchematicLoadListWidget(SchematicLoadScreen parent) {
		super(Schematica.getMinecraft(), parent.width, parent.height, 32, parent.height - 79 + 4, ENTRY_HEIGHT);
		this.parent = parent;
	}

	private List<String> entries() {
		return this.parent.getEntries();
	}

	@Override
	protected int getEntryCount() {
		return this.entries().size();
	}

	@Override
	protected void entryClicked(int index, boolean doubleClick) {
		this.state.selectedSchematic = index;
	}

	@Override
	protected boolean isSelectedEntry(int index) {
		return index == this.state.selectedSchematic;
	}

	@Override
	protected int getEntriesHeight() {
		return this.getEntryCount() * ENTRY_HEIGHT;
	}

	@Override
	protected void renderBackground() {
		this.parent.renderBackground();
	}

	@Override
	protected void renderEntry(int index, int x, int y, int entryHeight, Tessellator tessellator) {
		List<String> entries = this.entries();
		if (index < 0 || index >= entries.size()) {
			return;
		}

		String name = entries.get(index);
		if (name.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
			name = name.substring(0, name.length() - ".schematic".length());
		}

		int textureId = Schematica.getMinecraft().textureManager.getTextureId(ICON_TEXTURE);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		tessellator.startQuads();
		tessellator.color(0xFFFFFF);
		tessellator.vertex(x, y + entryHeight, 0.0, 0.0, 1.0);
		tessellator.vertex(x + ICON_SIZE, y + entryHeight, 0.0, 1.0, 1.0);
		tessellator.vertex(x + ICON_SIZE, y, 0.0, 1.0, 0.0);
		tessellator.vertex(x, y, 0.0, 0.0, 0.0);
		tessellator.draw();

		this.parent.drawStringWithShadow(Schematica.getMinecraft().textRenderer, name, x + ICON_SIZE + 2, y + 1, 0xFFFFFF);
	}
}
