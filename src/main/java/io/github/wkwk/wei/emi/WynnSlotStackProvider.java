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
import dev.emi.emi.api.stack.EmiStackInteraction;
import io.github.wkwk.wei.data.WynnCatalog;
import io.github.wkwk.wei.mixin.AbstractContainerScreenAccessor;
import io.github.wkwk.wei.util.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * インベントリ・銀行・取引所などで、カーソルの下の Wynncraft のアイテムを EMI のスタックに置き換える。
 * これで手持ちのアイテムでも R（入手元）・U（使い道）が効く。
 *
 * <p>描画スレッドで呼ばれるので Wynntils に問い合わせてよい。クリック操作は奪わない（clickable = false）。
 * 該当しなければ空を返し、EMI の標準の処理に任せる。
 */
final class WynnSlotStackProvider implements EmiStackProvider<Screen> {
    // EMI は描画のたびにカーソルの下を問い合わせてくる。同じアイテム（同じ ItemStack）なら前の結果を返す
    private ItemStack lastStack;
    private WynnLookup lastLookup;
    private EmiStackInteraction lastResult = EmiStackInteraction.EMPTY;

    @Override
    public EmiStackInteraction getStackAt(Screen screen, int x, int y) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return EmiStackInteraction.EMPTY;
        Slot slot = ((AbstractContainerScreenAccessor) screen).wei$getHoveredSlot();
        if (slot == null) return EmiStackInteraction.EMPTY;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return EmiStackInteraction.EMPTY;
        WynnLookup lookup = WynnLookup.current();
        if (stack == lastStack && lookup == lastLookup) return lastResult;
        EmiStackInteraction result = resolve(stack, lookup);
        lastStack = stack;
        lastLookup = lookup;
        lastResult = result;
        return result;
    }

    private EmiStackInteraction resolve(ItemStack stack, WynnLookup lookup) {
        try {
            Optional<WynnItem> wynnItem = Models.Item.getWynnItem(stack);
            if (wynnItem.isEmpty()) return EmiStackInteraction.EMPTY;

            // 未鑑定ボックス：系統のスタックを返し、R でそのレベル範囲の候補へ飛ぶ
            if (wynnItem.get() instanceof GearBoxItem box) {
                // ツールチップの「可能性: レベル 101 … Lanternfly Leg」は Wynntils（ItemGuess）が足している行。
                // 同じ Models.Gear.getPossibleGears で候補を取り、その候補だけを出す
                List<WynnEmiStack> candidates = possibleGears(lookup, box, stack);
                WynnLookup.BoxIndex index = lookup.boxes.get(
                        WynnCatalog.boxFamilyKey(box.getGearType().name(), box.getGearTier().name()));
                if (!candidates.isEmpty() && index != null) {
                    // この箱だけの一時的なスタックを作り、R/U ではその候補への「未鑑定ボックス」のレシピだけを出す
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

            String key = keyOf(wynnItem.get());
            WynnEmiStack found = key == null ? null : lookup.byKey.get(key);
            if (key != null && found == null) {
                Diag.once(7, "miss:" + key, "Inventory item key not in catalog: '{}' ({})", key,
                        wynnItem.get().getClass().getSimpleName());
            }
            if (found == null) {
                // 種類ごとの対応が無いもの（パウダー・ルーンなど）は名前で引く
                String name = wynnItem.get() instanceof NamedItemProperty named ? named.getName() : null;
                if (name == null) name = stack.getHoverName().getString();
                String byName = lookup.keyByPlainName.get(Names.strip(name).toLowerCase(Locale.ROOT));
                if (byName != null) found = lookup.byKey.get(byName);
                // diag#7: 名前だけで対応付けた（別のアイテムと取り違える可能性がある）
                Diag.once(7, "name:" + wynnItem.get().getClass().getName() + ":" + name,
                        "Inventory item matched BY NAME: '{}' ({}) -> {}", name,
                        wynnItem.get().getClass().getSimpleName(), byName == null ? "no match" : byName);
                if (byName == null) logNameCandidates(lookup, name);
            }
            return found == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(found, null, false);
        } catch (Throwable t) {
            return EmiStackInteraction.EMPTY;
        }
    }

    /** カタログのキーと同じ形（WynnCatalog の add に渡す名前）を作る */
    private static @Nullable String keyOf(WynnItem item) {
        if (item instanceof GearItem gear) return "gear/" + gear.getName();
        if (item instanceof IngredientItem ing) return "ingredient/" + ing.getIngredientInfo().name();
        if (item instanceof MaterialItem mat) return "material/" + mat.getMaterialInfo().name() + " T" + mat.getQualityTier();
        if (item instanceof CharmItem charm) return "charm/" + charm.getItemInfo().name();
        if (item instanceof TomeItem tome) return "tome/" + tome.getItemInfo().name();
        if (item instanceof AspectItem aspect) return "aspect/" + aspect.getAspectInfo().name();
        return null;
    }

    /** diag#7: 名前で見つからなかったとき、最後の単語を含むカタログ名を最大5件出す（表記ゆれの調査用） */
    private static void logNameCandidates(WynnLookup lookup, String name) {
        String stripped = Names.strip(name).toLowerCase(Locale.ROOT).trim();
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

    /** 未鑑定ボックスの中身の候補（Wynntils の判定）のうち、一覧にあるもの */
    private static List<WynnEmiStack> possibleGears(WynnLookup lookup, GearBoxItem box, ItemStack stack) {
        List<WynnEmiStack> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        try {
            for (var gear : Models.Gear.getPossibleGears(box)) {
                names.add(gear.name());
                WynnEmiStack found = lookup.byKey.get("gear/" + gear.name());
                if (found == null) {
                    String key = lookup.keyByPlainName.get(Names.strip(gear.name()).toLowerCase(Locale.ROOT));
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
