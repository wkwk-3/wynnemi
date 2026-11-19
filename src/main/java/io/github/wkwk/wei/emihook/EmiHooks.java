package io.github.wkwk.wei.emihook;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import io.github.wkwk.wei.Diag;
import io.github.wkwk.wei.Wei;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * PF-EMI に Mixin で差し込む機能の受け口。
 *
 * <ul>
 *   <li>検索の拡張</li>
 *   <li>読み込みの待ち合わせ（一覧の完成までプラグイン登録を止める）</li>
 *   <li>読めなかったお気に入り・履歴の保持</li>
 *   <li>R/U のレシピ画面の差し替え</li>
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
