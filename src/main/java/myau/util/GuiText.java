package myau.util;

/**
 * Text helpers for UI layouts: trims strings to fit a pixel budget.
 */
public class GuiText {

    /**
     * Trims {@code text} from the left so that it fits within {@code maxWidth}
     * pixels at the given font size, appending "..." when truncated.
     */
    public static String trim(String text, int maxWidth, float size) {
        if (text == null || text.isEmpty()) return "";
        if (maxWidth <= 0) return "";
        if (FontManager.getStringWidth(text, size) <= maxWidth) return text;
        String result = text;
        while (!result.isEmpty() && FontManager.getStringWidth(result + "...", size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
}
