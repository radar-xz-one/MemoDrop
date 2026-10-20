# MemoDrop / メモポイ

超シンプルで直感的な1ページ限定の「手書き専用・使い捨てブロックメモアプリ」

## 機能

- 完全手書き専用・1ページ限定
- 地図のような広大な真っ白な手書きキャンバス
- 下部のめくりエリアを上にスワイプで「ビリッ」とちぎって削除
- 初回起動時のオープニング演出（自動で落書きがちぎれる）
- ローカルストレージに自動保存
- 多言語対応（英語/日本語）

## プロジェクト構成

```
MemoDrop/
├── app/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/memodrop/
│           │   ├── MainActivity.kt
│           │   └── HandwritingCanvas.kt
│           ├── res/
│           │   ├── layout/
│           │   │   └── activity_main.xml
│           │   ├── values/
│           │   │   └── strings.xml
│           │   ├── values-ja/
│           │   │   └── strings.xml
│           │   └── raw/
│           │       └── tear_sound.mp3  ← 効果音ファイルをここに配置
├── build.gradle
└── settings.gradle
```

## 効果音ファイルの配置手順

1. **効果音ファイルを用意**
   - 紙を破く「ビリッ」という音声ファイル（MP3形式推奨）
   - ファイル名: `tear_sound.mp3`
   - 長さ: 0.2〜0.5秒程度の短い音

2. **配置場所**
   - ファイルを以下のフォルダに配置:
   ```
   app/src/main/res/raw/tear_sound.mp3
   ```

3. **rawフォルダがない場合**
   - `app/src/main/res/` フォルダ内に `raw` フォルダを作成
   - その中に `tear_sound.mp3` を配置

4. **効果音の入手方法**
   - 無料音素材サイトから探す:
     - [OtoLogic](https://otologic.jp/)
     - [効果音ラボ](https://soundeffect-lab.info/)
     - [魔王魂](https://maoudamashii.jokersounds.com/)
   - 検索キーワード: 「紙を破く」「ちぎる」「破れる」

5. **コード内での参照**
   - `R.raw.tear_sound` で自動的に参照されます
   - MainActivity.kt で `MediaPlayer.create(this, R.raw.tear_sound)` として使用

## ビルド方法

### Android Studioでビルド

1. Android Studioを開く
2. 「Open」を選択し、`MemoDrop` フォルダを選択
3. Gradle同期が完了したら、実行ボタン（▶）をクリック

### コマンドラインでビルド

```bash
cd MemoDrop
./gradlew assembleDebug
```

## 多言語対応

- 英語（デフォルト）: `app_name = "MemoDrop"`
- 日本語: `app_name = "メモポイ"`
- 端末の言語設定に応じて自動的に切り替わります

## 操作方法

1. **手書き**: キャンバス上部70%のエリアで指で描画
2. **削除**: 下部30%のめくりエリアを上にスワイプ
   - 画面の高さの30%以上移動で削除確定
   - 「ビリッ」という効果音と共に紙が飛んでいく
3. **初回起動**: 自動で落書きが描かれ、1秒後にちぎれる演出

## 注意点

- Pathのシリアライズは簡易実装です。実プロジェクトではより堅牢な方法を推奨
- アダプティブバナー広告はプレースホルダーです。実際の広告SDKを追加してください
