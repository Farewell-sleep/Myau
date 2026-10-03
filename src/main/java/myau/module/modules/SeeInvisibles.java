package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.RenderLivingEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;

/**
 * SEE INVISIBLES — skidded from Onxy (SeeInvisiblesModule).
 * Renders invisible entities. The model pass is forced via
 * MixinRendererLivingEntity (isInvisible redirect); this module
 * applies the translucency and the per-type toggles.
 */
public class SeeInvisibles extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty players = new BooleanProperty("Players", true);
    public final BooleanProperty mobs = new BooleanProperty("Mobs", true);
    public final BooleanProperty animals = new BooleanProperty("Animals", false);
    public final PercentProperty opacity = new PercentProperty("Opacity", 60);

    public SeeInvisibles() {
        super("SeeInvisibles", false);
    }

    public boolean shouldShow(EntityLivingBase entity) {
        if (entity == null || !entity.isInvisible()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            return this.players.getValue();
        } else if (entity instanceof IMob) {
            return this.mobs.getValue();
        } else if (entity instanceof EntityAnimal) {
            return this.animals.getValue();
        }
        return false;
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        EntityLivingBase entity = event.getEntity();
        if (!this.shouldShow(entity)) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            float alpha = this.opacity.getValue().floatValue() / 100.0F;
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        } else {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
        }
    }
}
