package myau.ui.liquid;

import myau.util.shader.Shader;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Liquid Glass rendering utilities: continuous-curvature (super-ellipse)
 * rounded rects, frosted glass panels with soft shadows, spring-eased
 * animation helpers and a backdrop saturation shader (saturate 180%).
 *
 * IMPORTANT: every GL state change goes through GlStateManager so its
 * caches (blend / alpha / texture binding / matrices) stay in sync with
 * the fixed-function state — vanilla font rendering depends on those caches.
 */
public final class GlassRenderer {

    /** Apple Liquid Glass palette */
    public static final int GLASS_BODY = 0xB91A2028;      // translucent deep glass
    public static final int GLASS_SHEEN = 0x1410141C;
    public static final int GLASS_HIGHLIGHT = 0x42FFFFFF; // top edge light
    public static final int GLASS_OUTLINE = 0x2AFFFFFF;   // hairline border
    public static final int ACCENT = 0xFF3B82F6;       // modern blue
    public static final int ACCENT_DIM = 0x4D3B82F6;
    public static final int TEXT_MAIN = 0xFFF2F4F8;
    public static final int TEXT_DIM = 0xFF8A92A6;
    public static final int TEXT_FAINT = 0xFF5A6276;
    public static final int HOVER_FILL = 0x1AFFFFFF;
    public static final int ACTIVE_FILL = 0x2E3B82F6;

    /** Motion spec: 200ms ease-out with spring overshoot. */
    public static final long DURATION = 200L;

    private static final int SEGMENTS = 12;

    private GlassRenderer() {
    }

    // ------------------------------------------------------------------
    // Animation (CSS cubic-bezier(0.25, 1, 0.5, 1) style ease-out + spring)
    // ------------------------------------------------------------------

