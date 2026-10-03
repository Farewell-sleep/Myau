package myau.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeyBindUtil {
    public static final int NONE = 0;
    public static final int CHAR_OFFSET = 256;

    public static int fromTyped(char typedChar, int keyCode) {
        if (keyCode != Keyboard.KEY_NONE) return keyCode;
        return typedChar == 0 ? NONE : typedChar + CHAR_OFFSET;
    }

    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                case 3:
                    return "MOUSE3";
                case 4:
                    return "MOUSE4";
                case 5:
                    return "MOUSE5";
                case 6:
                    return "MOUSE6";
                case 7:
                    return "MOUSE7";
                default:
                    String buttonName = Mouse.getButtonName(mouseButton);
                    return buttonName != null ? buttonName : "MOUSE" + mouseButton;
            }
        }
        return Keyboard.getKeyName(keyCode);
    }

    private static final ThreadLocal<Boolean> SYNTHETIC = new ThreadLocal<Boolean>();

    public static boolean isSynthetic() {
        return Boolean.TRUE.equals(SYNTHETIC.get());
    }


    public static boolean isKeyDown(int keyCode) {
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    public static void updateKeyState(int keyCode) {
        KeyBindUtil.setKeyBindState(keyCode, keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode));
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }
}
