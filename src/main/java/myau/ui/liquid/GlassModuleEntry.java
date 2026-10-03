package myau.ui.liquid;

import myau.module.Module;
import myau.util.FontManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * A module row inside a glass panel.
 * - Hover: brightens + scales up slightly (spring, 200ms)
 * - Press: gentle squash feedback
 * - Right-click expands its properties
 */
public class GlassModuleEntry {
    private static final Minecraft mc = Minecraft.getMinecraft();
    // small, symmetric scale anchored at the ENTRY centre — scaling from the
    // row centre would push a tall entry's bottom controls (e.g. Bind) down
    // onto the next module's row (KillAura: ~11px overlap)
    private static final float HOVER_SCALE = 0.008F;
    private static final float PRESS_SCALE = 0.02F;

    /** true = toggle switch on the right; false = accent fill + bar + dot */
    public static boolean switchStyle = true;

    public final Module module;
    public final GlassPanel panel;
    private boolean expanded;
    private final List<GlassComponent> controls = new ArrayList<>();
    private int y;
    private int w;

    // animation state
    private boolean lastHovered;
    private long hoverStart;
    private boolean pressed;
    private long pressStart;
    private long releaseStart;

    public GlassModuleEntry(Module module, GlassPanel panel, int width) {
        this.module = module;
        this.panel = panel;
        this.w = width;
        this.controls.addAll(GlassControls.buildFor(module, width));
        long now = System.currentTimeMillis();
        this.hoverStart = now;
        this.pressStart = now;
        this.releaseStart = now;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setWidth(int w) {
        this.w = w;
    }

    public int getHeight() {
        // 22 matches where the controls start (rowY + 22) and leaves a small
        // gap after the last control (e.g. the Bind row) so it never overlaps
        // the next module's row
        int h = 22;
        if (expanded) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    h += c.getHeight() + 4;
                }
            }
        }
        return h;
    }

    public void draw(int mouseX, int mouseY, boolean hovered, int alpha) {
        long now = System.currentTimeMillis();

        // hover animation
        if (hovered != lastHovered) {
            hoverStart = now;
            lastHovered = hovered;
        }
        float hoverEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(hoverStart, now)));
        // press squash
        float pressEase;
        if (pressed) {
            pressEase = GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(pressStart, now)));
        } else {
            pressEase = 1 - GlassRenderer.spring(GlassRenderer.easeOutCubic(GlassRenderer.animate(releaseStart, now)));
        }

        // y is an absolute screen coordinate set by the panel
        int rowY = y;
        float scale = 1 + hoverEase * HOVER_SCALE - pressEase * PRESS_SCALE;
        float midX = panel.getX() + w / 2.0F;
        float midY = rowY + getHeight() / 2.0F;  // entry centre as anchor

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(midX, midY, 0);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1);
        net.minecraft.client.renderer.GlStateManager.translate(-midX, -midY, 0);

        // row background — tank-tread pill, no accent fill: the on/off state
        // is shown by the toggle switch on the right instead
        if (hoverEase > 0.02F) {
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY, w - 8, 16,
                    GlassRenderer.lerpColor(0x00000000, GlassRenderer.HOVER_FILL, hoverEase));
        }
        // name
        int baseColor = module.isEnabled() ? GlassRenderer.TEXT_MAIN : GlassRenderer.TEXT_DIM;
        int textColor = (baseColor & 0xFFFFFF) | (alpha << 24);
        FontManager.drawString(module.getName(), panel.getX() + 10, rowY + 4, textColor, false, 11.0F);
        // state indicator: toggle switch OR accent fill + bar + dot (switchable)
        if (switchStyle) {
            GlassRenderer.drawCapsule(panel.getX() + w - 30, rowY + 2, 24, 12,
                    module.isEnabled(), GlassRenderer.ACCENT);
        } else if (module.isEnabled()) {
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY, w - 8, 16, GlassRenderer.ACTIVE_FILL);
            GlassRenderer.drawCapsuleRect(panel.getX() + 4, rowY + 4, 2.5F, 8, GlassRenderer.ACCENT);
            GlassRenderer.drawRoundedRect(panel.getX() + w - 13, rowY + 6, 4, 4, 2, GlassRenderer.ACCENT);
        }

        // controls
        if (expanded) {
            int cy = rowY + 22;
            for (GlassComponent c : controls) {
                if (!c.isVisible()) continue;
                c.setBounds(panel.getX() + 8, cy, w - 16, c.getHeight());
                c.draw(mouseX, mouseY);
                cy += c.getHeight() + 4;
            }
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();
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
            }
            return;
        }
        if (expanded) {
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
        if (expanded) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    c.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (expanded) {
            for (GlassComponent c : controls) {
                if (c.isVisible()) {
                    c.keyTyped(typedChar, keyCode);
                }
            }
        }
    }
}
