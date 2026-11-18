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

    /**
     * 公式 API のアイテム検索（https://docs.wynncraft.com/modules/item-recipe/search-items）と同じ分類。
     * 値は API の表記（小文字・camelCase）に揃え、検索の {@code type:} {@code tier:} などで使う。
     */
    public record ApiFacets(
            /** weapon / armour / accessory / tome / charm / tool / ingredient / material */
            @Nullable String type,
            /** 武器種・防具部位・アクセサリー種・トーム種（weaponTome など）・ツール種（rod など） */
            @Nullable String subType,
            /** 装備: normal〜mythic、素材: tier_0〜tier_3 */
            @Nullable String tier,
            /** superSlow〜superFast */
            @Nullable String attackSpeed,
            int level,
            /** 製作職業（素材）または採集職業（マテリアル・ツール） */
            Set<String> professions,
            /** ID の API 名（小文字）→ 取りうる最大値。並び替えに使う */
            Map<String, Integer> identifications,
            /** Major ID の名前（英数字だけの小文字） */
            Set<String> majorIds) {
        public static final ApiFacets NONE = new ApiFacets(null, null, null, null, 0, Set.of(), Map.of(), Set.of());
    }

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
            @Nullable String requiredQuest,
            ApiFacets facets) {

        Entry withTooltip(ItemStack newStack, List<Component> newTooltip) {
            return new Entry(key, group, newStack, displayName, plainName, newTooltip, obtain, craftProfessions,
                    gatherProfession, level, aliases, favoriteName, boxFamily, requiredQuest, facets);
        }
    }

    /** セット装備。メンバーはカタログのキー */
    public record SetSnapshot(String name, List<String> memberKeys, List<Component> bonusTooltip) {}

    /** アスペクトのティア。threshold 個集めるとそのティアになる */
    public record AspectTier(int tier, int threshold, List<Component> effect, ItemStack stack, List<Component> tooltip) {}

    /** アスペクト1種類ぶん（一覧には1件だけ出し、ティアはレシピ画面で見せる） */
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

    /** 説明文が作れずバニラの名前で代用したアイテムのキー（ホバー時の調査ログ用） */
    private static final java.util.Set<String> TOOLTIP_FALLBACK_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isTooltipFallback(String key) {
        return TOOLTIP_FALLBACK_KEYS.contains(key);
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

    /** 診断用：その瞬間の接続状態（WynnDataWatcher のキャッシュではない） */
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
     * データが変わったかを見るための軽い指紋（クライアントスレッドで呼ぶ）。
     * 内容の更新は Wynntils のダウンロード完了イベントで拾うので、ここでは件数だけを見る（初回読み込みの取りこぼし防止用）。
     *
     * <p>プレイヤーのクラスは含めない。説明文の「クラスに合うか」の表示は変わるが、そのためだけに
     * キャラクターを切り替えるたびに全体を作り直し、EMI を再読み込みするのは重すぎる（実機ログで確認: 1 回の切り替えで 2 回ずつ）。
     * 説明文はカタログを作ったときのクラスのまま。
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
     * 数千件を1ティックで作るとゲームが止まるので、小さな作業に分けて少しずつ進める。
     * 作業の一覧づくり（各モデルの列挙）も1つの作業として扱う。
     */
    static final class Build {
        /** 1 つの作業。label は遅かったときにログで見分けるための名前 */
        private record Task(String label, Runnable body) {}

        /** この時間を超えた作業は遅いものとして記録する（diag#3: カクつきの原因の特定用） */
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
            TOOLTIP_FALLBACK_KEYS.clear();
            // 入手元は API の情報と有志のデータを合わせたもの（Wynntils のツールチップと同じ）を使う
            // 外すかどうかの判定も各アイテムの処理の中で行う（計画の段階で 5000 件を一度に見ると1ティックが重くなる）
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

            // アスペクトは1種類1件（最高ティアの見た目）。ティアと必要数はレシピ画面で見せる
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
                            // そのティアの見た目と説明文（レシピ画面の各行で使う）
                            ItemStack tierStack = new GuideAspectItemStack(a, t + 1);
                            tiers.add(new AspectTier(t + 1, effect.key(), List.copyOf(lines), tierStack,
                                    tooltipOf(tierStack).lines()));
                        }
                        aspects.add(new AspectSnapshot(key, List.copyOf(tiers)));
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

        /** 診断用：遅かった作業の数と、最初の 10 件 */
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
            Wei.LOGGER.info("[WEI tooltip] build summary: {} item(s) fell back to the vanilla name (player at build time: {})",
                    tooltipFallbacks, Minecraft.getInstance().player != null);
            Wei.LOGGER.info("[WEI item] name sources: {} / data name and tooltip name differ: {} item(s)",
                    nameSourceCounts, nameMismatches);
            retryEmptyTooltips();
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
                // このグループの各アイテムを、次のグループより先に処理する（並び順を保つ）
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
            // 同じグループのエラーは最初の1回だけ詳しく出す
            if (failedGroups.add(group)) {
                Wei.LOGGER.warn("Failed to load some Wynncraft {} items", group, t);
            }
        }

        private void addUnnamed(String group, ItemStack stack) {
            add(group, null, stack, Extra.NONE);
        }

        private String add(String group, @Nullable String plainName, ItemStack stack, Extra extra) {
            TooltipResult result = tooltipOf(stack);
            List<Component> tooltip = result.lines();
            // Wynntils の説明文は1行目が空行で、名前は飾り文字（私用領域の文字）と一緒に2行目以降にある。
            // 表示名は「飾りを除いて文字が残る最初の行」から取る。取れなければ Wynntils のデータの名前、最後にバニラの名前
            String nameLine = firstTextLine(tooltip);
            boolean fallback = nameLine == null && plainName == null;
            Component display = plainName != null ? Component.literal(plainName)
                    : nameLine != null ? Component.literal(nameLine)
                    : stack.getHoverName();
            if (nameLine == null && plainName != null) {
                Wei.LOGGER.warn("[WEI tooltip] no text line in tooltip; using data name group={} name='{}' lines={} first={}",
                        group, plainName, tooltip.size(), firstLinesForLog(tooltip));
            }
            if (tooltip.isEmpty()) tooltip = List.of(display);
            if (fallback) {
                tooltipFallbacks++;
                // 原因調査用。設定に関係なく常に出す
                String why = result.error() != null ? "exception " + result.error()
                        : result.lines().isEmpty() ? "empty tooltip"
                        : "no text line (" + result.lines().size() + " lines, first=" + firstLinesForLog(result.lines()) + ")";
                Wei.LOGGER.warn("[WEI tooltip] FALLBACK #{} group={} name='{}' stackClass={} vanillaName='{}' player={} cause={} detail={}",
                        tooltipFallbacks, group, plainName, stack.getClass().getName(), stack.getHoverName().getString(),
                        Minecraft.getInstance().player != null, why, describeForLog(stack));
                if (result.error() != null) {
                    if (tooltipFallbacks <= 20) {
                        Wei.LOGGER.warn("[WEI tooltip] stack trace for '{}':", plainName, result.error());
                    } else {
                        StackTraceElement[] st = result.error().getStackTrace();
                        Wei.LOGGER.warn("[WEI tooltip] (trace omitted after 20) top frames: {} | {} | {}",
                                st.length > 0 ? st[0] : "-", st.length > 1 ? st[1] : "-", st.length > 2 ? st[2] : "-");
                    }
                }
            }

            String plain = plainName != null ? plainName : Names.strip(display.getString());
            if (plain.isBlank()) plain = Names.strip(stack.getHoverName().getString());

            String key = group + "/" + plain;
            int n = 2;
            while (!keys.add(key)) {
                key = group + "/" + plain + "#" + n++;
            }
            if (fallback) {
                TOOLTIP_FALLBACK_KEYS.add(key);
                Wei.LOGGER.warn("[WEI tooltip] FALLBACK key='{}' display='{}'", key, display.getString());
            }

            // ---- 全アイテムの表示名の決まり方（常に出す）
            String nameSource = plainName != null ? "data" : nameLine != null ? "tooltipLine" : "vanilla";
            nameSourceCounts.merge(group + ":" + nameSource, 1, Integer::sum);
            int nameLineIndex = -1;
            for (int i = 0; i < tooltip.size(); i++) {
                if (!Names.strip(tooltip.get(i).getString()).trim().isEmpty()) {
                    nameLineIndex = i;
                    break;
                }
            }
            String tooltipName = nameLine == null ? "" : nameLine;
            boolean mismatch = plainName != null && nameLine != null
                    && !Names.normalize(plainName).equals(Names.normalize(nameLine));
            if (mismatch) nameMismatches++;
            if (entries.isEmpty() || tooltip.size() <= 1) {
                Wei.LOGGER.info("[WEI item] context for #{}: player={} classType={} tooltipLines={}",
                        entries.size() + 1, Minecraft.getInstance().player != null, safeClassType(), tooltip.size());
            }
            Wei.LOGGER.info("[WEI item] #{} key='{}' group={} nameSource={} display='{}' dataName='{}' tooltipName='{}'{} "
                            + "nameLineIndex={} lines={} vanillaName='{}' class={} line0={} line1={}",
                    entries.size() + 1, key, group, nameSource, display.getString(), plainName, tooltipName,
                    mismatch ? " NAME-MISMATCH" : "", nameLineIndex, tooltip.size(), stack.getHoverName().getString(),
                    stack.getClass().getSimpleName(),
                    tooltip.isEmpty() ? "-" : escape(tooltip.get(0).getString()),
                    tooltip.size() < 2 ? "-" : escape(tooltip.get(1).getString()));
            // 先頭の数件と、名前の行が見つからない・名前が食い違うものは、行の中身の構造（フォントなど）まで出す
            if (structureLogs < 30 || nameLine == null || mismatch) {
                structureLogs++;
                for (int i = 0; i < Math.min(3, tooltip.size()); i++) {
                    Wei.LOGGER.info("[WEI item]    line{} structure: {}", i, tooltip.get(i));
                }
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

        // ---- 一覧に出さないもの（件数は diag#3 に出す）
        private final Map<String, Integer> hiddenCounts = new java.util.TreeMap<>();

        private void countHidden(String reason) {
            hiddenCounts.merge(reason, 1, Integer::sum);
        }

        /** Wynncraft の API で名前に ֎ が付いたもの（同じ名前の古い版。ゲーム内では同じ名前が2つ並ぶ） */
        private boolean isDuplicateMarked(String name, java.util.Optional<String> apiName) {
            boolean marked = name.indexOf('\u058e') >= 0 || apiName.map(n -> n.indexOf('\u058e') >= 0).orElse(false);
            if (marked) countHidden("duplicate(֎)");
            return marked;
        }

        /** 入手元が「入手不可」（廃止） */
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
            // ID もメジャー ID も無いイベント景品（見た目だけのマスク・チーム防具など）。normal の初期装備は残す
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

        private int tooltipFallbacks;

        /**
         * 一覧作成の最初のほうで、Wynntils が空の説明文を返すことがある（ログで最初の16件）。
         * Wynntils のガイド用アイテムは一度作った説明文を覚えてしまうので、新しく作り直してもう一度取る。
         */
        private void retryEmptyTooltips() {
            int retried = 0;
            int fixed = 0;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.tooltip().size() > 1) continue;
                if (!(e.stack() instanceof com.wynntils.screens.guides.gear.GuideGearItemStack gear)) {
                    Wei.LOGGER.warn("[WEI tooltip] RETRY skipped key='{}' class={} lines={} (only gear can be rebuilt)",
                            e.key(), e.stack().getClass().getSimpleName(), e.tooltip().size());
                    continue;
                }
                retried++;
                var fresh = new com.wynntils.screens.guides.gear.GuideGearItemStack(gear.getGearInfo());
                TooltipResult result = tooltipOf(fresh);
                boolean ok = result.lines().size() > 1;
                if (ok) {
                    fixed++;
                    entries.set(i, e.withTooltip(fresh, result.lines()));
                }
                Wei.LOGGER.info("[WEI tooltip] RETRY key='{}' -> {} line(s){}{}", e.key(), result.lines().size(),
                        ok ? " FIXED" : " still empty", result.error() != null ? " error=" + result.error() : "");
            }
            Wei.LOGGER.info("[WEI tooltip] RETRY summary: {} retried, {} fixed (player={}, class={})", retried, fixed,
                    Minecraft.getInstance().player != null, safeClassType());
        }

        private static String safeClassType() {
            try {
                return String.valueOf(Models.Character.getClassType());
            } catch (Throwable t) {
                return "error " + t;
            }
        }
        private int nameMismatches;
        private int structureLogs;
        private final Map<String, Integer> nameSourceCounts = new java.util.TreeMap<>();

        /** 私用領域の文字や制御文字を \\uXXXX にして、ログで見えるようにする */
        private static String escape(String s) {
            StringBuilder sb = new StringBuilder("'");
            s.codePoints().forEach(cp -> {
                if (Character.getType(cp) == Character.PRIVATE_USE || Character.isISOControl(cp)
                        || Character.getType(cp) == Character.SURROGATE) {
                    sb.append(String.format("\\u{%X}", cp));
                } else {
                    sb.appendCodePoint(cp);
                }
            });
            return sb.append("'").toString();
        }

        /** 飾り文字を除いて文字が残る最初の行（名前の行）。無ければ null */
        private static @Nullable String firstTextLine(List<Component> lines) {
            for (Component line : lines) {
                String text = Names.strip(line.getString()).trim();
                if (!text.isEmpty()) return text;
            }
            return null;
        }

        private static String firstLinesForLog(List<Component> lines) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                if (i > 0) sb.append(" | ");
                sb.append('\'').append(lines.get(i).getString().replaceAll("\\p{Co}", "?")).append('\'');
            }
            return sb.append(']').toString();
        }

        record TooltipResult(List<Component> lines, @Nullable Throwable error) {}

        private static TooltipResult tooltipOf(ItemStack stack) {
            try {
                return new TooltipResult(List.copyOf(stack.getTooltipLines(
                        Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)), null);
            } catch (Throwable t) {
                return new TooltipResult(List.of(), t);
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

    /** 調査ログ用: Wynntils のガイド用アイテムなら、元になったデータの要点を書き出す */
    static String describeForLog(ItemStack stack) {
        try {
            if (stack instanceof com.wynntils.screens.guides.gear.GuideGearItemStack gear) {
                var g = gear.getGearInfo();
                StringBuilder sb = new StringBuilder();
                sb.append("gear{type=").append(g.type()).append(", tier=").append(g.tier())
                        .append(", level=").append(g.requirements().level())
                        .append(", preIdentified=").append(g.metaInfo().preIdentified())
                        .append(", material=").append(g.metaInfo().material())
                        .append(", powderSlots=").append(g.powderSlots())
                        .append(", majorIds=").append(g.fixedStats().majorIds().isPresent())
                        .append(", stats=[");
                for (var stat : g.variableStats()) sb.append(stat.key().getApiName()).append(' ');
                return sb.append("]}").toString();
            }
            return stack.getItem().toString() + " components=" + stack.getComponents();
        } catch (Throwable t) {
            return "(describe failed: " + t + ")";
        }
    }
}
