package myau.ui.liquid;

import myau.module.Module;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
import myau.module.modules.LeaderFontManager;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All Liquid Glass property widgets in one place.
 *
 * 视觉语言（与规范第 2 节一致）：
 *  - 半透明玻璃底 + 1px 发丝描边（GLASS_OUTLINE）
 *  - 主题蓝 ACCENT 作强调（滑块填充 / 圆头描边 / 选中文字）
 *  - 圆角 4-6px；滑块轨道为胶囊形，滑块头为圆形带 accent 描边
 *  - 颜色控件显示色板圆点；绑定控件显示键位芯片 + 「点击设置」提示
 *
 * 字体统一走 {@link FontManager}（舒窈衡水），条目 10px、提示 9px。
 */
public final class GlassControls {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat DF = new DecimalFormat("0.00", new java.text.DecimalFormatSymbols(Locale.US));

    /** 条目字号（面板内属性行）。 */
    public static final float SIZE_LABEL = 10.0F;
    /** 芯片 / 小字提示字号。 */
    public static final float SIZE_SMALL = 9.0F;

    /**
     * 渲染期透明度覆盖（0..1）。由 GlassModuleEntry 在条目展开动画时设置，
     * 让子设置区随行高一起淡入淡出；默认 1，无任何影响。
     */
    public static float RENDER_ALPHA = 1.0F;

    private GlassControls() {
    }

    /** 把颜色的 alpha 分量乘上 RENDER_ALPHA（不影响 RGB）。 */
    private static int fade(int color) {
        if (RENDER_ALPHA >= 1.0F) return color;
        int a = Math.round(((color >> 24) & 255) * RENDER_ALPHA);
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    private static void drawText(String text, float x, float y, int color) {
        LeaderFontManager.drawString(text, x, y, fade(color), true, SIZE_LABEL);
    }

    private static void drawText(String text, float x, float y, int color, float size) {
        LeaderFontManager.drawString(text, x, y, fade(color), true, size);
    }

    private static int textWidth(String text) {
        return LeaderFontManager.getStringWidth(text, SIZE_LABEL);
    }

    private static int textWidth(String text, float size) {
        return LeaderFontManager.getStringWidth(text, size);
    }

    public static List<GlassComponent> buildFor(Module module, int width) {
        List<GlassComponent> out = new ArrayList<>();
        List<Property<?>> props = null;
        try {
            props = myau.OpenMyau.propertyManager.properties.get(module.getClass());
        } catch (Exception ignored) {
        }
        if (props != null) {
            for (Property<?> p : props) {
                if (p instanceof BooleanProperty) {
                    out.add(new CheckBox((BooleanProperty) p));
                } else if (p instanceof FloatProperty) {
                    FloatProperty fp = (FloatProperty) p;
                    out.add(new Slider(p.getName(), p::getValue, v -> fp.setValue(v.floatValue()),
                            fp.getMinimum(), fp.getMaximum()));
                } else if (p instanceof IntProperty) {
                    IntProperty ip = (IntProperty) p;
                    out.add(new Slider(p.getName(), p::getValue, v -> ip.setValue(v.intValue()),
                            ip.getMinimum().floatValue(), ip.getMaximum().floatValue()));
                } else if (p instanceof PercentProperty) {
                    PercentProperty pp = (PercentProperty) p;
                    out.add(new Slider(p.getName(), p::getValue, v -> pp.setValue(v.intValue()),
                            0.0F, 100.0F));
                } else if (p instanceof ModeProperty) {
                    out.add(new ModeCycle((ModeProperty) p));
                } else if (p instanceof ColorProperty) {
                    out.add(new ColorRow((ColorProperty) p));
                } else if (p instanceof TextProperty) {
                    out.add(new TextField((TextProperty) p));
                }
            }
        }
        out.add(new HideToggle(module));
        out.add(new BindButton(module));
        return out;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Toggle switch（BooleanProperty）——液体玻璃小开关，保持不变。 */
    public static class CheckBox extends GlassComponent {
        private final BooleanProperty prop;

        public CheckBox(BooleanProperty prop) {
            this.prop = prop;
            this.h = 18;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            boolean on = prop.getValue();
            drawText(prop.getName(), x + 2, y + 2, on ? GlassRenderer.TEXT_MAIN : GlassRenderer.TEXT_DIM);
            GlassRenderer.drawCapsule(x + w - 26, y + 2, 24, 12, on, GlassRenderer.ACCENT);
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                prop.setValue(!prop.getValue());
            }
        }
    }

    /**
     * 胶囊轨道滑块（Float / Int / Percent）。
     * 轨道与填充均为胶囊形；滑块头为圆形白色圆点，带 1px accent 描边。
     */
    public static class Slider extends GlassComponent {
        private final String name;
        private final java.util.function.Supplier<Object> getter;
        private final java.util.function.Consumer<Float> setter;
        private final float min, max;
        private boolean dragging;
        private boolean typing;
        private String typed = "";

        public Slider(String name, java.util.function.Supplier<Object> getter,
                      java.util.function.Consumer<Float> setter, float min, float max) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.h = 20;
        }

        private float value() {
            Object v = getter.get();
            return v instanceof Number ? ((Number) v).floatValue() : 0;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            float v = value();
            float t = (max - min) <= 0 ? 0 : clamp01((v - min) / (max - min));
            String label = name + ": " + DF.format(v);
            drawText(label, x + 2, y + 1, typing ? GlassRenderer.TEXT_MAIN : GlassRenderer.TEXT_DIM);

            // 胶囊轨道（3px 高）
            float ty = y + h - 5.0F;
            GlassRenderer.drawCapsuleRect(x + 2, ty, w - 4, 3.0F, fade(0x33FFFFFF));
            // accent 填充
            float fillW = (w - 4) * t;
            if (fillW > 0.5F) {
                GlassRenderer.drawCapsuleRect(x + 2, ty, fillW, 3.0F, fade(GlassRenderer.ACCENT));
            }
            // 圆形滑块头：白圆点 + accent 描边
            float kx = x + 2 + fillW;
            float ky = ty + 1.5F;
            GlassRenderer.drawCircle(kx, ky, 3.4F, fade(0xF2FFFFFF));
            GlassRenderer.drawCircleOutline(kx, ky, 3.4F, 1.0F, fade(GlassRenderer.ACCENT));

            if (typing && !typed.isEmpty()) {
                drawText(typed, x + w - textWidth(typed) - 2, y + 1, GlassRenderer.ACCENT, SIZE_SMALL);
            }
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                dragging = true;
                apply(mouseX);
            } else if (button == 1 && isHovered(mouseX, mouseY)) {
                typing = !typing;
                typed = "";
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY, int button) {
            dragging = false;
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
            if (!typing) return;
            if (keyCode == Keyboard.KEY_RETURN) {
                try {
                    setter.accept(Float.parseFloat(typed));
                } catch (Exception ignored) {
                }
                typing = false;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
                return;
            }
            if (Character.isDigit(typedChar) || typedChar == '.' || typedChar == '-') {
                typed += typedChar;
            }
        }

        private void apply(int mouseX) {
            float t = clamp01((mouseX - (x + 2)) / (float) (w - 4));
            setter.accept(min + (max - min) * t);
        }

        public boolean isTyping() {
            return typing;
        }

        public String getTyped() {
            return typed;
        }
    }

