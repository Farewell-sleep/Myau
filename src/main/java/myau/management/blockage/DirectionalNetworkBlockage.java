package myau.management.blockage;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public abstract class DirectionalNetworkBlockage<T extends INetHandler> {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    private final List<NetworkBlock> blockageList = new ArrayList<NetworkBlock>();
    private final List<BlockedPacket> packetList = new ArrayList<BlockedPacket>();
    private long id;

    protected final Object lock = new Object();

    public NetworkBlock newBlockage() {
        return this.newBlockage(null, null);
    }

    public NetworkBlock newBlockage(PacketTransformer packetTransformer, PacketValidator packetValidator) {
        return this.newBlockage(packetTransformer, packetValidator, false);
    }

    public NetworkBlock newBlockage(PacketTransformer packetTransformer, PacketValidator packetValidator, boolean priority) {
        synchronized (this.lock) {
            NetworkBlock blockage = new NetworkBlock(packetTransformer, packetValidator, priority, this.getBlockageId());
            this.blockageList.add(blockage);
            return blockage;
        }
    }

    private long getBlockageId() {
        long id = this.id;
        for (NetworkBlock block : this.blockageList) {
            if (block.isPriority() && id >= block.getId()) {
                id = block.getId();
            }
        }
        return id;
    }

    public void releaseBlockage(NetworkBlock networkBlock) {
        synchronized (this.lock) {
            if (this.blockageList.contains(networkBlock)) {
                this.blockageList.remove(networkBlock);
                this.sort();
                this.flush(
                        this.blockageList.isEmpty() ? null : Long.valueOf(this.blockageList.get(0).getId()),
                        networkBlock.getPacketTransformer()
                );
            }
        }
    }

    private void flush(Long id, PacketTransformer packetTransformer) {
        NetworkManager connection = getConnection();
        List<Packet<?>> packetsToFlush = new ArrayList<Packet<?>>();
        for (Iterator<BlockedPacket> iterator = this.packetList.iterator(); iterator.hasNext(); ) {
            BlockedPacket blockedPacket = iterator.next();
            if (id == null || blockedPacket.getId() < id.longValue()) {
                if (connection != null) {
                    Packet<?> packet = blockedPacket.getPacket();
                    if (packetTransformer != null) {
                        packet = packetTransformer.transform(packet);
                    }
                    if (packet != null) {
                        packetsToFlush.add(packet);
                    }
                }
                iterator.remove();
            }
        }
        if (connection == null || connection != this.boundConnection) {
            return;
        }
        for (Packet<?> packet : packetsToFlush) {
            this.flushPacket(connection, packet);
        }
    }

    protected abstract void flushPacket(NetworkManager connection, Packet<?> packet);

    private NetworkManager boundConnection;

    private void dropIfConnectionChanged() {
        NetworkManager connection = getConnection();
        if (this.boundConnection != connection) {
            this.boundConnection = connection;
            this.blockageList.clear();
            this.packetList.clear();
            this.id = 0L;
        }
    }

    public boolean isBlocked(Packet<?> packet) {
        synchronized (this.lock) {
            this.dropIfConnectionChanged();
            if (!this.blockageList.isEmpty()) {
                this.sort();
                NetworkBlock blockage = this.blockageList.get(0);
                PacketValidator packetValidator = blockage.getPacketValidator();
                boolean valid = false;
                if (packetValidator == null) {
                    valid = true;
                } else if (packetValidator.isValid(packet)) {
                    valid = true;
                } else {
                    for (NetworkBlock block : this.blockageList) {
                        if (block.equals(blockage)) {
                            continue;
                        }
                        PacketValidator blockValidator = block.getPacketValidator();
                        if (blockValidator == null || blockValidator.isValid(packet)) {
                            valid = true;
                            break;
                        }
                    }
                }
                if (valid) {
                    this.packetList.add(new BlockedPacket(packet, this.id));
                    this.id++;
                    return true;
                }
            }
            return false;
        }
    }

    private void sort() {
        synchronized (this.lock) {
            this.blockageList.sort(Comparator.comparingLong(new java.util.function.ToLongFunction<NetworkBlock>() {
                @Override
                public long applyAsLong(NetworkBlock value) {
                    return value.getId();
                }
            }));
            this.packetList.sort(Comparator.comparingLong(new java.util.function.ToLongFunction<BlockedPacket>() {
                @Override
                public long applyAsLong(BlockedPacket value) {
                    return value.getId();
                }
            }));
        }
    }

    public void reset() {
        synchronized (this.lock) {
            this.blockageList.clear();
            this.packetList.clear();
            this.id = 0L;
            this.boundConnection = getConnection();
        }
    }

    public boolean isAnyBlockages() {
        synchronized (this.lock) {
            return !this.blockageList.isEmpty();
        }
    }

    protected static NetworkManager getConnection() {
        return mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager();
    }
}
