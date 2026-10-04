package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.risefont.RiseFontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MODULE TOGGLE NOTIFY — 模块开关提示。保留原有聊天输出契约，
 * 同时新增屏幕右上角玻璃卡片通知队列（淡入淡出 ease-out + ON/OFF 主题色）。
 * 视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public final class ModuleToggleNotify extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static ModuleToggleNotify instance;
    private static boolean ready;

    private static final int GLASS_BODY = 0xB91A2028;
    private static final int GLASS_OUTLINE = 0x2AFFFFFF;
    private static final int TEXT_MAIN = 0xFFF2F4F8;
    private static final int ON_COLOR = 0xFF4ADE80;
    private static final int OFF_COLOR = 0xFFF87171;
    private static final float FS = 8.0F;

    private final CopyOnWriteArrayList<Notification> queue = new CopyOnWriteArrayList<>();

    private static final long LIFE = 2200L;
    private static final long FADE_IN = 200L;
    private static final long FADE_OUT = 350L;

    private static final class Notification {
        final String name;
        final boolean enabled;
        final long born;

        Notification(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
            this.born = System.currentTimeMillis();
        }
    }

    public ModuleToggleNotify() {
        super("ModuleToggleNotify", true, true, true);
    }

    /**
     * 由 OpenMyau 注册模块表时调用：创建唯一单例、置 ready，并返回该实例，
     * 使注册进 moduleManager 的对象与 notifyToggle() 入队的对象为同一个
     * （否则卡片进了静态队列却不被事件总线渲染）。
     */
    public static ModuleToggleNotify init() {
        instance = new ModuleToggleNotify();
        ready = true;
        return instance;
    }

    public static boolean isReady() {
        return ready;
    }

    public static void notifyToggle(Module module) {
        if (!ready || module == null || module.isHidden() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        boolean enabled = module.isEnabled();
        // 聊天输出（保留原行为）
        String state = enabled ? "&a&lON" : "&c&lOFF";
        ChatUtil.sendFormatted("&7[&bMyau&7] &f" + module.getName() + " &8» " + state);
        // 屏幕玻璃卡片队列
        if (instance != null) {
            instance.queue.add(new Notification(module.getName(), enabled));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || this.queue.isEmpty() || mc.thePlayer == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        long now = System.currentTimeMillis();

        GlStateManager.pushMatrix();
        RenderUtil.enableRenderState();
        float cardH = 14.0F;
        float gap = 3.0F;
        float y = 4.0F;
        for (Iterator<Notification> it = this.queue.iterator(); it.hasNext(); ) {
            Notification n = it.next();
            long age = now - n.born;
            if (age > LIFE) {
                it.remove();
                continue;
            }
            // 透明度：淡入 ease-out → 保持 → 淡出
            float alpha = 1.0F;
            if (age < FADE_IN) {
                float t = age / (float) FADE_IN;
                alpha = 1.0F - (float) Math.pow(1.0F - t, 3.0);
            } else if (age > LIFE - FADE_OUT) {
                float t = (LIFE - age) / (float) FADE_OUT;
                alpha = Math.max(0.0F, t);
            }
            String state = n.enabled ? "ON" : "OFF";
            int statusColor = n.enabled ? ON_COLOR : OFF_COLOR;
            float nameW = (float) RiseFontManager.getStringWidth(n.name, FS);
            float stateW = (float) RiseFontManager.getStringWidth(state, FS);
            float cardW = nameW + stateW + 16.0F;

            float x = sr.getScaledWidth() - cardW - 4.0F;
            int body = GLASS_BODY & 0x00FFFFFF | ((int) (0xB9 * alpha) << 24);
            int outline = GLASS_OUTLINE & 0x00FFFFFF | ((int) (0x2A * alpha) << 24);
            RenderUtil.drawRoundedRect(x, y, cardW, cardH, 4.0F, body);
            RenderUtil.drawRoundedOutline(x, y, x + cardW, y + cardH, 4.0F, 1.0F, outline);
            // 左强调条
            int accent = (statusColor & 0x00FFFFFF) | ((int) (255 * alpha) << 24);
            RenderUtil.drawRoundedRect(x, y, 2.0F, cardH, 1.0F, accent);
            int nameColor = TEXT_MAIN & 0x00FFFFFF | ((int) (0xF2 * alpha) << 24);
            int stColor = (statusColor & 0x00FFFFFF) | ((int) (255 * alpha) << 24);
            RiseFontManager.drawString(n.name, x + 6.0F, y + 3.0F, nameColor, false, FS);
            RiseFontManager.drawString(state, x + cardW - stateW - 5.0F, y + 3.0F, stColor, false, FS);
            y += cardH + gap;
        }
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }
}
