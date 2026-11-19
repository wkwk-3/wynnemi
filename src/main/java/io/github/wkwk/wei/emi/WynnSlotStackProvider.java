package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Diag;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.AspectItem;
import com.wynntils.models.items.items.game.CharmItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.models.items.items.game.MaterialItem;
import com.wynntils.models.items.items.game.TomeItem;
import com.wynntils.models.items.properties.NamedItemProperty;
import dev.emi.emi.api.EmiStackProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.data.WynnCatalog;
import io.github.wkwk.wei.mixin.AbstractContainerScreenAccessor;
import io.github.wkwk.wei.util.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * カーソル下の Wynncraft のアイテムを EMI のスタックに置き換え、手持ちのアイテムでも R/U を効かせる。
 *
 * <p>描画スレッドで呼ばれる（Wynntils に問い合わせ可）。クリックは奪わない。該当しなければ空を返す。
 */
final class WynnSlotStackProvider implements EmiStackProvider<Screen> {
    // EMI は毎フレーム問い合わせるため、同じ ItemStack なら前回の結果を返す
    private ItemStack lastStack;
    private WynnLookup lastLookup;
    private EmiStackInteraction lastResult = EmiStackInteraction.EMPTY;
    private boolean retryLater;

    @Override
    public EmiStackInteraction getStackAt(Screen screen, int x, int y) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return EmiStackInteraction.EMPTY;
        Slot slot = ((AbstractContainerScreenAccessor) screen).wei$getHoveredSlot();
        if (slot == null) return EmiStackInteraction.EMPTY;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return EmiStackInteraction.EMPTY;
        WynnLookup lookup = WynnLookup.current();
        if (stack == lastStack && lookup == lastLookup) return lastResult;
        retryLater = false;
        EmiStackInteraction result = resolve(stack, lookup);
        // Wynntils の解析待ちや例外の結果は覚えない（次のフレームで引き直す）
        if (retryLater) {
            lastStack = null;
            return result;
        }
        lastStack = stack;
        lastLookup = lookup;
        lastResult = result;
        return result;
    }

    /** 検索欄のハイライト表示で、スロットのアイテムを WEI の条件で判定できるようにする */
    static void registerHighlight() {
        EmiHooks.setSlotResolver(WynnSlotStackProvider::searchTargets, WynnLookup::current);
    }

    /** 検索条件と照らす対象。未鑑定ボックスは候補すべて */
    private static List<? extends EmiStack> searchTargets(ItemStack stack) {
        Optional<WynnItem> wynnItem = Models.Item.getWynnItem(stack);
        if (wynnItem.isEmpty()) return List.of();
        WynnLookup lookup = WynnLookup.current();
        if (wynnItem.get() instanceof GearBoxItem box) return possibleGears(lookup, box, stack);
        WynnEmiStack found = findItem(lookup, wynnItem.get(), stack);
        return found == null ? List.of() : List.of(found);
    }

    private EmiStackInteraction resolve(ItemStack stack, WynnLookup lookup) {
        try {
            Optional<WynnItem> wynnItem = Models.Item.getWynnItem(stack);
            if (wynnItem.isEmpty()) {
                retryLater = true;
                return EmiStackInteraction.EMPTY;
            }

            // 未鑑定ボックス
            if (wynnItem.get() instanceof GearBoxItem box) {
                // 候補はツールチップの「可能性:」行（Wynntils の ItemGuess）と同じ getPossibleGears で取る
                List<WynnEmiStack> candidates = possibleGears(lookup, box, stack);
                WynnLookup.BoxIndex index = lookup.boxes.get(
                        WynnCatalog.boxFamilyKey(box.getGearType().name(), box.getGearTier().name()));
                if (!candidates.isEmpty() && index != null) {
                    WynnEmiStack instance = WynnBoxInstances.create(stack, box, index, candidates);
                    if (instance != null) return new EmiStackInteraction(instance, null, false);
                }
                if (!candidates.isEmpty()) {
                    if (candidates.size() == 1) return new EmiStackInteraction(candidates.get(0), null, false);
                    return new EmiStackInteraction(dev.emi.emi.api.stack.EmiIngredient.of(candidates), null, false);
                }
                if (index == null) return EmiStackInteraction.EMPTY;
                WynnLinkRecipe focus = index.firstInRange(box.getLevelRange().low(), box.getLevelRange().high());
                return new EmiStackInteraction(index.stack(), focus, false);
            }

            WynnEmiStack found = findItem(lookup, wynnItem.get(), stack);
            return found == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(found, null, false);
        } catch (Throwable t) {
            Diag.swallowed("WynnSlotStackProvider: resolve", t);
            retryLater = true;
            return EmiStackInteraction.EMPTY;
        }
    }

    /** 一覧のスタック。キーで引き、対応が無いもの（パウダー・ルーンなど）は名前で引く */
    private static @Nullable WynnEmiStack findItem(WynnLookup lookup, WynnItem item, ItemStack stack) {
        String key = keyOf(item);
        WynnEmiStack found = key == null ? null : lookup.byKey.get(key);
        if (found != null) return found;
        if (key != null) {
            Diag.once(7, "miss:" + key, "Inventory item key not in catalog: '{}' ({})", key, item.getClass().getSimpleName());
        }
        String name = item instanceof NamedItemProperty named ? named.getName() : null;
        if (name == null) name = stack.getHoverName().getString();
        String byName = lookup.keyByPlainName.get(Names.nameKey(name));
        if (byName != null) found = lookup.byKey.get(byName);
        Diag.once(7, "name:" + item.getClass().getName() + ":" + name, "Inventory item matched BY NAME: '{}' ({}) -> {}",
                name, item.getClass().getSimpleName(), byName == null ? "no match" : byName);
        if (byName == null) logNameCandidates(lookup, name);
        return found;
    }

    /** カタログと同じ形式のキー */
    private static @Nullable String keyOf(WynnItem item) {
        if (item instanceof GearItem gear) return "gear/" + gear.getName();
        if (item instanceof IngredientItem ing) return "ingredient/" + ing.getIngredientInfo().name();
        if (item instanceof MaterialItem mat) return "material/" + mat.getMaterialInfo().name() + " T" + mat.getQualityTier();
        if (item instanceof CharmItem charm) return "charm/" + charm.getItemInfo().name();
        if (item instanceof TomeItem tome) return "tome/" + tome.getItemInfo().name();
        if (item instanceof AspectItem aspect) return "aspect/" + aspect.getAspectInfo().name();
        return null;
    }

    /** diag#7: 名前で見つからないとき、最後の単語を含むカタログ名を最大 5 件出す */
    private static void logNameCandidates(WynnLookup lookup, String name) {
        String stripped = Names.nameKey(name);
        String[] words = stripped.split("\\s+");
        String last = words.length == 0 ? "" : words[words.length - 1];
        if (last.length() < 3) return;
        List<String> candidates = new ArrayList<>();
        for (String plain : lookup.keyByPlainName.keySet()) {
            if (plain.contains(last)) {
                candidates.add(plain);
                if (candidates.size() >= 5) break;
            }
        }
        Diag.once(7, "cand:" + stripped, "Name candidates for '{}' (stripped '{}', word '{}'): {}", name, stripped, last,
                candidates.isEmpty() ? "none" : candidates);
    }

    /** 未鑑定ボックスの候補のうち一覧にあるもの */
    private static List<WynnEmiStack> possibleGears(WynnLookup lookup, GearBoxItem box, ItemStack stack) {
        List<WynnEmiStack> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        try {
            for (var gear : Models.Gear.getPossibleGears(box)) {
                names.add(gear.name());
                WynnEmiStack found = lookup.byKey.get("gear/" + gear.name());
                if (found == null) {
                    String key = lookup.keyByPlainName.get(Names.nameKey(gear.name()));
                    if (key != null) found = lookup.byKey.get(key);
                }
                if (found == null) missing.add(gear.name());
                else if (!out.contains(found)) out.add(found);
            }
        } catch (Throwable t) {
            Diag.warn(7, "getPossibleGears failed: {}", t.toString());
        }
        if (Diag.enabled()) {
            String name = Names.strip(stack.getHoverName().getString());
            Diag.once(7, "box:" + name + "|" + box.getLevelRange(), "Box '{}' type={} tier={} levels={} possible={} -> shown={} notInList={}",
                    name, box.getGearType(), box.getGearTier(), box.getLevelRange(), names,
                    out.stream().map(WynnEmiStack::key).toList(), missing);
        }
        return out;
    }

}
