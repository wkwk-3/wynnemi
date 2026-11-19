package io.github.wkwk.wei.data;

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
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.util.Names;
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
 * Wynntils のデータから作る、EMI に渡す不変のスナップショット。
 *
 * <p>Wynntils に触れる処理（Guide*ItemStack の生成・説明文・クエスト名）はすべてクライアントスレッドの
 * {@link Build} で行う。EMI のプラグイン（別スレッド）はこれを読むだけ。
 */
public final class WynnCatalog {
    /** 入手元（構築時に解決済み） */
    public record ObtainSnapshot(
            ItemObtainType type,
            /** 独立した入手元アイテムの名前。null なら種類だけ（Guild など） */
            @Nullable String sourceName,
            @Nullable String caption) {}

    /** 未鑑定ボックスの系統（装備の種類 × レアリティ） */
    public record BoxFamily(String key, Component displayName) {}

    /**
     * 公式 API のアイテム検索と同じ分類。値は API の表記（小文字・camelCase）。
     */
    public record ApiFacets(
            /** weapon / armour / accessory / tome / charm / tool / ingredient / material */
            @Nullable String type,
            /** 武器種・防具部位・アクセサリー種・トーム種・ツール種 */
            @Nullable String subType,
            /** 装備: normal〜mythic、素材: tier_0〜tier_3 */
            @Nullable String tier,
            /** superSlow〜superFast */
            @Nullable String attackSpeed,
            int level,
            Set<String> professions,
            /** ID 名（小文字）→ 最大値 */
            Map<String, Integer> identifications,
            /** 英数字だけの小文字 */
            Set<String> majorIds,
            /** str / dex / int / def / agi → 必要ポイント（0 は含めない） */
            Map<String, Integer> skillRequirements) {
        public static final ApiFacets NONE = new ApiFacets(null, null, null, null, 0, Set.of(), Map.of(), Set.of());

        public ApiFacets(@Nullable String type, @Nullable String subType, @Nullable String tier, @Nullable String attackSpeed,
                int level, Set<String> professions, Map<String, Integer> identifications, Set<String> majorIds) {
            this(type, subType, tier, attackSpeed, level, professions, identifications, majorIds, Map.of());
        }
    }

    /** アイテムごとの追加情報（構築時に取り出す） */
    public record Extra(
            @Nullable List<ItemObtainInfo> obtain,
            @Nullable List<ProfessionType> craftProfessions,
            @Nullable ProfessionType gatherProfession,
            /** 無ければ 0 */
            int level,
            /** 検索用の別名（レアリティ・種類・レベル・クラスなど） */
            @Nullable String aliases,
            /** Wynntils のお気に入り名（getName() と同じ） */
            @Nullable String favoriteName,
            @Nullable BoxFamily boxFamily,
            /** 必要クエスト（Wynntils の生の名前） */
            @Nullable String requiredQuest,
            ApiFacets facets) {
        static final Extra NONE = new Extra(null, null, null, 0, null, null, null, null, ApiFacets.NONE);
    }

    public record Entry(
            String key,
            String group,
            ItemStack stack,
            Component displayName,
            String plainName,
            /** 構築時の説明文 */
            List<Component> tooltip,
            List<ObtainSnapshot> obtain,
            List<ProfessionType> craftProfessions,
            @Nullable ProfessionType gatherProfession,
            int level,
            @Nullable String aliases,
            @Nullable String favoriteName,
            @Nullable BoxFamily boxFamily,
            @Nullable String requiredQuest,
            ApiFacets facets) {

        Entry withTooltip(ItemStack newStack, List<Component> newTooltip) {
            return new Entry(key, group, newStack, displayName, plainName, newTooltip, obtain, craftProfessions,
                    gatherProfession, level, aliases, favoriteName, boxFamily, requiredQuest, facets);
        }
    }

    public record SetSnapshot(String name, List<String> memberKeys, List<Component> bonusTooltip) {}

