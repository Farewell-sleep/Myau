package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.settings.KeyBinding;

import java.lang.reflect.Field;

public final class AccessorKeyBinding {
    private static final String OWNER = "net.minecraft.client.settings.KeyBinding";
    private static final Field F_PRESSED =
            MappingBridge.field(OWNER, "pressed", boolean.class);
    private AccessorKeyBinding() {
    }
    public static void setPressed(KeyBinding owner, boolean value) {
        try {
            F_PRESSED.setBoolean(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "pressed", t);
        }
    }
}
