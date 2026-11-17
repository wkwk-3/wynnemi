# Wynn EMI

Wynntils が持っている Wynncraft のアイテムデータを EMI（EMI Port Forward）の画面に表示する Fabric 用クライアント MOD です。

- 対応: Minecraft 1.21.11 / Fabric
- 必須: [Wynntils](https://modrinth.com/mod/wynntils)（v4.2.17 で確認）、[EMI Port Forward](https://github.com/bostonstuddard/PF-EMI)、Fabric API
  - どれか1つでも入っていなければ Fabric が起動を止めて不足を表示します

## できること

| 操作 | 結果 |
|---|---|
| EMI の一覧を見る | 装備・素材・マテリアル・ツール・チャーム・トーム・アスペクト・パウダー・ルーン・ワード・オーグメント・エメラルド・ダンジョンキーが並ぶ（説明文は Wynntils のガイドと同じ） |
| アイテムで **R** | 「入手: ダンジョン報酬」「入手: 商人」などのタブで入手元がわかる |
| 入手元（例: `Decrepit Sewers`）で **U** | そこで手に入るアイテムの一覧（逆引き）。入手元は一覧の検索からも探せる |
| 入手方法のアイコン（作業台欄）をクリック | 例: レイドで手に入る全アイテム |
| 製作素材で **U** | 使える製作職業 |
| 職業で **R** / **U** | その職業で使える素材 / 採集で取れるマテリアル |

Wynncraft に接続している間は、EMI からバニラのアイテムとレシピを隠します。

## 設定 `config/wynnemi.json`

| 項目 | 既定 | 内容 |
|---|---|---|
| `hideVanillaOnWynncraft` | `true` | Wynncraft 接続中はバニラのアイテム・レシピを隠す |
| `showSourcesInIndex` | `true` | 入手元・職業の仮想アイテムも一覧に並べる（検索で逆引き用） |
| `showObtainRecipes` | `true` | 入手方法のレシピを出す |
| `showProfessionRecipes` | `true` | 職業関連のレシピを出す |

変更後はゲーム内で `/wynnemi reload`。

## しくみと注意点

- **データの読み込みタイミング**: Wynntils は起動後にアイテムデータを非同期でダウンロードします。1 秒ごとにデータ量を監視し、揃ったら（変化したら）一覧を作り直して EMI を再読み込みします。EMI の再読み込みは公開 API に無いため内部クラスをリフレクションで呼びます。失敗した場合はワールドに入り直すか `/wynnemi reload`。
- **説明文**: Wynntils のガイド用アイテムは `getTooltipLines()` のオーバーライドで説明文を作るため、EMI 標準の `EmiStack.of()` だと消えます。専用の `WynnEmiStack` で元の ItemStack を保持して解決しています。Wynntils の独自ツールチップ枠（レアリティ色の枠）は EMI の描画では再現されません。
- **同一判定**: Wynncraft のアイテムはバニラのアイテムを流用しているため、EMI 標準ではまとめられてしまいます。アイテムごとに固有のキーを持たせています。
- **お気に入り**: EMI のお気に入りに登録でき、キーで保存されます（Wynntils 側でアイテム名が変わると外れます）。
- **入手元データの限界**: Wynntils のデータは「種類＋名前」だけで、ドロップ率・座標・必要条件はありません。クラウドソースのため「入手方法不明」も多めです。
- **Wynntils の内部クラスに依存**: `Guide*ItemStack` は Wynntils の公開 API ではないため、Wynntils の更新で壊れることがあります。種類ごとに例外を捕まえているので、1 種類が壊れても他は表示されます（ログに `WynnEMI` で警告）。
- **PF-EMI**: [bostonstuddard/PF-EMI](https://github.com/bostonstuddard/PF-EMI) の `1.21.11` ブランチ（mod ID `emi`、EMI 1.1.23 ベース）の API に合わせています。

## ビルド

### GitHub Actions（おすすめ）

push すると `.github/workflows/build.yml` が Wynntils（GitHub Releases）と PF-EMI（Modrinth）を取ってきてビルドし、jar を Artifacts に上げます。

### 手元でビルド

1. `libs/` に次の 2 つを置く（ファイル名の先頭が一致すれば OK）
   - `wynntils*.jar` … Wynntils の Fabric 版（1.21.11）
   - `emi*.jar` … EMI Port Forward の Fabric 版（1.21.11）
2. `./gradlew build`
3. `build/libs/wynnemi-<version>.jar` を mods フォルダへ
