package myau.management.blockage;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S45PacketTitle;

public final class InboundNetworkBlockage extends DirectionalNetworkBlockage<INetHandlerPlayClient> {
    private static final InboundNetworkBlockage instance = new InboundNetworkBlockage();

    public static InboundNetworkBlockage get() {
        return instance;
    }

    public static final PacketValidator VISUAL_VALIDATOR = new PacketValidator() {
        @Override
        public boolean isValid(Packet<?> packet) {
            if (packet instanceof S19PacketEntityStatus) {
                int opCode = ((S19PacketEntityStatus) packet).getOpCode();
                return opCode != 2 && opCode != 3;
            }
            if (packet instanceof S1CPacketEntityMetadata) {
                return mc.thePlayer == null
                        || ((S1CPacketEntityMetadata) packet).getEntityId() == mc.thePlayer.getEntityId();
            }
            return !(packet instanceof S0BPacketAnimation
                    || packet instanceof S45PacketTitle
                    || packet instanceof S29PacketSoundEffect
                    || packet instanceof S02PacketChat
                    || packet instanceof S04PacketEntityEquipment);
        }
    };

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void flushPacket(NetworkManager connection, Packet<?> packet) {
        if (mc.getNetHandler() == null) {
            return;
        }
        final Packet raw = packet;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.getNetHandler() != null) {
                    raw.processPacket(mc.getNetHandler());
                }
            }
        });
    }
}
