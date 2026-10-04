package myau.module;

import myau.OpenMyau;
import myau.module.modules.HUD;
import myau.util.KeyBindUtil;

public abstract class Module {
    protected final String name;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    protected final boolean defaultForced;
    protected boolean enabled;
    protected int key;
    protected boolean hidden;
    protected boolean forced;

    public Module(String name, boolean enabled) {
        this(name, enabled, false, false);
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this(name, enabled, hidden, false);
    }

    public Module(String name, boolean enabled, boolean hidden, boolean forced) {
        this.name = name;
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
        this.forced = this.defaultForced = forced;
    }

    public String getName() {
        return this.name;
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
        }
    }

    public boolean toggle() {
        if (this.forced && this.enabled) {
            return false;
        }
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((HUD) OpenMyau.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
                OpenMyau.moduleManager.playSound();
            }
            return true;
        } else {
            return false;
        }
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int integer) {
        this.key = integer;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean boolean1) {
        this.hidden = boolean1;
    }

    public boolean isForced() {
        return this.forced;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }
}
