package dev.emi.emi.fork;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;

import dev.emi.emi.runtime.EmiLog;

/**
 * [WynnCodex fork] Entry point for APIs that only exist in this fork.
 * Mods using it should depend on the "wynncodex_emi" mod id, which only this fork provides.
 */
public final class EmiForkApi {
	private static final List<EmiSearchExtension> SEARCH_EXTENSIONS = new CopyOnWriteArrayList<>();

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
	 * Runs every extension's {@link EmiSearchExtension#prepare(String)} on the calling (client) thread.
	 */
	@ApiStatus.Internal
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
				EmiLog.error("Search extension failed to prepare query:", t);
			}
		}
		return new PreparedSearch(remaining, List.copyOf(prepared));
	}

	@ApiStatus.Internal
	public record PreparedSearch(String remainingQuery, List<EmiSearchExtension.Prepared> extensions) {
	}
}
