# 同遊（TripShare）MVP 規格

給自己和朋友用的旅遊 app：一趟旅程裡，大家一起記帳、分帳、看行程。

名稱：中文「同遊」、英文「TripShare」。兩個平台的手機桌面上都顯示「同遊」。repo 名稱維持 `sharetrip`，套件 ID 也維持 `com.charliechen.sharetrip`（使用者看不到）。

期限 2026-11-30。凡是不在「MVP 範圍」裡的，都等 11/30 之後再說。

## MVP 範圍

### 旅程與成員
- 建立旅程：名稱、日期區間、**結算幣別**（預設 TWD）。
- 用 8 碼邀請碼（24 小時有效）邀朋友加入，沿用抱寶的做法。不用註冊，第一次開 app 用 Firebase 匿名登入，只要取一個暱稱。
- **成員名單由群主決定**（2026-11-17 改，參考 LINE Lightsplit）：建旅程的人就是群主，建立時先填所有人的名字。朋友用邀請碼加入時，只能從還沒被選走的名字裡挑一個；名單上沒有自己的名字就進不來。
- 只有群主能加人、改別人的名字、移除成員、解除綁定（換手機時讓對方重新選回同一個名字）和解散旅程。已經有記帳的成員不能移除。
- 沒人選走的名字就是「只有名字」的成員（例如長輩），一樣能分帳。
- 一台手機可以加入多趟旅程。

### 記帳與分帳
- 一筆支出包含：項目名稱、金額、**幣別**、誰付的（一個人）、分給誰、分法、類別、日期時間、備註。
- 分法：
  - **平均**：勾選的人平分。
  - **指定金額**：每人填各自的金額，加總必須等於總額。
  - **份數**：例如 A 2 份、B 1 份。
- 多幣別：每筆支出存「原幣金額」加上「記帳當下對結算幣別的匯率」。匯率會自動帶入當天參考匯率，可以手動改成實際刷卡匯率。之後匯率變動，不影響已經記的帳。
- 結算頁：每人總付、總分攤、淨額（結算幣別），以及「誰該轉多少給誰」的建議。
- 標記「已轉帳」：記一筆還款，淨額跟著更新。
- 尾數：除不盡的最小單位，分給「被捨去最多」的人一人多 1（一樣多時依成員順序），保證加總等於總額，不會憑空多出或少掉 1 元。

### 行程
- 按天列出項目：時間（可空）、標題、地點、地圖連結、備註；同一天內可拖曳排序。
- 地點一鍵開啟 Google Maps / Apple 地圖。不嵌入地圖、不自動排路線。

### 首頁、航班、住宿、清單（2026-11-16 加入範圍）
- 旅程內用底部選單切換：首頁、行程、清單、記帳（支出／結算）、成員。原本參考的網站是「頂端固定選單」，不過手機 app 的慣例是底部選單，單手也比較好按。
- 首頁：旅程名稱、日期、出發倒數（旅行中顯示「第 N 天」），以及航班卡片和住宿卡片。
- 航班：航班編號、航空公司、出發／抵達機場代碼與名稱、當地日期時間、備註。時間照機票上的當地時間存成文字，不做時區換算。
- 住宿：名稱、入住／退房日期（自動算出幾晚）、地址、地圖連結、訂房編號、備註。
- 行程項目另外有「怎麼去」（步行／電車／巴士／計程車／開車／飛機／船，用彩色小標籤顯示）和「必去」標記。有時間的項目依時間排序，沒時間的排在最後。目前**不支援拖曳排序**。
- 清單：待辦、出發前、行李三種，大家共用，打勾會顯示是誰勾的；左滑刪除。出發前和行李清單是空的時候，可以一鍵加入常用項目。

### 發布
- 照抱寶的方式：改 `pubspec.yaml` 的版本號並推上 `main`，CI 就把 Android APK 和 iOS 開發簽章版發到 Firebase App Distribution。
- iPhone（2026-11-17 改）：不發 iOS app，改用網頁版 https://tripshare-f6209.web.app ，Safari「加入主畫面」。CI 在改版號時一起部署。iOS 開發簽章版的流程留著，用 repo 變數 `IOS_DISTRIBUTION` 開關。
- 身分備份：匿名身分可以綁定 Google 帳號（身分 ID 不變），換裝置或網頁資料被清時用 Google 找回。沒綁的人請群主在成員頁「解除綁定」，再用新邀請碼加入、選回原本的名字。

## 視覺風格

