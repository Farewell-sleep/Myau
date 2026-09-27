package myau.management.blockage;

public final class NetworkBlock {
    private PacketTransformer packetTransformer;
    private final PacketValidator packetValidator;
    private final boolean priority;
    private final long id;
    private final long creationTime = System.currentTimeMillis();

    public NetworkBlock(PacketTransformer packetTransformer, PacketValidator packetValidator, boolean priority, long id) {
        this.packetTransformer = packetTransformer;
        this.packetValidator = packetValidator;
        this.priority = priority;
        this.id = id;
    }

    public PacketTransformer getPacketTransformer() {
        return this.packetTransformer;
    }

    public void setPacketTransformer(PacketTransformer packetTransformer) {
        this.packetTransformer = packetTransformer;
    }

    public PacketValidator getPacketValidator() {
        return this.packetValidator;
    }

    public long getId() {
        return this.id;
    }

    public boolean isPriority() {
        return this.priority;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }
        return this.id == ((NetworkBlock) other).id;
    }

    @Override
    public int hashCode() {
        return (int) (this.id ^ (this.id >>> 32));
    }
}
