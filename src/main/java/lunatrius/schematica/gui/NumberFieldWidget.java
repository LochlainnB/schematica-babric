package lunatrius.schematica.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

/**
 * A text field holding a whole number.
 *
 * <p>Beta's field takes any character and hands back a string, so the digits-only rule, the value
 * itself and the "this has not been applied yet" state all live here. The value is committed - and
 * only then handed to the {@link Listener} - on Enter, on Tab and when the focus is lost, so a
 * half-typed coordinate never drags anything across the world.
 */
public class NumberFieldWidget extends TextFieldWidget {
	/** Nine characters covers any coordinate the game can hold, sign included. */
	private static final int MAX_LENGTH = 9;

	/** Room the text has inside the box, matching where the vanilla field starts drawing. */
	private static final int PADDING = 4;

	/** What ctrl-V arrives as, the same character the vanilla field pastes on. */
	private static final int PASTE = 0x16;

	private static final int COLOR_BORDER = 0xFFA0A0A0;
	private static final int COLOR_BACKGROUND = 0xFF000000;
	private static final int COLOR_APPLIED = 0xE0E0E0;
	/** What is on screen is not what is applied yet; the same yellow a hovered button uses. */
	private static final int COLOR_EDITED = 0xFFFFA0;
	private static final int COLOR_DISABLED = 0x707070;

	private final TextRenderer textRenderer;
	private final Listener listener;
	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private int value;
	private int ticks;
	/** The value counts as selected until the first keystroke edits it. */
	private boolean replacing = false;

	public NumberFieldWidget(Screen parent, TextRenderer textRenderer, int x, int y, int width, int height, Listener listener) {
		super(parent, textRenderer, x, y, width, height, "0");
		this.textRenderer = textRenderer;
		this.listener = listener;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.setMaxLength(MAX_LENGTH);
	}

	public int getValue() {
		return this.value;
	}

	/**
	 * Points the field at {@code value}. The text is left alone while the field is being typed into,
	 * so this can be called every frame to follow whatever else moves the value.
	 */
	public void setValue(int value) {
		this.value = value;
		if (!this.focused) {
			this.setText(Integer.toString(value));
		}
	}

	/** Throws away what is being typed and gives up the focus. */
	public void cancel() {
		this.setText(Integer.toString(this.value));
		this.setFocused(false);
	}

	@Override
	public void setFocused(boolean focused) {
		boolean wasFocused = this.focused;
		super.setFocused(focused);

		if (wasFocused && !focused) {
			// Clicking elsewhere is as good as pressing Enter: the number is on screen, so leaving it
			// behind unapplied would just look broken.
			this.commit();
		} else if (focused && !wasFocused) {
			this.ticks = 0;
			this.replacing = true;
		}
	}

	@Override
	public void keyPressed(char character, int keyCode) {
		if (!this.enabled || !this.focused) {
			return;
		}

		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			this.commit();
			return;
		}

		// Beta's field only ever appends, so typing over a value it is already showing would give
		// "12" for a 1 with a 2 typed into it. The first edit clears it instead, which is what
		// clicking a number and typing does everywhere else.
		if (this.replacing && (isNumeric(character) || character == PASTE || keyCode == Keyboard.KEY_BACK)) {
			this.replacing = false;
			this.setText("");
		}

		super.keyPressed(character, keyCode);

		// Vanilla appends anything printable, and its paste path ignores the length limit entirely.
		String filtered = filter(this.getText());
		if (!filtered.equals(this.getText())) {
			this.setText(filtered);
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.ticks++;
	}

	@Override
	public void render() {
		this.fill(this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, COLOR_BORDER);
		this.fill(this.x, this.y, this.x + this.width, this.y + this.height, COLOR_BACKGROUND);

		String text = this.getText();
		int color = COLOR_APPLIED;
		if (!this.enabled) {
			color = COLOR_DISABLED;
		} else if (this.focused) {
			if (!text.equals(Integer.toString(this.value))) {
				color = COLOR_EDITED;
			}
			if (this.ticks / 6 % 2 == 0) {
				text = text + "_";
			}
		}

		this.drawStringWithShadow(this.textRenderer, this.trim(text), this.x + PADDING, this.y + (this.height - 8) / 2, color);
	}

	/**
	 * Drops leading characters until the text fits the box. Beta's field neither scrolls nor clips,
	 * so a long coordinate would otherwise be drawn straight over whatever sits next to it.
	 */
	private String trim(String text) {
		int available = this.width - PADDING * 2;
		String trimmed = text;
		while (trimmed.length() > 1 && this.textRenderer.getWidth(trimmed) > available) {
			trimmed = trimmed.substring(1);
		}
		return trimmed;
	}

	/** Applies the typed number, if it is one, and puts the applied value back on screen. */
	private void commit() {
		// Whatever happens the box ends up showing an applied value again, so the next keystroke
		// types over it rather than onto the end of it.
		this.replacing = true;

		String text = this.getText();
		if (!text.isEmpty() && !text.equals("-")) {
			try {
				int parsed = Integer.parseInt(text);
				if (parsed != this.value) {
					this.value = parsed;
					this.listener.onNumberEntered(this, parsed);
				}
			} catch (NumberFormatException exception) {
				// Only reachable if the length limit ever grows past what an int can hold.
			}
		}
		this.setText(Integer.toString(this.value));
	}

	private static boolean isNumeric(char character) {
		return (character >= '0' && character <= '9') || character == '-';
	}

	/** Keeps the digits, and a minus sign only where a minus sign can go. */
	private static String filter(String text) {
		StringBuilder builder = new StringBuilder(text.length());
		for (int i = 0; i < text.length() && builder.length() < MAX_LENGTH; i++) {
			char character = text.charAt(i);
			if (character >= '0' && character <= '9') {
				builder.append(character);
			} else if (character == '-' && builder.length() == 0) {
				builder.append(character);
			}
		}
		return builder.toString();
	}

	/** Told about a value only once it has been committed, never mid-keystroke. */
	public interface Listener {
		void onNumberEntered(NumberFieldWidget field, int value);
	}
}
