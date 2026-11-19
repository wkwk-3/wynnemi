package io.github.wkwk.wei.data;

import io.github.wkwk.wei.emihook.EmiHooks;
import io.github.wkwk.wei.Diag;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wynntils.core.components.Services;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import io.github.wkwk.wei.Wei;
import io.github.wkwk.wei.WeiConfig;
import io.github.wkwk.wei.emi.WynnLookup;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * EMI のお気に入りを Wynntils のお気に入りに反映する（一方向）。
 *
 * <ul>
 *   <li>WEI が追加した名前だけを記録し、EMI から外したらそれだけを外す。
 *       元から Wynntils にあるものには触れない。</li>
 *   <li>EMI の内部クラスを読むため、失敗が続けばこの機能だけ止める。</li>
 * </ul>
 *
 * クライアントスレッドで 1 秒ごと。
 */
public final class WynnFavoritesSync {
    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("wei-favorites.json");

    private static final int MAX_FAILURES = 5;

    private static int ticks;
    private static int failures;
    private static boolean disabled;
    private static Set<String> added;

    private WynnFavoritesSync() {}

    public static void tick() {
        if (disabled || ++ticks % 20 != 0) return;
        if (!WeiConfig.get().syncFavoritesToWynntils) return;
        if (WynnCatalog.current().entries().isEmpty()) return;
        // EMI の読み込み中（お気に入りが一時的に空）や索引の完成前は動かない。
        // 動くと Wynntils 側を全部外す（diag#2）
        if (!dev.emi.emi.runtime.EmiReloadManager.isLoaded() || !WynnLookup.isReady()) return;
        // 前回から変化が無ければ何もしない
        long fingerprint = fingerprint();
        if (fingerprint == lastFingerprint) return;
        try {
            if (added == null) added = load();

            Set<String> wanted = new LinkedHashSet<>();
            for (EmiFavorite favorite : EmiFavorites.favorites) {
                String name = WynnLookup.favoriteNameOf(favorite.getStack());
                if (name != null) wanted.add(name);
            }

            // 読めなかったお気に入りは EMI 側で保持中のため外さない
            Set<String> keep = unresolvedNames();
            boolean changed = false;
            int emiFavorites = EmiFavorites.favorites.size();
            boolean emiLoaded = dev.emi.emi.runtime.EmiReloadManager.isLoaded();
            for (String name : wanted) {
                if (added.contains(name) || Services.Favorites.isFavorite(name)) continue;
                Services.Favorites.addFavorite(name);
                added.add(name);
                changed = true;
                Diag.log(2, "Wynntils favorite ADDED '{}' (EMI favorites={}, emiLoaded={})", name, emiFavorites, emiLoaded);
            }
            int removed = 0;
            int trackedBefore = added.size();
            for (String name : Set.copyOf(added)) {
                if (wanted.contains(name) || keep.contains(name)) continue;
                Services.Favorites.removeFavorite(name);
                added.remove(name);
                changed = true;
                removed++;
                Diag.log(2, "Wynntils favorite REMOVED '{}' (EMI favorites={}, WEI favorites in EMI={}, emiLoaded={}, lookup ready={})",
                        name, emiFavorites, wanted.size(), emiLoaded, WynnLookup.isReady());
            }
            if (removed > 0 && removed == trackedBefore) {
                // 準備完了後のみ動くため、利用者の操作
                Diag.log(2, "Removed all {} tracked Wynntils favorites (user removed them in EMI; emiLoaded={}, lookup ready={})",
                        removed, emiLoaded, WynnLookup.isReady());
            }
            if (changed) save(added);
            failures = 0;
            lastFingerprint = fingerprint;
        } catch (Throwable t) {
            // 失敗が続けば内部が変わったとみなして止める
            if (++failures >= MAX_FAILURES) {
                disabled = true;
                Wei.LOGGER.warn("Favorites sync to Wynntils disabled", t);
            }
        }
    }

    private static long lastFingerprint = Long.MIN_VALUE;

    private static long fingerprint() {
        long h = System.identityHashCode(WynnCatalog.current());
        h = h * 31 + System.identityHashCode(EmiHooks.unresolvedFavorites());
        h = h * 31 + EmiFavorites.favorites.size();
        for (EmiFavorite f : EmiFavorites.favorites) h = h * 31 + System.identityHashCode(f.getStack());
        return h;
    }

    /** 保持中の未解決お気に入りの、Wynntils でのお気に入り名 */
    private static Set<String> unresolvedNames() {
        Set<String> names = new HashSet<>();
        for (com.google.gson.JsonObject json : EmiHooks.unresolvedFavorites()) {
            try {
                com.google.gson.JsonObject stack = json.getAsJsonObject("stack");
                if (stack == null || !stack.has("key")) continue;
                String key = stack.get("key").getAsString();
                String name = key.substring(key.indexOf('/') + 1).replaceAll("#\\d+$", "");
                names.add(name);
                // 「名前 T1」形式はティアを除く
                names.add(name.replaceAll(" T\\d+$", ""));
            } catch (RuntimeException ignored) {
            }
        }
        return names;
    }

    private static Set<String> load() {
        if (!Files.exists(FILE)) return new HashSet<>();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Set<String> set = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            return set == null ? new HashSet<>() : new HashSet<>(set);
        } catch (Exception e) {
            Wei.LOGGER.warn("Failed to read {}", FILE, e);
            return new HashSet<>();
        }
    }

    private static void save(Set<String> set) {
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(set, writer);
        } catch (Exception e) {
            Wei.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }
}
