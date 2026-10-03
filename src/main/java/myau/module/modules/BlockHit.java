package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * BlockHit — block right after every hit and keep the block up long enough
 * that the player is never left open to a combo.
 *
 * The attack itself is handled by KillAura (which releases a manual block for
 * the attack frame). This module re-presses use immediately after the hit and
 * holds it for {@code HoldTicks}; if the player is not physically holding
 * right-click it then restores the physical key state until the next hit.
 */
public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** How many ticks the block stays up after a hit. */
    public final IntProperty holdTicks = new IntProperty("HoldTicks", 4, 1, 40);

    private boolean shouldBlock;
    private int tick;

    public BlockHit() {
        super("BlockHit", false);
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        shouldBlock = false;
        tick = 0;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (ItemUtil.isHoldingSword() && event.getTarget() instanceof EntityLivingBase) {
            shouldBlock = true;
            tick = 0;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()
                || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!shouldBlock) {
            return;
        }
        tick++;
        // re-press use immediately so the block covers the post-attack window
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        if (tick > this.holdTicks.getValue()) {
            // held long enough; restore physical state unless the player is
            // manually holding right-click (then keep it)
            if (!mc.gameSettings.keyBindUseItem.isKeyDown() || !mc.thePlayer.isUsingItem()) {
                KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                shouldBlock = false;
                tick = 0;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.holdTicks.getValue() + "t"};
    }
}
