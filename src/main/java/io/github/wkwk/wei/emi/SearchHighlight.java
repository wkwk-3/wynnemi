package io.github.wkwk.wei.emi;

import com.wynntils.core.WynntilsMod;
import com.wynntils.mc.event.SlotRenderEvent;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.emihook.EmiHooks;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * EMI の検索欄のハイライト表示（ダブルクリック）で、条件に合わないスロットを暗くする。
 * EMI の検索語に加えて WEI の検索語（tier: や id. など）も効く。EMI 自身の暗転は Mixin で止めている。
 */
public final class SearchHighlight {
    private static final int DIM = 0x99000000;
    private static boolean registered;

    private SearchHighlight() {}

    public static void registerOnce() {
        if (registered) return;
        registered = true;
        try {
            WynntilsMod.registerEventListener(new SearchHighlight());
        } catch (Throwable t) {
            Wei.LOGGER.warn("Could not listen to Wynntils slot rendering; search highlight is off", t);
        }
    }

    @SubscribeEvent
    public void onRenderSlotPost(SlotRenderEvent.Post event) {
        try {
            if (EmiScreenManager.search == null || !EmiScreenManager.search.highlight) return;
            EmiSearch.CompiledQuery query = EmiSearch.compiledQuery;
            if (query == null) return;
            Slot slot = event.getSlot();
            ItemStack item = slot.getItem();
            if (query.test(EmiStack.of(item)) && EmiHooks.highlightPass(query, item)) return;
            event.getGuiGraphics().fill(slot.x, slot.y, slot.x + 16, slot.y + 16, DIM);
        } catch (Throwable t) {
            Diag.swallowed("SearchHighlight: slot overlay", t);
        }
    }
}
