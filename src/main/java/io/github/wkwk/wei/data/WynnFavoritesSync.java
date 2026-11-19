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
 * EMI のお気に入りに入れた Wynncraft のアイテムを、Wynntils のお気に入りにも入れる（EMI → Wynntils の一方向）。
 * Wynntils はお気に入りのアイテムを目立たせて表示するので、チェストや取引所で見つけやすくなる。
 *
 * <ul>
 *   <li>このMODが追加した名前だけを記録し、EMI から外したらそれだけを Wynntils からも外す。
 *       もともと Wynntils でお気に入りにしていたものには触れない。</li>
 *   <li>EMI のお気に入りは公開 API に無いため内部クラスを読む。EMI の更新で使えなくなったら、
 *       ログに一度だけ警告を出してこの機能だけ止まる。</li>
 * </ul>
 *
 * クライアントスレッドで1秒ごとに動く。
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
        // Wynntils のデータと設定が読み込まれるまで待つ
        if (WynnCatalog.current().entries().isEmpty()) return;
        // EMI の読み込み中（お気に入り一覧がいったん空になる）や、WEI の索引ができる前は動かない。
        // 実機ログで、この間に Wynntils 側のお気に入りを全部外していた（diag#2）
        if (!dev.emi.emi.runtime.EmiReloadManager.isLoaded() || !WynnLookup.isReady()) return;
        // EMI のお気に入り・索引・保持中の未解決分が前回と同じなら比べない
        long fingerprint = fingerprint();
        if (fingerprint == lastFingerprint) return;
        try {
            if (added == null) added = load();

            Set<String> wanted = new LinkedHashSet<>();
            for (EmiFavorite favorite : EmiFavorites.favorites) {
                String name = WynnLookup.favoriteNameOf(favorite.getStack());
                if (name != null) wanted.add(name);
            }

            // 読めなかったお気に入り（一覧から外したアイテムなど）は EMI 側に残っているので、Wynntils からも外さない
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
                // diag#2: 一度に全部外した＝EMI の再読み込み中や準備前に動いた疑い
                // EMI と WEI の準備ができているときだけ動くので、これは利用者が EMI 側で全部外した操作
                Diag.log(2, "Removed all {} tracked Wynntils favorites (user removed them in EMI; emiLoaded={}, lookup ready={})",
                        removed, emiLoaded, WynnLookup.isReady());
            }
            if (changed) save(added);
            failures = 0;
            lastFingerprint = fingerprint;
        } catch (Throwable t) {
            // 起動直後の一時的な失敗は見逃し、続くなら EMI / Wynntils の内部が変わったとみてこの機能だけ止める
            if (++failures >= MAX_FAILURES) {
                disabled = true;
                Wei.LOGGER.warn("Favorites sync to Wynntils disabled", t);
            }
        }
    }

    private static long lastFingerprint = Long.MIN_VALUE;

    private static long fingerprint() {
        long h = System.identityHashCode(WynnLookup.current());
        h = h * 31 + System.identityHashCode(EmiHooks.unresolvedFavorites());
        h = h * 31 + EmiFavorites.favorites.size();
        for (EmiFavorite f : EmiFavorites.favorites) h = h * 31 + System.identityHashCode(f.getStack());
        return h;
    }

    /** EMI が読めずに保持しているお気に入りのうち WEI のものを、Wynntils のお気に入りの名前の候補にする */
    private static Set<String> unresolvedNames() {
        Set<String> names = new HashSet<>();
        for (com.google.gson.JsonObject json : EmiHooks.unresolvedFavorites()) {
            try {
                com.google.gson.JsonObject stack = json.getAsJsonObject("stack");
                if (stack == null || !stack.has("key")) continue;
                String key = stack.get("key").getAsString();
                String name = key.substring(key.indexOf('/') + 1).replaceAll("#\\d+$", "");
                names.add(name);
                // マテリアル・アスペクトのキーは「名前 T1」。Wynntils のお気に入りはティアなしの名前
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