    /** 模式循环芯片（ModeProperty）：玻璃小药丸 + 发丝描边，左键前进 / 右键后退。 */
    public static class ModeCycle extends GlassComponent {
        private final ModeProperty prop;

        public ModeCycle(ModeProperty prop) {
            this.prop = prop;
            this.h = 18;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            String name = prop.getName();
            String mode = prop.getModeString();
            drawText(name, x + 2, y + 2, GlassRenderer.TEXT_DIM);
            // 右侧模式芯片：半透明玻璃底 + 发丝描边，圆角 4px
            int chipW = textWidth(mode, SIZE_SMALL) + 12;
            float chipX = x + w - chipW - 2;
            GlassRenderer.drawRoundedRect(chipX, y + 2, chipW, 11, 4,
                    fade(isHovered(mouseX, mouseY) ? 0x26FFFFFF : 0x14FFFFFF));
            GlassRenderer.drawRoundedOutline(chipX + 0.5F, y + 2.5F, chipW - 1, 10, 3.5F, 1.0F,
                    fade(GlassRenderer.GLASS_OUTLINE));
            drawText(mode, chipX + 6, y + 3, GlassRenderer.ACCENT, SIZE_SMALL);
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (isHovered(mouseX, mouseY)) {
                if (button == 0) prop.nextMode();
                else if (button == 1) prop.previousMode();
            }
        }
    }

    /** 隐藏开关（Module.hidden）：开启后模块从 HUD 数组列表中消失。 */
    public static class HideToggle extends GlassComponent {
        private final Module module;

