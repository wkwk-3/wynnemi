package io.github.wkwk.wei.emihook;

import dev.emi.emi.api.stack.EmiStack;
import java.util.Comparator;
import org.jetbrains.annotations.Nullable;

/**
 * EMI の検索に条件を足す拡張。Mixin で EMI の検索に差し込む。
 * prepare はクライアントスレッド、Prepared の test / sorter は検索スレッドで呼ばれる。
 */
public interface EmiSearchExtension {
    /** 担当の語を取り出す。無ければ null。 */
    @Nullable Prepared prepare(String query);

    interface Prepared {
        /** EMI 本体に渡す残りの語 */
        String remainingQuery();

        boolean test(EmiStack stack);

        /** 並べ替えるときだけ返す */
        default @Nullable Comparator<EmiStack> sorter() {
            return null;
        }
    }
}
