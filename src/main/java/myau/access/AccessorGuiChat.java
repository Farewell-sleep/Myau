package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;

import java.lang.reflect.Field;

public final class AccessorGuiChat {
    private static final String OWNER = "net.minecraft.client.gui.GuiChat";
    private static final Field F_INPUTFIELD =
            MappingBridge.field(OWNER, "inputField", GuiTextField.class);
    private AccessorGuiChat() {
    }
    public static GuiTextField getInputField(GuiChat owner) {
        try {
            return (GuiTextField) F_INPUTFIELD.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "inputField", t);
            return null;
        }
    }
}
