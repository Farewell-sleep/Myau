package myau.ui.liquid;

import myau.module.Module;
import myau.risefont.RiseFontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Draggable Liquid Glass category panel.
 * - Hover: glass brightens + scales up slightly (spring, 200ms)
 * - Press: gentle squash feedback
 * - Enter: fade-in-up with per-panel stagger delay
 */
public class GlassPanel {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int HEADER_H = 34;
    private static final float RADIUS = 24.0F;          // continuous-curvature large corner
    private static final int MAX_CONTENT_H = 260;
    private static final float HOVER_SCALE = 0.018F;    // slight grow on hover
    private static final float PRESS_SCALE = 0.045F;    // gentle squash on press

    public final String name;
    private final List<GlassModuleEntry> entries = new ArrayList<>();
    private int x = 10;
    private int y = 46;
    private int w = 122;
    private boolean opened = true;
    private boolean dragging;
    private int dragOffX, dragOffY;
    private int scroll;
    private float animScroll;
    private String filter = "";
    private long filterStart;

    // animation state
    private final long enterStart;
    private long openStart;
    private boolean lastHovered;
    private long hoverStart;
    private boolean pressed;
    private long pressStart;
    private long releaseStart;
    // private String lastScrollDbg = "";   // [DEBUG]

    public GlassPanel(String name, List<Module> modules, int staggerIndex) {
        this.name = name;
        // 面板入场 stagger：每块面板递增 30ms
        this.enterStart = System.currentTimeMillis() + staggerIndex * 30L;
        // panels start fully open: put the collapse clock in the past
        this.openStart = System.currentTimeMillis() - GlassRenderer.DURATION;
        this.hoverStart = System.currentTimeMillis();
        this.releaseStart = System.currentTimeMillis();
        this.filterStart = System.currentTimeMillis();
        for (Module m : modules) {
            entries.add(new GlassModuleEntry(m, this, w));
        }
        for (GlassModuleEntry e : entries) {
            int tw = RiseFontManager.getStringWidth(e.module.getName(), GlassControls.SIZE_LABEL) + 30;
            if (tw > w) w = tw;
        }
        w = Math.min(w, 156);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return w;
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setFilter(String filter) {
        String newFilter = filter == null ? "" : filter.trim().toLowerCase();
        // only reset the scroll when the filter actually changes — drawScreen
        // calls this every frame, and resetting unconditionally would zero
        // the scroll right after every wheel event (nothing could scroll)
        if (!newFilter.equals(this.filter)) {
            this.filter = newFilter;
            this.filterStart = System.currentTimeMillis();
            scroll = 0;
            animScroll = 0;
        }
    }

    private List<GlassModuleEntry> visibleEntries() {
        if (filter.isEmpty()) return entries;
        List<GlassModuleEntry> out = new ArrayList<>();
        for (GlassModuleEntry e : entries) {
            if (e.module.getName().toLowerCase().contains(filter)) {
                out.add(e);
            }
        }
        return out;
    }

    private int contentHeight() {
        int h = 0;
        for (GlassModuleEntry e : visibleEntries()) {
            h += e.getHeight();
        }
        return h;
    }

    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + HEADER_H + contentHeight();
    }

