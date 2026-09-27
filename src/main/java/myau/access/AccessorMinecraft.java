package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

public final class AccessorMinecraft {
    private static final String OWNER = "net.minecraft.client.Minecraft";
    private static final Field F_LOGGER =
            MappingBridge.field(OWNER, "logger", Logger.class);
    private static final Field F_TIMER =
            MappingBridge.field(OWNER, "timer", Timer.class);
    private static final Field F_RIGHTCLICKDELAYTIMER =
            MappingBridge.field(OWNER, "rightClickDelayTimer", int.class);
    private static final Field F_LEFTCLICKCOUNTER =
            MappingBridge.field(OWNER, "leftClickCounter", int.class);
    private AccessorMinecraft() {
    }
    public static void setLeftClickCounter(Minecraft owner, int value) {
        try {
            F_LEFTCLICKCOUNTER.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "leftClickCounter", t);
        }
    }

    public static Logger getLogger(Minecraft owner) {
        try {
            return (Logger) F_LOGGER.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "logger", t);
            return null;
        }
    }
    public static Timer getTimer(Minecraft owner) {
        try {
            return (Timer) F_TIMER.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "timer", t);
            return null;
        }
    }
    public static int getRightClickDelayTimer(Minecraft owner) {
        try {
            return F_RIGHTCLICKDELAYTIMER.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "rightClickDelayTimer", t);
            return 0;
        }
    }
    public static void setRightClickDelayTimer(Minecraft owner, int value) {
        try {
            F_RIGHTCLICKDELAYTIMER.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "rightClickDelayTimer", t);
        }
    }
}
