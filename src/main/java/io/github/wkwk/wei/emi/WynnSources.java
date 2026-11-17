package io.github.wkwk.wei.emi;

import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import io.github.wkwk.wei.data.WynnCatalog;
import io.github.wkwk.wei.util.Names;

/**
 * 「入手元」と「職業」の仮想アイテムを作って使い回す（同じ入手元は1つのスタックにまとめる）。
 * EMI のスレッドで動くので、Wynntils のモデルには触れず、カタログのスナップショットだけを使う。
 */
final class WynnSources {
    private final Map<String, WynnEmiStack> sources = new HashMap<>();
    private final Map<ProfessionType, WynnEmiStack> professions = new HashMap<>();

    /** 入手方法の種類そのもの（カテゴリのアイコンと作業台に使う） */
    WynnEmiStack typeStack(ItemObtainType type) {
        return sources.computeIfAbsent("source/" + type.name(), key -> WynnEmiStack.ofVirtual(
                key,
                new ItemStack(iconFor(type)),
                Component.literal(type.getDisplayName()).withStyle(ChatFormatting.GOLD),
                List.of(Component.translatable("wei.tooltip.obtain_type").withStyle(ChatFormatting.GRAY))));
    }

    /** 具体的な入手元（例: Decrepit Sewers）。名前が無いものは種類のスタックを返す。 */
    WynnEmiStack sourceStack(WynnCatalog.ObtainSnapshot info) {
        ItemObtainType type = info.type();
        String name = info.sourceName();
        if (name == null) return typeStack(type);

        return sources.computeIfAbsent("source/" + type.name() + "/" + Names.normalize(name), key -> WynnEmiStack.ofVirtual(
                key,
                new ItemStack(iconFor(type)),
                Component.literal(name).withStyle(ChatFormatting.YELLOW),
                List.of(
                        Component.literal(type.getDisplayName()).withStyle(ChatFormatting.GOLD),
                        Component.translatable("wei.tooltip.source").withStyle(ChatFormatting.DARK_GRAY))));
    }

    WynnEmiStack professionStack(ProfessionType profession) {
        return professions.computeIfAbsent(profession, p -> WynnEmiStack.ofVirtual(
                "profession/" + p.name(),
                new ItemStack(iconFor(p)),
                Component.literal(p.getDisplayName()).withStyle(ChatFormatting.AQUA),
                List.of(Component.translatable("wei.tooltip.profession").withStyle(ChatFormatting.GRAY))));
    }

    Iterable<WynnEmiStack> allSourceStacks() {
        return sources.values();
    }

    Iterable<WynnEmiStack> allProfessionStacks() {
        return professions.values();
    }

    private static ItemLike iconFor(ItemObtainType type) {
        return switch (type) {
            case BOSS_ALTAR -> Items.END_CRYSTAL;
            case CHALLENGE -> Items.TARGET;
            case DUNGEON -> Items.TRIAL_KEY;
            case DUNGEON_MERCHANT -> Items.EMERALD_BLOCK;
            case EVENT -> Items.FIREWORK_ROCKET;
            case GUILD -> Items.WHITE_BANNER;
            case LOOTRUN -> Items.COMPASS;
            case MERCHANT -> Items.EMERALD;
            case MINIBOSS -> Items.ZOMBIE_HEAD;
            case QUEST -> Items.WRITABLE_BOOK;
            case RAID -> Items.NETHER_STAR;
            case CAVE_COMPLETION -> Items.POINTED_DRIPSTONE;
            case CAVE_LOOT_CHEST -> Items.TRAPPED_CHEST;
            case DISCOVERY -> Items.SPYGLASS;
            case ENVIRONMENT -> Items.OAK_SAPLING;
            case FORGERY_CHEST -> Items.ENDER_CHEST;
            case GATHERING -> Items.IRON_PICKAXE;
            case INTERACTION -> Items.LEVER;
            case LOOT_CHEST -> Items.CHEST;
            case MOB_REGION -> Items.ROTTEN_FLESH;
            case NORMAL_MOB_DROP -> Items.BONE;
            case RARE_MOB_REGION -> Items.WITHER_SKELETON_SKULL;
            case SPECIFIC_MOB_DROP -> Items.SKELETON_SKULL;
            case TIME_TRIAL -> Items.CLOCK;
            case TINKERING -> Items.ANVIL;
            case WORLD_EVENT -> Items.BELL;
            case UNAVAILABLE -> Items.BARRIER;
            default -> Items.STRUCTURE_VOID;
        };
    }

    private static ItemLike iconFor(ProfessionType profession) {
        return switch (profession) {
            case WOODCUTTING -> Items.IRON_AXE;
            case MINING -> Items.IRON_PICKAXE;
            case FISHING -> Items.FISHING_ROD;
            case FARMING -> Items.IRON_HOE;
            case ALCHEMISM -> Items.BREWING_STAND;
            case ARMOURING -> Items.IRON_CHESTPLATE;
            case COOKING -> Items.SMOKER;
            case JEWELING -> Items.AMETHYST_SHARD;
            case SCRIBING -> Items.BOOK;
            case TAILORING -> Items.LEATHER;
            case WEAPONSMITHING -> Items.IRON_SWORD;
            case WOODWORKING -> Items.BOW;
            default -> Items.PAPER;
        };
    }
}
