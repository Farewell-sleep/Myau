package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.world.World;

/**
 * SnowFog — render-category module driven by the vanilla weather system.
 *
 * On enable it snapshots the current weather state; while enabled it forces
 * a snowy client render (rain strength = snow density, snow branch enforced
 * in MixinEntityRenderer). On disable it restores the exact snapshot, so the
 * world/server state is never corrupted and no rendering artifacts remain.
 */
public class SnowFog extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty snowDensity = new IntProperty("snow-density", 40, 0, 100);

    private boolean savedRaining;
    private int savedRainTime;
    private float savedRainStrength;
    private boolean savedThundering;
    private int savedThunderTime;
    private boolean weatherSaved;

    public SnowFog() {
        super("SnowFog", false);
    }

    @Override
    public void onEnabled() {
        saveWeather();
        applySnow();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null) return;
        if (!weatherSaved) saveWeather();
        applySnow();
    }

    @Override
    public void onDisabled() {
        restoreWeather();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S2BPacketChangeGameState)) return;
        int state = ((S2BPacketChangeGameState) event.getPacket()).getGameState();
        // 1 = begin rain, 2 = end rain, 7 = rain level, 8 = thunder level
        if (state == 1 || state == 2 || state == 7 || state == 8) {
            event.setCancelled(true);
        }
    }

    private void saveWeather() {
        if (mc.theWorld == null) return;
        this.savedRaining = mc.theWorld.getWorldInfo().isRaining();
        this.savedRainTime = mc.theWorld.getWorldInfo().getRainTime();
        this.savedRainStrength = mc.theWorld.getRainStrength(1.0F);
        this.savedThundering = mc.theWorld.getWorldInfo().isThundering();
        this.savedThunderTime = mc.theWorld.getWorldInfo().getThunderTime();
        this.weatherSaved = true;
    }

    private void applySnow() {
        if (mc.theWorld == null) return;
        mc.theWorld.setRainStrength(this.snowDensity.getValue() / 100.0F);
        mc.theWorld.getWorldInfo().setRaining(true);
        mc.theWorld.getWorldInfo().setRainTime(Integer.MAX_VALUE);
        mc.theWorld.getWorldInfo().setThunderTime(0);
        mc.theWorld.getWorldInfo().setThundering(false);
    }

    private void restoreWeather() {
        this.weatherSaved = false;
        if (mc.theWorld == null) return;
        if (this.savedRainStrength > 0.0F) {
            mc.theWorld.setRainStrength(this.savedRainStrength);
        } else {
            mc.theWorld.setRainStrength(0.0F);
        }
        mc.theWorld.getWorldInfo().setRaining(this.savedRaining);
        mc.theWorld.getWorldInfo().setRainTime(this.savedRainTime);
        mc.theWorld.getWorldInfo().setThundering(this.savedThundering);
        mc.theWorld.getWorldInfo().setThunderTime(this.savedThunderTime);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Snow " + this.snowDensity.getValue() + "%"};
    }
}
