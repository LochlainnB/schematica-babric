package lunatrius.schematica.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Base for the screens built out of {@link AxisControls} rows. It owns what they share: handing out
 * button ids, routing clicks and keys to whichever row holds the focus, and the tab order.
 */
public abstract class AxisScreen extends Screen {
	private final List<AxisControls> axes = new ArrayList<>();

	/** Called once a row has changed its value, so the screen can act on it. */
	protected abstract void axisChanged();

	@Override
	public void init(Minecraft minecraft, int width, int height) {
		// init() runs again on every resize, and vanilla clears the button list around it; the rows
		// hand out those same buttons, so they have to be forgotten in step with them.
		this.axes.clear();
		super.init(minecraft, width, height);
	}

	/** Lays a row out and returns the next free button id. */
	protected int addAxis(AxisControls axis, int id, int x, int y, int fieldWidth, int buttonWidth) {
		this.axes.add(axis);
		return axis.build(this, id, x, y, fieldWidth, buttonWidth);
	}

	protected ButtonWidget addButton(int id, int x, int y, int width, int height, String text) {
		ButtonWidget button = new ButtonWidget(id, x, y, width, height, text);
		this.buttons.add(button);
		return button;
	}

	/** Applies a click on a row button; false when the button belongs to the screen itself. */
	protected boolean axisClicked(ButtonWidget button) {
		for (AxisControls axis : this.axes) {
			if (axis.owns(button)) {
				axis.click(button);
				return true;
			}
		}
		return false;
	}

	protected void renderAxes() {
		for (AxisControls axis : this.axes) {
			axis.render();
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		// Before the buttons: clicking away from a field applies what was typed into it, so a click
		// straight onto [+] steps the number the player just entered rather than the old one.
		for (AxisControls axis : this.axes) {
			axis.mouseClicked(mouseX, mouseY, button);
		}
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		for (AxisControls axis : this.axes) {
			if (axis.keyPressed(character, keyCode)) {
				return;
			}
		}
		super.keyPressed(character, keyCode);
	}

	@Override
	public void tick() {
		for (AxisControls axis : this.axes) {
			axis.tick();
		}
		super.tick();
	}

	/** Tab steps through the rows in the order they were laid out, applying each as it leaves. */
	@Override
	public void handleTab() {
		int focused = -1;
		for (int i = 0; i < this.axes.size(); i++) {
			if (this.axes.get(i).isFocused()) {
				focused = i;
				break;
			}
		}

		this.clearFocus();
		if (!this.axes.isEmpty()) {
			this.axes.get((focused + 1) % this.axes.size()).setFocused(true);
		}
	}

	/** Drops the focus from every field; overridden where a screen owns more of them. */
	protected void clearFocus() {
		for (AxisControls axis : this.axes) {
			axis.setFocused(false);
		}
	}

	TextRenderer getTextRenderer() {
		return this.textRenderer;
	}
}
