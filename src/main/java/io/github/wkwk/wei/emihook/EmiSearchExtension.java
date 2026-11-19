package io.github.wkwk.wei.emihook;

import dev.emi.emi.api.stack.EmiStack;
import java.util.Comparator;
import org.jetbrains.annotations.Nullable;

/**
 * EMI の検索に条件を足す仕組み（本家 EMI には無いので、WEI の Mixin で EMI の検索に差し込む）。
 * prepare はクライアントスレッドで、Prepared.test / sorter は EMI の検索スレッドで呼ばれる。
 */
public interface EmiSearchExtension {
    /** 自分の担当する語を取り出す。担当が無ければ null */
    @Nullable Prepared prepare(String query);

    interface Prepared {
        /** EMI 本体の検索に渡す残りの語 */
        String remainingQuery();

        boolean test(EmiStack stack);

        /** 並べ替えたいときだけ返す */
        default @Nullable Comparator<EmiStack> sorter() {
            return null;
        }
    }
}
