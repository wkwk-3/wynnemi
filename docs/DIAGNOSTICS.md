# 実機テスト用の診断ログ

ログ（`logs/latest.log`）の行の先頭に `[WEI diag#番号]` が付きます。番号はコードレビューで挙げた問題の番号です。
止めるには `config/wei.json` の `"diagnostics": false`（WEI 本体）か、JVM 引数 `-Dwei.diag=false`（本体とフォークの両方）。

`grep "WEI diag#1" logs/latest.log` のように番号で絞り込めます。

| # | 確かめたいこと | やること | 問題が起きている証拠 |
|---|---|---|---|
| 1 | WEI のお気に入りが消える | WEI のアイテムを EMI のお気に入りに入れる → ゲームを再起動 → ログイン | `Plugin registered with an EMPTY catalog` / `Deserialize MISS` / `Favorite DROPPED on load` の後に `Persistent data SAVE` が出て、その SAVE の `WEI 0` が直前の LOAD の `WEI n` より少ない |
| 2 | お気に入りの連動が Wynntils 側を勝手に外す | 1 と同じ操作。`/wei reload` も数回 | `Wynntils favorite REMOVED`（`emiLoaded=false` か `lookup ready=false`）、`Removed ALL ... at once` |
| 3 | ログインのたびに EMI の再読み込みが何回も走る | 起動 → ログイン → キャラクター選択 → クラスを変える | `EMI reload requested` の回数と `from ...`（呼び出し元）。`WEI triggers EMI reload: reasons=[view]` / `[catalogBuilt]`、`Data signature changed` の最後の項目（クラス）の変化、`cachedOnWynncraft` と `liveOnWynncraft` の食い違い、`Catalog build DONE` の時間と `max step` |
| 4 | 検索の入力でカクつく | 検索欄に `level:100-105 rarity:mythic` などを 1 文字ずつ入力 | `Search evaluation took ...ms (> 1 frame)`、`Search extensions prepared in ...ms` |
| 5 | 失敗が続くと EMI の再読み込みが繰り返される | （普段は起きない。Wynntils の更新後など） | `Retry #n scheduled` と、そのたびの `EMI reload requested` |
| 6 | Wynntils の書き方が効かなくなる | 検索で `rarity:mythic` を使う | `Wynntils filter keys FAILED to load` |
| 7 | 手持ちのアイテムが別のアイテムとして扱われる | インベントリ・銀行のアイテム（クラフト品や改名した物を含む）で R/U | `Inventory item matched BY NAME` / `Inventory item key not in catalog` |
| 8 | 否定が効かない | 検索で `-rarity:mythic` | `Negated WEI term ... is not supported` |
| 9 | 説明文が古いまま | Wynntils でお気に入りの星を付け外しする | `Wynntils favorites changed after the catalog was built` |
| 10 | 同梱のフォークではない EMI が読み込まれる | 起動するだけ | `EMI loaded: name=... containedIn=...`（`containedIn=wei` なら同梱のフォーク）、`wei_emi provided: false` |
| 12 | Wynncraft で EMI が読み込まれない（タグかレシピの片方しか届かない） | ログインする | `Only the tags signal arrived; reloading in 3000ms` の後に `Sync fallback FIRED`、続いて `EMI reload requested` と `EMI reload finished`。`Sync fallback cancelled` だけで終わり、`EMI reload finished` が無ければ未解決 |
| 11 | EMI の再読み込みの呼び出しに失敗する | ログインなど | `EMI reload method not found` |

起動直後の `diag#10` に `WEI version: 1.0.0-test.2+1.21.11` のように出るので、どの jar でテストしたか確認できます。

ログを共有するときは、`grep "WEI diag" logs/latest.log` の結果をそのまま貼ってもらえれば足ります。
