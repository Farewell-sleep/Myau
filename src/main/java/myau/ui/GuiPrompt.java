package myau.ui;

import myau.util.FontManager;
import myau.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Lightweight modal text prompt used by the ClickGUI (slider value input,
 * text properties). Dark rounded dialog with a single text field.
 */
public class GuiPrompt extends GuiScreen {

    private static GuiPrompt active;

    private final String title;
    private final String initial;
    private final Consumer<String> onCommit;
    private final GuiScreen parent;
    private GuiTextField field;
    private long openedAt;

    private GuiPrompt(String title, String initial, Consumer<String> onCommit, GuiScreen parent) {
        this.title = title;
        this.initial = initial == null ? "" : initial;
        this.onCommit = onCommit;
        this.parent = parent;
    }

    public static void prompt(String title, String initial, Consumer<String> onCommit, GuiScreen parent) {
        active = new GuiPrompt(title, initial, onCommit, parent);
        active.mc = parent.mc;
        active.initGui();
        parent.mc.displayGuiScreen(active);
    }

    @Override
    public void initGui() {
        this.openedAt = System.currentTimeMillis();
        Keyboard.enableRepeatEvents(true);
        int w = 260;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        this.field = new GuiTextField(0, this.fontRendererObj, x + 12, y + 34, w - 24, 18);
        this.field.setMaxStringLength(64);
        this.field.setText(this.initial);
        this.field.setFocused(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int w = 260;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        float open = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / 180.0F);
        float ease = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);
        RenderUtil.drawRoundedRectWithGl(x - 0.5F, y - 0.5F, x + w + 0.5F, y + h + 0.5F, 10.5F, rgba(255, 255, 255, 18));
        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 10.0F, rgba(14, 15, 20, (int) (200 * ease)));
        FontManager.drawString(this.title, x + 12.0F, y + 12.0F, 0xFFE8EBF2, false, 14.0F);
        this.field.drawTextBox();
        String hint = "Enter  =  OK      Esc  =  Cancel";
        float hintW = FontManager.getStringWidth(hint, 10.0F);
        FontManager.drawString(hint, x + w - 12.0F - hintW, y + h - 14.0F, 0xFF78808F, false, 10.0F);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.close();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            String value = this.field.getText();
            this.close();
            if (this.onCommit != null) {
                try {
                    this.onCommit.accept(value);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        this.field.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        this.field.mouseClicked(mouseX, mouseY, button);
    }

    private void close() {
        Keyboard.enableRepeatEvents(false);
        if (active == this) active = null;
        this.mc.displayGuiScreen(this.parent);
        if (this.parent != null) this.parent.initGui();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
