package myau.util.shader;

import myau.util.RenderUtil;
import net.minecraft.client.shader.Framebuffer;

/**
 * Frosted-glass blur helpers (skid from OpenMyau-Plus, only the blur path).
 * prepareBlur() redirects rendering to an offscreen framebuffer so the GUI
 * shapes drawn afterwards become the blur mask; blurEnd() blurs the scene
 * behind those shapes and composites it back onto the main framebuffer.
 */
public class BlurUtils {
    private static Framebuffer stencilFrameBufferBlur = new Framebuffer(1, 1, false);

    public static void prepareBlur() {
        stencilFrameBufferBlur = RenderUtil.createFrameBuffer(stencilFrameBufferBlur);
        stencilFrameBufferBlur.framebufferClear();
        stencilFrameBufferBlur.bindFramebuffer(false);
    }

    public static void blurEnd(int passes, float radius) {
        stencilFrameBufferBlur.unbindFramebuffer();
        KawaseBlur.renderBlur(stencilFrameBufferBlur.framebufferTexture, passes, radius);
    }
}
