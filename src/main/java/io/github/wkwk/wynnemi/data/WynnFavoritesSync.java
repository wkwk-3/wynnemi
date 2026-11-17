package io.github.wkwk.wynnemi.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wynntils.core.components.Services;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import io.github.wkwk.wynnemi.WynnEmiClient;
import io.github.wkwk.wynnemi.WynnEmiConfig;
import io.github.wkwk.wynnemi.emi.WynnLookup;
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
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("wynnemi-favorites.json");

    private static final int MAX_FAILURES = 5;

    private static int ticks;
    private static int failures;
    private static boolean disabled;
    private static Set<String> added;

    private WynnFavoritesSync() {}

    public static void tick() {
        if (disabled || ++ticks % 20 != 0) return;
        if (!WynnEmiConfig.get().syncFavoritesToWynntils) return;
        // Wynntils のデータと設定が読み込まれるまで待つ
        if (WynnCatalog.current().entries().isEmpty()) return;
        try {
            if (added == null) added = load();

            Set<String> wanted = new LinkedHashSet<>();
            for (EmiFavorite favorite : EmiFavorites.favorites) {
                String name = WynnLookup.favoriteNameOf(favorite.getStack());
                if (name != null) wanted.add(name);
            }

            boolean changed = false;
            for (String name : wanted) {
                if (added.contains(name) || Services.Favorites.isFavorite(name)) continue;
                Services.Favorites.addFavorite(name);
                added.add(name);
                changed = true;
            }
            for (String name : Set.copyOf(added)) {
                if (wanted.contains(name)) continue;
                Services.Favorites.removeFavorite(name);
                added.remove(name);
                changed = true;
            }
            if (changed) save(added);
            failures = 0;
        } catch (Throwable t) {
            // 起動直後の一時的な失敗は見逃し、続くなら EMI / Wynntils の内部が変わったとみてこの機能だけ止める
            if (++failures >= MAX_FAILURES) {
                disabled = true;
                WynnEmiClient.LOGGER.warn("Favorites sync to Wynntils disabled", t);
            }
        }
    }

    private static Set<String> load() {
        if (!Files.exists(FILE)) return new HashSet<>();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Set<String> set = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            return set == null ? new HashSet<>() : new HashSet<>(set);
        } catch (Exception e) {
            WynnEmiClient.LOGGER.warn("Failed to read {}", FILE, e);
            return new HashSet<>();
        }
    }

    private static void save(Set<String> set) {
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(set, writer);
        } catch (Exception e) {
            WynnEmiClient.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }
}
