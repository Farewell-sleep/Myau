package myau.risefont;

/**
 * Skidded from Rise (com.alan.clients.util.font.FontWeight).
 */
public enum RiseFontWeight {
    NONE(0, ""),
    LIGHT(1, "Light", "light", "LIGHT"),
    MEDIUM(2, "Medium", "medium", "MEDIUM"),
    REGULAR(3, "Regular", "regular", "REGULAR"),
    BOLD(4, "Bold", "bold", "BOLD");

    private final int weight;
    private final String[] aliases;

    RiseFontWeight(int weight, String... aliases) {
        this.weight = weight;
        this.aliases = aliases;
    }

    public int getWeight() {
        return this.weight;
    }

    public String[] getAliases() {
        return this.aliases;
    }
}
