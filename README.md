# eUtil

[EarthMC](https://earthmc.net/) 向けの Fabric クライアント Mod です。
VoteParty の進捗表示や、崩壊予定(削除予定)の町を一覧できるコマンドなど、EarthMC をプレイする上で便利な機能をまとめています。

## 動作環境

| 項目 | バージョン |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 以上 |
| Fabric API | 必須 |
| Mod Menu | 17.0.0-alpha.1 以上 |
| Java | 21 以上 |

## 機能

### 🗳️ VoteParty HUD

EarthMC API (`https://api.earthmc.net/v4/`) を60秒間隔でポーリングし、次回 VoteParty までの残り投票数を画面上に常時表示します。

- 表示位置(四隅)・オフセット・バー表示の有無・文字色・バーの色などを [Mod Menu](https://modrinth.com/mod/modmenu) の設定画面から変更可能
- 設定は `config/eutil-voteparty.json` に保存されます

### 🌲 `/falling` コマンド

最終ログインから42日が経過すると自治体(町)が自動崩壊(削除)される仕様に対し、**崩壊が近づいている町**を一覧表示するコマンドです。

```
/falling                      # デフォルト(崩壊が近い順)でソートして一覧表示
/falling <sort>                # ソート順を指定
/falling <sort> <nation>       # 国家名でさらに絞り込み(部分一致)
```

**ソートオプション**

| オプション | 内容 |
|---|---|
| (指定なし) | 崩壊予定が近い順 |
| `alphabetical` | 町名順 |
| `founded` | 建国日が新しい順 |
| `residents` | 住民数が多い順 |
| `size` | プロット数が多い順 |
| `balance` | 残高が多い順 |
| `capital` | 首都かどうか |
| `open` | オープンタウンかどうか |

各町について、残り時間(緊急度に応じて色分け)・崩壊予定日時・座標(クリックでコピー)・市長・住民数・プロット数・残高・首都/オープン/外人スポーン/PVP の可否を表示します。一覧は5件ごとにページ分割され、チャット上のボタン(`<<` `<` `>` `>>`)からページ送りができます。

### データキャッシュについて

- 崩壊予定の町データは EarthMC API から取得し、`config/eutil/falling_cache.json` にキャッシュされます
- 削除判定(最終ログイン + 42日)は毎日 **19:01 (JST)** に自動更新されます
- 既にこのタイミングでのキャッシュが存在する場合は、API へのリクエストを行わず再利用します

## ビルド方法

```bash
./gradlew build
```

生成された jar は `build/libs/` に出力されます。

## 開発

```bash
./gradlew runClient
```

でテスト用クライアントを起動できます。

## ライセンス

CC0-1.0
