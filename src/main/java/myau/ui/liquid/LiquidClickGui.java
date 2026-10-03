package myau.ui.liquid;

import myau.OpenMyau;
import myau.module.Module;
import myau.module.modules.*;
import myau.util.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Apple Liquid Glass ClickGUI.
 *
 * Backdrop behaves like real liquid glass:
 *  - backdrop-filter blur (two-stage FBO downsample ~ 20-40px equivalent)
 *  - saturate(180%) via GLSL shader
 *  - large continuous-curvature corners (24-32px)
 *  - soft diffuse shadows
 * Panels enter with a staggered fade-in-up; hover grows/brightens;
 * press squashes (200ms spring ease-out).
 */
public class LiquidClickGui extends GuiScreen {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static LiquidClickGui instance;

    private final List<GlassPanel> panels = new ArrayList<>();
    private final StringBuilder search = new StringBuilder();
    private boolean searchFocused;
    private int searchW = 220;
    private int searchX = 10;

    // backdrop blur pipeline: blurA holds the (full-res) frame copy and
    // later the final blurred result; blurB is the intermediate horizontal
    // pass. Two separable gaussian passes (H then V) give the smooth
    // Windows Terminal acrylic look — no pixelation, no ghosting.
    private Framebuffer blurA;
    private Framebuffer blurB;
    private boolean blurFailed;
    private String blurError;
    // private int lastWheel = 0;        // [DEBUG]
    // private boolean wheelEventSeen;   // [DEBUG]

    public LiquidClickGui() {
        instance = this;
        int i = 0;
        panels.add(new GlassPanel("Combat", category(AimAssist.class, AutoClicker.class, KillAura.class, Wtap.class,
                Velocity.class, Freeze.class, Reach.class, TargetStrafe.class, NoHitDelay.class, AntiFireball.class,
                LagRange.class, BackTrack.class, BlockHit.class, Autoblock.class, HitBox.class, MoreKB.class, Refill.class, HitSelect.class, AutoThrow.class, NewKillAura.class, SmartAttack.class), i++));
        panels.add(new GlassPanel("Movement", category(AntiAFK.class, Fly.class, Speed.class, LongJump.class, Sprint.class,
                SafeWalk.class, Jesus.class, Blink.class, NoFall.class, NoSlow.class, KeepSprint.class, Eagle.class,
                NoJumpDelay.class, AntiVoid.class), i++));
        panels.add(new GlassPanel("Render", category(ESP.class, Chams.class, FullBright.class, Tracers.class, NameTags.class,
                Xray.class, BedESP.class, ItemESP.class, ItemPhysics.class, BreakProgress.class,
                Freelook.class, ViewClip.class, NoHurtCam.class,
                GuiModule.class, ChestESP.class, Trajectories.class, Radar.class, CuteVisuals.class, SnowFog.class,
                Zoom.class, Crosshair.class, SeeInvisibles.class, Ambience.class, Hurtcam.class, FogRemove.class), i++));
        panels.add(new GlassPanel("HUD", category(HUD.class, TargetHUD.class, Indicators.class, PotionHUD.class, Watermark.class, Keybinds.class), i++));
        panels.add(new GlassPanel("Player", category(Clutch.class, AutoHeal.class, AutoTool.class, ChestStealer.class, InvManager.class,
                InvWalk.class, Scaffold.class, Telly.class, NewScaffold.class, Fruitberries.class, GreatWall.class, SyBridge.class, AutoBlockIn.class, SpeedMine.class, FastPlace.class,
                GhostHand.class, MCF.class, AntiDebuff.class), i++));
        panels.add(new GlassPanel("Misc", category(Spammer.class, BedNuker.class, BedTracker.class, LightningTracker.class,
                NoRotate.class, NickHider.class, AntiObbyTrap.class, AntiObfuscate.class, AutoAnduril.class,
                InventoryClicker.class, Disabler.class), i));

        int px = 14;
        for (GlassPanel p : panels) {
            p.setLocation(px, 50);
            px += p.getWidth() + 12;
        }
    }

    public static LiquidClickGui getInstance() {
        return instance;
    }

    /** Blurred backdrop texture id for panels to draw as their frosted-glass background. */
    public static int getBlurTexture() {
        return instance != null && !instance.blurFailed && instance.blurA != null
                ? instance.blurA.framebufferTexture : -1;
    }

    private static List<Module> category(Class<?>... classes) {
        List<Module> list = new ArrayList<>();
        for (Class<?> c : classes) {
            Module m = OpenMyau.moduleManager.getModule(c);
            if (m != null) list.add(m);
        }
        list.sort(Comparator.comparing(m -> m.getName().toLowerCase()));
        return list;
    }

