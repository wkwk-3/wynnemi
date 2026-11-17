package dev.emi.emi.fork;

import org.jetbrains.annotations.ApiStatus;

import dev.emi.emi.runtime.EmiLog;

/**
 * [WEI fork] Diagnostic logging for issues being verified in play tests.
 * Every line starts with "[WEI diag#N]" so a log can be filtered by issue number.
 * Disable with the JVM flag -Dwei.diag=false.
 */
@ApiStatus.Internal
public final class EmiForkDiag {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("wei.diag"));

	private EmiForkDiag() {
	}

	public static void log(int issue, String message) {
		if (ENABLED) {
			EmiLog.info("[WEI diag#" + issue + "] " + message);
		}
	}

	/** Short caller description, skipping this class and the immediate caller's own frame. */
	public static String caller(int depth) {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		StringBuilder sb = new StringBuilder();
		// 0 getStackTrace, 1 caller(), 2 the logging method, 3+ who called it
		for (int i = 3; i < Math.min(trace.length, 3 + depth); i++) {
			if (sb.length() > 0) {
				sb.append(" <- ");
			}
			StackTraceElement e = trace[i];
			String cls = e.getClassName();
			sb.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
		}
		return sb.toString();
	}

	/** Counts entries serialized by WEI ("type":"wei") in a JSON text. */
	public static int countWei(String json) {
		int count = 0;
		int index = 0;
		while ((index = json.indexOf("\"type\":\"wei\"", index)) >= 0) {
			count++;
			index++;
		}
		return count;
	}
}
