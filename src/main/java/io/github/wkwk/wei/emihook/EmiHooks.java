package io.github.wkwk.wei.emihook;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * PF-EMI に Mixin で差し込む機能の受け口。
 *
 * <ul>
 *   <li>検索の拡張（一覧の絞り込みとハイライト表示）</li>
 *   <li>読み込みの待ち合わせ（一覧の完成までプラグイン登録を止める）</li>
 *   <li>読めなかったお気に入り・履歴の保持</li>
 *   <li>R/U のレシピ画面の差し替え</li>
 *   <li>保存しない一時スタック</li>
 * </ul>
 *
 * 効いた Mixin は {@link #applied} に記録する（diag#13）。
 */
public final class EmiHooks {
    private static final List<EmiSearchExtension> SEARCH_EXTENSIONS = new CopyOnWriteArrayList<>();
    private static final List<LoadGate> LOAD_GATES = new CopyOnWriteArrayList<>();
    private static final List<RecipeOverride> RECIPE_OVERRIDES = new CopyOnWriteArrayList<>();
    private static final long LOAD_GATE_TIMEOUT_MS = 30_000;

    /** 読めなかったお気に入り（favorites.json の 1 件そのまま） */
    private static volatile List<JsonObject> unresolvedFavorites = List.of();
    private static volatile List<JsonElement> unresolvedLookupHistory = List.of();

    public static final Set<String> applied = ConcurrentHashMap.newKeySet();

    private record LoadGate(String name, BooleanSupplier ready) {}

    /** R（uses=false）/ U（uses=true）のページ。差し替えないなら null。 */
    public interface RecipeOverride {
        @Nullable Map<EmiRecipeCategory, List<EmiRecipe>> pages(EmiIngredient stack, boolean uses);
    }

    public record PreparedSearch(String remainingQuery, List<EmiSearchExtension.Prepared> extensions) {}

    private EmiHooks() {}

    public static void mark(String hook) {
        if (applied.add(hook)) Diag.log(13, "EMI hook active: {}", hook);
    }

    /** Mixin の当たり先。PF-EMI の更新でクラスが無くなる・変わると、目印が付かない */
    private static final List<String> HOOKED_CLASSES = List.of(
            "dev.emi.emi.api.EmiApi",
            "dev.emi.emi.runtime.EmiFavorites",
            "dev.emi.emi.runtime.EmiReloadManager",
            "dev.emi.emi.runtime.EmiReloadManager$ReloadWorker",
            "dev.emi.emi.screen.EmiScreenManager",
            "dev.emi.emi.runtime.EmiSidebars",
            "dev.emi.emi.search.EmiSearch$SearchWorker");

    private static volatile boolean verified;

    /**
     * EMI の初回読み込みの最後に 1 回だけ、差し込みが効いたかを確かめる。
     * 効いていなければ調査用ログの設定にかかわらず警告する（PF-EMI の更新で黙って機能が止まるのを防ぐ）。
     * 使ったときにしか通らない差し込み（R/U・ハイライトなど）はクラス単位でしか確かめられない。
     */
    public static void verifyHooks(boolean persistentDataExists) {
        if (verified) return;
        verified = true;
        List<String> missing = new ArrayList<>();
        ClassLoader loader = EmiHooks.class.getClassLoader();
        for (String name : HOOKED_CLASSES) {
            try {
                if (!WeiHooked.class.isAssignableFrom(Class.forName(name, false, loader))) missing.add(name);
            } catch (Throwable t) {
                missing.add(name + " (not found)");
            }
        }
        // 読み込みのたびに必ず通る差し込み。history は emi.json があるときだけ読まれる
        List<String> expected = new ArrayList<>(List.of("loadGate", "search"));
        if (persistentDataExists) expected.add("history");
        for (String hook : expected) if (!applied.contains(hook)) missing.add("hook " + hook);
        if (missing.isEmpty()) {
            Diag.log(13, "EMI hooks verified ({})", emiVersion());
            return;
        }
        Wei.LOGGER.warn("[WEI] Some PF-EMI hooks did not apply (PF-EMI {}): {}. "
                + "Related WEI features (search terms, favorites/history keeping, box R/U) may not work.",
                emiVersion(), missing);
    }

    private static String emiVersion() {
        return FabricLoader.getInstance().getModContainer("emi")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    // ---- 検索

    public static void registerSearchExtension(EmiSearchExtension extension) {
        SEARCH_EXTENSIONS.add(extension);
    }

    public static PreparedSearch prepareSearch(String query) {
        List<EmiSearchExtension.Prepared> prepared = new ArrayList<>();
        String remaining = query;
        for (EmiSearchExtension extension : SEARCH_EXTENSIONS) {
            try {
                EmiSearchExtension.Prepared p = extension.prepare(remaining);
                if (p != null) {
                    prepared.add(p);
                    remaining = p.remainingQuery();
                }
            } catch (Throwable t) {
                Wei.LOGGER.error("Search extension failed to prepare query", t);
            }
        }
        return new PreparedSearch(remaining, List.copyOf(prepared));
    }

    /** 検索スレッドが使った EMI のクエリと WEI の条件（ハイライト表示で同じ条件を使うため） */
    private record ActiveSearch(Object query, List<EmiSearchExtension.Prepared> extensions) {}

    private static volatile @Nullable ActiveSearch activeSearch;
    private static volatile Function<ItemStack, List<? extends EmiStack>> slotResolver = s -> List.of();
    private static volatile Supplier<Object> slotResolverVersion = () -> null;
    /** 以下 3 つは描画スレッドのみ。ItemStack は equals を持たないため同一性で引ける */
    private static final Map<ItemStack, Boolean> HIGHLIGHT_CACHE = new WeakHashMap<>();
    private static @Nullable ActiveSearch highlightCacheSearch;
    private static @Nullable Object highlightCacheVersion;

    public static void setActiveSearch(Object compiledQuery, List<EmiSearchExtension.Prepared> extensions) {
        activeSearch = new ActiveSearch(compiledQuery, extensions);
    }

    /** スロットのアイテムを検索の対象（WEI のスタック）に変換する。version が変わると結果を捨てる */
    public static void setSlotResolver(Function<ItemStack, List<? extends EmiStack>> resolver, Supplier<Object> version) {
        slotResolver = resolver;
        slotResolverVersion = version;
    }

    /**
     * ハイライト表示用。WEI の条件が無ければ null。
     * あれば EmiStack.of(アイテム) → 条件に合うか の表を返す（候補のどれかが合えば合格）。
     */
    public static @Nullable Map<EmiStack, Boolean> highlightResults(Object compiledQuery, List<ItemStack> items) {
        ActiveSearch search = activeSearch;
        if (search == null || search.query() != compiledQuery || search.extensions().isEmpty()) {
            Diag.once(13, "highlight-skip:" + (search == null ? "none" : search.query() != compiledQuery ? "query" : "noext"),
                    "Highlight: WEI terms not applied (activeSearch={}, sameQuery={}, extensions={})",
                    search != null, search != null && search.query() == compiledQuery,
                    search == null ? 0 : search.extensions().size());
            return null;
        }
        Object version = slotResolverVersion.get();
        if (search != highlightCacheSearch || version != highlightCacheVersion) {
            HIGHLIGHT_CACHE.clear();
            highlightCacheSearch = search;
            highlightCacheVersion = version;
        }
        Map<EmiStack, Boolean> out = new HashMap<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            Boolean pass = HIGHLIGHT_CACHE.get(item);
            if (pass == null) {
                pass = matches(search, item);
                HIGHLIGHT_CACHE.put(item, pass);
                Diag.log(13, "Highlight: '{}' -> {}", item.getHoverName().getString(), pass ? "match" : "dim");
            }
            out.merge(EmiStack.of(item), pass, Boolean::logicalOr);
        }
        return out;
    }

    private static boolean matches(ActiveSearch search, ItemStack item) {
        try {
            for (EmiStack target : slotResolver.apply(item)) {
                boolean all = true;
                for (EmiSearchExtension.Prepared extension : search.extensions()) {
                    if (!extension.test(target)) {
                        all = false;
                        break;
                    }
                }
                if (all) return true;
            }
        } catch (Throwable t) {
            Diag.swallowed("EmiHooks: highlight resolve", t);
        }
        return false;
    }

    // ---- 読み込みの待ち合わせ

    public static void registerLoadGate(String name, BooleanSupplier ready) {
        LOAD_GATES.add(new LoadGate(name, ready));
    }

    /** 全条件が整う・30 秒経過・abort のいずれかまで EMI の読み込みスレッドを止める */
    public static void awaitLoadGates(Consumer<String> step, BooleanSupplier abort) {
        long start = System.currentTimeMillis();
        String waitingFor = null;
        while (true) {
            String notReady = null;
            for (LoadGate gate : LOAD_GATES) {
                boolean ready;
                try {
                    ready = gate.ready().getAsBoolean();
                } catch (Throwable t) {
                    Wei.LOGGER.error("Load gate '{}' failed; ignoring it", gate.name(), t);
                    ready = true;
                }
                if (!ready) {
                    notReady = gate.name();
                    break;
                }
            }
            long waited = System.currentTimeMillis() - start;
            if (notReady == null) {
                if (waitingFor != null) Diag.log(1, "Load gates ready after {}ms", waited);
                return;
            }
            if (abort.getAsBoolean()) {
                Diag.log(1, "Waiting for load gate '{}' aborted after {}ms", notReady, waited);
                return;
            }
            if (waited >= LOAD_GATE_TIMEOUT_MS) {
                Wei.LOGGER.warn("EMI loaded before '{}' was ready ({}ms); some favorites may not show until the next reload", notReady, waited);
                return;
            }
            if (!notReady.equals(waitingFor)) {
                waitingFor = notReady;
                Diag.log(1, "EMI waits for '{}' before registering plugins", notReady);
                step.accept(notReady);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ---- 読めなかったお気に入り・履歴

    public static List<JsonObject> unresolvedFavorites() {
        return unresolvedFavorites;
    }

    public static void setUnresolvedFavorites(List<JsonObject> list) {
        unresolvedFavorites = Collections.unmodifiableList(new ArrayList<>(list));
    }

    public static List<JsonElement> unresolvedLookupHistory() {
        return unresolvedLookupHistory;
    }

    public static void setUnresolvedLookupHistory(List<JsonElement> list) {
        unresolvedLookupHistory = Collections.unmodifiableList(new ArrayList<>(list));
    }

    // ---- 一時スタック

    private static volatile Predicate<EmiIngredient> transientStack = s -> false;
    private static volatile Predicate<JsonElement> transientJson = j -> false;

    /** 履歴・お気に入りに残さないスタック（その場限りの未鑑定ボックスなど）。json は保存済みの形での判定 */
    public static void registerTransient(Predicate<EmiIngredient> stack, Predicate<JsonElement> json) {
        transientStack = stack;
        transientJson = json;
    }

    public static boolean isTransient(EmiIngredient stack) {
        try {
            return transientStack.test(stack);
        } catch (Throwable t) {
            Diag.swallowed("EmiHooks: transient stack check", t);
            return false;
        }
    }

    public static boolean isTransient(JsonElement json) {
        try {
            return transientJson.test(json);
        } catch (Throwable t) {
            Diag.swallowed("EmiHooks: transient json check", t);
            return false;
        }
    }

    // ---- レシピ画面の差し替え

    public static void registerRecipeOverride(RecipeOverride override) {
        RECIPE_OVERRIDES.add(override);
    }

    public static @Nullable Map<EmiRecipeCategory, List<EmiRecipe>> overrideRecipes(EmiIngredient stack, boolean uses) {
        for (RecipeOverride override : RECIPE_OVERRIDES) {
            try {
                var pages = override.pages(stack, uses);
                if (pages != null && !pages.isEmpty()) return pages;
            } catch (Throwable t) {
                Wei.LOGGER.warn("Recipe override failed", t);
            }
        }
        return null;
    }
}
