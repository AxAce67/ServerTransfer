<div align="center">

<img src=".github/icon.png" width="96" height="96" alt="">

# ServerTransfer

メニューから別のサーバーへ移動できる Minecraft プラグイン（プロキシ不要）

[![build](https://img.shields.io/github/actions/workflow/status/AxAce67/ServerTransfer/ci.yml?branch=main&label=build)](https://github.com/AxAce67/ServerTransfer/actions/workflows/ci.yml) [![release](https://img.shields.io/github/v/release/AxAce67/ServerTransfer?include_prereleases&label=release)](https://github.com/AxAce67/ServerTransfer/releases) [![Modrinth](https://img.shields.io/modrinth/dt/servertransfer?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/servertransfer) [![license](https://img.shields.io/github/license/AxAce67/ServerTransfer)](LICENSE)<br>
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.5%E2%80%9326.3-62B47A) ![server](https://img.shields.io/badge/server-Paper%20%7C%20Purpur-1E88E5) ![Java](https://img.shields.io/badge/Java-21%2B-E76F00)

[English](README.md) | **日本語**

</div>

ダウンロード: [Modrinth](https://modrinth.com/plugin/servertransfer) · [GitHub Releases](https://github.com/AxAce67/ServerTransfer/releases)

Minecraft サーバー（Paper / Purpur）用の、サーバー選択プラグインです。プレイヤーはメニューからサーバーを選び、Minecraft の転送機能
（1.20.5 以降）で移動します。プロキシは必要ありません。

- 各サーバーのオンライン状態・人数・応答時間をメニューに表示
- 移動前のカウントダウン（動く・ダメージを受けるとキャンセル）と、連続移動の待ち時間
- 応答しないサーバーには移動させない
- `/st <サーバー>` や `/st send <プレイヤー|@a> <サーバー>` で直接移動（NPC やコマンドブロックからも使えます）
- メニューを開くアイテムをホットバーに配る機能（任意）
- ゲーム内でサーバーの追加・削除・並べ替え
- MiniMessage と `&` カラーコードに対応。日本語・英語はプレイヤーごとに自動で切り替え

## 動作環境

- Paper 1.20.5〜26.x（Purpur などのフォークも可）。Spigot と Folia には対応していません。
- Java 21 以降（Paper のバージョンが必要とするもの）
- **移動先のサーバーすべてで、`server.properties` に `accepts-transfers=true` が必要です。**
  設定がないと、プレイヤーはエラーで切断されます。このプラグインを入れるサーバー側には不要です。

移動先には新しく接続する形になるため、持ち物や位置は移動先サーバーのものになります。移動先は、
転送を受け付けるサーバーならプロキシでもかまいません（Velocity は `accepts-transfers = true`）。

## はじめかた

1. jar を `plugins/` に入れてサーバーを起動します。
2. アイコンにしたいアイテムを手に持って、サーバーを追加します:
   `/st add lobby lobby.example.com`（`ホスト:ポート` も可）
3. `/st` でメニューが開きます。並べ替えは `/st edit`、名前や説明は
   `plugins/ServerTransfer/config.yml` を編集して `/st reload` してください。

1.x からの更新: 古い `config.yml` は最初の起動時に自動で変換され、元のファイルは `config-1.x.yml` として残ります。

## コマンド

`/st` は `/servertransfer` の省略形です。

| コマンド | 権限 | |
|---|---|---|
| `/st` | `servertransfer.use` | メニューを開く |
| `/st <サーバー>` | `servertransfer.use` | サーバーへ移動 |
| `/st list` | `servertransfer.use` | サーバーの一覧と状態（クリックで移動） |
| `/st send <プレイヤー\|@a> <サーバー>` | `servertransfer.admin` | プレイヤーを移動させる（カウントダウンなし） |
| `/st add <ID> <アドレス> [スロット]` | `servertransfer.admin` | サーバーを追加（アイコンは手に持ったアイテム） |
| `/st remove <サーバー>` | `servertransfer.admin` | サーバーを削除 |
| `/st edit [行数]` | `servertransfer.admin` | アイコンをドラッグで並べ、閉じると保存 |
| `/st reload` | `servertransfer.admin` | 設定とメッセージを再読み込み |

`servertransfer.use` は全員、`servertransfer.admin` は OP に付きます。
`servertransfer.bypass`（標準では誰にも付きません）があると、カウントダウンと待ち時間がなくなります。
`permission` を設定したサーバーは、その権限がないプレイヤーには表示されません。

## 設定

設定項目は [config.yml](src/main/resources/config.yml) を参照してください。主なものは次のとおりです。

| 項目 | 内容 |
|---|---|
| `language` | `auto`（プレイヤーのゲームの言語）、`en`、`ja` |
| `menu.rows` | メニューの行数（1〜6）。スロットが入りきらないときは自動で増えます |
| `menu.filler` | 空きスロットを埋めるアイテム（例: `GRAY_STAINED_GLASS_PANE`） |
| `transfer.countdown-seconds` | 移動までの秒数。0 ですぐに移動 |
| `transfer.check-online` | 移動前に応答を確認し、オフラインなら移動させない |
| `join-item.enabled` | メニューを開くアイテムを配る |
| `servers.<ID>` | `address`、`name`、`icon`、`slot`、`lore`、`permission` |

名前と説明文では `{online}`（人数）、`{max}`（最大人数）、`{ping}`（応答時間）が使えます。

アドレスは書いたままプレイヤーに送られます。SRV レコードはクライアントが自分で調べるため、
ホスト名で振り分けるプロキシやホスティングでもそのまま動きます。状態の確認でも、クライアントと同じ方法で
SRV レコードを調べます。

メッセージを変えたいときは、`plugins/ServerTransfer/lang/messages_ja.properties` を作り、変えたいキーだけを
書いてください。キーの一覧は [messages_ja.properties](src/main/resources/lang/messages_ja.properties) にあります。

## ビルド

```bash
./gradlew build                     # build/libs に jar を作成し、単体テストを実行
./gradlew runServer                 # テスト用サーバー
scripts/smoke-test.sh 1.20.5 26.3   # バージョンごとに実サーバーで確認
```

## ライセンス

[MIT](LICENSE)
