package myau.management.blockage;

import net.minecraft.network.Packet;

public final class BlockedPacket {
    private final Packet<?> packet;
    private final long id;

    public BlockedPacket(Packet<?> packet, long id) {
        this.packet = packet;
        this.id = id;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    public long getId() {
        return this.id;
    }
}
