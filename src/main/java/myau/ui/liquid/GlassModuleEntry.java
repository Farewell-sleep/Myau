package myau.ui.liquid;

import myau.module.Module;
import myau.risefont.RiseFontManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 玻璃面板中的一个模块行。
 * - 悬停：提亮 + 轻微放大（spring，200ms）；按压：轻微 squash
 * - 右键展开子设置：行高与透明度走 easeOutCubic 动画（与 GlassRenderer 同套工具）
 */
public class GlassModuleEntry {
    private static final Minecraft mc = Minecraft.getMinecraft();
    // 小而对称的缩放锚定在行中心 —— 从行中心缩放会把高条目底部控件压到下一行
    private static final float HOVER_SCALE = 0.008F;
    private static final float PRESS_SCALE = 0.02F;

    /** true = 右侧开关；false = accent 填充条 + 圆点 */
    public static boolean switchStyle = true;

    public final Module module;
    public final GlassPanel panel;
    private boolean expanded;
    private final List<GlassComponent> controls = new ArrayList<>();
    private int y;
    private int w;

    // 动画状态
    private boolean lastHovered;
    private long hoverStart;
    private boolean pressed;
    private long pressStart;
    private long releaseStart;
    // 展开动画：0 = 收起，1 = 完全展开
    private long expandStart;
    private float expandAnim;

    public GlassModuleEntry(Module module, GlassPanel panel, int width) {
        this.module = module;
        this.panel = panel;
        this.w = width;
        this.controls.addAll(GlassControls.buildFor(module, width));
        long now = System.currentTimeMillis();
        this.hoverStart = now;
        this.pressStart = now;
        this.releaseStart = now;
        this.expandStart = now;
        this.expandAnim = 0.0F;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setWidth(int w) {
        this.w = w;
    }

    /** 展开动画中的实时高度（收起 22，展开随 easeOutCubic 过渡）。 */
    public int getHeight() {
        int base = 22;
        float extra = 0;
        for (GlassComponent c : controls) {
            if (c.isVisible()) {
                extra += c.getHeight() + 4;
            }
        }
        return base + Math.round(extra * expandAnim);
    }

    public void draw(int mouseX, int mouseY, boolean hovered, int alpha) {
        long now = System.currentTimeMillis();

        // 悬停动画
        if (hovered != lastHovered) {
            hoverStart = now;
            lastHovered = hovered;
        }
        float hoverEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(hoverStart, now)));
        // 按压 squash
        float pressEase;
        if (pressed) {
            pressEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(pressStart, now)));
        } else {
            pressEase = 1 - GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(releaseStart, now)));
        }
        // 展开动画：朝 0/1 目标 easeOutCubic 过渡
        float eased = GlassRenderer.easeOutCubic(GlassRenderer.animate(expandStart, now));
        expandAnim = GlassRenderer.clampT(expanded ? eased : 1 - eased);

        // y 是面板设置的绝对屏幕坐标
        int rowY = y;
        float scale = 1 + hoverEase * HOVER_SCALE - pressEase * PRESS_SCALE;
        float midX = panel.getX() + w / 2.0F;
        float midY = rowY + getHeight() / 2.0F;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(midX, midY, 0);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1);
        net.minecraft.client.renderer.GlStateManager.translate(-midX, -midY, 0);
        try {
        // 行背景：悬停胶囊填充
        if (hoverEase > 0.02F) {
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY, w - 8, 16,
                    GlassRenderer.lerpColor(0x00000000, GlassRenderer.HOVER_FILL, hoverEase));
        }
        // 模块名（Rise 字体 10px，顶部语义；行高 22、显示高约 12 → 顶部
        // 落在 rowY+5 即垂直居中，与衡水体时期 rowY+5 一致）
        int baseColor = module.isEnabled() ? GlassRenderer.TEXT_MAIN : GlassRenderer.TEXT_DIM;
        int textColor = (baseColor & 0xFFFFFF) | (alpha << 24);
        float nameY = rowY + 5.0F;
        RiseFontManager.drawString(module.getName(), panel.getX() + 10, nameY, textColor, false, GlassControls.SIZE_LABEL);
        // 状态指示：开关 OR accent 填充条 + 圆点（可切换）
        if (switchStyle) {
            GlassRenderer.drawCapsule(panel.getX() + w - 30, rowY + 2, 24, 12,
                    module.isEnabled(), GlassRenderer.ACCENT);
        } else if (module.isEnabled()) {
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY, w - 8, 16, GlassRenderer.ACTIVE_FILL);
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY + 4, 2.5F, 8, GlassRenderer.ACCENT);
            GlassRenderer.drawRoundedRect(panel.getX() + w - 13, rowY + 6, 4, 4, 2, GlassRenderer.ACCENT);
        }

        // 子设置：随展开动画淡入（RENDER_ALPHA 控制文本透明度，行高负责纵向wipe）
        if (expandAnim > 0.02F) {
            int cy = rowY + 22;
            float savedAlpha = GlassControls.RENDER_ALPHA;
            GlassControls.RENDER_ALPHA = expandAnim;
            try {
                for (GlassComponent c : controls) {
                    if (!c.isVisible()) continue;
                    c.setBounds(panel.getX() + 8, cy, w - 16, c.getHeight());
                    c.draw(mouseX, mouseY);
                    cy += c.getHeight() + 4;
                }
            } finally {
                GlassControls.RENDER_ALPHA = savedAlpha;
            }
        }
        } finally {
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    public void mouseDown(int mouseX, int mouseY, int button) {
        int rowY = y;
        if (mouseX >= panel.getX() + 4 && mouseX <= panel.getX() + w - 4 && mouseY >= rowY && mouseY <= rowY + 18) {
            pressed = true;
            pressStart = System.currentTimeMillis();
            if (button == 0) {
                module.toggle();
            } else if (button == 1) {
                expanded = !expanded;
                expandStart = System.currentTimeMillis();
            }
            return;
        }
        if (expandAnim > 0.02F) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    c.mouseDown(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (pressed) {
            pressed = false;
            releaseStart = System.currentTimeMillis();
        }
        if (expandAnim > 0.02F) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    c.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (expandAnim > 0.02F) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    c.keyTyped(typedChar, keyCode);
                }
            }
        }
    }
}
