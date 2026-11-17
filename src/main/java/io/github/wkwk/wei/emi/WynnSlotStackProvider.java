package io.github.wkwk.wei.emi;

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
    @Override
    public EmiStackInteraction getStackAt(Screen screen, int x, int y) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return EmiStackInteraction.EMPTY;
        try {
            Slot slot = ((AbstractContainerScreenAccessor) screen).wei$getHoveredSlot();
            if (slot == null) return EmiStackInteraction.EMPTY;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return EmiStackInteraction.EMPTY;

            Optional<WynnItem> wynnItem = Models.Item.getWynnItem(stack);
            if (wynnItem.isEmpty()) return EmiStackInteraction.EMPTY;
            WynnLookup lookup = WynnLookup.current();

            // 未鑑定ボックス：系統のスタックを返し、R でそのレベル範囲の候補へ飛ぶ
            if (wynnItem.get() instanceof GearBoxItem box) {
                WynnLookup.BoxIndex index = lookup.boxes.get(
                        WynnCatalog.boxFamilyKey(box.getGearType().name(), box.getGearTier().name()));
                if (index == null) return EmiStackInteraction.EMPTY;
                WynnLinkRecipe focus = index.firstInRange(box.getLevelRange().low(), box.getLevelRange().high());
                return new EmiStackInteraction(index.stack(), focus, false);
            }

            String key = keyOf(wynnItem.get());
            WynnEmiStack found = key == null ? null : lookup.byKey.get(key);
            if (found == null) {
                // 種類ごとの対応が無いもの（パウダー・ルーンなど）は名前で引く
                String name = wynnItem.get() instanceof NamedItemProperty named ? named.getName() : null;
                if (name == null) name = stack.getHoverName().getString();
                String byName = lookup.keyByPlainName.get(Names.strip(name).toLowerCase(Locale.ROOT));
                if (byName != null) found = lookup.byKey.get(byName);
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
        if (item instanceof AspectItem aspect) return "aspect/" + aspect.getAspectInfo().name() + " T" + aspect.getTier();
        return null;
    }
}
