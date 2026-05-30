# Nimura: 瞬き検出×姿勢推定による健康推進Androidアプリ

スマートフォンのインカメラとIMUセンサーを用いて、瞬き頻度と姿勢（首の前傾角度）を連続的にモニタリングし、複合疲労スコアを算出するAndroidアプリ。ナッジ理論に基づく段階的な介入通知により、ユーザーの健康行動変容を促す。修士論文のシステムとして開発し、ユーザビリティと行動変容効果をRCT（ランダム化比較試験）で評価する。

---

## 研究の背景

### 先行研究

| 研究領域 | 知見 | 出典 |
|---|---|---|
| 瞬きと疲労 | 画面注視時に瞬き頻度が約66%減少（15-20回/分 → 5-7回/分） | Cleveland Clinic; Cognitive demand, digital screens and blink rate (2015) |
| EAR (Eye Aspect Ratio) | 閉眼検出の標準指標。閾値 < 0.2 | Soukupova & Cech (2016) |
| PERCLOS | FHWA/NHTSAが検証した眠気の生理指標。0.15で疲労開始、0.3以上で眠気 | Wierwille (1994); PERCLOS最適化研究 (ACM, 2022) |
| 瞬き+姿勢の融合 | 瞬きに姿勢情報を融合しドライバー眠気推定のF1スコアが0.358→0.523に向上 | パナソニック技報 (2021) |
| Text Neck | 首の前傾15度で約27lbs、30度で40lbs、45度で49lbsの頚椎負荷 | Hansraj (2014) |

### 本研究の新規性

1. **瞬き×姿勢の複合指標をスマホ単体で実現** — 先行研究は専用デバイス・車載カメラが中心
2. **顔ランドマーク + IMUセンサーの融合**による姿勢推定 — 肩が映らないスマホ利用環境に対応
3. **バックグラウンド連続モニタリング**による自然な使用環境下でのデータ取得
4. **ナッジ理論に基づく段階的介入**と行動変容効果のRCTによる検証

---

## システム構成

### 技術スタック

| 項目 | 技術 | バージョン |
|---|---|---|
| 言語 / UI | Java + XML Layout | - |
| 顔ランドマーク検出 | MediaPipe Face Landmarker | tasks-vision 0.10.14 |
| IMUセンサー | Android Accelerometer API | - |
| ローカルDB | Room (SQLite) | 2.6.1 |
| グラフ描画 | MPAndroidChart | 3.1.0 |
| カメラ | CameraX | 1.3.4 |
| ターゲット | Android 8.0+ (API 26) | - |

### アーキテクチャ

```
入力層
├── インカメラ (CameraX) → MediaPipe Face Landmarker (478点)
└── 加速度計 (IMU) → DevicePostureHelper

検出層
├── 瞬き検出: EAR (Eye Aspect Ratio) + 2フレーム連続判定
├── 頭部前傾: Face Meshの顔比率からPitch角度を推定
├── 頭部傾き: 左右目の角度からRoll角度を算出
└── スマホ傾き: IMU加速度計から端末のtilt角度を取得

分析層
├── BlinkTracker: 瞬き頻度(回/分)、PERCLOS、ベースラインからの低下率
├── 姿勢統合: 首前傾角度 = (90 - スマホ傾き) + 頭部Pitch × 0.3
└── FatigueScoreEngine: 複合疲労スコア (0-100)

介入層
├── NudgeEngine: 段階的通知 (5分クールダウン)
└── NudgeNotificationManager: プッシュ通知 + 応答記録

記録層
├── Room DB: sessions, measurements, nudge_logs, daily_summaries
└── CsvExporter: 全テーブルCSV出力
```

---

## 検出アルゴリズム

### 瞬き検出

MediaPipe Face Landmarkerの478点の顔ランドマークからEAR (Eye Aspect Ratio) を算出する。

```
EAR = (|p2-p6| + |p3-p5|) / (2 × |p1-p4|)
```

| | p1 (外側) | p2 (上1) | p3 (上2) | p4 (内側) | p5 (下2) | p6 (下1) |
|---|---|---|---|---|---|---|
| 左目 | 33 | 160 | 158 | 133 | 153 | 144 |
| 右目 | 362 | 385 | 387 | 263 | 373 | 380 |

