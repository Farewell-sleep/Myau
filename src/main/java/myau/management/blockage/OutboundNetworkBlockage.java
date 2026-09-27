package myau.management.blockage;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import myau.util.PacketUtil;
import net.minecraft.network.play.INetHandlerPlayServer;

public final class OutboundNetworkBlockage extends DirectionalNetworkBlockage<INetHandlerPlayServer> {
    private static final OutboundNetworkBlockage instance = new OutboundNetworkBlockage();

    public static OutboundNetworkBlockage get() {
        return instance;
    }

    public static void sendPacketDirect(Packet<?> packet) {
        PacketUtil.sendPacketNoEvent(packet);
    }

    @Override
    protected void flushPacket(NetworkManager connection, Packet<?> packet) {
        connection.sendPacket(packet, null);
    }
}