        public HideToggle(Module module) {
            this.module = module;
            this.h = 18;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            boolean on = module.isHidden();
            drawText("Hide", x + 2, y + 2, on ? GlassRenderer.TEXT_MAIN : GlassRenderer.TEXT_DIM);
            GlassRenderer.drawCapsule(x + w - 26, y + 2, 24, 12, on, GlassRenderer.ACCENT);
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                module.setHidden(!module.isHidden());
            }
        }
    }

    /**
     * 绑定按钮：左侧名称，右侧键位玻璃芯片。
     * 点击进入「点击设置」态（芯片文字变 accent 提示），再按任意键完成绑定；右键清除。
     */
    public static class BindButton extends GlassComponent {
        private final Module module;
        private boolean binding;

        public BindButton(Module module) {
            this.module = module;
            this.h = 18;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            drawText("Bind", x + 2, y + 2, GlassRenderer.TEXT_DIM);
            // 右侧键位芯片：半透明玻璃底 + 发丝描边
            String key = binding ? "..." : (module.getKey() == 0 ? "None" : KeyBindUtil.getKeyName(module.getKey()));
            int chipW = textWidth(key, SIZE_SMALL) + 12;
            float chipX = x + w - chipW - 2;
            GlassRenderer.drawRoundedRect(chipX, y + 2, chipW, 11, 4,
                    fade(binding ? GlassRenderer.ACCENT_DIM : 0x14FFFFFF));
            GlassRenderer.drawRoundedOutline(chipX + 0.5F, y + 2.5F, chipW - 1, 10, 3.5F, 1.0F,
                    fade(binding ? GlassRenderer.ACCENT : GlassRenderer.GLASS_OUTLINE));
            drawText(key, chipX + 6, y + 3,
                    binding ? GlassRenderer.ACCENT : GlassRenderer.TEXT_DIM, SIZE_SMALL);
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                binding = !binding;
            } else if (button == 1 && isHovered(mouseX, mouseY)) {
                module.setKey(0);
                binding = false;
            }
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
            if (!binding) return;
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE) {
                module.setKey(0);
            } else {
                module.setKey(keyCode);
            }
            binding = false;
        }
    }

    /**
     * 颜色行（ColorProperty）：右侧色板圆点（玻璃描边），左键展开 R/G/B 三个胶囊滑块。
     */
    public static class ColorRow extends GlassComponent {
        private final ColorProperty prop;
        private boolean expanded;
        private final Slider r, g, b;

        public ColorRow(ColorProperty prop) {
            this.prop = prop;
            this.h = 18;
            this.r = new Slider("R", () -> (prop.getValue() >> 16) & 255,
                    v -> setChannel(16, v.intValue()), 0, 255);
            this.g = new Slider("G", () -> (prop.getValue() >> 8) & 255,
                    v -> setChannel(8, v.intValue()), 0, 255);
            this.b = new Slider("B", () -> prop.getValue() & 255,
                    v -> setChannel(0, v.intValue()), 0, 255);
        }

        private void setChannel(int shift, int value) {
            int rgb = prop.getValue();
            int mask = 255 << shift;
            rgb = (rgb & ~mask) | ((value & 255) << shift);
            prop.setValue(rgb);
        }

        @Override
        public int getHeight() {
            // 色板行 16 + R/G/B 滑块（16px 间距）展开后 64
            return expanded ? 64 : 16;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            int rgb = prop.getValue() & 0xFFFFFF;
            drawText(prop.getName(), x + 2, y + 2, GlassRenderer.TEXT_DIM);
            // 色板圆点：实心色 + 玻璃描边
            float dotX = x + w - 9;
            float dotY = y + 7;
            GlassRenderer.drawCircle(dotX, dotY, 5.0F, fade(0xFF000000 | rgb));
            GlassRenderer.drawCircleOutline(dotX, dotY, 5.0F, 1.0F, fade(0x40FFFFFF));
            if (expanded) {
                r.setBounds(x + 6, y + 16, w - 12, 16);
                g.setBounds(x + 6, y + 32, w - 12, 16);
                b.setBounds(x + 6, y + 48, w - 12, 16);
                r.draw(mouseX, mouseY);
                g.draw(mouseX, mouseY);
                b.draw(mouseX, mouseY);
            }
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                if (mouseY < y + 16) {
                    expanded = !expanded;
                    return;
                }
            }
            if (expanded) {
                r.mouseDown(mouseX, mouseY, button);
                g.mouseDown(mouseX, mouseY, button);
                b.mouseDown(mouseX, mouseY, button);
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY, int button) {
            if (expanded) {
                r.mouseReleased(mouseX, mouseY, button);
                g.mouseReleased(mouseX, mouseY, button);
                b.mouseReleased(mouseX, mouseY, button);
            }
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
            if (expanded) {
                r.keyTyped(typedChar, keyCode);
                g.keyTyped(typedChar, keyCode);
                b.keyTyped(typedChar, keyCode);
            }
        }
    }

    /** 内联文本框（TextProperty）。 */
    public static class TextField extends GlassComponent {
        private final TextProperty prop;
        private boolean focused;

        public TextField(TextProperty prop) {
            this.prop = prop;
            this.h = 18;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            String name = prop.getName();
            String value = prop.getValue();
            drawText(name, x + 2, y + 2, GlassRenderer.TEXT_DIM);
            int nameW = textWidth(name) + 6;
            String shown = focused ? value + "_" : value;
            drawText(shown, x + nameW + 2, y + 3, focused ? GlassRenderer.TEXT_MAIN : GlassRenderer.ACCENT);
        }

        @Override
        public void mouseDown(int mouseX, int mouseY, int button) {
            if (button == 0 && isHovered(mouseX, mouseY)) {
                focused = true;
            }
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
            if (!focused) return;
            if (keyCode == Keyboard.KEY_BACK) {
                String v = prop.getValue();
                if (!v.isEmpty()) prop.setValue(v.substring(0, v.length() - 1));
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                focused = false;
                return;
            }
            if (typedChar >= 32) {
                prop.setValue(prop.getValue() + typedChar);
            }
        }
    }
}
