package myau.risefont;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Skidded from Rise (com.alan.clients.util.font.impl.rise.FontUtil).
 * Loads AWT fonts from mod resources or filesystem.
 */
public class RiseFontUtil {
    private RiseFontUtil() {
    }

    public static Font loadFromResource(String resourcePath, int size) {
        try {
            return Font.createFont(0, Minecraft.getMinecraft().getResourceManager()
                            .getResource(new ResourceLocation(resourcePath)).getInputStream())
                    .deriveFont((float) size);
        } catch (FontFormatException | IOException e) {
            return null;
        }
    }

    public static Font loadFromFile(String filePath, int size) {
        try {
            return Font.createFont(0, new File(filePath)).deriveFont((float) size);
        } catch (FontFormatException | IOException e) {
            return null;
        }
    }
}
