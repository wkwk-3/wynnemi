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
    /** 価格が取得中のアイテムを見直す間隔（最初は短く、見つからないたびに倍にする） */
    private static final long RECHECK_FIRST_NANOS = 250_000_000L;
    private static final long RECHECK_MAX_NANOS = 8_000_000_000L;
    /** 1つのチェストで覚えておくアイテムの上限（中身の送り直しで増え続けないように） */
    private static final int MAX_CACHED = 512;

    private static boolean registered;

    /** value が empty なら nextCheckAt まで聞き直さない（売れない GUI のアイテムなどを毎フレーム聞かない） */
    private record Price(OptionalLong value, long nextCheckAt, long backoff) {}

    private final Map<ItemStack, Price> prices = new IdentityHashMap<>();
    /** 同じ画面で鳴らしすぎないための間隔 */
    private static final long SOUND_INTERVAL_NANOS = 1_000_000_000L;

    private Screen screen;
    private String priceType = "";
    /** 高額と判定したスロット → そのときのアイテム名（中身が入れ替わったら新しい当たりとして鳴らす） */
    private final Map<Integer, String> flagged = new java.util.HashMap<>();
    private long lastSoundAt;
    /** いま高額と判定しているスロット（枠を描く） */
    private final java.util.Set<Slot> highSlots = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    public static void registerOnce() {
        if (registered) return;
        registered = true;
        try {
            WynntilsMod.registerEventListener(new LootHighlight());
        } catch (Throwable t) {
            Wei.LOGGER.warn("Could not listen to Wynntils slot rendering; loot highlight is off", t);
        }
    }

    public static boolean isRewardChest(Container container) {
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

        if (!config.lootHighlightPrice.equals(priceType)) {
            // 判定に使う価格の種類が変わった
            priceType = config.lootHighlightPrice;
            prices.clear();
        }
        if (event.getScreen() != screen) {
            // 別のチェストを開いた
            screen = event.getScreen();
            prices.clear();
            flagged.clear();
            highSlots.clear();
            lastSoundAt = 0;
            Diag.log(14, "Reward chest opened: {} (price={}, threshold={} LE)",
                    container.getClass().getSimpleName(), config.lootHighlightPrice, config.lootHighlightThresholdLe);
        }

        OptionalLong price = priceOf(stack, config.lootHighlightPrice);
        if (price.isEmpty()) return;
        long threshold = Math.round(config.lootHighlightThresholdLe * 4096);
        if (price.getAsLong() < threshold) {
            // 安いものに入れ替わったスロットは、また高額品が入ったときに鳴らせるよう忘れる
            flagged.remove(slot.index);
            highSlots.remove(slot);
            return;
        }

        int x = slot.x;
        int y = slot.y;
        // 背景は薄く塗り、目立たせるのはアイテムの上に描く枠（onRenderSlotPost）
        int base = config.lootHighlightColor | 0xFF000000;
        event.getGuiGraphics().fill(x, y, x + 16, y + 16, (base & 0x00FFFFFF) | 0x70000000);
        highSlots.add(slot);

        // 初めての当たり、または同じスロットの中身が入れ替わって（ローランのリロールなど）また当たったとき
        String name = stack.getHoverName().getString();
        if (!name.equals(flagged.put(slot.index, name))) {
            long now = System.nanoTime();
            if (lastSoundAt != 0 && now - lastSoundAt < SOUND_INTERVAL_NANOS) return;
            lastSoundAt = now;
            Diag.log(14, "High value item: '{}' = {} emeralds ({} LE)", name,
                    price.getAsLong(), String.format("%.2f", price.getAsLong() / 4096.0));
            if (config.lootHighlightSound) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 1.0f));
            }
        }
    }

    /** アイテムの上に、点滅する太い枠を描く（Wynntils のレア度の色付けと見分けがつくように） */
    @SubscribeEvent
    public void onRenderSlotPost(SlotRenderEvent.Post event) {
        Slot slot = event.getSlot();
        if (!highSlots.contains(slot) || event.getScreen() != screen) return;
        if (slot.getItem().isEmpty()) {
            highSlots.remove(slot);
            return;
        }
        int rgb = WeiConfig.get().lootHighlightColor & 0x00FFFFFF;
        // 0.8 秒周期で明るさを変える
        double phase = (System.currentTimeMillis() % 800L) / 800.0;
        int alpha = (int) (0xA0 + 0x5F * (0.5 + 0.5 * Math.sin(phase * Math.PI * 2)));
        int color = (alpha << 24) | rgb;
        var g = event.getGuiGraphics();
        int x = slot.x;
        int y = slot.y;
        // 外側 1px（スロットの隙間）+ 内側 1px の 2px の枠
        g.fill(x - 1, y - 1, x + 17, y + 1, color);
        g.fill(x - 1, y + 15, x + 17, y + 17, color);
        g.fill(x - 1, y + 1, x + 1, y + 15, color);
        g.fill(x + 15, y + 1, x + 17, y + 15, color);
    }

    private OptionalLong priceOf(ItemStack stack, String priceType) {
        long now = System.nanoTime();
        Price cached = prices.get(stack);
        if (cached != null && (cached.value().isPresent() || now < cached.nextCheckAt())) {
            return cached.value();
        }
        if (prices.size() >= MAX_CACHED) prices.clear();
        Object gearBox = null;
        Optional<WynnItem> item = Models.Item.getWynnItem(stack);
        if (item.isPresent() && item.get() instanceof GearBoxItem box) gearBox = box;
        OptionalLong value = WynnventoryPrices.priceOf(stack, gearBox, priceType);
        long backoff = cached == null ? RECHECK_FIRST_NANOS : Math.min(RECHECK_MAX_NANOS, cached.backoff() * 2);
        prices.put(stack, new Price(value, now + backoff, backoff));
        return value;
    }
}