距離計算はピクセルスケール（正規化座標 × 画像解像度）で行い、アスペクト比の歪みを補正する。

**瞬き判定条件:**
- EAR < 閾値（デフォルト0.2、個人キャリブレーション対応）
- **2フレーム連続**で閾値以下を維持（ノイズによる誤検出の防止）
- 閉眼状態から開眼状態への遷移を1回の瞬きとしてカウント

**個人キャリブレーション（対応済み）:**
```
閾値 = 閉眼EAR中央値 + 0.5 × (開眼EAR中央値 - 閉眼EAR中央値)
```

### 姿勢推定

2つのセンサーデータを融合して首の前傾角度を推定する。

**1. 頭部Pitch角度（Face Meshから）:**
- 額(index 10)・鼻先(index 1)・顎(index 152)の位置関係から推定
- 上顔面比率 = (鼻先Y - 額Y) / (顎Y - 額Y)
- 下を向くと上顔面比率が増加 → Pitch角度に変換

**2. スマホ傾き角度（IMU加速度計から）:**
- 加速度センサーのx,y,z値から端末の傾き角度を算出
- 90度 = 垂直（良い姿勢）、0度 = 水平（下を向いている）

**首前傾角度の統合:**
```
首前傾角度 = (90 - スマホ傾き角度) + max(0, -頭部Pitch) × 0.3
```

**頭部Roll角度（Face Meshから）:**
- 左目外側(index 33)と右目外側(index 263)を結ぶ線の水平からの角度
- 頭が左右に傾いている度合いを検出

---

## 疲労スコアの算出

### 入力指標と閾値（先行研究に基づく）

| 指標 | 正常 | 疲労 | 出典 |
|---|---|---|---|
| 瞬き頻度 | ≥ 15回/分 | ≤ 7回/分 | Cleveland Clinic; 画面使用時66%減少 |
| PERCLOS | < 0.15 | ≥ 0.3 | FHWA/NHTSA; ACM 2022 |
| 首前傾角度 | ≤ 15度 | ≥ 45度 | Hansraj (2014) Text Neck研究 |
| 頭部Roll | ≤ 5度 | ≥ 20度 | - |

### スコア算出式

```
目の疲労スコア (0-100) = max(瞬き頻度スコア, PERCLOSスコア)
姿勢疲労スコア (0-100) = max(首前傾スコア, 頭部Rollスコア)
複合疲労スコア (0-100) = α × 目の疲労 + β × 姿勢疲労
```

- α, β: 重み係数（初期値 各0.5、予備実験で決定）
- 直近5回の評価結果の移動平均を使用

### ナッジ介入の段階

| スコア | 段階 | 介入内容 |
|---|---|---|
| 0-30 | 良好 | 介入なし（記録のみ） |
| 31-60 | 軽度疲労 | 穏やかな通知（「少し目を休めましょう」） |
| 61-80 | 中度疲労 | 具体的なアクション提案（20-20-20ルール、ストレッチ） |
| 81-100 | 重度疲労 | 強い休憩推奨 + 休憩タイマー提示 |

- ナッジ通知のクールダウン: 最低5分間
- 対照群モード: 通知を抑制するがデータは記録

---

## モニタリングの仕組み

### 動作フロー

```
[0:00] 計測開始 → Foreground Service起動、カメラON
       ↓
[0:00 - 2:00] BASELINEフェーズ
       カメラ常時稼働、全フレームをMediaPipeで解析（~10fps）
       個人の正常な瞬き頻度をベースラインとして学習
       1分ごとにデータ記録（ナッジは出さない）
       ↓
[2:00] ベースライン確立 → MONITORINGフェーズへ移行
       ↓
[2:00+] 連続モニタリング
       1分ごとに評価:
         - 60秒間の瞬き頻度・PERCLOS・姿勢を集計
         - ベースラインからの低下率を計算
         - 複合疲労スコアを算出
         - 閾値超過ならナッジ通知を発行
         - ユーザーのナッジへの応答（実行/あとで/無視）を記録
```

### バックグラウンド動作

- **Foreground Service + CameraX** でバックグラウンドカメラ動作
- 常駐通知「Nimura - 計測中」を表示（Android要件）
- ステータスバーに緑のカメラインジケータ表示（Android 12以降のOS仕様）
- ユーザーは計測中に他のアプリ（YouTube、SNS等）を自由に使用可能
- バッテリー残量15%以下で自動停止

