package io.github.wkwk.wynnemi.emi;

import com.wynntils.core.components.Services;
import com.wynntils.services.itemfilter.type.ItemProviderType;
import com.wynntils.services.itemfilter.type.ItemSearchQuery;
import com.wynntils.services.itemfilter.type.ItemStatProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.fork.EmiSearchExtension;
import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.data.WynnCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * EMI の検索欄で Wynntils のアイテムフィルターの書き方を使えるようにする。
 *
 * <pre>
 *   level:100-105 rarity:mythic gearType:wand rawStrength:&gt;10 sort:rawStrength
 * </pre>
 *
 * <p>Wynntils が知っているキー（{@code key:value}）と {@code sort:} だけをこのクラスが受け持ち、
 * 残りの語は今までどおり EMI が検索する。条件の評価は {@link #prepare} の中（クライアントスレッド）で
 * Wynntils に任せて結果を作り、EMI の検索スレッドではその結果を読むだけにする。
 */
public final class WynnSearchExtension implements EmiSearchExtension {
    private static final String SORT_KEY = "sort";

    private @Nullable Set<String> knownKeys;
    // 同じ条件で何度も評価しないためのキャッシュ（入力のたびに検索が走るため）
    private @Nullable String cachedQuery;
    private @Nullable WynnCatalog cachedCatalog;
    private @Nullable Result cachedResult;

    private record Result(Map<String, Integer> rankByKey, boolean sorted) {}

    @Override
    public @Nullable Prepared prepare(String query) {
        if (query.indexOf(':') < 0) return null;

        List<String> wynnTokens = new ArrayList<>();
        List<String> otherTokens = new ArrayList<>();
        for (String token : query.trim().split("\\s+")) {
            if (isWynnToken(token)) {
                wynnTokens.add(token);
            } else if (!token.isEmpty()) {
                otherTokens.add(token);
            }
        }
        if (wynnTokens.isEmpty()) return null;

        String wynnQuery = String.join(" ", wynnTokens);
        Result result = evaluate(wynnQuery);
        String remaining = String.join(" ", otherTokens);

        return new Prepared() {
            @Override
            public String remainingQuery() {
                return remaining;
            }

            @Override
            public boolean test(EmiStack stack) {
                return stack instanceof WynnEmiStack wynn && result.rankByKey().containsKey(wynn.wynnKey());
            }

            @Override
            public @Nullable Comparator<EmiStack> sorter() {
                if (!result.sorted()) return null;
                return Comparator.comparingInt(stack -> stack instanceof WynnEmiStack wynn
                        ? result.rankByKey().getOrDefault(wynn.wynnKey(), Integer.MAX_VALUE)
                        : Integer.MAX_VALUE);
            }
        };
    }

    /** EMI の書き方（@ # $ - " /）で始まる語は EMI に任せる */
    private boolean isWynnToken(String token) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) return false;
        char first = token.charAt(0);
        if (first == '@' || first == '#' || first == '$' || first == '-' || first == '"' || first == '/') return false;
        String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
        return knownKeys().contains(key);
    }

    private Set<String> knownKeys() {
        if (knownKeys == null) {
            Set<String> keys = new HashSet<>();
            keys.add(SORT_KEY);
            try {
                for (ItemStatProvider<?> provider : Services.ItemFilter.getItemStatProviders()) {
                    keys.add(provider.getName().toLowerCase(Locale.ROOT));
                    for (String alias : provider.getAliases()) {
                        keys.add(alias.toLowerCase(Locale.ROOT));
                    }
                }
            } catch (Throwable t) {
                WynnEmiClient.LOGGER.warn("Could not read Wynntils item filter keys", t);
            }
            knownKeys = keys;
        }
        return knownKeys;
    }

    private Result evaluate(String wynnQuery) {
        WynnCatalog catalog = WynnCatalog.current();
        if (wynnQuery.equals(cachedQuery) && catalog == cachedCatalog && cachedResult != null) {
            return cachedResult;
        }

        Map<String, Integer> rank = new HashMap<>();
        boolean sorted = false;
        try {
            ItemSearchQuery query = Services.ItemFilter.createSearchQuery(wynnQuery, true, ItemProviderType.normalTypes());
            sorted = !query.sorts().isEmpty();

            Map<ItemStack, String> keyByStack = new IdentityHashMap<>();
            List<ItemStack> stacks = new ArrayList<>();
            for (WynnCatalog.Entry entry : catalog.entries()) {
                keyByStack.put(entry.stack(), entry.key());
                stacks.add(entry.stack());
            }

            List<ItemStack> matched = Services.ItemFilter.filterAndSort(query, stacks);
            for (int i = 0; i < matched.size(); i++) {
                String key = keyByStack.get(matched.get(i));
                if (key != null) rank.putIfAbsent(key, i);
            }
        } catch (Throwable t) {
            WynnEmiClient.LOGGER.warn("Wynntils item filter failed for '{}'", wynnQuery, t);
        }

        Result result = new Result(Map.copyOf(rank), sorted);
        cachedQuery = wynnQuery;
        cachedCatalog = catalog;
        cachedResult = result;
        return result;
    }
}
