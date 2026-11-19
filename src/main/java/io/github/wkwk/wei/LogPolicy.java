package io.github.wkwk.wei;

import org.jetbrains.annotations.Nullable;

/** 調査用ログを出すかの判定。依存の無い純粋関数（単体で検証できるように分けている）。 */
public final class LogPolicy {
    private LogPolicy() {}

    /** バージョン（例: 2.1.0-test.6+1.21.11）の「+」より前に「-」があればプレリリース */
    public static boolean isPrerelease(String version) {
        return version.split("\\+")[0].contains("-");
    }

    /** 優先順: JVM 引数 > 設定 > プレリリースか */
    public static boolean enabled(@Nullable String systemProperty, @Nullable Boolean configured, boolean prerelease) {
        if (systemProperty != null) return Boolean.parseBoolean(systemProperty);
        return configured != null ? configured : prerelease;
    }
}
