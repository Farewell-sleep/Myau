package myau.management.blockage;

import java.util.ArrayList;
import java.util.List;

public final class TickingBlockHolder {
    private final DirectionalNetworkBlockage<?> networkBlockage;
    private final PacketValidator packetValidator;
    private final List<NetworkBlock> networkBlockList = new ArrayList<NetworkBlock>();

    public TickingBlockHolder(DirectionalNetworkBlockage<?> networkBlockage, PacketValidator packetValidator) {
        this.networkBlockage = networkBlockage;
        this.packetValidator = packetValidator;
    }

    public TickingBlockHolder(DirectionalNetworkBlockage<?> networkBlockage) {
        this(networkBlockage, null);
    }

    public void tick() {
        synchronized (this.networkBlockList) {
            this.networkBlockList.add(this.networkBlockage.newBlockage(null, this.packetValidator));
        }
    }

    public void release(int count) {
        synchronized (this.networkBlockList) {
            while (!this.networkBlockList.isEmpty() && count > 0) {
                this.networkBlockage.releaseBlockage(this.networkBlockList.remove(0));
                count--;
            }
        }
    }

    public void release() {
        synchronized (this.networkBlockList) {
            while (!this.networkBlockList.isEmpty()) {
                this.networkBlockage.releaseBlockage(this.networkBlockList.remove(0));
            }
        }
    }

    public boolean isBlocking() {
        synchronized (this.networkBlockList) {
            return !this.networkBlockList.isEmpty();
        }
    }

    public int getTickCount() {
        synchronized (this.networkBlockList) {
            return this.networkBlockList.size();
        }
    }

    public List<NetworkBlock> getNetworkBlockList() {
        return this.networkBlockList;
    }
}
