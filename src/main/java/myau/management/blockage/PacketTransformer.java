package myau.management.blockage;

import net.minecraft.network.Packet;

public interface PacketTransformer {
    Packet<?> transform(Packet<?> packet);
}
