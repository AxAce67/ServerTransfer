# ServerTransfer (Minecraft Plugin)

Minecraft 1.20.5+ の `Player#transfer` を使って、GUI から別サーバーへ転送するプラグインです。

## 特徴
- サーバー選択 GUI
- オンライン/オフライン表示と人数表示
- 転送前カウントダウン（移動でキャンセル）
- 参加時アイテムで GUI を開く
- GUI エディター（`/st setting`）
- 日本語/英語メッセージ対応

## 動作環境
- Minecraft 1.20.5 以上
- Spigot / Paper 系サーバー

## インストール
1. `build/libs` の jar を `plugins` に入れる
2. サーバー起動
3. `plugins/ServerTransfer/config.yml` を編集

## コマンド
- `/st` : サーバー選択 GUI を開く
- `/st help` : ヘルプ
- `/st reload` : 設定を再読み込み（管理者）
- `/st setting [size]` : GUI エディターを開く（管理者）

## 権限
- `servertransfer.use` : GUI を使用（デフォルト true）
- `servertransfer.admin` : reload/setting（デフォルト op）

## 設定例（config.yml）
```yaml
language: ja
gui-title: "&1Server Selector"
gui-size: 9
show-status: true
show-player-count: true
countdown-time: 3
status-separator: "&7----------------"
give-join-item: false
join-item-material: "COMPASS"
join-item-slot: 8

servers:
  lobby:
    address: "lobby.example.com:25565"
    material: "GRASS_BLOCK"
    display_name: "&aLobby"
    slot: 0
    lore:
      - "&7Click to transfer"
```

## GUI エディターについて
`/st setting [size]` で GUI を編集できます。インベントリを閉じると保存され、`config.yml` に反映されます。

## ビルド
```bash
./gradlew build
```
