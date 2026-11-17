package io.github.wkwk.wei.emi;

import io.github.wkwk.wei.Diag;
import com.wynntils.core.components.Services;
import com.wynntils.services.itemfilter.type.ItemProviderType;
import com.wynntils.services.itemfilter.type.ItemSearchQuery;
import com.wynntils.services.itemfilter.type.ItemStatProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.fork.EmiSearchExtension;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.data.WynnCatalog;
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
 * EMI の検索欄で、2 種類の {@code key:value} を使えるようにする。
 *
 * <ul>
 *   <li><b>公式 API と同じキー</b>（{@link ApiSearchTerms}）:
 *       {@code type:weapon tier:mythic attackSpeed:superFast levelRange:100-105 professions:cooking
 *       identifications:rawStrength majorIds:...}</li>
 *   <li><b>Wynntils のアイテムフィルター</b>: {@code rarity:mythic gearType:wand rawStrength:>10 sort:rawStrength}
 *       など（ID の値の範囲や並び替えはこちら）</li>
 * </ul>
 * 同じ名前のキー（tier・attackSpeed）は公式 API の意味を優先する。残りの語は今までどおり EMI が検索する。
 *
 * <p>条件の評価は {@link #prepare} の中（クライアントスレッド）で済ませ、EMI の検索スレッドでは結果を読むだけにする。
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

        List<String> apiTokens = new ArrayList<>();
        List<String> wynnTokens = new ArrayList<>();
        List<String> otherTokens = new ArrayList<>();
        // 頭に - を付けた条件は「当てはまるものを除く」
        List<String> negatedApiTokens = new ArrayList<>();
        List<String> negatedWynnTokens = new ArrayList<>();
        for (String token : query.trim().split("\\s+")) {
            if (token.length() > 1 && token.startsWith("-") && isApiToken(token.substring(1))) {
                negatedApiTokens.add(token.substring(1));
            } else if (token.length() > 1 && token.startsWith("-") && isWynnToken(token.substring(1))) {
                negatedWynnTokens.add(token.substring(1));
            } else if (isApiToken(token)) {
                apiTokens.add(token);
            } else if (isWynnToken(token)) {
                wynnTokens.add(token);
            } else if (!token.isEmpty()) {
                otherTokens.add(token);
            }
        }
        if (apiTokens.isEmpty() && wynnTokens.isEmpty() && negatedApiTokens.isEmpty() && negatedWynnTokens.isEmpty()) {
            return null;
        }

        Result result = evaluate(String.join(" ", apiTokens), String.join(" ", wynnTokens),
                negatedApiTokens, negatedWynnTokens);
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
    private static @Nullable String keyOf(String token) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) return null;
        char first = token.charAt(0);
        if (first == '@' || first == '#' || first == '$' || first == '-' || first == '"' || first == '/') return null;
        return token.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    private static boolean isApiToken(String token) {
        String key = keyOf(token);
        return key != null && ApiSearchTerms.isApiKey(key);
    }

    private boolean isWynnToken(String token) {
        String key = keyOf(token);
        return key != null && knownKeys().contains(key);
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
                Wei.LOGGER.warn("Could not read Wynntils item filter keys", t);
                // diag#6: 空のまま覚えると、再起動まで Wynntils の書き方が効かない
                Diag.warn(6, "Wynntils filter keys FAILED to load; cached {} keys (only 'sort') until restart", keys.size());
            }
            Diag.log(6, "Wynntils filter keys loaded: {} keys (thread={})", keys.size(), Diag.thread());
            knownKeys = keys;
        }
        return knownKeys;
    }

    private Result evaluate(String apiQuery, String wynnQuery, List<String> negatedApi, List<String> negatedWynn) {
        WynnCatalog catalog = WynnCatalog.current();
        String cacheKey = apiQuery + "\u0000" + wynnQuery + "\u0000" + negatedApi + "\u0000" + negatedWynn;
        if (cacheKey.equals(cachedQuery) && catalog == cachedCatalog && cachedResult != null) {
            Diag.log(4, "Search cache HIT api='{}' wynntils='{}'", apiQuery, wynnQuery);
            return cachedResult;
        }
        long diagStart = System.nanoTime();
        double diagWynntilsMs = 0;

        // 1. 公式 API と同じキーで絞り込む
        ApiSearchTerms api = new ApiSearchTerms();
        for (String token : apiQuery.isEmpty() ? new String[0] : apiQuery.split(" ")) {
            int colon = token.indexOf(':');
            api.add(token.substring(0, colon), token.substring(colon + 1));
        }
        List<WynnCatalog.Entry> candidates = new ArrayList<>();
        for (WynnCatalog.Entry entry : catalog.entries()) {
            if (api.isEmpty() || api.matches(entry.facets())) candidates.add(entry);
        }

        // 2. Wynntils のフィルターで絞り込む（並び替えもこちら）
        List<WynnCatalog.Entry> ordered = candidates;
        boolean sorted = false;
        if (!wynnQuery.isEmpty()) {
            try {
                ItemSearchQuery query = Services.ItemFilter.createSearchQuery(wynnQuery, true, ItemProviderType.normalTypes());
                sorted = !query.sorts().isEmpty();
                Map<ItemStack, WynnCatalog.Entry> entryByStack = new IdentityHashMap<>();
                List<ItemStack> stacks = new ArrayList<>();
                for (WynnCatalog.Entry entry : candidates) {
                    entryByStack.put(entry.stack(), entry);
                    stacks.add(entry.stack());
                }
                List<WynnCatalog.Entry> matched = new ArrayList<>();
                long wynntilsStart = System.nanoTime();
                List<ItemStack> filtered = Services.ItemFilter.filterAndSort(query, stacks);
                diagWynntilsMs = Diag.ms(wynntilsStart);
                if (!query.errors().isEmpty()) {
                    Diag.log(4, "Wynntils filter reported errors for '{}': {}", wynnQuery, query.errors());
                }
                for (ItemStack stack : filtered) {
                    WynnCatalog.Entry entry = entryByStack.get(stack);
                    if (entry != null) matched.add(entry);
                }
                ordered = matched;
            } catch (Throwable t) {
                Wei.LOGGER.warn("Wynntils item filter failed for '{}'", wynnQuery, t);
            }
        }

        // 2b. 頭に - を付けた条件に当てはまるものを除く（条件ごとに判定し、どれかに当てはまれば除外）
        if (!negatedApi.isEmpty() || !negatedWynn.isEmpty()) {
            Set<String> excluded = excludedKeys(catalog, negatedApi, negatedWynn);
            List<WynnCatalog.Entry> kept = new ArrayList<>(ordered.size());
            for (WynnCatalog.Entry entry : ordered) {
                if (!excluded.contains(entry.key())) kept.add(entry);
            }
            Diag.log(8, "Negated terms api={} wynntils={} excluded {} of {} items", negatedApi, negatedWynn,
                    ordered.size() - kept.size(), ordered.size());
            ordered = kept;
        }

        // 3. Wynntils の sort: が無く identifications を指定していれば、公式サイトと同じく最初の ID の値が大きい順
        String firstId = api.firstIdentification();
        if (!sorted && firstId != null) {
            List<WynnCatalog.Entry> byId = new ArrayList<>(ordered);
            byId.sort(Comparator.comparingInt((WynnCatalog.Entry e) -> {
                Integer v = ApiSearchTerms.identificationValue(e.facets().identifications(), firstId);
                return v == null ? Integer.MIN_VALUE : v;
            }).reversed());
            ordered = byId;
            sorted = true;
        }

        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            rank.putIfAbsent(ordered.get(i).key(), i);
        }
        Result result = new Result(Map.copyOf(rank), sorted);
        // diag#4: クライアントスレッドでの評価時間（入力のたびに走る）
        double totalMs = Diag.ms(diagStart);
        if (totalMs > 16) {
            Diag.warn(4, "Search evaluation took {}ms (> 1 frame) api='{}' wynntils='{}' candidates={} matched={} (wynntils filter {}ms)",
                    totalMs, apiQuery, wynnQuery, candidates.size(), rank.size(), diagWynntilsMs);
        } else {
            Diag.log(4, "Search evaluation {}ms api='{}' wynntils='{}' candidates={} matched={} (wynntils filter {}ms)",
                    totalMs, apiQuery, wynnQuery, candidates.size(), rank.size(), diagWynntilsMs);
        }
        cachedQuery = cacheKey;
        cachedCatalog = catalog;
        cachedResult = result;
        return result;
    }

    private Set<String> excludedKeys(WynnCatalog catalog, List<String> negatedApi, List<String> negatedWynn) {
        Set<String> excluded = new HashSet<>();
        for (String token : negatedApi) {
            int colon = token.indexOf(':');
            ApiSearchTerms term = new ApiSearchTerms();
            term.add(token.substring(0, colon), token.substring(colon + 1));
            for (WynnCatalog.Entry entry : catalog.entries()) {
                if (term.matches(entry.facets())) excluded.add(entry.key());
            }
        }
        if (!negatedWynn.isEmpty()) {
            Map<ItemStack, String> keyByStack = new IdentityHashMap<>();
            List<ItemStack> stacks = new ArrayList<>();
            for (WynnCatalog.Entry entry : catalog.entries()) {
                keyByStack.put(entry.stack(), entry.key());
                stacks.add(entry.stack());
            }
            for (String token : negatedWynn) {
                try {
                    ItemSearchQuery query = Services.ItemFilter.createSearchQuery(token, false, ItemProviderType.normalTypes());
                    for (ItemStack stack : Services.ItemFilter.filterAndSort(query, stacks)) {
                        String key = keyByStack.get(stack);
                        if (key != null) excluded.add(key);
                    }
                } catch (Throwable t) {
                    Wei.LOGGER.warn("Wynntils item filter failed for negated term '{}'", token, t);
                }
            }
        }
        return excluded;
    }
}
