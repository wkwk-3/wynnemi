package dev.emi.emi.fork;

import java.util.Comparator;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiStack;

/**
 * [Wynn EMI fork] Lets a mod take over part of the search query, filter the index with its own
 * logic, and optionally reorder the results.
 *
 * <p>{@link #prepare(String)} is called on the client thread every time a search starts, so it may
 * read game or mod state. The returned {@link Prepared} is then used on EMI's search thread and must
 * only read what it captured.
 */
public interface EmiSearchExtension {

	/**
	 * @param query the current search text (after earlier extensions removed their terms)
	 * @return null if this extension has nothing to do with the query
	 */
	@Nullable
	Prepared prepare(String query);

	interface Prepared {

		/**
		 * The part of the query EMI should still search normally (this extension's terms removed).
		 */
		String remainingQuery();

		/**
		 * Called on the search thread for every stack. Return false to hide the stack.
		 */
		boolean test(EmiStack stack);

		/**
		 * Order to apply to the matching stacks, or null to keep EMI's order.
		 * Called once per search on the search thread.
		 */
		@Nullable
		default Comparator<EmiStack> sorter() {
			return null;
		}
	}
}
