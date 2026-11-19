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
 * 本家の EMI（PF-EMI）に WEI の Mixin で差し込む機能の受け口。
 * 以前は EMI のフォーク（dev.emi.emi.fork.EmiForkApi）にあったもの。
 *
 * <ul>
 *   <li>検索の拡張（WEI 専用の検索語・否定検索）</li>
 *   <li>読み込みの待ち合わせ（WEI のアイテム一覧ができるまで EMI がプラグインを登録しない）</li>
 *   <li>読めなかったお気に入り・履歴を消さずに書き戻す</li>
 *   <li>R/U のレシピ画面の差し替え（未鑑定ボックス）</li>
 * </ul>
 *
 * どの Mixin が実際に効いたかは {@link #applied} に記録し、起動時のログに出す。
 */
public final class EmiHooks {
    private static final List<EmiSearchExtension> SEARCH_EXTENSIONS = new CopyOnWriteArrayList<>();
    private static final List<LoadGate> LOAD_GATES = new CopyOnWriteArrayList<>();
    private static final List<RecipeOverride> RECIPE_OVERRIDES = new CopyOnWriteArrayList<>();
    private static final long LOAD_GATE_TIMEOUT_MS = 30_000;

    /** 読めずに保持しているお気に入り（EMI の favorites.json の1件そのまま） */
    private static volatile List<JsonObject> unresolvedFavorites = List.of();
    /** 読めずに保持している検索履歴 */
    private static volatile List<JsonElement> unresolvedLookupHistory = List.of();

    /** 実際に動いた Mixin の名前（EMI の版によっては差し込めない所があるので、ログで確かめる） */
    public static final Set<String> applied = ConcurrentHashMap.newKeySet();

    private record LoadGate(String name, BooleanSupplier ready) {}

    /** R（uses=false）/ U（uses=true）のレシピ画面の中身を差し替える。差し替えないなら null */
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

    /** EMI の読み込みスレッドを、すべての待ち合わせが整うか、30 秒たつか、abort が true になるまで止める */
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
