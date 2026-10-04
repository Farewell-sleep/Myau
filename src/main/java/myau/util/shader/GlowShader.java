package myau.util.shader;

import org.lwjgl.opengl.GL20;

import java.awt.*;

public class GlowShader extends Shader {
    private static final String shader = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D texture;",
            "uniform vec4 color;",
            "void main() {",
            "vec4 st = texture2D(texture, gl_TexCoord[0].st);",
            "gl_FragColor = vec4(color.rgb, st.a > 0.0 ? color.a : 0.0);",
            "}"
    );

    public GlowShader() {
        super(shader);
    }

    @Override
    public void onLink() {
        this.setUniform("texture");
        this.setUniform("color");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
        Integer texLoc = this.getUniformLocationCached("texture");
        if (texLoc != null && texLoc != -1) {
            GL20.glUniform1i(texLoc, 0);
        }
        // 默认颜色置白；实际实体颜色随后由 W(Color) 覆盖。
        // （原实现误把 vec4 写进 sampler 的 location，会触发 GL_INVALID_OPERATION。）
        Integer colorLoc = this.getUniformLocationCached("color");
        if (colorLoc != null && colorLoc != -1) {
            GL20.glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public void W(Color color) {
        GL20.glUniform4f(
                this.getUniformLocationCached("color"),
                (float) color.getRed() / 255.0F,
                (float) color.getGreen() / 255.0F,
                (float) color.getBlue() / 255.0F,
                (float) color.getAlpha() / 255.0F
        );
    }
}
