package io.github.wkwk.wynnemi.emi;

import com.wynntils.core.components.Models;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
import com.wynntils.models.wynnitem.type.ItemObtainType;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/** 「入手元」と「職業」の仮想アイテムを作って使い回す（同じ入手元は1つのスタックにまとめる）。 */
final class WynnSources {
    private final Map<String, WynnEmiStack> sources = new HashMap<>();
    private final Map<ProfessionType, WynnEmiStack> professions = new HashMap<>();

    /** 入手方法の種類そのもの（カテゴリのアイコンと作業台に使う） */
    WynnEmiStack typeStack(ItemObtainType type) {
        return sources.computeIfAbsent("source/" + type.name(), key -> WynnEmiStack.ofVirtual(
                key,
                new ItemStack(iconFor(type)),
                Component.literal(type.getDisplayName()).withStyle(ChatFormatting.GOLD),
                List.of(Component.translatable("wynnemi.tooltip.obtain_type").withStyle(ChatFormatting.GRAY))));
    }

    /** 具体的な入手元（例: Dungeon Rain: Decrepit Sewers）。名前が無いものは種類のスタックを返す。 */
    WynnEmiStack sourceStack(ItemObtainInfo info) {
        ItemObtainType type = info.sourceType();
        String name = displayName(info);
        if (name == null) return typeStack(type);

        String normalized = normalize(name);
        return sources.computeIfAbsent("source/" + type.name() + "/" + normalized, key -> WynnEmiStack.ofVirtual(
                key,
                new ItemStack(iconFor(type)),
                Component.literal(name).withStyle(ChatFormatting.YELLOW),
                List.of(
                        Component.literal(type.getDisplayName()).withStyle(ChatFormatting.GOLD),
                        Component.translatable("wynnemi.tooltip.source").withStyle(ChatFormatting.DARK_GRAY))));
    }

    WynnEmiStack professionStack(ProfessionType profession) {
        return professions.computeIfAbsent(profession, p -> WynnEmiStack.ofVirtual(
                "profession/" + p.name(),
                new ItemStack(iconFor(p)),
                Component.literal(p.getDisplayName()).withStyle(ChatFormatting.AQUA),
                List.of(Component.translatable("wynnemi.tooltip.profession").withStyle(ChatFormatting.GRAY))));
    }

    Iterable<WynnEmiStack> allSourceStacks() {
        return sources.values();
    }

    Iterable<WynnEmiStack> allProfessionStacks() {
        return professions.values();
    }

    /** Wynntils のツールチップと同じ考え方で、具体名を出す価値があるものだけ名前を返す。 */
    private static @Nullable String displayName(ItemObtainInfo info) {
        if (info.name().isEmpty()) return null;
        ItemObtainType type = info.sourceType();
        if (type == ItemObtainType.GUILD) return null;

        String name = info.name().get().replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) return null;
        if (type == ItemObtainType.LOOTRUN && name.equals("Lootrun Rewards")) return null;

        if (type == ItemObtainType.QUEST) {
            try {
                var quest = Models.Quest.getQuestInfo(name);
                if (quest != null && quest.displayName() != null && !quest.displayName().isBlank()) {
                    name = quest.displayName();
                }
            } catch (Throwable ignored) {
            }
        }
        return name;
    }

    /** 表記揺れ（大文字小文字・空白・色コード）をまとめるためのキー */
    static String normalize(String name) {
        String stripped = ChatFormatting.stripFormatting(name);
        if (stripped == null) stripped = name;
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
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
