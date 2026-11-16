# ShareTrip MVP 規格

給自己和朋友用的旅遊 app：一趟旅程裡，大家一起記帳、分帳、看行程。
期限 2026-11-30。凡是不在「MVP 範圍」裡的，都等 11/30 之後再說。

## MVP 範圍

### 旅程與成員
- 建立旅程：名稱、日期區間、**結算幣別**（預設 TWD）。
- 用 8 碼邀請碼（24 小時有效）邀朋友加入，沿用抱寶的做法。不用註冊，第一次開 app 用 Firebase 匿名登入，只要取一個暱稱。
- **成員不一定要有 app**：可以手動加一個「只有名字」的成員（例如長輩），一樣能分帳。之後那個人若裝了 app，可以「認領」這個名字。
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
- 尾數：除不盡的最小單位，依成員順序一人多分 1，保證加總等於總額，不會憑空多出或少掉 1 元。

### 行程
- 按天列出項目：時間（可空）、標題、地點、地圖連結、備註；同一天內可拖曳排序。
- 地點一鍵開啟 Google Maps / Apple 地圖。不嵌入地圖、不自動排路線。

### 發布
- 照抱寶的方式：改 `pubspec.yaml` 的版本號並推上 `main`，CI 就把 Android APK 和 iOS 開發簽章版發到 Firebase App Distribution。
- iPhone 朋友要先在 App Tester 註冊裝置，再把 UDID 加進 Apple team。

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
  date: string                      // "2026-12-21"
  time: string?                     // "09:30"
  title: string
  place: string?
  mapUrl: string?
  note: string?
  order: int

invites/{code}
  tripId: string
  expiresAt: Timestamp
```

安全規則：只有 `memberUids` 裡的人可以讀寫該旅程。加入旅程時，比照抱寶，用 `users/{uid}.joinCode` 證明這次加入是哪張邀請碼授權的，免費方案不需要 Cloud Functions。

## 計算規則

1. 每筆支出先在**原幣**按分法拆給每個人（含尾數分配）。
2. 再把付款人的總額、每人的分攤各自乘上 `rateToBase`，換成結算幣，四捨五入到結算幣的最小單位；換算後的尾數一樣依成員順序調整，確保「付款人換算總額 = 分攤換算總額」。
3. 淨額 = 總付 − 總分攤 + 已收還款 − 已付還款。所有人的淨額加總恆為 0。
4. 轉帳建議：每次讓欠最多的人轉給被欠最多的人，直到歸零。最多 n−1 筆。這不一定是理論上的最少筆數（那是 NP-hard 問題），但對 10 人以內的旅行團夠用。

## 待確認
- 「沒有 app 的成員」是否真的需要？目前先做，因為它不影響資料模型，成本低。
- iOS bundle id 目前是 `com.charliechen.sharetrip`，第一次在 Mac 上用 Xcode 自動簽章時，會註冊到 team V885M744V2 底下。
