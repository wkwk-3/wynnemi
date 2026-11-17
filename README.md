# Wynn EMI

Wynntils が持っている Wynncraft のアイテムデータを EMI（EMI Port Forward）の画面に表示する Fabric 用クライアント MOD です。

- 対応: Minecraft 1.21.11 / Fabric
- 必須: [Wynntils](https://modrinth.com/mod/wynntils)（v4.2.17 で確認）、Fabric API
  - EMI は**このMODに同梱**しています（[PF-EMI](https://github.com/bostonstuddard/PF-EMI) をもとにしたフォーク。詳しくは [FORK.md](FORK.md)）。EMI や PF-EMI を別に入れる必要はなく、**一緒に入れないでください**
  - 必要なものが足りなければ Fabric が起動を止めて不足を表示します

## できること

| 操作 | 結果 |
|---|---|
| EMI の一覧を見る | 装備・素材・マテリアル・ツール・チャーム・トーム・アスペクト・パウダー・ルーン・ワード・オーグメント・エメラルド・ダンジョンキーが並ぶ（説明文は Wynntils のガイドと同じ） |
| アイテムで **R** | 「入手: ダンジョン報酬」「入手: 商人」などのタブで入手元がわかる |
| 入手元（例: `Decrepit Sewers`）で **U** | そこで手に入るアイテムの一覧（逆引き）。入手元は一覧の検索からも探せる |
| 入手方法のアイコン（作業台欄）をクリック | 例: レイドで手に入る全アイテム |
| 製作素材で **U** | 使える製作職業（必要レベルつき） |
| 職業で **R** / **U** | その職業で使える素材（レベル順） / 採集で取れるマテリアル |
| **インベントリ・銀行・取引所のアイテム**で **R** / **U** | EMI の一覧と同じように入手元・使い道を表示 |
| 手持ちの**未鑑定ボックス**で **R** | 中身の候補（そのボックスのレベル範囲のページへ移動） |
| 装備で **R** | 必要なクエストと所属するセットも表示 |
| クエストで **U** | クリア報酬と、使えるようになる装備 |
| セットで **U** | メンバーの装備と、個数ごとのボーナス（セットの説明文） |
| EMI で検索 | 名前に加えて `mythic wand`、`lv105`、`mage`、`weaponsmithing` などでも絞り込める |
| EMI で `key:value` 検索 | Wynntils のアイテムフィルターと同じ書き方。例: `level:100-105 rarity:mythic gearType:wand rawStrength:>10 sort:rawStrength`（範囲・以上以下・並び替えに対応）。ほかの語は今までどおり EMI が検索 |
| EMI のお気に入りに追加 | Wynntils のお気に入りにも入り、チェストや取引所で目立つ |

Wynncraft に接続している間は、EMI からバニラのアイテムとレシピを隠します。

## 設定 `config/wynnemi.json`

| 項目 | 既定 | 内容 |
|---|---|---|
| `hideVanillaOnWynncraft` | `true` | Wynncraft 接続中はバニラのアイテム・レシピを隠す |
| `showSourcesInIndex` | `true` | 入手元・職業の仮想アイテムも一覧に並べる（検索で逆引き用） |
| `showObtainRecipes` | `true` | 入手方法のレシピを出す |
| `showProfessionRecipes` | `true` | 職業関連のレシピを出す |
| `showGearRecipes` | `true` | 必要クエスト・未鑑定ボックスの中身・セット装備のレシピを出す |
| `syncFavoritesToWynntils` | `true` | EMI のお気に入りを Wynntils のお気に入りにも入れる |

## コマンド

| コマンド | 内容 |
|---|---|
| `/wynnemi reload` | 設定を読み直して EMI だけ再読み込み（アイテム一覧は作り直さない） |
| `/wynnemi rebuild` | Wynntils のデータからアイテム一覧を作り直す |

## しくみと注意点

- **データの読み込みタイミング**: Wynntils は起動後にアイテムデータを非同期でダウンロードします。Wynntils のダウンロード完了イベントを受けて一覧を作り直し、EMI を再読み込みします（保険として件数も監視）。プレイヤーのクラスが変わったときも、説明文が変わるので作り直します。
- **作り直しの負荷**: 1 ティックあたり 4 ミリ秒ずつに分けて作るので、ゲームは止まりません。完了まで数秒かかることがあります。
- **一部の失敗**: 一部の種類が読めなかった場合も、読めた分は表示したうえで 5 秒、15 秒、1 分、5 分後に作り直しを再試行します。それでも駄目なら、データが変わるか `/wynnemi rebuild` まで待ちます。
- **設定・接続状態の変化**: 一覧は作り直さず、EMI の再読み込みだけを行います。EMI がワールド参加時などに自分で最新の内容を読み込んでいれば、重ねて再読み込みはしません。
- **EMI の再読み込み**: 公開 API に無いため内部クラスをリフレクションで呼びます。失敗した場合はワールドに入り直してください。
- **説明文**: Wynntils のガイド用アイテムは `getTooltipLines()` のオーバーライドで説明文を作るため、EMI 標準の `EmiStack.of()` だと消えます。一覧を作るときに説明文を一度だけ取得して保存し、専用の `WynnEmiStack` で使います。EMI の別スレッドからは Wynntils に一切触れません。Wynntils の独自ツールチップ枠（レアリティ色の枠）は EMI の描画では再現されません。
- **同一判定**: Wynncraft のアイテムはバニラのアイテムを流用しているため、EMI 標準ではまとめられてしまいます。アイテムごとに固有のキーを持たせています。
- **お気に入り**: EMI のお気に入りに登録でき、キーで保存されます（Wynntils 側でアイテム名が変わると外れます）。Wynntils への連動は EMI → Wynntils の一方向で、このMODが追加したものだけを記録して（`config/wynnemi-favorites.json`）、EMI から外したときにそれだけを外します。もともと Wynntils でお気に入りにしていたものには触れません。EMI の内部クラスを読んでいるため、EMI の更新で使えなくなったらこの機能だけ止まります。
- **未鑑定ボックス**: 候補は「装備の種類 × レアリティ」ごとに全レベル分を並べ、手持ちのボックスで R を押すとそのレベル範囲の最初の候補へ移動します（候補の条件は Wynntils のアイテム推測と同じ）。
- **手持ちのアイテム**: Wynntils がアイテムを認識できたものだけが対象です。クラフト品など、Wynntils のデータに無いアイテムは EMI 標準の動きになります。
- **入手元データの限界**: Wynntils のデータは「種類＋名前」だけで、ドロップ率・座標・必要条件はありません。クラウドソースのため「入手方法不明」も多めです。
- **Wynntils の内部クラスに依存**: `Guide*ItemStack` は Wynntils の公開 API ではないため、Wynntils の更新で壊れることがあります。種類ごとに例外を捕まえているので、1 種類が壊れても他は表示されます（ログに `WynnEMI` で警告）。
- **PF-EMI**: [bostonstuddard/PF-EMI](https://github.com/bostonstuddard/PF-EMI) の `1.21.11` ブランチ（mod ID `emi`、EMI 1.1.23 ベース）の API に合わせています。

## ビルド

### GitHub Actions（おすすめ）

push すると `.github/workflows/build.yml` が Wynntils（GitHub Releases）を取ってきて、`emi/`（EMI のフォーク）→ MOD 本体の順にビルドし、jar を Artifacts に上げます。EMI のフォークは MOD 本体の jar に同梱されます。

### 手元でビルド

1. `libs/wynntils*.jar` に Wynntils の Fabric 版（1.21.11）を置く
2. `cd emi && ./gradlew :fabric:remapJar` で EMI のフォークをビルドし、`emi/fabric/build/libs/` の jar（`-api` / `-sources` / `-dev` が付かないもの）を `libs/emi.jar` としてコピー
3. ルートで `./gradlew build`
4. `build/libs/wynnemi-<version>.jar` を mods フォルダへ（EMI は同梱済み）
