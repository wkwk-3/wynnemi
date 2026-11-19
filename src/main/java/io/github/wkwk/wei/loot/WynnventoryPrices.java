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
 * 取引所の価格を Wynnventory MOD から取る（鍵・通信・キャッシュは Wynnventory のもの）。
 * 非公開 API のため、見つからなければ機能を止める。
 *
 * <p>使用箇所（Wynnventory 2.2.5）:
 * <ul>
 *   <li>{@code TrademarketItemSnapshot.resolveSnapshot(ItemStack)}: 初回は null を返して裏で取得</li>
 *   <li>{@code TrademarketItemSnapshot.resolveGearBoxItem(GearBoxItem)}: 候補ごとの価格</li>
 *   <li>{@code snapshot.live() / historic()}、{@code TrademarketItemSummary.getLowestPrice()} など</li>
 * </ul>
 */
public final class WynnventoryPrices {
    private static final String SNAPSHOT = "com.wynnventory.model.item.trademarket.TrademarketItemSnapshot";

    private static volatile boolean initialized;
    private static volatile boolean available;
    /** getLowestPrice などのメソッドのキャッシュ */
    private static final Map<String, Method> GETTERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static Method resolveSnapshot;
    private static @Nullable Method resolveGearBox;
    private static Method live;
    private static Method historic;

    private WynnventoryPrices() {}

    /** スロット描画ごとに呼ばれるため、初期化後はロックを取らない */
    public static boolean isAvailable() {
        if (!initialized) {
            synchronized (WynnventoryPrices.class) {
                if (!initialized) {
                    init();
                    initialized = true;
                }
            }
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
                Wei.LOGGER.warn("[WEI] Wynnventory {} has no gear box price lookup; unidentified boxes are not checked",
                        wynnventoryVersion());
            }
            available = true;
            Diag.log(14, "Using Wynnventory prices (gear box lookup: {})", resolveGearBox != null);
        } catch (Throwable t) {
            Wei.LOGGER.warn("[WEI] Wynnventory {} was found but its price classes have changed; loot highlight is off",
                    wynnventoryVersion(), t);
        }
    }

    private static final String VERSION = FabricLoader.getInstance().getModContainer("wynnventory")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    private static final String LOOKUP_WHERE = "WynnventoryPrices: price lookup (Wynnventory " + VERSION + ")";

    private static String wynnventoryVersion() {
        return VERSION;
    }

    /**
     * 価格（エメラルド）。取得中・価格なしは empty。未鑑定ボックスは候補の最高値。
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
            // メソッド名の変更などは毎回失敗するので、調査用ログの設定にかかわらず種類ごとに 1 回警告する
            Diag.swallowed(LOOKUP_WHERE, t);
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
        Class<?> type = summary.getClass();
        Method method = GETTERS.get(type.getName() + "#" + getter);
        if (method == null) {
            method = type.getMethod(getter);
            GETTERS.put(type.getName() + "#" + getter, method);
        }
        Object value = method.invoke(summary);
        if (value instanceof Number n && n.doubleValue() > 0) return OptionalLong.of(Math.round(n.doubleValue()));
        return OptionalLong.empty();
    }
}
