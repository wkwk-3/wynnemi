# 実機テスト用の診断ログ

ログ（`logs/latest.log`）の行の先頭に `[WEI diag#番号]` が付きます。番号はコードレビューで挙げた問題の番号です。
止めるには `config/wei.json` の `"diagnostics": false`（WEI 本体）か、JVM 引数 `-Dwei.diag=false`（本体とフォークの両方）。

`grep "WEI diag#1" logs/latest.log` のように番号で絞り込めます。

| # | 確かめたいこと | やること | 問題が起きている証拠 |
|---|---|---|---|
| 1 | WEI のお気に入りが消える | WEI のアイテムを EMI のお気に入りに入れる → ゲームを再起動 → ログイン | `Plugin registered with an EMPTY catalog` / `Deserialize MISS` / `Favorite DROPPED on load` の後に `Persistent data SAVE` が出て、その SAVE の `WEI 0` が直前の LOAD の `WEI n` より少ない |
| 2 | お気に入りの連動が Wynntils 側を勝手に外す | 1 と同じ操作。`/wei reload` も数回 | `Wynntils favorite REMOVED`（`emiLoaded=false` か `lookup ready=false`）、`Removed ALL ... at once` |
| 3 | ログインのたびに EMI の再読み込みが何回も走る（test.3 で修正: クラス変更では作り直さない） | 起動 → ログイン → キャラクター選択 → クラスを変える | `EMI reload requested` の回数と `from ...`（呼び出し元）。`WEI triggers EMI reload: reasons=[view]` / `[catalogBuilt]`、`Data signature changed` の最後の項目（クラス）の変化、`cachedOnWynncraft` と `liveOnWynncraft` の食い違い、`Catalog build DONE` の時間と `max step`、`Slow catalog task: <グループ>#<番号>=XXms`（カクつきの原因になった作業） |
| 4 | 検索の入力でカクつく | 検索欄に `level:100-105 rarity:mythic` などを 1 文字ずつ入力 | `Search evaluation took ...ms (> 1 frame)`、`Search extensions prepared in ...ms` |
| 5 | 失敗が続くと EMI の再読み込みが繰り返される | （普段は起きない。Wynntils の更新後など） | `Retry #n scheduled` と、そのたびの `EMI reload requested` |
| 6 | Wynntils の書き方が効かなくなる | 検索で `rarity:mythic` を使う | `Wynntils filter keys FAILED to load` |
| 7 | 手持ちのアイテムが別のアイテムとして扱われる | インベントリ・銀行のアイテム（クラフト品や改名した物を含む）で R/U | `Inventory item matched BY NAME` / `Inventory item key not in catalog` |
| 8 | 否定が効かない | 検索で `-rarity:mythic` | `Negated WEI term ... is not supported` |
| 9 | 説明文が古いまま | Wynntils でお気に入りの星を付け外しする | `Wynntils favorites changed after the catalog was built` |
| 10 | 同梱のフォークではない EMI が読み込まれる | 起動するだけ | `EMI loaded: name=... containedIn=...`（`containedIn=wei` なら同梱のフォーク）、`wei_emi provided: false` |
| 12 | Wynncraft で EMI が読み込まれない（タグかレシピの片方しか届かない） | ログインする | `Only the tags signal arrived; reloading in 3000ms` の後に `Sync fallback FIRED`、続いて `EMI reload requested` と `EMI reload finished`。`Sync fallback cancelled` だけで終わり、`EMI reload finished` が無ければ未解決 |
| 13 | 名前の先頭のアイコン文字（U+100002 など）が消えず、名前で見つからない | 取引所・銀行で、名前の前にアイコンが付いたアイテムで R | `Inventory item matched BY NAME: '󐀂Name' ... -> no match` が出なくなり、`-> gear/Name` などになれば解決 |
| 11 | EMI の再読み込みの呼び出しに失敗する | ログインなど | `EMI reload method not found` |

起動直後の `diag#10` に `WEI version: 1.0.0-test.3+1.21.11` のように出るので、どの jar でテストしたか確認できます。

ログを共有するときは、`grep "WEI diag" logs/latest.log` の結果をそのまま貼ってもらえれば足ります。

## test.4 で追加・変更

- #1: `EMI waits for load gate 'WEI item list'` / `Load gate ... ready after Nms`。EMI は WEI のアイテム一覧ができるまで読み込みを待つ（最大30秒）。
- #1: `Favorite UNRESOLVED on load (kept…)`。読めなかったお気に入りや履歴は消さずに残し、保存時に書き戻す。
- #2: お気に入り同期は EMI の読み込みと WEI の索引の準備ができるまで動かない。
- #8: `-key:value` を否定条件として扱う。`Negated terms ... excluded X of Y items`。
- #7: 名前で見つからないとき `Name candidates for ...` で似た名前を最大5件出す（ルーン調査用）。

## 1.0.1 で追加

- #3: `Hidden from the item list: {...}`。一覧から外した件数（֎ 付きの重複、廃止、クエスト専用、ID の無いイベント景品）。
- #14: 高額アイテム通知。`Using Wynnventory prices` / `Wynnventory is not installed` / `Reward chest opened: <種類>` / `High value item: '<名前>' = <エメラルド> (<LE>)` / `Wynnventory price lookup failed`。
- `[WEI tooltip]`（常に出る）: 一覧作成時に説明文が作れず、バニラの名前（「クラフト不可能なポーション」など）で代用したアイテム。`FALLBACK #n ... cause=...` と元データの要点、例外のスタックトレース（最初の20件）、`build summary`、ホバー時の作り直し結果 `HOVER retry`。
- `[WEI item]`（常に出る）: 一覧の全アイテム1件ずつ。キー、表示名とその出どころ（data / tooltipLine / vanilla）、データの名前と説明文の名前（食い違えば NAME-MISMATCH）、名前の行の位置、行数、バニラ名、説明文の1・2行目（私用領域の文字は \u{XXXX}）。先頭30件と問題のあるものは行の構造（フォントなど）も。最後に `name sources` で集計。
- `[WEI hover]`（常に出る）: 画面で説明文を出したアイテムごとに1回、出した名前と先頭3行。

## 2.1.0 から

- 調査用ログ（`[WEI diag#…]`）は、プレリリース（バージョンに「-」を含む）では既定で出し、リリースでは出さない。`config/wei.json` の `diagnostics`（true / false）か JVM 引数 `-Dwei.diag=true|false` で上書きできる。
- リリースで常に出るのは、起動・一覧の完成（1 行）・失敗や機能停止の警告だけ。
- #13: `EMI hook active: <名前>`。PF-EMI に差し込めた Mixin（search / syncFallback / loadGate / favorites / history / recipeOverride）。
