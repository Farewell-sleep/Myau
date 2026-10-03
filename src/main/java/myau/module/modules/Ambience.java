package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S03PacketTimeUpdate;

/**
 * AMBIENCE — skidded from Onxy (AmbienceModule, time-lock part).
 * Locks the client-side time of day without changing server time.
 */
public class Ambience extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty timeLock = new BooleanProperty("Time lock", true);
    public final IntProperty time = new IntProperty("Time", 6000, 0, 24000);

    public Ambience() {
        super("Ambience", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && this.timeLock.getValue() && event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S03PacketTimeUpdate) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && this.timeLock.getValue() && event.getType() == EventType.POST
                && mc.theWorld != null) {
            mc.theWorld.setWorldTime(this.time.getValue());
        }
    }
}