---

## 画面構成

| 画面 | 機能 |
|---|---|
| ホーム | 疲労スコア（大）、瞬き頻度・姿勢スコア（小）、計測開始/停止ボタン。計測中は10秒ごとに自動更新 |
| 履歴 | MPAndroidChartで日/週/月の疲労スコアトレンドを表示。閾値ライン(30/60/80)付き |
| 設定 | ナッジ通知ON/OFF、対照群モード切替、被験者ID入力、CSVエクスポート |
| 結果 | ナッジ通知タップで表示。疲労スコア + 内訳 + 改善提案 |

ナビゲーション: Bottom Navigation（ホーム / 履歴 / 設定）

---

## データモデル（Room DB）

### sessions

| カラム | 型 | 説明 |
|---|---|---|
| id | long (PK) | セッションID |
| start_time | long | 計測開始時刻 (Unix ms) |
| end_time | long | 計測終了時刻 |
| is_active | boolean | 計測中フラグ |

### measurements

| カラム | 型 | 説明 |
|---|---|---|
| id | long (PK) | 計測ID |
| session_id | long (FK) | セッションID |
| timestamp | long | 記録時刻 |
| blink_rate | float | 瞬き頻度 (回/分) |
| ear_value | float | EAR値 |
| perclos | float | PERCLOS値 (0.0-1.0) |
| head_tilt_angle | float | 首前傾角度 (度) |
| shoulder_tilt_angle | float | 頭部Roll角度 (度) |
| eye_fatigue_score | int | 目の疲労スコア (0-100) |
| posture_fatigue_score | int | 姿勢疲労スコア (0-100) |
| composite_fatigue_score | int | 複合疲労スコア (0-100) |

### nudge_logs

| カラム | 型 | 説明 |
|---|---|---|
| id | long (PK) | ナッジID |
| measurement_id | long (FK) | トリガーとなった計測ID |
| timestamp | long | 通知時刻 |
| nudge_level | String | "mild" / "moderate" / "severe" |
| nudge_content | String | 通知内容テキスト |
| user_response | String | "accepted" / "dismissed" / "ignored" / "control_group" |
| response_time_ms | long | 応答までの時間 (ms) |

### daily_summaries

| カラム | 型 | 説明 |
|---|---|---|
| id | long (PK) | サマリーID |
| date | String | 日付 (yyyy-MM-dd) |
| avg_fatigue_score | float | 平均疲労スコア |
| avg_blink_rate | float | 平均瞬き頻度 |
| avg_posture_score | float | 平均姿勢スコア |
| total_nudges | int | ナッジ送信回数 |
| nudge_accept_rate | float | ナッジ応答率 |
| total_measurement_minutes | int | 総計測時間 (分) |

---

## 評価実験の設計

### 実験構成

| Phase | 期間 | 内容 |
|---|---|---|
| Phase 0 | 約1週間 | **予備実験**: 5-10名で複合スコアの重み(α,β)とナッジ閾値を決定 |
| Phase 1 | 1週間 | **ベースライン期**: 全被験者にアプリ（通知なし）を使用してもらいデータ収集 |
| Phase 2 | 2週間 | **介入期**: 介入群にはナッジ通知あり、対照群は記録のみ |
| Phase 3 | 1-2日 | **事後評価**: アンケート + データ分析 |

### 群間比較（RCT）

被験者30-40名をランダムに2群に割り付け:

| | 介入群 (15-20名) | 対照群 (15-20名) |
|---|---|---|
| 瞬き + 姿勢の計測 | あり | あり |
| ナッジ通知 | **あり** | **なし** |
| 履歴画面 | 表示 | 非表示 |
| データ記録 | すべて記録 | すべて記録 |

対照群には「計測のみのアプリ」と説明し、ナッジ機能の存在を伝えない。アプリの設定画面で「対照群モード」を切り替え可能。

### 予備実験

| 項目 | 内容 |
|---|---|
| 目的 | 複合スコアの重み(α,β)、ナッジ閾値を決定 |
| 被験者 | 5-10名（本実験とは別の参加者） |
| 手順 | 30分間のスマホ使用中にアプリでデータ取得 + 5分ごとにVAS(主観疲労度)を記入 |
| 分析 | VASと各指標の相関分析 → 重みと閾値を決定 |

