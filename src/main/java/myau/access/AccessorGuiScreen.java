package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Method;

public final class AccessorGuiScreen {
    private static final String OWNER = "net.minecraft.client.gui.GuiScreen";
    private static final Method M_MOUSECLICKED =
            MappingBridge.method(OWNER, "mouseClicked", int.class, int.class, int.class);
    private AccessorGuiScreen() {
    }
    public static void callMouseClicked(GuiScreen owner, int mouseX, int mouseY, int mouseButton) {
        try {
            M_MOUSECLICKED.invoke(owner, mouseX, mouseY, mouseButton);
        } catch (Throwable t) {
            Access.report(OWNER, "mouseClicked", t);
        }
    }
}