    /** threshold は累計の必要数 */
    public record AspectTier(int tier, int threshold, List<Component> effect, com.wynntils.models.aspects.type.AspectInfo info) {
        /** 描画スレッドから呼ぶ */
        public ItemStack createStack() {
            return new GuideAspectItemStack(info, tier);
        }
    }

    /** アスペクト 1 種類。一覧は 1 件、ティアはレシピ画面 */
    public record AspectSnapshot(String key, List<AspectTier> tiers) {}

    private static volatile WynnCatalog current = new WynnCatalog(List.of(), List.of(), List.of());

    private final List<Entry> entries;
    private final List<SetSnapshot> sets;
    private final List<AspectSnapshot> aspects;

    private WynnCatalog(List<Entry> entries, List<SetSnapshot> sets, List<AspectSnapshot> aspects) {
        this.entries = entries;
        this.sets = sets;
        this.aspects = aspects;
    }

    public List<AspectSnapshot> aspects() {
        return aspects;
    }

    public List<SetSnapshot> sets() {
        return sets;
    }

    /** 装備 1 件で説明文が作れるか。データが無ければ true。 */
    public static boolean tooltipsReady() {
        try {
            var first = Models.Gear.getAllGearInfos().findFirst();
            if (first.isEmpty()) return true;
            return Build.tooltipOf(new GuideGearItemStack(first.get())).size() > 1;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 描画スレッドから呼ぶ */
    public static List<Component> tooltipLines(ItemStack stack) {
        return Build.tooltipOf(stack);
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

    /** その時点の接続状態（WynnDataWatcher のキャッシュではない） */
    public static boolean isOnWynncraftNow() {
        return isOnWynncraft();
    }

    static boolean isOnWynncraft() {
        try {
            return Managers.Connection.onServer();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * データの変化を見る軽い指紋。内容の更新は取得完了イベントで拾うため、件数だけを見る。
     *
     * <p>クラスは含めない（切り替えのたびに全体を作り直すのは重すぎる）。説明文のクラス表示は構築時のまま。
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
        sb.append(safe(() -> Models.WynnItem.hasObtainInfo()));
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
     * 小さな作業に分けて 1 ティックずつ進める構築処理。モデルの列挙も 1 つの作業。
     */
    static final class Build {
        /** label はログ用 */
        private record Task(String label, Runnable body) {}

        /** これを超えた作業を記録する（diag#3） */
        private static final long SLOW_TASK_NANOS = 8_000_000L;

        private final ArrayDeque<Task> tasks = new ArrayDeque<>();
        private final List<String> slowTasks = new ArrayList<>();
        private int slowTaskCount;
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        private final Set<String> failedGroups = new LinkedHashSet<>();
        private final Map<String, String> questNames = new HashMap<>();
        private final List<SetSnapshot> sets = new ArrayList<>();
        private final List<AspectSnapshot> aspects = new ArrayList<>();

        Build() {
            // 入手元は API と有志データの合成（Wynntils のツールチップと同じ）
            // 除外判定は各作業内で行う（列挙時に全件見ると 1 ティックが重い）
            group("gear", () -> Models.Gear.getAllGearInfos()
                    .map(g -> (Runnable) () -> {
                        List<ItemObtainInfo> obtain = Models.Gear.getObtainInfo(g);
                        if (isHiddenGear(g, obtain)) return;
                        int level = g.requirements().level();
                        add("gear", g.name(), new GuideGearItemStack(g), new Extra(
                                obtain, null, null, level,
                                gearAliases(g.tier().getName(), g.type().name(),
                                        g.requirements().classType().map(Enum::name).orElse(null), level),
                                g.name(),
                                canBeInBox(g.metaInfo().preIdentified(), obtain)
                                        ? boxFamily(g.type().name(), g.tier().name(), g.tier().getName())
                                        : null,
                                g.requirements().quest().filter(q -> !q.isBlank()).orElse(null),
                                ApiFacetsFactory.gear(g)));
                    })
                    .toList());

            group("ingredient", () -> Models.Ingredient.getAllIngredientInfos()
                    .filter(i -> !isDuplicateMarked(i.name(), i.apiName())
                            && !isDiscontinued("ingredient", i.name(), Models.Ingredient.getObtainInfo(i)))
                    .map(i -> (Runnable) () -> add("ingredient", i.name(), new GuideIngredientItemStack(i), new Extra(
                            Models.Ingredient.getObtainInfo(i), i.professions(), null, i.level(),
                            "ingredient lv" + i.level() + " lv." + i.level() + " " + "*".repeat(Math.max(0, i.tier()))
                                    + " t" + i.tier() + professionWords(i.professions()),
                            i.name(), null, null, ApiFacetsFactory.ingredient(i))))
                    .toList());

            group("material", () -> Models.Profession.getAllMaterialInfos()
                    .<Runnable>mapMulti((m, out) -> {
                        for (int tier = 1; tier <= 3; tier++) {
                            int t = tier;
                            out.accept(() -> add("material", m.name() + " T" + t, new GuideMaterialItemStack(m, t),
                                    new Extra(null, null, m.professionType(), m.level(),
                                            "material lv" + m.level() + " lv." + m.level() + " t" + t + " "
                                                    + m.professionType().getDisplayName().toLowerCase(Locale.ROOT),
                                            m.name(), null, null, ApiFacetsFactory.material(m))));
                        }
                    })
                    .toList());

            group("tool", () -> Models.Profession.getAllGatheringToolInfos()
                    .map(t -> (Runnable) () -> add("tool", t.name(), new GuideGatheringToolItemStack(t),
                            new Extra(null, null, null, t.level(), "tool lv" + t.level(), t.name(), null, null,
                                    ApiFacetsFactory.tool(t))))
                    .toList());

            group("charm", () -> Models.Rewards.getAllCharmInfos()
                    .filter(c -> !isDiscontinued("charm", c.name(), Models.Rewards.getCharmObtainInfo(c)))
                    .map(c -> (Runnable) () -> add("charm", c.name(), new GuideCharmItemStack(c), new Extra(
                            Models.Rewards.getCharmObtainInfo(c), null, null, c.requirements().level(),
                            "charm " + c.tier().getName().toLowerCase(Locale.ROOT) + " lv" + c.requirements().level(),
                            c.name(), null, null, ApiFacetsFactory.charm(c))))
                    .toList());

            group("tome", () -> Models.Rewards.getAllTomeInfos()
                    .filter(t -> !isDuplicateMarked(t.name(), t.metaInfo().apiName())
                            && !isDiscontinued("tome", t.name(), Models.Rewards.getTomeObtainInfo(t)))
                    .map(t -> (Runnable) () -> add("tome", t.name(), new GuideTomeItemStack(t), new Extra(
                            Models.Rewards.getTomeObtainInfo(t), null, null, t.requirements().level(),
                            "tome " + t.tier().getName().toLowerCase(Locale.ROOT) + " lv" + t.requirements().level(),
                            t.name(), null, null, ApiFacetsFactory.tome(t))))
                    .toList());

            // 1 種類 1 件（最高ティアの見た目）
            group("aspect", () -> Models.Aspect.getAllAspectInfos()
                    .map(a -> (Runnable) () -> {
                        int maxTier = Math.max(1, a.effects().size());
                        String key = add("aspect", a.name(), new GuideAspectItemStack(a, maxTier),
                                new Extra(null, null, null, 0,
                                        "aspect " + a.gearTier().getName().toLowerCase(Locale.ROOT)
                                                + (a.classType() == null ? "" : " " + a.classType().name().toLowerCase(Locale.ROOT)),
                                        a.name(), null, null, ApiFacets.NONE));
                        List<AspectTier> tiers = new ArrayList<>();
                        for (int t = 0; t < a.effects().size(); t++) {
                            var effect = a.effects().get(t);
                            List<Component> lines = new ArrayList<>();
                            for (var line : effect.value()) lines.add(line.getComponent());
                            tiers.add(new AspectTier(t + 1, effect.key(), List.copyOf(lines), a));
                        }
                        aspects.add(new AspectSnapshot(key, List.copyOf(tiers)));
                    })
                    .toList());

            group("powder", () -> Models.Element.getAllPowderTierInfo().stream()
                    .map(p -> (Runnable) () -> addUnnamed("powder", p.element().name() + "_" + p.tier(), new GuidePowderItemStack(p)))
                    .toList());

            group("rune", () -> Models.Rewards.getAllRuneInfo().stream()
                    .map(r -> (Runnable) () -> addUnnamed("rune", r.name(), new GuideRuneItemStack(r)))
                    .toList());

            group("ward", () -> Models.Rewards.getAllWardInfo().stream()
                    .map(w -> (Runnable) () -> addUnnamed("ward", w.name(), new GuideWardItemStack(w)))
                    .toList());

            group("augment", () -> {
                List<Runnable> list = new ArrayList<>();
                Models.Rewards.getAllAmplifierInfo()
                        .forEach(a -> list.add(() -> addUnnamed("augment", "amplifier_" + a.tier(), new GuideAmplifierItemStack(a))));
                list.add(() -> addUnnamed("augment", "simulator", new GuideSimulatorItemStack()));
                list.add(() -> addUnnamed("augment", "insulator", new GuideInsulatorItemStack()));
                return list;
            });

            group("emerald", () -> {
                List<Runnable> list = new ArrayList<>();
                // 順番は EmeraldUnits と同じ
                List<? extends ItemStack> units = Models.Emerald.getAllEmeraldItems();
                for (int u = 0; u < units.size(); u++) {
                    ItemStack unit = units.get(u);
                    String id = u < com.wynntils.models.emeralds.type.EmeraldUnits.values().length
                            ? com.wynntils.models.emeralds.type.EmeraldUnits.values()[u].name() : "unit_" + u;
                    list.add(() -> addUnnamed("emerald", id, unit));
                }
                for (int i = 1; i <= 10; i++) {
                    int tier = i;
                    list.add(() -> addUnnamed("emerald", "pouch_" + tier, new GuideEmeraldPouchItemStack(tier)));
                }
                return list;
            });

            group("dungeon_key", () -> {
                List<Runnable> list = new ArrayList<>();
                for (Dungeon dungeon : Dungeon.values()) {
                    if (dungeon.doesExist()) {
                        list.add(() -> addUnnamed("dungeon_key", dungeon.name(), new GuideDungeonKeyItemStack(dungeon, false, false)));
                        if (dungeon.doesCorruptedExist()) {
                            list.add(() -> addUnnamed("dungeon_key", dungeon.name() + "_broken", new GuideDungeonKeyItemStack(dungeon, false, true)));
                        }
                    }
                    if (dungeon.doesCorruptedExist()) {
                        list.add(() -> addUnnamed("dungeon_key", dungeon.name() + "_corrupted", new GuideDungeonKeyItemStack(dungeon, true, false)));
                        if (dungeon == Dungeon.LOST_SANCTUARY) {
                            list.add(() -> addUnnamed("dungeon_key", dungeon.name() + "_corrupted_broken", new GuideDungeonKeyItemStack(dungeon, true, true)));
                        }
                    }
                }
                return list;
            });

            group("set", () -> Models.Set.getSets().stream()
                    .map(set -> (Runnable) () -> addSet(set.name(), set.items(), set.bonuses()))
                    .toList());
        }

        /** 期限まで進める。完了で true。 */
        boolean step(long deadlineNanos) {
            while (!tasks.isEmpty()) {
                Task task = tasks.pollFirst();
                long start = System.nanoTime();
                task.body().run();
                long took = System.nanoTime() - start;
                if (took >= SLOW_TASK_NANOS) recordSlowTask(task.label(), took);
                if (System.nanoTime() >= deadlineNanos) break;
            }
            return tasks.isEmpty();
        }

        private void recordSlowTask(String label, long nanos) {
            slowTaskCount++;
            double ms = Math.round(nanos / 10_000.0) / 100.0;
            String entry = label + "=" + ms + "ms";
            Diag.warn(3, "Slow catalog task: {}", entry);
            if (slowTasks.size() < 10) slowTasks.add(entry);
        }

        /** 遅い作業の数と最初の 10 件 */
        String slowTaskSummary() {
            return slowTaskCount + " slow task(s)" + (slowTasks.isEmpty() ? "" : " " + slowTasks);
        }

        boolean complete() {
            return failedGroups.isEmpty();
        }

        Set<String> failedGroups() {
            return failedGroups;
        }

        WynnCatalog result() {
            io.github.wkwk.wei.Diag.log(3, "Hidden from the item list: {}", hiddenCounts);
            if (emptyTooltips > 0) {
                // 構築前に確認済みのため通常は 0
                Wei.LOGGER.warn("{} item(s) have no tooltip text (e.g. {})", emptyTooltips, emptyTooltipSamples);
            }
            return new WynnCatalog(List.copyOf(entries), List.copyOf(sets), List.copyOf(aspects));
        }

        int size() {
            return entries.size();
        }

        private void group(String name, Supplier<List<Runnable>> planner) {
            tasks.addLast(new Task("plan:" + name, () -> {
                List<Runnable> items;
                try {
                    items = planner.get();
                } catch (Throwable t) {
                    fail(name, t);
                    return;
                }
                // 並び順を保つため、次のグループより先に処理する
                for (int i = items.size() - 1; i >= 0; i--) {
                    Runnable item = items.get(i);
                    tasks.addFirst(new Task(name + "#" + i, () -> {
                        try {
                            item.run();
                        } catch (Throwable t) {
                            fail(name, t);
                        }
                    }));
                }
            }));
        }

        private void fail(String group, Throwable t) {
            // スタックトレースはグループごとに 1 回
            if (failedGroups.add(group)) {
                Wei.LOGGER.warn("Failed to load some Wynncraft {} items", group, t);
            }
        }

        /**
         * データに名前の無いアイテム。表示名は説明文から取り、キーは言語に依存しない ID
         * （例: rune/AZ、powder/EARTH_3）にする。
         */
        private void addUnnamed(String group, String id, ItemStack stack) {
            add(group, null, stack, Extra.NONE, id);
        }

        private String add(String group, @Nullable String plainName, ItemStack stack, Extra extra) {
            return add(group, plainName, stack, extra, null);
        }

        private String add(String group, @Nullable String plainName, ItemStack stack, Extra extra, @Nullable String keyId) {
            List<Component> tooltip = tooltipOf(stack);
            // Wynntils の説明文は 1 行目が空行で、名前は 2 行目以降に飾り文字付きで入る。
            // 表示名の優先順: データの名前 > 説明文の最初の文字行 > バニラの名前
            String nameLine = plainName != null ? null : firstTextLine(tooltip);
            Component display = plainName != null ? Component.literal(plainName)
                    : nameLine != null ? Component.literal(nameLine)
                    : stack.getHoverName();
            if (tooltip.size() <= 1) {
                emptyTooltips++;
                if (emptyTooltipSamples.size() < 10) emptyTooltipSamples.add(group + "/" + (plainName != null ? plainName : display.getString()));
            }
            if (tooltip.isEmpty()) tooltip = List.of(display);

            String plain = plainName != null ? plainName : Names.strip(display.getString());
            if (plain.isBlank()) plain = Names.strip(stack.getHoverName().getString());

            String keyBase = keyId != null ? keyId : plain;
            String key = group + "/" + keyBase;
            int n = 2;
            while (!keys.add(key)) {
                key = group + "/" + keyBase + "#" + n++;
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
                    quest,
                    extra.facets()));
            return key;
        }

        // ---- 一覧から除外するもの（件数は diag#3）
        private final Map<String, Integer> hiddenCounts = new java.util.TreeMap<>();

        private void countHidden(String reason) {
            hiddenCounts.merge(reason, 1, Integer::sum);
        }

        /** 名前に ֎ が付いた旧版（ゲーム内では同名が 2 つ並ぶ） */
        private boolean isDuplicateMarked(String name, java.util.Optional<String> apiName) {
            boolean marked = name.indexOf('\u058e') >= 0 || apiName.map(n -> n.indexOf('\u058e') >= 0).orElse(false);
            if (marked) countHidden("duplicate(֎)");
            return marked;
        }

        /** 廃止（入手元が unavailable） */
        private boolean isDiscontinued(String group, String name, @Nullable List<ItemObtainInfo> obtain) {
            boolean gone = obtain != null && obtain.stream().anyMatch(o -> o.sourceType() == ItemObtainType.UNAVAILABLE);
            if (gone) countHidden("discontinued:" + group);
            return gone;
        }

        private boolean isHiddenGear(com.wynntils.models.gear.type.GearInfo g, List<ItemObtainInfo> obtain) {
            if (isDiscontinued("gear", g.name(), obtain)) return true;
            if (g.metaInfo().restrictions() == com.wynntils.models.gear.type.GearRestrictions.QUEST_ITEM) {
                countHidden("questItem");
                return true;
            }
            // ID もメジャー ID も無いイベント景品。normal の初期装備は残す
            if (g.tier() != com.wynntils.models.gear.type.GearTier.NORMAL
                    && g.variableStats().isEmpty()
                    && g.fixedStats().majorIds().isEmpty()) {
                countHidden("noIdEventGear");
                return true;
            }
            return false;
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
                        tooltip.add(Component.translatable("wei.tooltip.set_pieces", e.getKey())
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

        private int emptyTooltips;
        private final List<String> emptyTooltipSamples = new ArrayList<>();

        /** 飾り文字を除いて文字が残る最初の行。無ければ null。 */
        private static @Nullable String firstTextLine(List<Component> lines) {
            for (Component line : lines) {
                String text = Names.strip(line.getString()).trim();
                if (!text.isEmpty()) return text;
            }
            return null;
        }

        static List<Component> tooltipOf(ItemStack stack) {
            try {
                return List.copyOf(stack.getTooltipLines(
                        Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL));
            } catch (Throwable t) {
                Diag.warn(3, "Tooltip failed for {}: {}", stack.getHoverName().getString(), t.toString());
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

        /** 独立した入手元にする名前（Wynntils のツールチップと同じ基準） */
        private @Nullable String sourceNameOf(ItemObtainType type, String raw) {
            if (raw.isEmpty() || type == ItemObtainType.GUILD) return null;
            if (type == ItemObtainType.LOOTRUN && raw.equals("Lootrun Rewards")) return null;
            if (type == ItemObtainType.QUEST) return resolveQuest(raw);
            return raw;
        }
    }

    /** GearModel と同じ条件: 鑑定済み固定でなく、ボックスで落ちる */
    private static boolean canBeInBox(boolean preIdentified, List<ItemObtainInfo> obtain) {
        if (preIdentified || obtain == null) return false;
        for (ItemObtainInfo info : obtain) {
            if (info != null && ItemObtainType.BOXED_ITEMS.contains(info.sourceType())) return true;
        }
        return false;
    }

    /** 手持ちのボックス（GearBoxItem）からも同じ形で作れる */
    public static String boxFamilyKey(String gearTypeName, String gearTierName) {
        return "box/" + gearTypeName + "/" + gearTierName;
    }

    private static BoxFamily boxFamily(String gearTypeName, String gearTierName, String tierDisplay) {
        return new BoxFamily(boxFamilyKey(gearTypeName, gearTierName), Component.translatable(
                "wei.box.name", tierDisplay, Names.titleCase(gearTypeName)));
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
