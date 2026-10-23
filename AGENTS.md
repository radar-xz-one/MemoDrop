# MemoDrop / メモポイ

手書き専用・1ページの使い捨てメモ(Android, Kotlin)。下から上へめくって破って消す。

## ビルド・実機確認
- USBデバッグ有効のスマホを接続して `./gradlew.bat installDebug`(Windows)
- 起動: `adb shell monkey -p com.memodrop -c android.intent.category.LAUNCHER 1`
- adb: `C:/Users/ecino/AppData/Local/Android/Sdk/platform-tools/adb.exe`
- Wi-Fi経由のブラウザ確認(index.html)はスマホ側で接続できず断念。USB実機で確認する。
- 保存データ消去: `adb shell pm clear com.memodrop`

## 構成
- `HandwritingCanvas.kt`: 描画・めくりアニメ・保存復元・表紙演出
- `MainActivity.kt`: 起動処理・効果音・広告表示・課金連携
- `BillingManager.kt`: 買い切り課金(商品ID `remove_ads`)
- 効果音: `res/raw/tear_sound.mp3`(破く瞬間に再生)
- 多言語: `values`(英語)/`values-ja`(日本語)。画面文言は必ずstringsに置く

## 仕様・決定事項
- 手書きは座標をJSONでSharedPreferencesに保存。起動時に復元
- 起動時、メモが空のときだけ表紙(鉛筆の絵)が約1.8秒後に自動でめくれる。メモがあれば演出なし
- 下部のめくり帯は「わずかに傾けた平らな帯+影」で確定(`drawCornerCurl`)。ロール表現・隅だけの表現は不採用
- 線の太さ `strokeWidth = 8f`
- 収益化: 無料+上部バナー広告(横長320x50)、100円の買い切りで広告削除(月額ではない)
- ビルド設定: Kotlin 2.2.21 / compileSdk 36 / targetSdk 35 / minSdk 24

## 公開前TODO
- AdMob: 本番IDはreleaseビルド、テストIDはdebugビルドに `app/build.gradle` の resValue で設定済み(開発中に本番広告を自分でタップしない)
- Play Consoleで `remove_ads`(100円・管理対象アイテム)を作成
- 署名鍵作成、AABでリリースビルド
- アプリアイコン作成(現在は標準アイコン)
- プライバシーポリシー作成(日英)。内容: 広告(AdMob)・課金(Google Play)を利用、手書きメモは端末内のみ保存。公開URLと連絡先メールが必要
- EEA向けに配信する場合はUMP同意フォームが必要(未実装)
- ストア掲載情報は英語版も登録(タイトル MemoDrop / メモポイ)

## 状況
- クローズドテスト中。終了後に申請開始予定。そのとき公開手順とプライバシーポリシーを作成する

## 注意(実装メモ)
- targetSdk 35 は edge-to-edge 強制。`MainActivity` でシステムバーのinsetsをルートのpaddingに反映している
- めくり帯の描画オブジェクトは `onSizeChanged` でキャッシュ(onDrawで生成しない)
- 手書きの点(濁点)は2点目を0.1pxずらして1本の線として扱う

## 署名・公開の進捗(引き継ぎ)
- 署名鍵: プロジェクト直下に `memodrop-upload.keystore` と `.signing.env`(パスワード入り)を作成済み。`.gitignore` で除外済み(`.gitignore` の変更は未コミット)。中身は会話・ログに出さない
- GitHub Pages のデプロイは修正済み
- 署名設定済み(`app/build.gradle`。`.signing.env` または同名の環境変数から読む。鍵が無い環境ではリリースは未署名になるだけでビルドは通る)
- リリースAAB: `./gradlew.bat bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`(署名済みでビルド確認済み。versionCodeはアップロードごとに増やす)
- アイコン作成済み(青緑背景+黄色い鉛筆)。`python tools/make_icon.py`(プロジェクト直下で実行)でアダプティブアイコン・旧端末用PNG・Playストア用 `store/icon-512.png` を再生成できる
- プライバシーポリシー作成済み: `privacy.html`(日英、連絡先 ecinojo@gmail.com)。公開URL(Pages): https://radar-xz-one.github.io/MemoDrop/privacy.html (プッシュ後に表示を要確認)
- AdMob本番ID設定済み(versionCode 3)。残り: Play Consoleの申請作業
