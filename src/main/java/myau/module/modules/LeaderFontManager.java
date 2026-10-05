package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.LeaderFontRender;

/**
 * Skidded 1:1 from Leader-Lite (leader.module.modules.render.FontManager).
 * Controls the global custom font: 13 Leader font modes (default Xylitol) plus
 * a custom-font on/off switch.
 *
 * The static API mirrors RiseFontManager's signatures with the SAME size
 * semantics (size = rendered pixel height). Internally the size is converted
 * to the Leader AWT font size (Leader's CustomFontRenderer draws at half the
 * AWT size), so swapping RiseFontManager -> LeaderFontManager keeps every
 * existing layout / measurement unchanged while the glyphs come from the
 * Leader font chain.
 */
public class LeaderFontManager extends Module {

    /** Kept as a static reference so the static draw helpers can reach it. */
    public static LeaderFontManager INSTANCE;

    public final BooleanProperty customFont = new BooleanProperty("CustomFont", true);
    public final ModeProperty font = new ModeProperty("Font", 0, new String[]{
            "Xylitol", "Xylitol Bold", "HarmonyOS", "HarmonyOS Med",
            "Inter", "NotoSans", "NotoSansSC", "Nursultan",
            "ProductSans", "SF Display", "SF Rounded B", "SF Rounded M", "SF Rounded R"
    });
    private static int lastFontMode = -1;

    public LeaderFontManager() {
        super("FontManager", false);
        INSTANCE = this;
    }

    @Override
    public void onEnabled() {
        syncFontMode();
    }

    @Override
    public void onDisabled() {
    }

    private static boolean useCustom() {
        return INSTANCE != null && INSTANCE.customFont.getValue();
    }

    /** Public switch for components that render through LeaderFontRender directly. */
    public static boolean isCustomFont() {
        return useCustom();
    }

    private static void syncFontMode() {
        if (INSTANCE == null) {
            return;
        }
        int currentMode = INSTANCE.font.getValue();
        if (currentMode != lastFontMode) {
            lastFontMode = currentMode;
            LeaderFontRender.setFontMode(currentMode);
        }
    }

    /** Convert a rendered-pixel size (Rise semantics) to the Leader AWT size. */
    private static float toAwt(float size) {
        return size * 1.6667F;
    }

    // ---- static API (same signatures + semantics as RiseFontManager) ----

    public static void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, false, 14.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        drawString(text, x, y, color, shadow, 14.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size) {
        syncFontMode();
        LeaderFontRender.drawString(text, x, y, color, shadow, toAwt(size), useCustom());
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        drawString(text, x, y, color, true, 14.0F);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size) {
        drawString(text, x, y, color, true, size);
    }

    public static int getStringWidth(String text) {
        return getStringWidth(text, 14.0F);
    }

    public static int getStringWidth(String text, float size) {
        syncFontMode();
        return LeaderFontRender.getStringWidth(text, toAwt(size), useCustom());
    }

    public static int getFontHeight() {
        return getFontHeight(14.0F);
    }

    public static int getFontHeight(float size) {
        syncFontMode();
        return LeaderFontRender.getFontHeight(toAwt(size), useCustom());
    }

    public static float getBaseline(float size) {
        syncFontMode();
        return LeaderFontRender.getBaseline(toAwt(size), useCustom());
    }

    public static float getCapHeight(float size) {
        syncFontMode();
        return LeaderFontRender.getCapHeight(toAwt(size), useCustom());
    }
}
