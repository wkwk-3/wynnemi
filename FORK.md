# emi/ について

`emi/` は [PF-EMI](https://github.com/bostonstuddard/PF-EMI)（タグ `pf19+1.21.11`。配布されている PF-EMI-1.21.11.jar の元。`1.21.11` ブランチは 1.21.11 でビルドできないため使わない）を `git subtree` で取り込んだ EMI のフォークです。
PF-EMI は [EMI](https://github.com/emilyploszaj/emi)（MIT License, © Emi）のフォークで、このフォークも MIT License に従います（`emi/LICENSE`）。

## 方針

- Fabric 版だけをビルドする（`emi/gradle.properties` の `enabled_platforms=fabric`）
- mod ID は `emi` のまま（ほかの EMI アドオンがそのまま動くように）。このフォークだけが `wynncodex_emi` を provides する
- 追加 API は `dev.emi.emi.fork` パッケージにまとめる。既存ファイルの変更は最小限にし、`[WynnCodex fork]` のコメントを付ける

## 変更点

| ファイル | 内容 |
|---|---|
| `xplat/.../fork/EmiSearchExtension.java`, `EmiForkApi.java` | 検索に独自の条件と並び替えを足すための API（新規） |
| `xplat/.../search/EmiSearch.java` | 検索開始時（クライアントスレッド）に拡張を呼び、検索スレッドで絞り込みと並び替えを行う |
| `xplat/src/main/resources/fabric.mod.json` | 名前・説明、`provides: ["wynncodex_emi"]` |
| `gradle.properties` | jar 名、バージョン（タグの時点で Fabric のみ） |

## 上流の取り込み

```sh
git subtree pull --prefix emi https://github.com/bostonstuddard/PF-EMI.git <新しいタグ> --squash
```

`[WynnCodex fork]` で検索すると、こちらの変更箇所が見つかります。
