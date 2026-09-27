package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

/**
 * AUTO THROW — while the player is attacking (attack key held), automatically
 * switches to an egg or snowball in the hotbar and throws it at the target.
 * Uses the same projectile detection (ItemUtil.isProjectile) and the same
 * use-item packet trick (C08PacketPlayerBlockPlacement) as KillAura/AutoHeal.
 */
public class AutoThrow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty delay = new IntProperty("Delay", 250, 50, 1000);

    private long lastThrow = 0;

    public AutoThrow() {
        super("AutoThrow", false);
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;

        int slot = this.findThrowableSlot();
        if (slot == -1) return;

        long now = System.currentTimeMillis();
        if (now - lastThrow < this.delay.getValue()) return;

        mc.thePlayer.inventory.currentItem = slot;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !ItemUtil.isProjectile(held)) return;

        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.swingItem();
        lastThrow = now;
    }

    private int findThrowableSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && ItemUtil.isProjectile(stack)) {
                return i;
            }
        }
        return -1;
    }
}