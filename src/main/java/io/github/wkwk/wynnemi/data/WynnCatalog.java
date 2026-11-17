package io.github.wkwk.wynnemi.data;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.models.activities.type.Dungeon;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Wynntils のデータから作った表示用アイテムの一覧（スナップショット）。
 * Wynntils の Guide*ItemStack は描画スレッドで作る必要があるので、
 * {@link WynnDataWatcher} がクライアントスレッドで作り、EMI のプラグイン（別スレッド）は読むだけにする。
 */
public final class WynnCatalog {
    public record Entry(
            String key,
            String group,
            ItemStack stack,
            Component displayName,
            String plainName,
            List<ItemObtainInfo> obtainInfo,
            List<ProfessionType> craftProfessions,
            @Nullable ProfessionType gatherProfession) {}

    private static volatile WynnCatalog current = new WynnCatalog(List.of(), false);

    private final List<Entry> entries;
    private final boolean onWynncraft;

    private WynnCatalog(List<Entry> entries, boolean onWynncraft) {
        this.entries = entries;
        this.onWynncraft = onWynncraft;
    }

    public static WynnCatalog current() {
        return current;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean onWynncraft() {
        return onWynncraft;
    }

    public static boolean isOnWynncraft() {
        try {
            return Managers.Connection.onServer();
        } catch (Throwable t) {
            return false;
        }
    }

    /** データの量が変わったか判定するための軽い指紋。クライアントスレッドで呼ぶ。 */
    static String computeSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(safeCount(() -> Models.Gear.getAllGearInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Ingredient.getAllIngredientInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Profession.getAllMaterialInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Profession.getAllGatheringToolInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Rewards.getAllCharmInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Rewards.getAllTomeInfos().count())).append(',');
        sb.append(safeCount(() -> Models.Aspect.getAllAspectInfos().count())).append(',');
        sb.append(safeCount(() -> (long) Models.Element.getAllPowderTierInfo().size())).append(',');
        sb.append(safeCount(() -> (long) Models.Rewards.getAllAmplifierInfo().size())).append(',');
        sb.append(safeCount(() -> Models.WynnItem.hasObtainInfo() ? 1L : 0L)).append(',');
        sb.append(isOnWynncraft());
        return sb.toString();
    }

    private interface LongSupplier {
        long get() throws Throwable;
    }