    @Override
    public void initGui() {
        super.initGui();
        searchX = width / 2 - searchW / 2;
        if (!blurFailed) {
            try {
                if (blurA != null) blurA.deleteFramebuffer();
                if (blurB != null) blurB.deleteFramebuffer();
                // Full-resolution buffers: the two-pass separable gaussian
                // does the blurring, so there is zero downsampling and no
                // pixelation (Windows 11 Terminal acrylic look).
                blurA = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                blurB = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                blurA.setFramebufferColor(0, 0, 0, 0);
                blurB.setFramebufferColor(0, 0, 0, 0);
            } catch (Throwable t) {
                blurFailed = true;
                blurError = "LiquidGlass FBO init failed: " + t.getClass().getSimpleName() + " - " + t.getMessage();
                System.out.println("[OpenMyau] LiquidGlass FBO init failed: " + t);
                t.printStackTrace();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        renderBlurBackdrop();

        // subtle ambient glow blobs over the glass (reflected color/light)
        GlassRenderer.drawRoundedRect(width * 0.1F, height * 0.08F, width * 0.3F, height * 0.26F, 130, 0x1022C55E);
        GlassRenderer.drawRoundedRect(width * 0.6F, height * 0.6F, width * 0.32F, height * 0.3F, 150, 0x0C7DD3FC);

        // header — liquid glass title pill (true capsule)
        drawGlassCapsuleBg(12, 6, 118, 34, 1.0F);
        FontManager.drawString("Myau", 20, 10, GlassRenderer.ACCENT, true, 16.0F);
        FontManager.drawString("Dev TTHILLTT", 20, 24, GlassRenderer.TEXT_FAINT, true, 10.0F);

        // module state style toggle button (top-right) — same liquid glass pill
        int styleX = width - 92;
        boolean styleHover = mouseX >= styleX && mouseX <= styleX + 82 && mouseY >= 8 && mouseY <= 26;
        drawGlassCapsuleBg(styleX, 8, 82, 18, styleHover ? 1.0F : 0.85F);
        FontManager.drawString("State: " + (GlassModuleEntry.switchStyle ? "Switch" : "Accent"),
                styleX + 10, 12, GlassRenderer.TEXT_MAIN, true, 10.0F);

        // search bar — same liquid glass refraction as the panels
        int sy = 12;
        int searchH = 20;
        int blurTex = getBlurTexture();
        if (blurTex > 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
            GlStateManager.bindTexture(blurTex);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlassRenderer.LiquidGlassShader.INSTANCE.use();
            GlassRenderer.LiquidGlassShader.INSTANCE.setPanelParams(
                    0.5F, 0.5F * searchH / searchW,
                    Math.min(12.0F / searchW, Math.min(0.5F, 0.5F * searchH / searchW)),
                    searchW / (float) sw, searchH / (float) sh,
                    searchX / (float) sw, 1 - sy / (float) sh);
            GlassRenderer.LiquidGlassShader.INSTANCE.setCapsule(true);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(searchX, sy);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(searchX + searchW, sy);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(searchX + searchW, sy + searchH);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(searchX, sy + searchH);
            GL11.glEnd();
            GlassRenderer.LiquidGlassShader.INSTANCE.stop();
            GlStateManager.color(1, 1, 1, 1);
        }
        // soft shadow + hairline (true capsule)
        GlassRenderer.drawCapsuleRect(searchX, sy + 2, searchW, searchH, 0x15000000);
        GlassRenderer.drawCapsuleRect(searchX, sy + 4, searchW, searchH, 0x0C000000);
        GlassRenderer.drawCapsuleRect(searchX, sy + 6, searchW, searchH, 0x05000000);
        GlassRenderer.drawCapsuleOutline(searchX + 0.5F, sy + 0.5F, searchW - 1, searchH - 1, 1.0F, GlassRenderer.GLASS_OUTLINE);
        String hint = search.length() == 0 ? "Search modules..." : search.toString();
        int hintColor = search.length() == 0 ? GlassRenderer.TEXT_FAINT : GlassRenderer.TEXT_MAIN;
        FontManager.drawString(hint, searchX + 10, sy + 6, hintColor, true, 11.0F);
        if (searchFocused) {
            GlassRenderer.drawRoundedRect(searchX + 10 + FontManager.getStringWidth(hint, 11.0F), sy + 5, 1.2F, 10, 0.6F, 0x9922C55E);
        }
        drawSearchIcon(searchX + searchW - 18, sy + 6);

        // panels
        String filter = search.toString();
        for (GlassPanel p : panels) {
            p.setFilter(filter);
            p.draw(mouseX, mouseY);
            p.handleDrag(mouseX, mouseY);
        }

        // [DEBUG] scroll diagnostics
        // mc.fontRendererObj.drawStringWithShadow(
        //         "wheel=" + lastWheel + " seen=" + wheelEventSeen, 14, height - 40, 0xFFFFFF55);
        // int dbgY = 60;
        // for (GlassPanel p : panels) {
        //     mc.fontRendererObj.drawStringWithShadow(
        //             p.name + " scroll=" + p.getScroll() + "/" + p.getMaxScroll()
        //                     + " anim=" + p.getAnimScroll()
        //                     + " content=" + p.getContentHeight() + " | " + p.getLastScrollDbg(),
        //             14, dbgY, 0xFFFFFF55);
        //     dbgY += 10;
        // }

        // [DEBUG] backdrop status diagnostics (temporary, for testing)
        // if (blurFailed) {
        //     mc.fontRendererObj.drawStringWithShadow(blurError == null ? "LiquidGlass blur disabled" : blurError,
        //             14, height - 24, 0xFFFF5555);
        // } else if (blurA != null) {
        //     mc.fontRendererObj.drawStringWithShadow("LiquidGlass blur: OK",
        //             14, height - 24, 0xFF55FF55);
        //     if (blurError != null) {
        //         mc.fontRendererObj.drawStringWithShadow(blurError, 14, height - 12, 0xFFFFFF55);
        //     }
        // } else {
        //     mc.fontRendererObj.drawStringWithShadow("LiquidGlass blur: not initialized",
        //             14, height - 24, 0xFFFFFF55);
        // }

        // [DEBUG] vanilla drawRect vs GlassRenderer.drawRect comparison
        // net.minecraft.client.gui.Gui.drawRect(10, 10, 110, 60, 0xFFFF0000);           // vanilla red (top-left)
        // GlassRenderer.drawRect(width - 120, 10, 110, 50, 0xFF0000FF);                  // glass blue (top-right)
        // GlassRenderer.drawRoundedRect(width / 2 - 60, 10, 120, 50, 12, 0xFF00FF00);    // glass green (top-center)
    }

    /**
     * Liquid-glass backdrop: copy the frame to blurA, then run a separable
     * gaussian (horizontal pass blurA -> blurB, vertical pass blurB -> blurA)
     * — the smooth Windows Terminal acrylic blur. Panels sample blurA and
     * only apply the refraction there.
     */
    private void renderBlurBackdrop() {
        if (blurFailed || blurA == null || blurB == null || mc.getFramebuffer() == null
                || mc.getFramebuffer().framebufferTexture == 0) {
            GlassRenderer.drawRect(0, 0, width, height, 0x99050508);
            // [DEBUG] show the failure reason right on screen (no console needed)
            // if (blurError != null) {
            //     mc.fontRendererObj.drawStringWithShadow(blurError, 14, height - 24, 0xFFFF5555);
            // }
            return;
        }
        int srcTex = mc.getFramebuffer().framebufferTexture;

        try {
            // CRITICAL: do NOT touch the projection matrix. We draw in the
            // existing scaled GUI coordinates (0..width, 0..height) — a
            // fullscreen quad there covers the whole viewport regardless of
            // its size. Bind FBOs with raw GL30 calls + explicit viewport:
            // Framebuffer.bindFramebuffer() silently no-ops when isNotInit.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();

            // copy frame -> blurA (full resolution, NO gaussian blur — the
            // liquid glass look here is pure edge refraction, like the
            // original shuding liquid-glass effect)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurA.framebufferObject);
            GL11.glViewport(0, 0, blurA.framebufferWidth, blurA.framebufferHeight);
            GlassRenderer.drawTextureQuad(srcTex, width, height);

            // Back to the main framebuffer. The glass is drawn per-panel
            // sampling blurA; the backdrop outside stays untouched.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            // int err3 = GL11.glGetError(); // [DEBUG]

            // [DEBUG] red test bar at bottom (scaled coords)
            // GlStateManager.enableBlend();
            // GlassRenderer.drawRect(0, height - 30, 150, 20, 0x80FF0000);
            // int errRed = GL11.glGetError();
            // [DEBUG] blurA content preview, top-left (scaled coords)
            // GlStateManager.enableBlend();
            // GlassRenderer.drawTextureQuad(blurA.framebufferTexture, 100, 56);
            // blurError = "err1=" + err1 + " err2=" + err2 + " err3=" + err3
            //         + " redbar=" + errRed
            //         + " texA=" + blurA.framebufferTexture + " texB=" + blurB.framebufferTexture
            //         + " mainFbo=" + mc.getFramebuffer().framebufferObject;
        } catch (Throwable t) {
            blurFailed = true;
            blurError = "LiquidGlass blur disabled: " + t.getClass().getSimpleName() + " - " + t.getMessage();
            System.out.println("[OpenMyau] LiquidGlass blur pipeline failed: " + t);
            t.printStackTrace();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        } finally {
            // restore default GUI GL state (vanilla text needs all of these)
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private void drawSearchIcon(float cx, float cy) {
        GL11Helper.drawCircle(cx, cy, 2.6F, 0x9922C55E);
        GL11Helper.drawCircle(cx + 4.2F, cy + 4.2F, 1.2F, 0x9922C55E);
    }

    /** Small liquid-glass capsule backdrop: refraction + shadow + hairline,
     *  true semicircle ends (tank tread) — capsule radius = bh / 2. */
    private void drawGlassCapsuleBg(float bx, float by, float bw, float bh, float alpha) {
        int blurTex = getBlurTexture();
        if (blurTex <= 0) {
            GlassRenderer.drawCapsuleRect(bx, by, bw, bh, 0x59FFFFFF);
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        GlStateManager.bindTexture(blurTex);
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, alpha);
        GlassRenderer.LiquidGlassShader.INSTANCE.use();
        GlassRenderer.LiquidGlassShader.INSTANCE.setPanelParams(
                0.5F, 0.5F * bh / bw,
                Math.min(0.5F * bh / bw, Math.min(0.5F, 0.5F * bh / bw)),
                bw / (float) sw, bh / (float) sh,
                bx / (float) sw, 1 - by / (float) sh);
        GlassRenderer.LiquidGlassShader.INSTANCE.setCapsule(true);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(bx, by);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(bx + bw, by);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(bx + bw, by + bh);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(bx, by + bh);
        GL11.glEnd();
        GlassRenderer.LiquidGlassShader.INSTANCE.stop();
        GlStateManager.color(1, 1, 1, 1);
        // soft shadow + hairline — true capsule
        GlassRenderer.drawCapsuleRect(bx, by + 2, bw, bh, 0x15000000);
        GlassRenderer.drawCapsuleRect(bx, by + 4, bw, bh, 0x0C000000);
        GlassRenderer.drawCapsuleRect(bx, by + 6, bw, bh, 0x05000000);
        GlassRenderer.drawCapsuleOutline(bx + 0.5F, by + 0.5F, bw - 1, bh - 1, 1.0F, GlassRenderer.GLASS_OUTLINE);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        // GuiScreen.handleInput() already consumed the current event with
        // Mouse.next(), so read it directly here — an inner while(Mouse.next())
        // would skip the first (and often only) wheel event.
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            // lastWheel = wheel; wheelEventSeen = true; // [DEBUG]
            int dir = wheel > 0 ? -1 : 1;
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            for (GlassPanel p : panels) {
                p.onScroll(mx, my, dir);
            }
        }
        super.handleMouseInput();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        searchFocused = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= 12 && mouseY <= 32;
        // module state style toggle
        int styleX = width - 92;
        if (mouseButton == 0 && mouseX >= styleX && mouseX <= styleX + 82 && mouseY >= 8 && mouseY <= 26) {
            GlassModuleEntry.switchStyle = !GlassModuleEntry.switchStyle;
            return;
        }
        for (GlassPanel p : panels) {
            p.mouseDown(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        for (GlassPanel p : panels) {
            p.mouseReleased(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        for (GlassPanel p : panels) {
            p.keyTyped(typedChar, keyCode);
        }
        if (!searchFocused) return;
        if (keyCode == Keyboard.KEY_BACK && search.length() > 0) {
            search.deleteCharAt(search.length() - 1);
            return;
        }
        if (typedChar >= 32 && typedChar < 127) {
            search.append(typedChar);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (blurA != null) {
            blurA.deleteFramebuffer();
            blurA = null;
        }
        if (blurB != null) {
            blurB.deleteFramebuffer();
            blurB = null;
        }
    }

    /** Minimal GL helper for the decorative icon. */
    private static final class GL11Helper {
        static void drawCircle(float cx, float cy, float r, int color) {
            float a = ((color >> 24) & 255) / 255.0F;
            float red = ((color >> 16) & 255) / 255.0F;
            float green = ((color >> 8) & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(red, green, blue, a);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(cx, cy);
            for (int i = 0; i <= 16; i++) {
                double ang = Math.toRadians(i * 360.0 / 16);
                GL11.glVertex2d(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r);
            }
            GL11.glEnd();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