    public static float clampT(float t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /** Normalized 0..1 progress over 200ms from startMs. */
    public static float animate(long startMs, long nowMs) {
        return clampT((nowMs - startMs) / (float) DURATION);
    }

    /** Ease-out cubic: matches cubic-bezier(0.25, 1, 0.5, 1) closely. */
    public static float easeOutCubic(float t) {
        float f = 1 - t;
        return 1 - f * f * f;
    }

    /** Springy ease-out-back with a small overshoot for hover/press feedback. */
    public static float spring(float t) {
        float c1 = 1.70158F, c3 = c1 + 1, f = t - 1;
        return 1 + c3 * f * f * f + c1 * f * f;
    }

    // ------------------------------------------------------------------
    // Shapes — continuous curvature super-ellipse corners (n = 4)
    // ------------------------------------------------------------------

    public static void drawRoundedRect(float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2.0F, h / 2.0F));
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        // Face culling left enabled by 3D rendering would cull our winding
        // (opposite to vanilla Tessellator's) — everything would vanish
        // silently with no GL error.
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glBegin(GL11.GL_POLYGON);
        arcSq(x + r, y + r, r, 180, 270);
        arcSq(x + w - r, y + r, r, 270, 360);
        arcSq(x + w - r, y + h - r, r, 0, 90);
        arcSq(x + r, y + h - r, r, 90, 180);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void drawRoundedOutline(float x, float y, float w, float h, float r, float thickness, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2.0F, h / 2.0F));
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glLineWidth(thickness);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arcSq(x + r, y + r, r, 180, 270);
        arcSq(x + w - r, y + r, r, 270, 360);
        arcSq(x + w - r, y + h - r, r, 0, 90);
        arcSq(x + r, y + h - r, r, 90, 180);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void drawRoundedGradient(float x, float y, float w, float h, float r, int topColor, int bottomColor) {
        int layers = 16;
        float layerH = h / layers;
        for (int i = 0; i < layers; i++) {
            float t = i / (float) (layers - 1);
            int color = lerpColor(topColor, bottomColor, t);
            if (i == 0) {
                drawRoundedRect(x, y + i * layerH, w, layerH + 1, r, color);
            } else if (i == layers - 1) {
                drawRoundedRect(x, y + i * layerH - 1, w, layerH + 1, r, color);
            } else {
                drawRect(x, y + i * layerH, w, layerH, color);
            }
        }
    }

    /** Capsule outline: true semicircle ends (for glass hairlines). */
    public static void drawCapsuleOutline(float x, float y, float w, float h, float thickness, int color) {
        if (w <= 0 || h <= 0) return;
        float r = h / 2.0F;
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glLineWidth(thickness);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arcCirc(x + r, y + r, r, 180, 270);
        arcCirc(x + w - r, y + r, r, 270, 360);
        arcCirc(x + w - r, y + h - r, r, 0, 90);
        arcCirc(x + r, y + h - r, r, 90, 180);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Pill / tank-tread capsule: a long bar with true semicircle ends. */
    public static void drawCapsuleRect(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        float r = h / 2.0F;
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glBegin(GL11.GL_POLYGON);
        arcCirc(x + r, y + r, r, 180, 270);
        arcCirc(x + w - r, y + r, r, 270, 360);
        arcCirc(x + w - r, y + h - r, r, 0, 90);
        arcCirc(x + r, y + h - r, r, 90, 180);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void drawRect(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glBegin(GL11.GL_QUADS);
        // same winding as vanilla Tessellator (bottom-left first) so face
        // culling can never discard it
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Liquid Glass panel: layered soft shadow + glass body + sheen + highlight + hairline. */
    public static void drawGlassPanel(float x, float y, float w, float h, float r) {
        // soft diffuse shadow (light, three faint layers — no hard shadow)
        drawRoundedRect(x, y + 2, w, h, r, 0x15000000);
        drawRoundedRect(x, y + 4, w, h, r, 0x0C000000);
        drawRoundedRect(x, y + 6, w, h, r, 0x05000000);
        // glass body
        drawRoundedRect(x, y, w, h, r, GLASS_BODY);
        drawRoundedRect(x, y, w, h, r, GLASS_SHEEN);
        // top highlight (away from corner arcs)
        float innerW = w - 2 * r;
        if (innerW > 8) {
            drawRoundedRect(x + r, y + 1.4F, innerW, 1.0F, 0.5F, GLASS_HIGHLIGHT);
        }
        // hairline
        drawRoundedOutline(x + 0.5F, y + 0.5F, w - 1, h - 1, r, 1.0F, GLASS_OUTLINE);
    }

    public static void drawCapsule(float x, float y, float w, float h, boolean on, int accent) {
        // true pill track (semicircle ends, not a squircle)
        drawCapsuleRect(x, y, w, h, on ? accent : 0x59FFFFFF);
        // Liquid Glass knob — bigger (h-2) so the glass layers are visible
        float d = h - 2;
        float kx = on ? x + w - h + 1 : x + 1;
        float ky = y + 1;
        net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        drawGlassKnob(kx + d / 2.0F, ky + d / 2.0F, d / 2.0F,
                sr.getScaledWidth(), sr.getScaledHeight());
    }

    /**
     * Real Liquid Glass knob: samples the same blurred backdrop as the panels
     * through a circle-SDF + refraction shader — a tiny piece of liquid glass.
     * Falls back to a solid colour if the blur pipeline is unavailable.
     */
    private static void drawGlassKnob(float cx, float cy, float r, int screenW, int screenH) {
        int blurTex = LiquidClickGui.getBlurTexture();
        if (blurTex > 0) {
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager.bindTexture(blurTex);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
            KnobShader.INSTANCE.use();
            float d = r * 2.0F;
            KnobShader.INSTANCE.setParams(
                    d / screenW, d / screenH,
                    (cx - r) / screenW, 1 - (cy + r) / screenH);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(cx - r, cy - r);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(cx + r, cy - r);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(cx + r, cy + r);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(cx - r, cy + r);
            GL11.glEnd();
            KnobShader.INSTANCE.stop();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.bindTexture(prevTexture);
        } else {
            // fallback solid knob
            drawCircle(cx, cy, r, 0xE6FFFFFF);
        }

        // glass hairline only
        drawCircleOutline(cx, cy, r, 1.0F, 0x33FFFFFF);
    }

    public static void drawCircle(float cx, float cy, float r, int color) {
        if (r <= 0) return;
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(cx, cy);
        for (int i = 0; i <= SEGMENTS * 2; i++) {
            double ang = Math.toRadians(i * 360.0 / (SEGMENTS * 2));
            GL11.glVertex2d(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void drawCircleOutline(float cx, float cy, float r, float thickness, int color) {
        if (r <= 0) return;
        float a = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(red, green, blue, a);
        GL11.glLineWidth(thickness);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= SEGMENTS * 2; i++) {
            double ang = Math.toRadians(i * 360.0 / (SEGMENTS * 2));
            GL11.glVertex2d(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static int lerpColor(int from, int to, float t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        int a1 = (from >> 24) & 255, r1 = (from >> 16) & 255, g1 = (from >> 8) & 255, b1 = from & 255;
        int a2 = (to >> 24) & 255, r2 = (to >> 16) & 255, g2 = (to >> 8) & 255, b2 = to & 255;
        return ((int) (a1 + (a2 - a1) * t) << 24)
                | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8)
                | (int) (b1 + (b2 - b1) * t);
    }

    // ------------------------------------------------------------------
    // Textured quad (for the backdrop blur pipeline)
    // ------------------------------------------------------------------

    public static void drawTextureQuad(int texId, int w, int h) {
        // MUST go through GlStateManager so its texture-binding cache stays
        // in sync — vanilla font rendering relies on that cache.
        GlStateManager.bindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(w, 0);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, h);
        GL11.glEnd();
        GlStateManager.bindTexture(0);
    }

    /** Textured quad clipped to a rounded (super-ellipse) path — used for
     *  frosted-glass panel backdrops so the blur follows the panel's corner
     *  curvature instead of a hard rectangle. */
    public static void drawRoundedTextureQuad(int texId, float x, float y, float w, float h, float r,
                                              float fullW, float fullH, float alpha) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2.0F, h / 2.0F));
        GlStateManager.bindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, alpha);
        GL11.glBegin(GL11.GL_POLYGON);
        arcTex(x + r, y + r, r, 180, 270, fullW, fullH);
        arcTex(x + w - r, y + r, r, 270, 360, fullW, fullH);
        arcTex(x + w - r, y + h - r, r, 0, 90, fullW, fullH);
        arcTex(x + r, y + h - r, r, 90, 180, fullW, fullH);
        GL11.glEnd();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(0);
    }

    private static void arcTex(float cx, float cy, float r, int startDeg, int endDeg, float fullW, float fullH) {
        for (int i = 0; i <= SEGMENTS; i++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * i / (double) SEGMENTS);
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double px = cx + r * Math.signum(ca) * Math.pow(Math.abs(ca), 0.5);
            double py = cy + r * Math.signum(sa) * Math.pow(Math.abs(sa), 0.5);
            GL11.glTexCoord2d(px / fullW, 1.0 - py / fullH);
            GL11.glVertex2d(px, py);
        }
    }

    /**
     * True Liquid Glass shader (ported from github.com/shuding/liquid-glass):
     * SDF rounded-rect mask + edge displacement refraction (the glass edge
     * bends/magnifies like a lens — pixels near the border sample towards
     * the centre), plus contrast/brightness/saturation enhancement.
     */
    public static final class LiquidGlassShader extends Shader {
        public static final LiquidGlassShader INSTANCE = new LiquidGlassShader();

        private static final String FRAG = "#version 120\n"
                + "uniform sampler2D tex;\n"
                + "uniform vec2 uHalf;\n"
                + "uniform float uRadius;\n"
                + "uniform float uCapsule;\n"
                + "uniform vec2 uPanelSize;\n"
                + "uniform vec2 uPanelOrigin;\n"
                // anisotropic rounded-box SDF: scale Y to the same unit as X,
                // then the corner radius is isotropic. (The iq vec4 variant
                // drops the Y radius, breaking collapsed/short panels.)
                // Callers pass uHalf with b.y = 0.5 * h / w so the scaled
                // space is isotropic in screen pixels — the mask corner is a
                // true circle of the requested radius in both axes.
                // Corner norm is the n=4 super-ellipse (not euclidean length)
                // so the glass mask edge sits exactly on the drawn squircle
                // outline (arcSq): both are |x|^4 + |y|^4 = r^4.
                + "float sdRoundBox(vec2 p, vec2 b, float r) {\n"
                + "  float sy = b.y / max(b.x, 1e-5);\n"
                + "  vec2 q = vec2(p.x, p.y * sy);\n"
                + "  vec2 bs = vec2(b.x, b.x * sy);\n"
                + "  vec2 q2 = abs(q) - bs + r;\n"
                + "  vec2 c = max(q2, 0.0);\n"
                + "  vec2 c2 = c * c;\n"
                + "  float corner = c2.x * c2.x + c2.y * c2.y;\n"
                + "  return min(max(q2.x, q2.y), 0.0) + sqrt(sqrt(corner)) - r;\n"
                + "}\n"
                + "void main(void) {\n"
                + "  vec2 uv = gl_TexCoord[0].xy;\n"
                + "  vec2 p = uv - 0.5;\n"
                + "  float dist;\n"
                + "  if (uCapsule > 0.5) {\n"
                // true capsule (tank tread): straight middle + semicircle ends,
                // radius = uHalf.y in the screen-isotropic scaled space
                + "    vec2 q = vec2(p.x, p.y * (uHalf.y / max(uHalf.x, 1e-5)));\n"
                + "    vec2 qc = vec2(abs(q.x) - (uHalf.x - uHalf.y), q.y);\n"
                + "    dist = length(max(qc, vec2(0.0))) + min(max(qc.x, qc.y), 0.0) - uHalf.y;\n"
                + "  } else {\n"
                + "    dist = sdRoundBox(p, uHalf, uRadius);\n"
                + "  }\n"
                // rounded mask with a soft edge
                + "  float alpha = 1.0 - smoothstep(-0.04, 0.02, dist);\n"
                // full-panel refraction: the whole backdrop bends toward the
                // centre like a lens (centre ~10%, mid ~45%, edge ~75%),
                // strongest at the borders. mix(0.2, 1.0, ...) caps the edge
                // displacement at 80% — 20% weaker refraction.
                + "  float disp = 1.0 - smoothstep(-0.55, 0.40, dist + 0.05);\n"
                + "  float scaled = mix(0.2, 1.0, smoothstep(0.0, 1.0, disp));\n"
                + "  vec2 sampleUV = p * scaled + 0.5;\n"
                + "  vec2 fullUV = vec2(sampleUV.x * uPanelSize.x + uPanelOrigin.x,\n"
                + "                     uPanelOrigin.y - sampleUV.y * uPanelSize.y);\n"
                // the texture is already blurred by the two-pass separable
                // gaussian (BlurPassShader), so sample it directly — blur and
                // refraction are decoupled, which keeps everything ghost-free
                + "  vec4 c = texture2D(tex, fullUV);\n"
                // no colour enhancement — keep the backdrop true to the game
                + "  gl_FragColor = vec4(c.rgb, c.a * alpha);\n"
                + "}";

        private LiquidGlassShader() {
            super(FRAG);
            setUniform("tex");
            setUniform("uHalf");
            setUniform("uRadius");
            setUniform("uCapsule");
            setUniform("uPanelSize");
            setUniform("uPanelOrigin");
        }

        @Override
        public void onLink() {
        }

        @Override
        public void onUse() {
            if (programId < 0) return;
            GL20.glUseProgram(programId);
            int loc = getUniformLocationCached("tex");
            if (loc >= 0) GL20.glUniform1i(loc, 0);
        }

        public void setPanelParams(float halfW, float halfH, float radius,
                                   float sizeX, float sizeY, float ox, float oy) {
            if (programId < 0) return;
            int l;
            if ((l = getUniformLocationCached("uHalf")) >= 0) GL20.glUniform2f(l, halfW, halfH);
            if ((l = getUniformLocationCached("uRadius")) >= 0) GL20.glUniform1f(l, radius);
            // panels are rounded boxes, not capsules — reset the flag so a
            // previous capsule draw (search bar / pill) never leaks in
            if ((l = getUniformLocationCached("uCapsule")) >= 0) GL20.glUniform1f(l, 0);
            if ((l = getUniformLocationCached("uPanelSize")) >= 0) GL20.glUniform2f(l, sizeX, sizeY);
            if ((l = getUniformLocationCached("uPanelOrigin")) >= 0) GL20.glUniform2f(l, ox, oy);
        }

        /** True capsule mask (semicircle ends) instead of the rounded box. */
        public void setCapsule(boolean capsule) {
            if (programId < 0) return;
            int l = getUniformLocationCached("uCapsule");
            if (l >= 0) GL20.glUniform1f(l, capsule ? 1 : 0);
        }
    }

    /**
     * Circle variant of the Liquid Glass shader for the toggle knobs:
     * circle SDF mask + edge refraction + gaussian blur sampling the same
     * blurred backdrop as the panels — a tiny piece of real liquid glass.
     */
    public static final class KnobShader extends Shader {
        public static final KnobShader INSTANCE = new KnobShader();

        private static final String FRAG = "#version 120\n"
                + "uniform sampler2D tex;\n"
                + "uniform vec2 uPanelSize;\n"
                + "uniform vec2 uPanelOrigin;\n"
                + "void main(void) {\n"
                + "  vec2 uv = gl_TexCoord[0].xy;\n"
                + "  vec2 p = uv - 0.5;\n"
                + "  float dist = length(p) - 0.5;\n"
                // circle mask
                + "  float alpha = 1.0 - smoothstep(-0.03, 0.03, dist);\n"
                // full-knob refraction, same lens curve as the panels
                + "  float disp = 1.0 - smoothstep(-0.55, 0.40, dist + 0.05);\n"
                + "  float scaled = mix(0.2, 1.0, smoothstep(0.0, 1.0, disp));\n"
                + "  vec2 sampleUV = p * scaled + 0.5;\n"
                + "  vec2 fullUV = vec2(sampleUV.x * uPanelSize.x + uPanelOrigin.x,\n"
                + "                     uPanelOrigin.y - sampleUV.y * uPanelSize.y);\n"
                // texture already blurred by the separable gaussian pass
                + "  vec4 c = texture2D(tex, fullUV);\n"
                + "  gl_FragColor = vec4(c.rgb, c.a * alpha);\n"
                + "}";

        private KnobShader() {
            super(FRAG);
            setUniform("tex");
            setUniform("uPanelSize");
            setUniform("uPanelOrigin");
        }

        @Override
        public void onLink() {
        }

        @Override
        public void onUse() {
            if (programId < 0) return;
            GL20.glUseProgram(programId);
            int loc = getUniformLocationCached("tex");
            if (loc >= 0) GL20.glUniform1i(loc, 0);
        }

        public void setParams(float sizeX, float sizeY, float ox, float oy) {
            if (programId < 0) return;
            int l;
            if ((l = getUniformLocationCached("uPanelSize")) >= 0) GL20.glUniform2f(l, sizeX, sizeY);
            if ((l = getUniformLocationCached("uPanelOrigin")) >= 0) GL20.glUniform2f(l, ox, oy);
        }
    }

    /**
     * Separable 1D gaussian blur pass (σ≈1.5, 9 taps). Two passes (horizontal
     * then vertical) compose a smooth 2D gaussian — the Windows Terminal
     * acrylic algorithm: dense sampling, no pixelation, no ghosting.
     */
    public static final class BlurPassShader extends Shader {
        public static final BlurPassShader INSTANCE = new BlurPassShader();

        private static final String FRAG = "#version 120\n"
                + "uniform sampler2D tex;\n"
                + "uniform vec2 uDirection;\n"
                + "uniform vec2 uOffset;\n"
                + "void main(void) {\n"
                + "  vec2 uv = gl_TexCoord[0].xy;\n"
                + "  vec2 o = uDirection * uOffset;\n"
                + "  vec4 c = texture2D(tex, uv) * 0.2666;\n"
                + "  c += texture2D(tex, uv + o) * 0.2135;\n"
                + "  c += texture2D(tex, uv - o) * 0.2135;\n"
                + "  c += texture2D(tex, uv + o * 2.0) * 0.1096;\n"
                + "  c += texture2D(tex, uv - o * 2.0) * 0.1096;\n"
                + "  c += texture2D(tex, uv + o * 3.0) * 0.0361;\n"
                + "  c += texture2D(tex, uv - o * 3.0) * 0.0361;\n"
                + "  c += texture2D(tex, uv + o * 4.0) * 0.0076;\n"
                + "  c += texture2D(tex, uv - o * 4.0) * 0.0076;\n"
                + "  gl_FragColor = c;\n"
                + "}";

        private BlurPassShader() {
            super(FRAG);
            setUniform("tex");
            setUniform("uDirection");
            setUniform("uOffset");
        }

        @Override
        public void onLink() {
        }

        @Override
        public void onUse() {
            if (programId < 0) return;
            GL20.glUseProgram(programId);
            int loc = getUniformLocationCached("tex");
            if (loc >= 0) GL20.glUniform1i(loc, 0);
        }

        public void setParams(float dirX, float dirY, float ox, float oy) {
            if (programId < 0) return;
            int l;
            if ((l = getUniformLocationCached("uDirection")) >= 0) GL20.glUniform2f(l, dirX, dirY);
            if ((l = getUniformLocationCached("uOffset")) >= 0) GL20.glUniform2f(l, ox, oy);
        }
    }

    /** Saturation boost (saturate 180%) applied to the blurred backdrop. */
    public static final class SaturationShader extends Shader {
        public static final SaturationShader INSTANCE = new SaturationShader();

        private static final String FRAG = "#version 120\n"
                + "uniform sampler2D tex;\n"
                + "void main(void) {\n"
                + "  vec4 c = texture2D(tex, gl_TexCoord[0].xy);\n"
                + "  float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));\n"
                + "  c.rgb = mix(vec3(l), c.rgb, 1.8);\n"
                + "  gl_FragColor = c;\n"
                + "}";

        private SaturationShader() {
            super(FRAG);
            setUniform("tex");
        }

        @Override
        public void onLink() {
        }

        @Override
        public void onUse() {
            if (programId < 0) return;
            GL20.glUseProgram(programId);
            int loc = getUniformLocationCached("tex");
            if (loc >= 0) GL20.glUniform1i(loc, 0);
        }
    }

    private static void arcCirc(float cx, float cy, float r, int startDeg, int endDeg) {
        // true circular arc (for capsule ends)
        for (int i = 0; i <= SEGMENTS; i++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * i / (double) SEGMENTS);
            GL11.glVertex2d(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r);
        }
    }

    private static void arcSq(float cx, float cy, float r, int startDeg, int endDeg) {
        // super-ellipse corner: |x|^4 + |y|^4 = r^4  (Apple continuous curvature)
        for (int i = 0; i <= SEGMENTS; i++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * i / (double) SEGMENTS);
            double ca = Math.cos(ang), sa = Math.sin(ang);
            GL11.glVertex2d(cx + r * Math.signum(ca) * Math.pow(Math.abs(ca), 0.5),
                    cy + r * Math.signum(sa) * Math.pow(Math.abs(sa), 0.5));
        }
    }
}
