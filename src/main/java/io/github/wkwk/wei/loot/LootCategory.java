package io.github.wkwk.wei.loot;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.AspectItem;
import com.wynntils.models.items.items.game.CharmItem;
import com.wynntils.models.items.items.game.DungeonKeyItem;
import com.wynntils.models.items.items.game.EmeraldPouchItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.models.items.items.game.MaterialItem;
import com.wynntils.models.items.items.game.PowderItem;
import com.wynntils.models.items.items.game.RuneItem;
import com.wynntils.models.items.items.game.TomeItem;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** 高額通知の基準を種類ごとに変えるための分類（設定ファイルでは id で書く） */
public enum LootCategory {
    GEAR_BOX("gearBox"),
    GEAR("gear"),
    INGREDIENT("ingredient"),
    MATERIAL("material"),
    POWDER("powder"),
    TOME("tome"),
    CHARM("charm"),
    ASPECT("aspect"),
    RUNE("rune"),
    DUNGEON_KEY("dungeonKey"),
    EMERALD_POUCH("emeraldPouch"),
    OTHER("other");

    public final String id;

    LootCategory(String id) {
        this.id = id;
    }

    public static LootCategory of(ItemStack stack) {
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        if (item.isEmpty()) return OTHER;
        WynnItem w = item.get();
        if (w instanceof GearBoxItem) return GEAR_BOX;
        if (w instanceof GearItem) return GEAR;
        if (w instanceof IngredientItem) return INGREDIENT;
        if (w instanceof MaterialItem) return MATERIAL;
        if (w instanceof PowderItem) return POWDER;
        if (w instanceof TomeItem) return TOME;
        if (w instanceof CharmItem) return CHARM;
        if (w instanceof AspectItem) return ASPECT;
        if (w instanceof RuneItem) return RUNE;
        if (w instanceof DungeonKeyItem) return DUNGEON_KEY;
        if (w instanceof EmeraldPouchItem) return EMERALD_POUCH;
        return OTHER;
    }

    /** 種類ごとに選べるレア度（先頭は any） */
    public String[] rarities() {
        return switch (this) {
            case GEAR_BOX, GEAR, TOME, CHARM, ASPECT ->
                    new String[] {"any", "normal", "unique", "rare", "set", "legendary", "fabled", "mythic"};
            case INGREDIENT -> new String[] {"any", "tier0", "tier1", "tier2", "tier3"};
            case MATERIAL -> new String[] {"any", "tier1", "tier2", "tier3"};
            case POWDER -> new String[] {"any", "tier1", "tier2", "tier3", "tier4", "tier5", "tier6"};
            default -> new String[] {"any"};
        };
    }

    /** アイテムのレア度（Wynntils の判定）。分からなければ any */
    public static String rarityOf(ItemStack stack) {
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        if (item.isEmpty()) return "any";
        WynnItem w = item.get();
        if (w instanceof com.wynntils.models.items.properties.GearTierItemProperty g && g.getGearTier() != null) {
            return g.getGearTier().name().toLowerCase(java.util.Locale.ROOT);
        }
        if (w instanceof com.wynntils.models.items.properties.QualityTierItemProperty q) return "tier" + q.getQualityTier();
        if (w instanceof com.wynntils.models.items.properties.NumberedTierItemProperty n) return "tier" + n.getTier();
        return "any";
    }

    public static LootCategory byId(String id) {
        for (LootCategory c : values()) if (c.id.equalsIgnoreCase(id)) return c;
        return null;
    }
}
