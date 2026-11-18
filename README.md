# 同遊 TripShare

給朋友一起用的旅遊 app：記帳、多幣別分帳、每日行程。Kotlin Compose Multiplatform（Android + 網頁）+ Firebase，程式在 `kmp/`。

> 舊的 Flutter 版（`lib/`、`android/`、`ios/`、`web/`）已不再發布，留著備查，確認新版穩定後再刪。

規格與資料模型：[docs/SPEC.md](docs/SPEC.md)

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

測試一律用假資料，不要放真實人名、試算表內容或連結、旅程資料、uid 或 Email。

## 發布

把 `kmp/version.properties` 的 `versionCode` 加 1（需要的話一起改 `versionName`）並推上 `main`，CI 會跑測試、部署 `firestore.rules`、建 Android 版發給測試人員，並部署網頁版。只推上 `main` 不改版號時，只會部署規則。

CI 需要的金鑰與帳號都放在 GitHub Actions secrets，不放進 repo。
