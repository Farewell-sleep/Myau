package myau.management.blockage;

import net.minecraft.network.Packet;

public interface PacketValidator {
    boolean isValid(Packet<?> packet);
}
