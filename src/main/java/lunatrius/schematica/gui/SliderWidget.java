package lunatrius.schematica.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.opengl.GL11;

/**
 * A 0..1 slider drawn from the vanilla button sheet.
 *
 * <p>Beta's own slider is welded to {@code GameOptions}, so this is the same widget with the value
 * handed back to a listener instead. It reports through {@link Listener} rather than the usual
 * {@code buttonClicked} path because a drag has to update the value on every frame, not on release.
 */
class SliderWidget extends ButtonWidget {
	/** Width of the sliding knob in the gui texture. */
	private static final int KNOB_WIDTH = 8;

	private final Listener listener;
	private float value;
	private boolean dragging = false;

	SliderWidget(int id, int x, int y, int width, float value, Listener listener) {
		super(id, x, y, width, 20, "");
		this.value = clamp(value);
		this.listener = listener;
	}

	float getValue() {
		return this.value;
	}

	private void setValue(float value) {
		float clamped = clamp(value);
		if (clamped == this.value) {
			return;
		}

		this.value = clamped;
		this.listener.onSliderChanged(this);
	}

	@Override
	protected int method_1187(boolean hovered) {
		// The vanilla slider draws the flat, unlit track for every state and puts the knob on top.
		return 0;
	}

	@Override
	protected void method_1188(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}

		if (this.dragging) {
			this.setValue(this.valueAt(mouseX));
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		int knobX = this.x + (int) (this.value * (this.width - KNOB_WIDTH));
		this.drawTexture(knobX, this.y, 0, 66, 4, 20);
		this.drawTexture(knobX + 4, this.y, 196, 66, 4, 20);
	}

	@Override
	public boolean isMouseOver(Minecraft minecraft, int mouseX, int mouseY) {
		if (!super.isMouseOver(minecraft, mouseX, mouseY)) {
			return false;
		}

		this.setValue(this.valueAt(mouseX));
		this.dragging = true;
		return true;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY) {
		this.dragging = false;
	}

	/** The knob is grabbed by its middle, so the usable track is inset by half its width. */
	private float valueAt(int mouseX) {
		return (float) (mouseX - (this.x + KNOB_WIDTH / 2)) / (this.width - KNOB_WIDTH);
	}

	private static float clamp(float value) {
		return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
	}

	interface Listener {
		void onSliderChanged(SliderWidget slider);
	}
}