日系繪本風，可愛、簡約，只用淺色底（不做深色模式）。程式碼在 `lib/theme.dart`。
- 底色和紙白 `#FFFCF6`；3px 墨灰 `#3E3A39` 粗線框；大圓角（20）；平塗，不用漸層和陰影。
- 配色：珊瑚粉 `#F2A497`、山吹黃 `#F6D27A`、水色 `#A8D3E6`、若竹綠 `#B9DAA5`。
- 字型：Huninn（粉圓），隨 app 打包，出國沒網路也能正常顯示。
- 行程：上方用日期切換，下方是時間軸；交通方式用彩色小標籤；重點行程標「必去」。切換日期時有彈跳效果，並遵守系統的「減少動態效果」設定。

## 不做（MVP 之後）
一筆多人付款、收據拍照/OCR、離線匯率表、圖表統計、推播、匯出 Excel、行程地圖、多語系、深色模式以外的主題、App Store 上架。

## 資料模型（Firestore）

金額一律存**整數最小單位**（`amountMinor`），不用浮點數存錢。
各幣別小數位數依 ISO 4217，但 **TWD 和 JPY 都當 0 位**（實務上沒人付角/分）。
匯率存成十進位字串（例如 `"0.2134"`），避免 double 誤差在結算時累積。

```
users/{uid}
  nickname: string
  tripIds: string[]                 // 這台裝置加入的旅程

trips/{tripId}
  name: string
  baseCurrency: string              // 結算幣別，ISO 4217，例如 "TWD"
  startDate: string                 // "2026-12-20"
  endDate: string
  ownerUid: string
  memberUids: string[]              // 有 app 的成員，給安全規則用
  createdAt: int                    // epoch ms

trips/{tripId}/members/{memberId}
  name: string
  uid: string?                      // null = 沒有 app、只有名字的成員
  order: int                        // 顯示順序，也決定尾數分配順序
  joinedAt: int

trips/{tripId}/expenses/{expenseId}
  title: string
  category: string                  // food | transport | lodging | ticket | shopping | other
  amountMinor: int                  // 原幣，最小單位
  currency: string                  // 原幣，例如 "JPY"
  rateToBase: string                // 1 單位原幣 = ? 結算幣，十進位字串
  payerId: string                   // memberId
  splitMode: string                 // equal | exact | shares
  splits: map<memberId, int>        // equal: 1；exact: 原幣最小單位；shares: 份數
  occurredAt: int
  note: string?
  createdBy: string                 // uid
  updatedAt: int

trips/{tripId}/settlements/{id}     // 「已轉帳」紀錄
  fromId: string
  toId: string
  amountMinor: int                  // 結算幣別
  at: int
  createdBy: string

trips/{tripId}/items/{itemId}       // 行程
  transport: string                 // none | walk | train | bus | taxi | car | flight | ferry
  mustGo: bool
  date: string                      // "2026-12-21"
  time: string?                     // "09:30"
  title: string
  place: string?
  mapUrl: string?
  note: string?
  order: int

trips/{tripId}/flights/{id}
  flightNo, airline?, fromCode, fromName?, toCode, toName?: string
  departDate, departTime: string    // 出發地當地時間
  arriveDate?, arriveTime?: string  // 抵達地當地時間
  note?: string

trips/{tripId}/lodgings/{id}
  name, checkIn, checkOut: string
  address?, mapUrl?, confirmation?, note?: string

trips/{tripId}/checklist/{id}
  kind: string                      // todo | predeparture | packing
  text: string
  done: bool
  doneBy: string?                   // 勾選者的名字
  order: int

invites/{code}
  tripId: string
  expiresAt: Timestamp
```

安全規則：只有 `memberUids` 裡的人可以讀寫該旅程。加入旅程時，比照抱寶，用 `users/{uid}.joinCode` 證明這次加入是哪張邀請碼授權的，免費方案不需要 Cloud Functions。

## 計算規則

1. 每筆支出先在**原幣**按分法拆給每個人（含尾數分配）。
2. 把總額乘上 `rateToBase` 換成結算幣（四捨五入到最小單位），再依第 1 步的原幣分攤比例拆給每個人。所以付款人收回的，剛好等於大家分攤的總和。
3. 淨額 = 總付 − 總分攤 + 已收還款 − 已付還款。所有人的淨額加總恆為 0。
4. 轉帳建議：每次讓欠最多的人轉給被欠最多的人，直到歸零。最多 n−1 筆。這不一定是理論上的最少筆數（那是 NP-hard 問題），但對 10 人以內的旅行團夠用。

## 待確認
- 「沒有 app 的成員」是否真的需要？目前先做，因為它不影響資料模型，成本低。
- iOS bundle id 目前是 `com.charliechen.sharetrip`，第一次在 Mac 上用 Xcode 自動簽章時，會註冊到 team V885M744V2 底下。
