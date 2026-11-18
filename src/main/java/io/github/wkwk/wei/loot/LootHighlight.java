package io.github.wkwk.wei.loot;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.mc.event.SlotRenderEvent;
import com.wynntils.models.containers.Container;
import com.wynntils.models.containers.containers.LootrunRewardChestContainer;
import com.wynntils.models.containers.containers.RaidRewardChestContainer;
import com.wynntils.models.containers.containers.reward.EventContainer;
import com.wynntils.models.containers.containers.reward.LootChestContainer;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.WeiConfig;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * ルートチェスト・ローラン報酬・レイド報酬・イベント報酬を開いたとき、
 * 取引所の価格が基準以上のアイテムのスロットに色を付け、最初に見つかったときに1回だけ音を鳴らす。
 */
public final class LootHighlight {
    /** 価格が取得中のアイテムを見直す間隔 */
    private static final long RECHECK_NANOS = 250_000_000L;

    private static boolean registered;

    private record Price(OptionalLong value, long checkedAt) {}

    private final Map<ItemStack, Price> prices = new IdentityHashMap<>();
    private Screen screen;
    private boolean soundPlayed;

    public static void registerOnce() {
        if (registered) return;
        registered = true;
        try {
            WynntilsMod.registerEventListener(new LootHighlight());
        } catch (Throwable t) {
            Wei.LOGGER.warn("Could not listen to Wynntils slot rendering; loot highlight is off", t);
        }
    }

    static boolean isRewardChest(Container container) {
        return container instanceof LootChestContainer
                || container instanceof LootrunRewardChestContainer
                || container instanceof RaidRewardChestContainer
                || container instanceof EventContainer;
    }

    @SubscribeEvent
    public void onRenderSlot(SlotRenderEvent.Pre event) {
        WeiConfig config = WeiConfig.get();
        if (!config.lootHighlightEnabled) return;
        Slot slot = event.getSlot();
        if (slot.container instanceof Inventory) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        Container container = Models.Container.getCurrentContainer();
        if (container == null || !isRewardChest(container)) return;
        if (!WynnventoryPrices.isAvailable()) return;

        if (event.getScreen() != screen) {
            // 別のチェストを開いた
            screen = event.getScreen();
            prices.clear();
            soundPlayed = false;
            Diag.log(14, "Reward chest opened: {} (price={}, threshold={} LE)",
                    container.getClass().getSimpleName(), config.lootHighlightPrice, config.lootHighlightThresholdLe);
        }

        OptionalLong price = priceOf(stack, config.lootHighlightPrice);
        long threshold = Math.round(config.lootHighlightThresholdLe * 4096);
        if (price.isEmpty() || price.getAsLong() < threshold) return;

        int x = slot.x;
        int y = slot.y;
        event.getGuiGraphics().fill(x, y, x + 16, y + 16, config.lootHighlightColor);

        if (!soundPlayed) {
            soundPlayed = true;
            Diag.log(14, "High value item: '{}' = {} emeralds ({} LE)", stack.getHoverName().getString(),
                    price.getAsLong(), String.format("%.2f", price.getAsLong() / 4096.0));
            if (config.lootHighlightSound) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.5f, 1.0f));
            }
        }
    }

    private OptionalLong priceOf(ItemStack stack, String priceType) {
        long now = System.nanoTime();
        Price cached = prices.get(stack);
        if (cached != null && (cached.value().isPresent() || now - cached.checkedAt() < RECHECK_NANOS)) {
            return cached.value();
        }
        Object gearBox = null;
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        if (item.isPresent() && item.get() instanceof GearBoxItem box) gearBox = box;
        OptionalLong value = WynnventoryPrices.priceOf(stack, gearBox, priceType);
        prices.put(stack, new Price(value, now));
        return value;
    }
}
