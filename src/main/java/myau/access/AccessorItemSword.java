package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;

import java.lang.reflect.Field;

public final class AccessorItemSword {
    private static final String OWNER = "net.minecraft.item.ItemSword";
    private static final Field F_MATERIAL =
            MappingBridge.field(OWNER, "material", Item.ToolMaterial.class);
    private AccessorItemSword() {
    }
    public static Item.ToolMaterial getMaterial(ItemSword owner) {
        try {
            return (Item.ToolMaterial) F_MATERIAL.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "material", t);
            return null;
        }
    }
}
