package io.github.wkwk.wynncodex.data;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.models.activities.type.Dungeon;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
import com.wynntils.models.wynnitem.type.ItemObtainType;
import com.wynntils.screens.guides.aspect.GuideAspectItemStack;
import com.wynntils.screens.guides.augment.GuideAmplifierItemStack;
import com.wynntils.screens.guides.augment.GuideInsulatorItemStack;
import com.wynntils.screens.guides.augment.GuideSimulatorItemStack;
import com.wynntils.screens.guides.charm.GuideCharmItemStack;
import com.wynntils.screens.guides.dungeonkey.GuideDungeonKeyItemStack;
import com.wynntils.screens.guides.emerald.GuideEmeraldPouchItemStack;
import com.wynntils.screens.guides.gatheringtool.GuideGatheringToolItemStack;
import com.wynntils.screens.guides.gear.GuideGearItemStack;
import com.wynntils.screens.guides.ingredient.GuideIngredientItemStack;
import com.wynntils.screens.guides.material.GuideMaterialItemStack;
import com.wynntils.screens.guides.powder.GuidePowderItemStack;
import com.wynntils.screens.guides.rune.GuideRuneItemStack;
import com.wynntils.screens.guides.tome.GuideTomeItemStack;
import com.wynntils.screens.guides.ward.GuideWardItemStack;
import io.github.wkwk.wynncodex.WynnCodex;
import io.github.wkwk.wynncodex.util.Names;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.wynntils.models.gear.type.SetBonus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Wynntils のデータから作った、EMI に渡すための不変のスナップショット。
 *
 * <p>Guide*ItemStack の生成・説明文の取得・クエスト名の解決など Wynntils に触れる処理は、
 * すべてクライアントスレッドの {@link Build} で済ませる。EMI のプラグイン（別スレッド）は
 * このスナップショットを読むだけで、Wynntils のモデルには触れない。
 */
public final class WynnCatalog {
    /** 入手元。表示名などは構築時に解決済み。 */
    public record ObtainSnapshot(
            ItemObtainType type,
            /** 入手元アイテムとして独立させる名前。null なら種類だけ（Guild など） */
            @Nullable String sourceName,
            /** レシピ画面に出す元の名前 */
            @Nullable String caption) {}

    /** 未鑑定ボックスの系統（装備の種類 × レアリティ）。中身の候補はこの系統と必要レベルで決まる */
    public record BoxFamily(String key, Component displayName) {}

    /** アイテムごとの追加情報（カタログ構築時に Wynntils から取り出しておく） */
    public record Extra(
            @Nullable List<ItemObtainInfo> obtain,
            @Nullable List<ProfessionType> craftProfessions,
            @Nullable ProfessionType gatherProfession,
            /** 並び替えと表示に使うレベル（無ければ 0） */
            int level,
            /** 検索用の追加の語（レアリティ・種類・レベル・クラスなど） */
            @Nullable String aliases,
            /** Wynntils のお気に入りに使う名前（Wynntils の getName() と同じもの） */
            @Nullable String favoriteName,
            @Nullable BoxFamily boxFamily,
            /** 装備に必要なクエスト（Wynntils の生の名前） */
            @Nullable String requiredQuest) {
        static final Extra NONE = new Extra(null, null, null, 0, null, null, null, null);
    }

    public record Entry(
            String key,
            String group,
            ItemStack stack,
            Component displayName,
            String plainName,
            /** 構築時に1回だけ取った説明文（不変） */
            List<Component> tooltip,
            List<ObtainSnapshot> obtain,
            List<ProfessionType> craftProfessions,
            @Nullable ProfessionType gatherProfession,
            int level,
            @Nullable String aliases,
            @Nullable String favoriteName,
            @Nullable BoxFamily boxFamily,
            /** 表示用に解決済みのクエスト名 */
            @Nullable String requiredQuest) {}

    /** セット装備。メンバーはカタログのキー */
    public record SetSnapshot(String name, List<String> memberKeys, List<Component> bonusTooltip) {}

    private static volatile WynnCatalog current = new WynnCatalog(List.of(), List.of());

