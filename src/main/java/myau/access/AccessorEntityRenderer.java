package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.renderer.EntityRenderer;

import java.lang.reflect.Method;

public final class AccessorEntityRenderer {
    private static final String OWNER = "net.minecraft.client.renderer.EntityRenderer";
    private static final Method M_SETUPCAMERATRANSFORM =
            MappingBridge.method(OWNER, "setupCameraTransform", float.class, int.class);
    private static final java.lang.reflect.Field F_THIRDPERSONDISTANCE =
            MappingBridge.field(OWNER, "thirdPersonDistance", float.class);
    private AccessorEntityRenderer() {
    }

    public static float getThirdPersonDistance(EntityRenderer owner) {
        try {
            return F_THIRDPERSONDISTANCE.getFloat(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "thirdPersonDistance", t);
            return 4.0F;
        }
    }
    public static void callSetupCameraTransform(EntityRenderer owner, float partialTicks, int pass) {
        try {
            M_SETUPCAMERATRANSFORM.invoke(owner, partialTicks, pass);
        } catch (Throwable t) {
            Access.report(OWNER, "setupCameraTransform", t);
        }
    }
}
