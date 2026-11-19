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
 * {@link CatalogBuild} で行う。EMI のプラグイン（別スレッド）はこれを読むだけ。
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

    static WynnCatalog create(List<Entry> entries, List<SetSnapshot> sets, List<AspectSnapshot> aspects) {
        return new WynnCatalog(entries, sets, aspects);
    }

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
            return CatalogBuild.tooltipOf(new GuideGearItemStack(first.get())).size() > 1;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 描画スレッドから呼ぶ */
    public static List<Component> tooltipLines(ItemStack stack) {
        return CatalogBuild.tooltipOf(stack);
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

}
