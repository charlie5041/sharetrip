# 同遊 TripShare

給朋友一起用的旅遊 app：記帳、多幣別分帳、每日行程。Flutter（Android + iOS）+ Firebase。

- 規格與資料模型：[docs/SPEC.md](docs/SPEC.md)
- 發布方式比照 BabyCalendar（抱寶）：Firebase App Distribution，iOS 由家裡的 Mac 以 self-hosted runner 建置（設定見下方「發布設定」）。

## 開發

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

Firestore 安全規則測試（需要 Node 22 和 Java 21）：

```bash
cd test/rules && npm install && npm test
```

## 發布設定

流程和抱寶一樣：把 `pubspec.yaml` 的 `version`（例如 `1.0.0+1` → `1.0.0+2`）改大並推上 `main`，CI 會：

1. 跑 `flutter analyze`、`flutter test` 和規則測試。
2. 部署 `firestore.rules`（只要推上 `main` 就會部署，不需要改版號）。
3. 建 Android APK，發給 App Distribution 群組 `friends_android`。
4. 在家裡的 Mac（self-hosted runner）建 iOS 開發簽章版，發給群組 `friends_ios`。這一步預設關閉，runner 和 iOS 憑證設好後，在 repo 的 Variables 加 `IOS_DISTRIBUTION` = `true` 才會執行。

沒改版號只想重發，可以在 Actions 頁籤手動執行 CI（`workflow_dispatch`，可選 both / ios / android）。

### 一次性設定

只發 Android 的話，做第 1 到 3 步即可（群組只需要 `friends_android`）。第 4 到 6 步是 iOS 用的。

GitHub 的 secrets 都放在 repo 的 Settings → Secrets and variables → Actions。

| Secret | 內容 |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | `tripshare-f6209` 的服務帳戶 JSON 金鑰，角色：Firebase App Distribution Admin、Firebase Rules Admin、Service Usage Viewer |
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
