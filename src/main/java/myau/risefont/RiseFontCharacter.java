package myau.risefont;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Skidded from Rise (com.alan.clients.util.font.impl.rise.FontCharacter).
 */
public class RiseFontCharacter {
    private final int texture;
    private final float width;
    private final float height;

    public RiseFontCharacter(int texture, float width, float height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    public void render(float x, float y) {
        GlStateManager.bindTexture(this.texture);
        GL11.glBegin(7);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + this.height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + this.width, y + this.height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + this.width, y);
        GL11.glEnd();
    }

    public int getTexture() {
        return this.texture;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }
}
