package myau.mixin;

import myau.risefont.RiseFontManager;
import myau.util.RenderUtil;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

/**
 * Myau in-game pause menu overlay: keeps the original vanilla buttons
 * (200x20 layout) but draws a minimal dark backdrop and title on top.
 * Draws directly on top of GuiIngameMenu without replacing the screen
 * (replacing with a subclass caused infinite displayGuiScreen recursion).
 */
@Mixin(GuiIngameMenu.class)
public abstract class MixinGuiIngameMenu extends GuiScreen {

    private static final Color ACCENT = new Color(59, 130, 246); // ACCENT 0xFF3B82F6

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void myauDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        drawVGradient(0, 0, this.width, this.height, 0xC80B0D13, 0xD004050A);

        float size = 30.0F;
        String title = "Paused";
        float tw = RiseFontManager.getStringWidth(title, size);
        float x0 = this.width / 2.0F - tw / 2.0F;
        float y = this.height / 4.0F - 34.0F;
        RiseFontManager.drawString(title, x0, y - RiseFontManager.getFontHeight(size) / 2.0F,
                rgba(242, 244, 248, 255), false, size); // TEXT_MAIN

        // rounded accent underline under the title
        float uw = tw * 0.8F;
        RenderUtil.drawRoundedRect(this.width / 2.0F - uw / 2.0F, y + size * 0.62F, uw, 2.0F, 1.0F,
                rgba(ACCENT, 160));

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawVGradient(float x1, float y1, float x2, float y2, int c1, int c2) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, c1 >> 24 & 255).endVertex();
        wr.pos(x1, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, c2 >> 24 & 255).endVertex();
        wr.pos(x2, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, c2 >> 24 & 255).endVertex();
        wr.pos(x2, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, c1 >> 24 & 255).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static int rgba(Color c, int a) {
        return rgba(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
