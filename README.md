# 同遊 TripShare

給朋友一起用的旅遊 app：記帳、多幣別分帳、每日行程。Kotlin Compose Multiplatform（Android + 網頁）+ Firebase，程式在 `kmp/`。

> 舊的 Flutter 版（`lib/`、`android/`、`ios/`、`web/`）已不再發布，留著備查，確認新版穩定後再刪。

- 規格與資料模型：[docs/SPEC.md](docs/SPEC.md)
- 發布方式比照 BabyCalendar（抱寶）：Firebase App Distribution，iOS 由家裡的 Mac 以 self-hosted runner 建置（設定見下方「發布設定」）。

## 開發

```bash
cd kmp
./gradlew -Ptripshare.android=false :core:jvmTest           # 分帳、日期等邏輯的測試，不需要 Android SDK
./gradlew :androidApp:assembleRelease                        # Android APK（需要 Android SDK）
./gradlew :composeApp:wasmJsBrowserDevelopmentRun            # 網頁版，開在本機瀏覽器
```

網頁版要支援 WebAssembly GC 的瀏覽器：iPhone 需要 iOS 18.2 以上。

Firestore 安全規則測試（需要 Node 22 和 Java 21）：

```bash
cd test/rules && npm install && npm test
```

## 發布設定

把 `kmp/version.properties` 的 `versionCode` 加 1（需要的話一起改 `versionName`）並推上 `main`，CI 會：

1. 跑 Kotlin 邏輯測試和規則測試。
2. 部署 `firestore.rules`（只要推上 `main` 就會部署，不需要改版號）。
3. 建 Android APK（用 release 金鑰簽章，套件名稱和舊版相同，所以會直接更新），發給 App Distribution 群組 `friends_android`。
4. 建網頁版，部署到 https://tripshare-tw.web.app（iPhone 朋友用 Safari「加入主畫面」使用）。

沒改版號只想重發，可以在 Actions 頁籤手動執行 CI（`workflow_dispatch`，可選 both / android / web）。原生 iOS 版不再發布。

### 一次性設定

只發 Android 的話，做第 1 到 3 步即可（群組只需要 `friends_android`）。第 4 到 6 步是 iOS 用的。

GitHub 的 secrets 都放在 repo 的 Settings → Secrets and variables → Actions。

| Secret | 內容 |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | `tripshare-f6209` 的服務帳戶 JSON 金鑰，角色：Firebase App Distribution Admin、Firebase Rules Admin、Firebase Hosting Admin、Service Usage Viewer |
| `ANDROID_KEYSTORE_BASE64` | release keystore 檔案，`base64 -i upload.jks` 的結果 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密碼 |
| `ANDROID_KEY_ALIAS` | 金鑰別名（例如 `upload`） |
| `ANDROID_KEY_PASSWORD` | 金鑰密碼 |
| `IOS_SIGNING_P12` | Apple Development 憑證加私鑰匯出的 .p12，base64 |
| `IOS_SIGNING_P12_PASSWORD` | 匯出 .p12 時設的密碼 |

1. **Android keystore**（只做一次，檔案和密碼要另外備份，遺失後朋友得移除舊版再重裝）：
   ```bash
   keytool -genkey -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   base64 -i upload.jks | pbcopy
   ```
2. **服務帳戶**：Google Cloud Console → `tripshare-f6209` → IAM 與管理 → 服務帳戶 → 建立，給上表三個角色，建立 JSON 金鑰，整份貼進 `FIREBASE_SERVICE_ACCOUNT`。
3. **測試人員群組**：Firebase Console → App Distribution → 測試人員與群組，建立 `friends_android`（Android）和 `friends_ios`（iPhone）兩個群組，把朋友的 email 加進去。群組名稱不同的話，用 repo 的 Variables `FIREBASE_TESTER_GROUPS`、`FIREBASE_IOS_TESTER_GROUPS` 改。
4. **self-hosted runner**：抱寶的 runner 只綁抱寶的 repo，sharetrip 要另外註冊一個。repo Settings → Actions → Runners → New self-hosted runner → macOS，照畫面指令在 Mac 上裝在另一個資料夾（例如 `~/actions-runner-sharetrip`），預設標籤 `self-hosted, macOS` 即可。Mac 不能睡眠，否則 iOS 那一步會一直排隊。
5. **iOS 憑證**：用抱寶同一張 Apple Development 憑證（team V885M744V2），從「鑰匙圈存取」匯出成 .p12，`base64 -i dev.p12 | pbcopy` 貼進 `IOS_SIGNING_P12`。
6. **朋友的 iPhone**：開發簽章版只能裝在登記過的裝置上。朋友先從 App Distribution 的邀請信註冊裝置，Firebase Console 會列出 UDID；把 UDID 加到 Apple Developer → Devices，再重發一次（Xcode 自動簽章會把新裝置放進描述檔）。

### 錯誤與問題回報

- App 沒處理到的錯誤（每次開啟最多 20 筆，同樣的錯誤只記一次），以及朋友在旅程列表右上角「回報問題」打的內容，都寫進 Firestore 的 `errorReports`。
- 查看：Firebase Console → Firestore → `errorReports`，依 `createdAt` 排序。`kind` 是 `error`（自動）或 `feedback`（朋友寫的），`version` 有版號和平台（例如 `0.1.0+9 web-iOS`），`uid` 對得到 `users/{uid}` 的暱稱。
- 規則只允許新增，任何人都讀不到別人的回報。Crashlytics 不支援網頁版，所以沒有用它。

### 網頁版（iPhone）

- 網址：https://tripshare-tw.web.app 。用 Safari 打開，分享 →「加入主畫面」。在瀏覽器裡打開時，第一個畫面和旅程列表會出現「把同遊加到主畫面」的卡片，依 iPhone、Android、LINE 等內建瀏覽器顯示對應步驟（`lib/widgets/install_guide.dart`）；Android Chrome 可以一鍵安裝。它是 Firebase 專案裡另外開的 Hosting 網站 `tripshare-tw`，網域也要加在 Authentication → 設定 → 授權網域，Google 登入才會過。舊網址 tripshare-f6209.web.app 會轉到新網址（`firebase.json` 的第二個網站，內容在 `tool/old_site`）。
- 離線：`web/sw.js` 把整個 app 存在手機裡，打開過一次之後沒網路也能開。手動部署時，`flutter build web --release` 之後要先跑 `node tool/web_offline.mjs`，再 `firebase deploy --only hosting`。
- 身分備份：旅程列表右上角的盾牌可以綁定 Google 帳號。網頁資料被清掉或換手機時，在第一個畫面用 Google 找回。需要在 Firebase Console → Authentication → Sign-in method 啟用 Google。Android 用系統的 Google 帳號選單，需要把 release 簽章（`upload.jks`）的 SHA-1、SHA-256 加到 Firebase 專案設定的 Android App，否則會出現 `invalid-cert-hash` 或 `clientConfigurationError`。
- 沒綁 Google 又不見身分的人：請群主在成員頁點他的名字 →「解除綁定」，他再用新的邀請碼加入、選回原本的名字。
