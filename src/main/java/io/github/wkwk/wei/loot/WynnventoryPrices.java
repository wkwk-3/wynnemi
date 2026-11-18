package io.github.wkwk.wei.loot;

import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 取引所の価格を、入っている Wynnventory MOD に聞く（Wynnventory の鍵と通信・キャッシュをそのまま使う）。
 * Wynnventory の公開 API ではないため、クラスやメソッドが見つからなければ機能を止める。
 *
 * <p>使っているもの（Wynnventory 2.2.5 で確認）:
 * <ul>
 *   <li>{@code TrademarketItemSnapshot.resolveSnapshot(ItemStack)} … 1つのアイテムの価格（初回は null を返し裏で取得）</li>
 *   <li>{@code TrademarketItemSnapshot.resolveGearBoxItem(GearBoxItem)} … 未鑑定ボックスの中身の候補ごとの価格</li>
 *   <li>{@code snapshot.live() / historic()} と {@code TrademarketItemSummary.getLowestPrice()} など</li>
 * </ul>
 */
public final class WynnventoryPrices {
    private static final String SNAPSHOT = "com.wynnventory.model.item.trademarket.TrademarketItemSnapshot";

    private static boolean initialized;
    private static boolean available;
    private static Method resolveSnapshot;
    private static @Nullable Method resolveGearBox;
    private static Method live;
    private static Method historic;

    private WynnventoryPrices() {}

    public static synchronized boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            init();
        }
        return available;
    }

    private static void init() {
        if (!FabricLoader.getInstance().isModLoaded("wynnventory")) {
            Diag.log(14, "Wynnventory is not installed; loot highlight is off");
            return;
        }
        try {
            Class<?> snapshot = Class.forName(SNAPSHOT);
            resolveSnapshot = snapshot.getMethod("resolveSnapshot", ItemStack.class);
            live = snapshot.getMethod("live");
            historic = snapshot.getMethod("historic");
            try {
                Class<?> box = Class.forName("com.wynntils.models.items.items.game.GearBoxItem");
                resolveGearBox = snapshot.getMethod("resolveGearBoxItem", box);
            } catch (ReflectiveOperationException e) {
                resolveGearBox = null;
                Diag.warn(14, "Wynnventory has no gear box price lookup; unidentified boxes are not checked");
            }
            available = true;
            Diag.log(14, "Using Wynnventory prices (gear box lookup: {})", resolveGearBox != null);
        } catch (Throwable t) {
            Wei.LOGGER.warn("Wynnventory was found but its price classes have changed; loot highlight is off", t);
        }
    }

    /**
     * アイテム1つの価格（エメラルド）。まだ取得中・価格なし・売れないアイテムは empty。
     * 未鑑定ボックスは候補の中で一番高いもの。
     */
    public static OptionalLong priceOf(ItemStack stack, @Nullable Object gearBox, String priceType) {
        if (!isAvailable() || stack.isEmpty()) return OptionalLong.empty();
        try {
            if (gearBox != null) {
                if (resolveGearBox == null) return OptionalLong.empty();
                Object result = resolveGearBox.invoke(null, gearBox);
                if (!(result instanceof Map<?, ?> map)) return OptionalLong.empty();
                long best = -1;
                for (Object snapshot : map.values()) {
                    OptionalLong v = valueOf(snapshot, priceType);
                    if (v.isPresent()) best = Math.max(best, v.getAsLong());
                }
                return best < 0 ? OptionalLong.empty() : OptionalLong.of(best);
            }
            return valueOf(resolveSnapshot.invoke(null, stack), priceType);
        } catch (Throwable t) {
            Diag.once(14, "error:" + t.getClass().getName(), "Wynnventory price lookup failed: {}", t.toString());
            return OptionalLong.empty();
        }
    }

    private static OptionalLong valueOf(@Nullable Object snapshot, String priceType) throws ReflectiveOperationException {
        if (snapshot == null) return OptionalLong.empty();
        OptionalLong v = summaryValue(live.invoke(snapshot), priceType);
        return v.isPresent() ? v : summaryValue(historic.invoke(snapshot), priceType);
    }

    private static OptionalLong summaryValue(@Nullable Object summary, String priceType) throws ReflectiveOperationException {
        if (summary == null) return OptionalLong.empty();
        String getter = switch (priceType.toLowerCase(Locale.ROOT)) {
            case "average" -> "getAveragePrice";
            case "average80" -> "getAverageMid80PercentPrice";
            case "median" -> "getMedian";
            case "highest" -> "getHighestPrice";
            default -> "getLowestPrice";
        };
        Object value = summary.getClass().getMethod(getter).invoke(summary);
        if (value instanceof Number n && n.doubleValue() > 0) return OptionalLong.of(Math.round(n.doubleValue()));
        return OptionalLong.empty();
    }
}