### 評価指標

**1. 行動変容の客観データ（アプリから自動収集）:**
- ナッジ応答率（「実行する」を押した割合）
- 疲労スコアの推移（Phase 1 vs Phase 2）
- 休憩行動の頻度

**2. 主観評価アンケート:**
- SUS (System Usability Scale) — ユーザビリティの標準尺度
- 健康意識の変化 — 独自質問紙（リッカート5段階）
- ナッジの有用性 — 介入群のみ

**3. 群間比較の統計分析:**
- 介入群 vs 対照群の疲労スコア変化（t検定 or Mann-Whitney U検定）
- 健康意識スコアの群間比較

---

## プロジェクト構成

```
app/src/main/java/com/nimura/app/
├── MainActivity.java              # メイン画面 + BottomNavigation
├── data/                          # Room DB
│   ├── NimuraDatabase.java        # DBシングルトン
│   ├── entity/                    # Session, Measurement, NudgeLog, DailySummary
│   ├── dao/                       # 各テーブルのDAO
│   └── converter/                 # TypeConverter
├── detection/                     # センサー入力
│   ├── CameraManager.java         # CameraX + 画像変換
│   ├── FaceLandmarkerHelper.java  # MediaPipe顔検出 + EAR + 頭部角度
│   ├── DevicePostureHelper.java   # IMU加速度計による端末傾き検出
│   └── DetectionResult.java       # 検出結果POJO
├── scoring/                       # スコア算出
│   ├── BlinkTracker.java          # 瞬きカウント + PERCLOS + ベースライン追跡
│   ├── FatigueScoreEngine.java    # 複合疲労スコア算出
│   ├── FatigueLevel.java          # GOOD/MILD/MODERATE/SEVERE enum
│   └── SamplingController.java    # BASELINE→MONITORING制御
├── service/                       # バックグラウンドサービス
│   ├── MonitoringService.java     # Foreground Service (全体オーケストレーション)
│   └── BatteryMonitor.java        # バッテリー監視
├── nudge/                         # ナッジ介入
│   ├── NudgeEngine.java           # 介入判定 + クールダウン
│   ├── NudgeNotificationManager.java
│   ├── NudgeContent.java          # 通知文面
│   └── NudgeResponseReceiver.java # 通知応答受信
├── ui/                            # 画面
│   ├── HomeFragment.java
│   ├── HistoryFragment.java
│   └── SettingsFragment.java
├── settings/
│   └── AppPreferences.java        # SharedPreferences
└── export/
    └── CsvExporter.java           # 全テーブルCSV出力
```

---

## ビルドと実行

### 必要環境

- Android Studio
- JDK 17
- Android SDK (API 34)

### ビルド

```bash
./gradlew assembleDebug
```

APKの出力先: `app/build/outputs/apk/debug/app-debug.apk`

### 実機へのインストール

Android Studioの「Run」ボタン、または:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 動作要件

- Android 8.0 (API 26) 以上
- インカメラ搭載端末
- インターネット接続不要（全処理オンデバイス）

---

## 参考文献

- Cleveland Clinic. "Blinking Causes." https://my.clevelandclinic.org/health/articles/blinking
- Soukupova, T., & Cech, J. (2016). "Eye Blink Detection Using Facial Landmarks."
- Wierwille, W. W. (1994). "PERCLOS: A Valid Psychophysiological Measure of Alertness." FHWA-MCRT-98-006.
- PERCLOS Fatigue Driving Detection System Based on Perclos Algorithm Optimization (ACM, 2022).
- パナソニック技報 Vol.67 No.1 (2021). "瞬き・姿勢情報を融合した高精度ドライバー眠気推定."
- Hansraj, K. K. (2014). "Assessment of Stresses in the Cervical Spine Caused by Posture and Position of the Head." Surgical Technology International, 25, 277-279.
- Cognitive demand, digital screens and blink rate (ScienceDirect, 2015).

---

## ライセンス

- MediaPipe: Apache License 2.0
- MPAndroidChart: Apache License 2.0
- Room, CameraX: Apache License 2.0