    public void draw(int mouseX, int mouseY) {
        long now = System.currentTimeMillis();

        // --- hover animation (brightness + grow) ---
        boolean hovered = isHovered(mouseX, mouseY);
        if (hovered != lastHovered) {
            hoverStart = now;
            lastHovered = hovered;
        }
        float hoverEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(hoverStart, now)));
        // --- press squash ---
        float pressEase;
        if (pressed) {
            pressEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(pressStart, now)));
        } else {
            pressEase = 1 - GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(releaseStart, now)));
        }
        // --- enter: fade-in-up, staggered ---
        float enter = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(enterStart, now)));
        float rise = (1 - enter) * 22.0F;

        // --- collapse/expand transition: content height springs toward its
        // target (0 when collapsed) over 200ms; the title slides to stay
        // vertically centred as the panel changes height ---
        float openProgress = opened
                ? GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(openStart, now)))
                : 1 - GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(openStart, now)));
        float cp = GlassRenderer.clampT(openProgress);
        float targetContentH = opened ? Math.min(contentHeight(), MAX_CONTENT_H) : 0;
        float contentHF = Math.max(0, targetContentH * openProgress);
        int contentH = (int) contentHF;
        float panelHF = HEADER_H + contentHF + 6;
        float scale = 1 + hoverEase * HOVER_SCALE - pressEase * PRESS_SCALE;
        float midX = x + w / 2.0F, midY = y + panelHF / 2.0F;
        float drawY = y + rise;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(midX, midY, 0);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1);
        net.minecraft.client.renderer.GlStateManager.translate(-midX, -midY, 0);

        int bodyAlpha = (int) (enter * 255);
        // 搜索过滤切换时：整列条目淡入（与滚动复位同步）
        float filterEase = GlassRenderer.easeOutCubic(GlassRenderer.animate(filterStart, now));
        int entryAlpha = (int) (bodyAlpha * filterEase);

        // Liquid Glass backdrop: SDF rounded mask + edge refraction shader
        // sampling the blurred frame. No grey glass body — the panel IS the
        // glass. Fades in with the enter animation.
        int blurTex = LiquidClickGui.getBlurTexture();
        if (blurTex > 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            int scaledW = sr.getScaledWidth();
            int scaledH = sr.getScaledHeight();
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager.bindTexture(blurTex);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 0.72F * enter);
            GlassRenderer.LiquidGlassShader.INSTANCE.use();
            // uHalf Y scaled by h/w: the shader's anisotropic space becomes
            // screen-isotropic, so the mask corner is a true circle of the
            // clamped radius in both axes (the old (0.5, 0.5) made it an
            // ellipse of radius RADIUS x RADIUS*h/w — corners mismatched).
            // Radius clamp mirrors drawRoundedRect: min(r, w/2, h/2).
            float rUv = Math.min(RADIUS / w, Math.min(0.5F, 0.5F * panelHF / w));
            GlassRenderer.LiquidGlassShader.INSTANCE.setPanelParams(
                    0.5F, 0.5F * panelHF / w,              // panel half-extents (screen-isotropic UV space)
                    rUv,
                    w / (float) scaledW, panelHF / (float) scaledH,  // panel size / screen
                    x / (float) scaledW, 1 - y / (float) scaledH);   // panel top-left in screen UV
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(x + w, y);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(x + w, y + panelHF);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(x, y + panelHF);
            GL11.glEnd();
            GlassRenderer.LiquidGlassShader.INSTANCE.stop();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.bindTexture(prevTexture);
        }

        // soft shadow for depth (no grey body — shadow keeps the panel read)
        GlassRenderer.drawRoundedRect(x, drawY + 2, w, panelHF, RADIUS, 0x15000000);
        GlassRenderer.drawRoundedRect(x, drawY + 4, w, panelHF, RADIUS, 0x0C000000);
        GlassRenderer.drawRoundedRect(x, drawY + 6, w, panelHF, RADIUS, 0x05000000);
        // hairline only (Liquid Glass edge) — no top highlight line
        GlassRenderer.drawRoundedOutline(x + 0.5F, drawY + 0.5F, w - 1, panelHF - 1, RADIUS, 1.0F, GlassRenderer.GLASS_OUTLINE);
        // hover brightness wash
        if (hoverEase > 0.02F) {
            int wash = GlassRenderer.lerpColor(0x00000000, 0x16FFFFFF, hoverEase);
            GlassRenderer.drawRoundedRect(x, drawY, w, panelHF, RADIUS, wash);
        }

        // header —— 面板标题 12px，按 FontManager 度量垂直居中
        float titleCenter = 17.0F + (20.0F - 17.0F) * (1.0F - cp);
        float titleSize = 12.0F;
        float titleY = drawY + titleCenter - RiseFontManager.getBaseline(titleSize)
                + RiseFontManager.getCapHeight(titleSize) / 2.0F;
        int headerText = (GlassRenderer.TEXT_MAIN & 0xFFFFFF) | (bodyAlpha << 24);
        RiseFontManager.drawString(name, x + 14, titleY, headerText, false, titleSize);
        drawChevron(x + w - 17, drawY + titleCenter - 2.75F, cp > 0.5F, bodyAlpha);

        // content with scissor — drawn while the animated height is > 0 so
        // the entries shrink away during the collapse transition
        if (contentH > 0) {
            // clamp every frame with the latest content height
            int maxScroll = Math.max(0, contentHeight() - MAX_CONTENT_H);
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0) scroll = 0;
            animScroll += (scroll - animScroll) * 0.18F;
            if (Math.abs(scroll - animScroll) < 0.1F) animScroll = scroll;

            int cy = (int) (drawY + HEADER_H + 2 - animScroll);
            int bottom = (int) (drawY + HEADER_H + contentH + 4);

            ScaledResolution sr = new ScaledResolution(mc);
            double scaleF = sr.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int) ((x + 3) * scaleF), (int) ((sr.getScaledHeight() - bottom) * scaleF),
                    (int) ((w - 6) * scaleF), (int) ((contentH + 2) * scaleF));

            for (GlassModuleEntry e : visibleEntries()) {
                int eh = e.getHeight();
                // absolute screen Y — the entry draws at exactly this position,
                // independent of the panel's enter-animation rise
                e.setY(cy);
                e.setWidth(w);
                boolean rowHovered = mouseX >= x + 4 && mouseX <= x + w - 4
                        && mouseY >= cy && mouseY <= cy + 16;
                e.draw(mouseX, mouseY, rowHovered, entryAlpha);
                cy += eh;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            if (contentHeight() > MAX_CONTENT_H) {
                float thumbH = Math.max(16, MAX_CONTENT_H * MAX_CONTENT_H / (float) contentHeight());
                float thumbY = (float) (drawY + HEADER_H + 2 + animScroll * (MAX_CONTENT_H - thumbH) / maxScroll);
                // 细圆角滚动条：2.5px 宽、主题蓝
                GlassRenderer.drawRoundedRect(x + w - 4.5F, thumbY, 2.5F, thumbH, 1.25F, 0x993B82F6);
            }
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    private void drawChevron(float cx, float cy, boolean down, int alpha) {
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 0.55F * (alpha / 255.0F));
        GL11.glLineWidth(1.8F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        if (down) {
            GL11.glVertex2d(cx, cy + 1);
            GL11.glVertex2d(cx + 3.5, cy + 4.5);
            GL11.glVertex2d(cx + 7, cy + 1);
        } else {
            GL11.glVertex2d(cx, cy + 4.5);
            GL11.glVertex2d(cx + 3.5, cy + 1);
            GL11.glVertex2d(cx + 7, cy + 4.5);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
    }

    private boolean isHoveredHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + HEADER_H;
    }

    public void mouseDown(int mouseX, int mouseY, int button) {
        if (isHoveredHeader(mouseX, mouseY)) {
            pressed = true;
            pressStart = System.currentTimeMillis();
            if (button == 0) {
                dragging = true;
                dragOffX = mouseX - x;
                dragOffY = mouseY - y;
            } else if (button == 1) {
                opened = !opened;
                openStart = System.currentTimeMillis();
            }
            return;
        }
        if (!opened) return;
        int contentTop = y + HEADER_H;
        int contentBottom = y + HEADER_H + Math.min(contentHeight(), MAX_CONTENT_H);
        if (mouseY < contentTop || mouseY > contentBottom || mouseX < x || mouseX > x + w) return;
        int cy = contentTop + 2 - (int) animScroll;
        for (GlassModuleEntry e : visibleEntries()) {
            int eh = e.getHeight();
            if (mouseY >= cy && mouseY <= cy + eh) {
                e.mouseDown(mouseX, mouseY, button);
                return;
            }
            cy += eh;
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (pressed) {
            pressed = false;
            releaseStart = System.currentTimeMillis();
        }
        dragging = false;
        int contentTop = y + HEADER_H;
        int contentBottom = y + HEADER_H + Math.min(contentHeight(), MAX_CONTENT_H);
        if (!opened || mouseY < contentTop || mouseY > contentBottom || mouseX < x || mouseX > x + w) return;
        int cy = contentTop + 2 - (int) animScroll;
        for (GlassModuleEntry e : visibleEntries()) {
            int eh = e.getHeight();
            if (mouseY >= cy && mouseY <= cy + eh) {
                e.mouseReleased(mouseX, mouseY, button);
                return;
            }
            cy += eh;
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!opened) return;
        for (GlassModuleEntry e : visibleEntries()) {
            e.keyTyped(typedChar, keyCode);
        }
    }

    public void onScroll(int mouseX, int mouseY, int dir) {
        // [DEBUG] lastScrollDbg = "dir=" + dir + " mx=" + mouseX + " my=" + mouseY
        //         + " panel(" + x + "," + y + "," + w + ") h=" + (y + HEADER_H + Math.min(contentHeight(), MAX_CONTENT_H));
        if (!opened) {
            // [DEBUG] lastScrollDbg += " [closed]";
            return;
        }
        if (mouseX < x || mouseX > x + w) {
            // [DEBUG] lastScrollDbg += " [x-reject]";
            return;
        }
        int contentTop = y + HEADER_H;
        int contentBottom = y + HEADER_H + Math.min(contentHeight(), MAX_CONTENT_H);
        if (mouseY < contentTop || mouseY > contentBottom) {
            // [DEBUG] lastScrollDbg += " [y-reject top=" + contentTop + " bot=" + contentBottom + "]";
            return;
        }
        // accumulate only; clamping happens every frame in draw() with the
        // latest content height, so expanding a module never leaves the
        // scroll stuck at the old maximum
        scroll += dir * 24;
        // [DEBUG] lastScrollDbg += " -> scroll=" + scroll;
    }

    public void handleDrag(int mouseX, int mouseY) {
        if (dragging) {
            x = mouseX - dragOffX;
            y = mouseY - dragOffY;
        }
    }
}
