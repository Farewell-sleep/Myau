package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.renderer.entity.RenderManager;

import java.lang.reflect.Field;

public final class AccessorRenderManager {
    private static final String OWNER = "net.minecraft.client.renderer.entity.RenderManager";
    private static final Field F_RENDERPOSX =
            MappingBridge.field(OWNER, "renderPosX", double.class);
    private static final Field F_RENDERPOSY =
            MappingBridge.field(OWNER, "renderPosY", double.class);
    private static final Field F_RENDERPOSZ =
            MappingBridge.field(OWNER, "renderPosZ", double.class);
    private AccessorRenderManager() {
    }
    public static double getRenderPosX(RenderManager owner) {
        try {
            return F_RENDERPOSX.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosX", t);
            return 0.0D;
        }
    }
    public static double getRenderPosY(RenderManager owner) {
        try {
            return F_RENDERPOSY.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosY", t);
            return 0.0D;
        }
    }
    public static double getRenderPosZ(RenderManager owner) {
        try {
            return F_RENDERPOSZ.getDouble(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "renderPosZ", t);
            return 0.0D;
        }
    }
}
