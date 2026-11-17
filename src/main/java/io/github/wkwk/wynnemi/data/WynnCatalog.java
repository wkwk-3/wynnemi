package io.github.wkwk.wynnemi.data;

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
import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.util.Names;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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
            @Nullable ProfessionType gatherProfession) {}

    private static volatile WynnCatalog current = new WynnCatalog(List.of());

    private final List<Entry> entries;

    private WynnCatalog(List<Entry> entries) {
        this.entries = entries;
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

        Build() {
            group("gear", () -> Models.Gear.getAllGearInfos()
                    .map(g -> (Runnable) () -> add("gear", g.name(), new GuideGearItemStack(g),
                            g.metaInfo().obtainInfo(), List.of(), null))
                    .toList());

            group("ingredient", () -> Models.Ingredient.getAllIngredientInfos()
                    .map(i -> (Runnable) () -> add("ingredient", i.name(), new GuideIngredientItemStack(i),
                            i.obtainInfo(), i.professions(), null))
                    .toList());

            group("material", () -> Models.Profession.getAllMaterialInfos()
                    .<Runnable>mapMulti((m, out) -> {
                        for (int tier = 1; tier <= 3; tier++) {
                            int t = tier;
                            out.accept(() -> add("material", m.name() + " T" + t, new GuideMaterialItemStack(m, t),
                                    List.of(), List.of(), m.professionType()));
                        }
                    })
                    .toList());

            group("tool", () -> Models.Profession.getAllGatheringToolInfos()
                    .map(t -> (Runnable) () -> add("tool", t.name(), new GuideGatheringToolItemStack(t),
                            List.of(), List.of(), null))
                    .toList());

            group("charm", () -> Models.Rewards.getAllCharmInfos()
                    .map(c -> (Runnable) () -> add("charm", c.name(), new GuideCharmItemStack(c),
                            c.metaInfo().obtainInfo(), List.of(), null))
                    .toList());

            group("tome", () -> Models.Rewards.getAllTomeInfos()
                    .map(t -> (Runnable) () -> add("tome", t.name(), new GuideTomeItemStack(t),
                            t.metaInfo().obtainInfo(), List.of(), null))
                    .toList());

            group("aspect", () -> Models.Aspect.getAllAspectInfos()
                    .<Runnable>mapMulti((a, out) -> {
                        for (int tier = 1; tier <= a.effects().size(); tier++) {
                            int t = tier;
                            out.accept(() -> add("aspect", a.name() + " T" + t, new GuideAspectItemStack(a, t),
                                    List.of(), List.of(), null));
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
            return new WynnCatalog(List.copyOf(entries));
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
                WynnEmiClient.LOGGER.warn("Failed to load some Wynncraft {} items", group, t);
            }
        }

        private void addUnnamed(String group, ItemStack stack) {
            add(group, null, stack, List.of(), List.of(), null);
        }

        private void add(String group, @Nullable String plainName, ItemStack stack, @Nullable List<ItemObtainInfo> obtain,
                @Nullable List<ProfessionType> craft, @Nullable ProfessionType gather) {
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

            entries.add(new Entry(key, group, stack, display, plain, tooltip,
                    snapshotObtain(obtain),
                    craft == null ? List.of() : List.copyOf(craft),
                    gather));
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
            if (type == ItemObtainType.QUEST) {
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
            return raw;
        }
    }
}