    private final List<Entry> entries;
    private final List<SetSnapshot> sets;

    private WynnCatalog(List<Entry> entries, List<SetSnapshot> sets) {
        this.entries = entries;
        this.sets = sets;
    }

    public List<SetSnapshot> sets() {
        return sets;
    }

    public static WynnCatalog current() {
        return current;
    }

    static void publish(WynnCatalog catalog) {
        current = catalog;
    }

    public List<Entry> entries() {
        return entries;
    }

    static boolean isOnWynncraft() {
        try {
            return Managers.Connection.onServer();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * データが変わったかを見るための軽い指紋（クライアントスレッドで呼ぶ）。
     * 内容の更新は Wynntils のダウンロード完了イベントで拾うので、ここでは件数と、
     * 説明文を変えるプレイヤーのクラスだけを見る（初回読み込みの取りこぼし防止用）。
     */
    static String computeDataSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(() -> Models.Gear.getAllGearInfos().count())).append(',');
        sb.append(safe(() -> Models.Ingredient.getAllIngredientInfos().count())).append(',');
        sb.append(safe(() -> Models.Profession.getAllMaterialInfos().count())).append(',');
        sb.append(safe(() -> Models.Profession.getAllGatheringToolInfos().count())).append(',');
        sb.append(safe(() -> Models.Rewards.getAllCharmInfos().count())).append(',');
        sb.append(safe(() -> Models.Rewards.getAllTomeInfos().count())).append(',');
        sb.append(safe(() -> Models.Aspect.getAllAspectInfos().count())).append(',');
        sb.append(safe(() -> Models.Element.getAllPowderTierInfo().size())).append(',');
        sb.append(safe(() -> Models.Rewards.getAllAmplifierInfo().size())).append(',');
        sb.append(safe(() -> Models.WynnItem.hasObtainInfo())).append(',');
        // Wynntils の説明文はクラスによって変わる（使えるかどうかの表示など）
        sb.append(safe(() -> Models.Character.getClassType()));
        return sb.toString();
    }

    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    private static String safe(ThrowingSupplier supplier) {
        try {
            return String.valueOf(supplier.get());
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * 数千件を1ティックで作るとゲームが止まるので、小さな作業に分けて少しずつ進める。
     * 作業の一覧づくり（各モデルの列挙）も1つの作業として扱う。
     */
    static final class Build {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        private final Set<String> failedGroups = new LinkedHashSet<>();
        private final Map<String, String> questNames = new HashMap<>();
        private final List<SetSnapshot> sets = new ArrayList<>();

        Build() {
            // 入手元は API の情報と有志のデータを合わせたもの（Wynntils のツールチップと同じ）を使う
            group("gear", () -> Models.Gear.getAllGearInfos()
                    .map(g -> (Runnable) () -> {
                        List<ItemObtainInfo> obtain = Models.Gear.getObtainInfo(g);
                        int level = g.requirements().level();
                        add("gear", g.name(), new GuideGearItemStack(g), new Extra(
                                obtain, null, null, level,
                                gearAliases(g.tier().getName(), g.type().name(),
                                        g.requirements().classType().map(Enum::name).orElse(null), level),
                                g.name(),
                                canBeInBox(g.metaInfo().preIdentified(), obtain)
                                        ? boxFamily(g.type().name(), g.tier().name(), g.tier().getName())
                                        : null,
                                g.requirements().quest().filter(q -> !q.isBlank()).orElse(null)));
                    })
                    .toList());

            group("ingredient", () -> Models.Ingredient.getAllIngredientInfos()
                    .map(i -> (Runnable) () -> add("ingredient", i.name(), new GuideIngredientItemStack(i), new Extra(
                            Models.Ingredient.getObtainInfo(i), i.professions(), null, i.level(),
                            "ingredient lv" + i.level() + " lv." + i.level() + " " + "*".repeat(Math.max(0, i.tier()))
                                    + " t" + i.tier() + professionWords(i.professions()),
                            i.name(), null, null)))
                    .toList());

            group("material", () -> Models.Profession.getAllMaterialInfos()
                    .<Runnable>mapMulti((m, out) -> {
                        for (int tier = 1; tier <= 3; tier++) {
                            int t = tier;
                            out.accept(() -> add("material", m.name() + " T" + t, new GuideMaterialItemStack(m, t),
                                    new Extra(null, null, m.professionType(), m.level(),
                                            "material lv" + m.level() + " lv." + m.level() + " t" + t + " "
                                                    + m.professionType().getDisplayName().toLowerCase(Locale.ROOT),
                                            m.name(), null, null)));
                        }
                    })
                    .toList());

            group("tool", () -> Models.Profession.getAllGatheringToolInfos()
                    .map(t -> (Runnable) () -> add("tool", t.name(), new GuideGatheringToolItemStack(t),
                            new Extra(null, null, null, t.level(), "tool lv" + t.level(), t.name(), null, null)))
                    .toList());

            group("charm", () -> Models.Rewards.getAllCharmInfos()
                    .map(c -> (Runnable) () -> add("charm", c.name(), new GuideCharmItemStack(c), new Extra(
                            Models.Rewards.getCharmObtainInfo(c), null, null, c.requirements().level(),
                            "charm " + c.tier().getName().toLowerCase(Locale.ROOT) + " lv" + c.requirements().level(),
                            c.name(), null, null)))
                    .toList());

            group("tome", () -> Models.Rewards.getAllTomeInfos()
                    .map(t -> (Runnable) () -> add("tome", t.name(), new GuideTomeItemStack(t), new Extra(
                            Models.Rewards.getTomeObtainInfo(t), null, null, t.requirements().level(),
                            "tome " + t.tier().getName().toLowerCase(Locale.ROOT) + " lv" + t.requirements().level(),
                            t.name(), null, null)))
                    .toList());

            group("aspect", () -> Models.Aspect.getAllAspectInfos()
                    .<Runnable>mapMulti((a, out) -> {
                        for (int tier = 1; tier <= a.effects().size(); tier++) {
                            int t = tier;
                            out.accept(() -> add("aspect", a.name() + " T" + t, new GuideAspectItemStack(a, t),
                                    new Extra(null, null, null, 0,
                                            "aspect t" + t + " " + a.gearTier().getName().toLowerCase(Locale.ROOT)
                                                    + (a.classType() == null ? "" : " " + a.classType().name().toLowerCase(Locale.ROOT)),
                                            a.name(), null, null)));
                        }
                    })
                    .toList());

            group("powder", () -> Models.Element.getAllPowderTierInfo().stream()
                    .map(p -> (Runnable) () -> addUnnamed("powder", new GuidePowderItemStack(p)))
                    .toList());

            group("rune", () -> Models.Rewards.getAllRuneInfo().stream()
                    .map(r -> (Runnable) () -> addUnnamed("rune", new GuideRuneItemStack(r)))
                    .toList());

            group("ward", () -> Models.Rewards.getAllWardInfo().stream()
                    .map(w -> (Runnable) () -> addUnnamed("ward", new GuideWardItemStack(w)))
                    .toList());

            group("augment", () -> {
                List<Runnable> list = new ArrayList<>();
                Models.Rewards.getAllAmplifierInfo()
                        .forEach(a -> list.add(() -> addUnnamed("augment", new GuideAmplifierItemStack(a))));
                list.add(() -> addUnnamed("augment", new GuideSimulatorItemStack()));
                list.add(() -> addUnnamed("augment", new GuideInsulatorItemStack()));
                return list;
            });

            group("emerald", () -> {
                List<Runnable> list = new ArrayList<>();
                Models.Emerald.getAllEmeraldItems().forEach(e -> list.add(() -> addUnnamed("emerald", e)));
                for (int i = 1; i <= 10; i++) {
                    int tier = i;
                    list.add(() -> addUnnamed("emerald", new GuideEmeraldPouchItemStack(tier)));
                }
                return list;
            });

            // Wynntils のダンジョンキーガイドと同じ組み合わせ
            group("dungeon_key", () -> {
                List<Runnable> list = new ArrayList<>();
                for (Dungeon dungeon : Dungeon.values()) {
                    if (dungeon.doesExist()) {
                        list.add(() -> addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, false, false)));
                        if (dungeon.doesCorruptedExist()) {
                            list.add(() -> addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, false, true)));
                        }
                    }
                    if (dungeon.doesCorruptedExist()) {
                        list.add(() -> addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, true, false)));
                        if (dungeon == Dungeon.LOST_SANCTUARY) {
                            list.add(() -> addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, true, true)));
                        }
                    }
                }
                return list;
            });

            // セット装備（メンバーのキーは装備のキーと同じ形）
            group("set", () -> Models.Set.getSets().stream()
                    .map(set -> (Runnable) () -> addSet(set.name(), set.items(), set.bonuses()))
                    .toList());
        }

        /** 期限まで作業を進める。全部終わったら true。 */
        boolean step(long deadlineNanos) {
            while (!tasks.isEmpty()) {
                tasks.pollFirst().run();
                if (System.nanoTime() >= deadlineNanos) break;
            }
            return tasks.isEmpty();
        }

        boolean complete() {
            return failedGroups.isEmpty();
        }

        Set<String> failedGroups() {
            return failedGroups;
        }

        WynnCatalog result() {
            return new WynnCatalog(List.copyOf(entries), List.copyOf(sets));
        }

        int size() {
            return entries.size();
        }

        private void group(String name, Supplier<List<Runnable>> planner) {
            tasks.addLast(() -> {
                List<Runnable> items;
                try {
                    items = planner.get();
                } catch (Throwable t) {
                    fail(name, t);
                    return;
                }
                // このグループの各アイテムを、次のグループより先に処理する（並び順を保つ）
                for (int i = items.size() - 1; i >= 0; i--) {
                    Runnable item = items.get(i);
                    tasks.addFirst(() -> {
                        try {
                            item.run();
                        } catch (Throwable t) {
                            fail(name, t);
                        }
                    });
                }
            });
        }

        private void fail(String group, Throwable t) {
            // 同じグループのエラーは最初の1回だけ詳しく出す
            if (failedGroups.add(group)) {
                WynnCodex.LOGGER.warn("Failed to load some Wynncraft {} items", group, t);
            }
        }

        private void addUnnamed(String group, ItemStack stack) {
            add(group, null, stack, Extra.NONE);
        }

        private void add(String group, @Nullable String plainName, ItemStack stack, Extra extra) {
            List<Component> tooltip = tooltipOf(stack);
            Component display = !tooltip.isEmpty() && !tooltip.get(0).getString().isBlank()
                    ? tooltip.get(0)
                    : stack.getHoverName();
            if (tooltip.isEmpty()) tooltip = List.of(display);

            String plain = plainName != null ? plainName : Names.strip(display.getString());
            if (plain.isBlank()) plain = Names.strip(stack.getHoverName().getString());

            String key = group + "/" + plain;
            int n = 2;
            while (!keys.add(key)) {
                key = group + "/" + plain + "#" + n++;
            }

            String favoriteName = extra.favoriteName() != null ? extra.favoriteName() : plain;
            String quest = extra.requiredQuest() == null ? null : resolveQuest(extra.requiredQuest());
            entries.add(new Entry(key, group, stack, display, plain, tooltip,
                    snapshotObtain(extra.obtain()),
                    extra.craftProfessions() == null ? List.of() : List.copyOf(extra.craftProfessions()),
                    extra.gatherProfession(),
                    extra.level(),
                    extra.aliases(),
                    favoriteName,
                    extra.boxFamily(),
                    quest));
        }

        private void addSet(String name, List<String> itemNames, Map<Integer, SetBonus> bonuses) {
            List<String> members = new ArrayList<>();
            for (String item : itemNames) {
                members.add("gear/" + item);
            }
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(name + " Set").withStyle(ChatFormatting.GREEN));
            bonuses.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        SetBonus bonus = e.getValue();
                        if (bonus.major().isEmpty() && bonus.minor().isEmpty()) return;
                        tooltip.add(Component.translatable("wynncodex.tooltip.set_pieces", e.getKey())
                                .withStyle(ChatFormatting.GRAY));
                        for (String major : bonus.major()) {
                            tooltip.add(Component.literal("  " + Names.strip(major)).withStyle(ChatFormatting.AQUA));
                        }
                        bonus.minor().forEach((stat, value) -> tooltip.add(Component.literal(
                                        "  " + (value >= 0 ? "+" : "") + value + stat.getUnit().getDisplayName()
                                                + " " + stat.getDisplayName())
                                .withStyle(value >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
                    });
            sets.add(new SetSnapshot(name, List.copyOf(members), List.copyOf(tooltip)));
        }

        private String resolveQuest(String raw) {
            return questNames.computeIfAbsent(raw, n -> {
                try {
                    var quest = Models.Quest.getQuestInfo(n);
                    if (quest != null && quest.displayName() != null && !quest.displayName().isBlank()) {
                        return quest.displayName();
                    }
                } catch (Throwable ignored) {
                }
                return n;
            });
        }

        private static List<Component> tooltipOf(ItemStack stack) {
            try {
                return List.copyOf(stack.getTooltipLines(
                        Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL));
            } catch (Throwable t) {
                return List.of();
            }
        }

        private List<ObtainSnapshot> snapshotObtain(@Nullable List<ItemObtainInfo> infos) {
            if (infos == null || infos.isEmpty()) return List.of();
            List<ObtainSnapshot> list = new ArrayList<>(infos.size());
            for (ItemObtainInfo info : infos) {
                if (info == null || info.sourceType() == null) continue;
                String raw = info.name().map(s -> Names.WHITESPACE.matcher(s).replaceAll(" ").trim()).orElse("");
                list.add(new ObtainSnapshot(info.sourceType(), sourceNameOf(info.sourceType(), raw), raw.isEmpty() ? null : raw));
            }
            return List.copyOf(list);
        }

        /** Wynntils のツールチップと同じ考え方で、入手元として独立させる名前を決める。 */
        private @Nullable String sourceNameOf(ItemObtainType type, String raw) {
            if (raw.isEmpty() || type == ItemObtainType.GUILD) return null;
            if (type == ItemObtainType.LOOTRUN && raw.equals("Lootrun Rewards")) return null;
            if (type == ItemObtainType.QUEST) return resolveQuest(raw);
            return raw;
        }
    }

    /** Wynntils の GearModel と同じ条件：鑑定済み固定でなく、ボックスで落ちる入手元がある */
    private static boolean canBeInBox(boolean preIdentified, List<ItemObtainInfo> obtain) {
        if (preIdentified || obtain == null) return false;
        for (ItemObtainInfo info : obtain) {
            if (info != null && ItemObtainType.BOXED_ITEMS.contains(info.sourceType())) return true;
        }
        return false;
    }

    /** 系統のキーは手持ちのボックス（GearBoxItem）からも同じ形で作れるようにする */
    public static String boxFamilyKey(String gearTypeName, String gearTierName) {
        return "box/" + gearTypeName + "/" + gearTierName;
    }

    private static BoxFamily boxFamily(String gearTypeName, String gearTierName, String tierDisplay) {
        return new BoxFamily(boxFamilyKey(gearTypeName, gearTierName), Component.translatable(
                "wynncodex.box.name", tierDisplay, Names.titleCase(gearTypeName)));
    }

    private static String gearAliases(String tier, String type, @Nullable String classType, int level) {
        StringBuilder sb = new StringBuilder();
        sb.append(tier.toLowerCase(Locale.ROOT)).append(' ')
                .append(type.toLowerCase(Locale.ROOT).replace('_', ' ')).append(' ')
                .append("lv").append(level).append(" lv.").append(level);
        if (classType != null) sb.append(' ').append(classType.toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    private static String professionWords(List<ProfessionType> professions) {
        StringBuilder sb = new StringBuilder();
        if (professions != null) {
            for (ProfessionType p : professions) {
                sb.append(' ').append(p.getDisplayName().toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