    private static long safeCount(LongSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** クライアントスレッドで呼ぶ。 */
    static void rebuild() {
        Builder b = new Builder();

        b.group("gear", () -> Models.Gear.getAllGearInfos()
                .forEach(g -> b.add("gear", g.name(), new GuideGearItemStack(g), g.metaInfo().obtainInfo(), List.of(), null)));

        b.group("ingredient", () -> Models.Ingredient.getAllIngredientInfos()
                .forEach(i -> b.add("ingredient", i.name(), new GuideIngredientItemStack(i), i.obtainInfo(), i.professions(), null)));

        b.group("material", () -> Models.Profession.getAllMaterialInfos().forEach(m -> IntStream.rangeClosed(1, 3)
                .forEach(tier -> b.add("material", m.name() + " T" + tier, new GuideMaterialItemStack(m, tier),
                        List.of(), List.of(), m.professionType()))));

        b.group("tool", () -> Models.Profession.getAllGatheringToolInfos()
                .forEach(t -> b.add("tool", t.name(), new GuideGatheringToolItemStack(t), List.of(), List.of(), null)));

        b.group("charm", () -> Models.Rewards.getAllCharmInfos()
                .forEach(c -> b.add("charm", c.name(), new GuideCharmItemStack(c), c.metaInfo().obtainInfo(), List.of(), null)));

        b.group("tome", () -> Models.Rewards.getAllTomeInfos()
                .forEach(t -> b.add("tome", t.name(), new GuideTomeItemStack(t), t.metaInfo().obtainInfo(), List.of(), null)));

        b.group("aspect", () -> Models.Aspect.getAllAspectInfos().forEach(a -> IntStream.rangeClosed(1, a.effects().size())
                .forEach(tier -> b.add("aspect", a.name() + " T" + tier, new GuideAspectItemStack(a, tier),
                        List.of(), List.of(), null))));

        b.group("powder", () -> Models.Element.getAllPowderTierInfo()
                .forEach(p -> b.addUnnamed("powder", new GuidePowderItemStack(p))));

        b.group("rune", () -> Models.Rewards.getAllRuneInfo()
                .forEach(r -> b.addUnnamed("rune", new GuideRuneItemStack(r))));

        b.group("ward", () -> Models.Rewards.getAllWardInfo()
                .forEach(w -> b.addUnnamed("ward", new GuideWardItemStack(w))));

        b.group("augment", () -> {
            Models.Rewards.getAllAmplifierInfo().forEach(a -> b.addUnnamed("augment", new GuideAmplifierItemStack(a)));
            b.addUnnamed("augment", new GuideSimulatorItemStack());
            b.addUnnamed("augment", new GuideInsulatorItemStack());
        });

        b.group("emerald", () -> {
            Models.Emerald.getAllEmeraldItems().forEach(e -> b.addUnnamed("emerald", e));
            for (int i = 1; i <= 10; i++) {
                b.addUnnamed("emerald", new GuideEmeraldPouchItemStack(i));
            }
        });

        // Wynntils のダンジョンキーガイドと同じ組み合わせ
        b.group("dungeon_key", () -> {
            for (Dungeon dungeon : Dungeon.values()) {
                if (dungeon.doesExist()) {
                    b.addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, false, false));
                    if (dungeon.doesCorruptedExist()) {
                        b.addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, false, true));
                    }
                }
                if (dungeon.doesCorruptedExist()) {
                    b.addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, true, false));
                    if (dungeon == Dungeon.LOST_SANCTUARY) {
                        b.addUnnamed("dungeon_key", new GuideDungeonKeyItemStack(dungeon, true, true));
                    }
                }
            }
        });

        current = new WynnCatalog(List.copyOf(b.entries), isOnWynncraft());
        WynnEmiClient.LOGGER.info("Built Wynncraft item catalog: {} items", b.entries.size());
    }

    private static final class Builder {
        final List<Entry> entries = new ArrayList<>();
        final Set<String> keys = new HashSet<>();

        void group(String name, Runnable body) {
            int before = entries.size();
            try {
                body.run();
            } catch (Throwable t) {
                // Wynntils 側の変更で1種類が壊れても他は出す
                WynnEmiClient.LOGGER.warn("Failed to load Wynncraft {} items ({} added before failure)",
                        name, entries.size() - before, t);
            }
        }

        void addUnnamed(String group, ItemStack stack) {
            add(group, null, stack, List.of(), List.of(), null);
        }

        void add(String group, @Nullable String plainName, ItemStack stack, List<ItemObtainInfo> obtain,
                List<ProfessionType> craft, @Nullable ProfessionType gather) {
            Component display = displayNameOf(stack);
            String plain = plainName != null ? plainName : strip(display.getString());
            if (plain.isBlank()) plain = strip(stack.getHoverName().getString());

            String key = group + "/" + plain;
            int n = 2;
            while (!keys.add(key)) {
                key = group + "/" + plain + "#" + n++;
            }
            entries.add(new Entry(key, group, stack, display, plain,
                    obtain == null ? List.of() : List.copyOf(obtain),
                    craft == null ? List.of() : List.copyOf(craft),
                    gather));
        }
    }

    /**
     * Wynntils の Guide*ItemStack は getTooltipLines() をオーバーライドして説明文を作り、結果をキャッシュする。
     * ここで1回呼んでおくと、EMI が別スレッドで検索インデックスを作るときもキャッシュが返るので安全。
     */
    private static Component displayNameOf(ItemStack stack) {
        try {
            List<Component> lines = stack.getTooltipLines(
                    Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL);
            if (!lines.isEmpty() && !lines.get(0).getString().isBlank()) {
                return lines.get(0);
            }
        } catch (Throwable ignored) {
        }
        return stack.getHoverName();
    }

    static String strip(String s) {
        String stripped = ChatFormatting.stripFormatting(s);
        if (stripped == null) return "";
        // Wynncraft のフォントのアイコン（私用領域の文字）を除く
        return stripped.replaceAll("[\\uE000-\\uF8FF]|[\\uDB80-\\uDBFF][\\uDC00-\\uDFFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
