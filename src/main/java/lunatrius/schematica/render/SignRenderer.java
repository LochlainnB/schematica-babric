package lunatrius.schematica.render;

import lunatrius.schematica.Schematica;
import lunatrius.schematica.schematic.SchematicWorld;
import net.minecraft.block.Block;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.SignModel;
import net.minecraft.client.texture.TextureManager;
import org.lwjgl.opengl.GL11;

/**
 * Sign rendering for the ghost overlay.
 *
 * <p>Vanilla's {@code SignBlockEntityRenderer} always resets the colour to opaque white and draws
 * the text fully opaque, which makes signs pop out of an otherwise translucent schematic. This is
 * the same geometry with the ghost alpha applied to both the board and the text.
 */
public class SignRenderer {
	private final SignModel model = new SignModel();

	public void render(SchematicWorld world, SignBlockEntity sign, float alpha) {
		Minecraft minecraft = Schematica.getMinecraft();
		TextureManager textureManager = BlockEntityRenderDispatcher.INSTANCE.textureManager;
		if (minecraft == null || textureManager == null) {
			return;
		}

		Block block = world.getBlock(sign.x, sign.y, sign.z);
		int metadata = world.method_1778(sign.x, sign.y, sign.z);

		GL11.glPushMatrix();
		float scale = 0.6666667F;

		if (block == Block.STANDING_SIGN) {
			GL11.glTranslatef(sign.x + 0.5F, sign.y + 0.75F * scale, sign.z + 0.5F);
			GL11.glRotatef(-(metadata * 360 / 16.0F), 0.0F, 1.0F, 0.0F);
			this.model.stick.visible = true;
		} else {
			float yaw = 0.0F;
			if (metadata == 2) {
				yaw = 180.0F;
			} else if (metadata == 4) {
				yaw = 90.0F;
			} else if (metadata == 5) {
				yaw = -90.0F;
			}

			GL11.glTranslatef(sign.x + 0.5F, sign.y + 0.75F * scale, sign.z + 0.5F);
			GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
			GL11.glTranslatef(0.0F, -0.3125F, -0.4375F);
			this.model.stick.visible = false;
		}

		textureManager.bindTexture(textureManager.getTextureId("/item/sign.png"));

		GL11.glPushMatrix();
		GL11.glScalef(scale, -scale, -scale);
		this.model.render();
		GL11.glPopMatrix();

		TextRenderer textRenderer = minecraft.textRenderer;
		float textScale = 0.016666668F * scale;
		GL11.glTranslatef(0.0F, 0.5F * scale, 0.07F * scale);
		GL11.glScalef(textScale, -textScale, textScale);
		GL11.glNormal3f(0.0F, 0.0F, -1.0F * textScale);
		GL11.glDepthMask(false);

		int color = ((int) (alpha * 255.0F) & 0xFF) << 24;
		for (int row = 0; row < sign.texts.length; row++) {
			String text = sign.texts[row];
			if (text == null) {
				continue;
			}
			textRenderer.draw(text, -textRenderer.getWidth(text) / 2, row * 10 - sign.texts.length * 5, color);
		}

		GL11.glDepthMask(true);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
		GL11.glPopMatrix();
	}
}
