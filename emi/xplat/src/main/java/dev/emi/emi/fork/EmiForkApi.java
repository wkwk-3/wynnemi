package dev.emi.emi.fork;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;

import dev.emi.emi.runtime.EmiLog;

/**
 * [WEI fork] Entry point for APIs that only exist in this fork.
 * Mods using it should depend on the "wei_emi" mod id, which only this fork provides.
 */
public final class EmiForkApi {
	private static final List<EmiSearchExtension> SEARCH_EXTENSIONS = new CopyOnWriteArrayList<>();
	private static final List<LoadGate> LOAD_GATES = new CopyOnWriteArrayList<>();
	private static final long LOAD_GATE_TIMEOUT_MS = 30_000;

	private record LoadGate(String name, BooleanSupplier ready) {
	}

	private EmiForkApi() {
	}

	/**
	 * Registers a search extension. Call once, for example from a client initializer.
	 * Extensions run in registration order; each one sees the query left over by the previous ones.
	 */
	public static void registerSearchExtension(EmiSearchExtension extension) {
		SEARCH_EXTENSIONS.add(extension);
	}

	/**
	 * Registers a condition EMI waits for before registering plugins (for example, "my mod's data is loaded").
	 * Without this, EMI can load while a plugin has nothing to register yet, and favorites / history entries
	 * for that plugin's stacks fail to deserialize. EMI waits at most 30 seconds, then loads anyway.
	 * The supplier is called from EMI's reload thread.
	 */
	public static void registerLoadGate(String name, BooleanSupplier ready) {
		LOAD_GATES.add(new LoadGate(name, ready));
	}

	/**
	 * Blocks the reload thread until every gate is ready, the timeout passes, or {@code abort} returns true.
	 */
	@ApiStatus.Internal
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
					EmiLog.error("Load gate '" + gate.name() + "' failed; ignoring it:", t);
					ready = true;
				}
				if (!ready) {
					notReady = gate.name();
					break;
				}
			}
			long waited = System.currentTimeMillis() - start;
			if (notReady == null) {
				if (waitingFor != null) {
					EmiForkDiag.log(1, "Load gates ready after " + waited + "ms");
				}
				return;
			}
			if (abort.getAsBoolean()) {
				EmiForkDiag.log(1, "Waiting for load gate '" + notReady + "' aborted after " + waited + "ms");
				return;
			}
			if (waited >= LOAD_GATE_TIMEOUT_MS) {
				EmiLog.warn("Load gate '" + notReady + "' not ready after " + waited + "ms; loading anyway");
				EmiForkDiag.log(1, "Load gate '" + notReady + "' TIMED OUT after " + waited + "ms; loading anyway");
				return;
			}
			if (!notReady.equals(waitingFor)) {
				waitingFor = notReady;
				EmiForkDiag.log(1, "EMI waits for load gate '" + notReady + "' before registering plugins");
				step.accept(notReady);
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/**
	 * Runs every extension's {@link EmiSearchExtension#prepare(String)} on the calling (client) thread.
	 */
	@ApiStatus.Internal
	public static PreparedSearch prepareSearch(String query) {
		// [WEI fork] diag#4: time spent preparing extensions on the client thread for each search
		long diagStart = System.nanoTime();
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
				EmiLog.error("Search extension failed to prepare query:", t);
			}
		}
		if (!prepared.isEmpty()) {
			EmiForkDiag.log(4, "Search extensions prepared in " + (System.nanoTime() - diagStart) / 1000 / 1000.0 + "ms for query '" + query + "'");
		}
		return new PreparedSearch(remaining, List.copyOf(prepared));
	}

	@ApiStatus.Internal
	public record PreparedSearch(String remainingQuery, List<EmiSearchExtension.Prepared> extensions) {
	}
	/** [WEI fork] Supplies recipe pages for an ingredient (R when uses=false, U when uses=true); return null to fall back to EMI. */
	public interface RecipeOverride {
		@org.jetbrains.annotations.Nullable
		java.util.Map<dev.emi.emi.api.recipe.EmiRecipeCategory, java.util.List<dev.emi.emi.api.recipe.EmiRecipe>> pages(
				dev.emi.emi.api.stack.EmiIngredient stack, boolean uses);
	}

	private static final java.util.List<RecipeOverride> RECIPE_OVERRIDES = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void registerRecipeOverride(RecipeOverride override) {
		RECIPE_OVERRIDES.add(override);
	}

	@org.jetbrains.annotations.ApiStatus.Internal
	public static @org.jetbrains.annotations.Nullable java.util.Map<dev.emi.emi.api.recipe.EmiRecipeCategory, java.util.List<dev.emi.emi.api.recipe.EmiRecipe>> overrideRecipes(
			dev.emi.emi.api.stack.EmiIngredient stack, boolean uses) {
		for (RecipeOverride override : RECIPE_OVERRIDES) {
			try {
				var pages = override.pages(stack, uses);
				if (pages != null && !pages.isEmpty()) return pages;
			} catch (Throwable t) {
				EmiForkDiag.log(15, "recipe override failed: " + t);
			}
		}
		return null;
	}
}
