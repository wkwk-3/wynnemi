# EMI について

2.1.0 から、WEI は EMI のフォークを同梱するのをやめ、本家の PF-EMI（mod ID `emi`）を前提にしました。

コンパイルには Modrinth で配布されている PF-EMI（`gradle.properties` の `pf_emi_version`）をそのまま使います（jar には入れません）。PF-EMI を更新したら、Mixin の当たり先（下の表）がまだあるかを確かめてください。

以前フォークで書き換えていた所は、WEI の Mixin（`io.github.wkwk.wei.emimixin`、設定 `wei.emi.mixins.json`）で差し込みます。

| Mixin | 差し込む所 | 役割 |
|---|---|---|
| SearchWorkerMixin | `EmiSearch$SearchWorker`（`EmiSearch.search` の中で作られる検索の作業） | WEI 専用の検索語（公式 API 形式・Wynntils 形式・否定）を取り出して絞り込み、並べ替える |
| ReloadWorkerMixin | EMI の読み込みスレッド（プラグイン登録の直前） | WEI のアイテム一覧ができるまで待つ（最大 30 秒） |
| EmiReloadManagerMixin | `reloadTags` / `reloadRecipes` / `clear` | 同期の合図が片方しか来ない（Wynncraft）ときも 3 秒後に読み込む |
| EmiFavoritesMixin / EmiSidebarsMixin | お気に入り・履歴の読み書き、`EmiSidebars.lookup` | 読めなかったものを消さずに書き戻す。その場限りのスタック（手持ちの未鑑定ボックス）は残さない |
| EmiReloadManagerAccessor | `EmiReloadManager` の内部の値（`loadedResourcesMask`・`clear`・`restart`） | ReloadWorkerMixin と EmiReloadManagerMixin が読み込みの状態を読み書きするための入り口 |
| EmiScreenManagerMixin | `renderSlotOverlays` | 検索欄のハイライト表示にも WEI の検索語を効かせる |
| EmiApiMixin | `displayRecipes` / `displayUses` | 手持ちの未鑑定ボックスの R/U で、その箱の候補のレシピだけを出す |

Mixin は `defaultRequire: 0` なので、PF-EMI の更新で差し込めなくても起動は止まりません（その機能だけ効かなくなります）。実際に効いたものはログに `[WEI diag#13] EMI hook active: <名前>` と出ます（診断ログが有効なとき）。
