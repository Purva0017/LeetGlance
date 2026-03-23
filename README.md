# LeetGlance 🟧

A native Android home-screen widget that displays your LeetCode activity — contribution graph, streak, and solved stats — directly on your home screen, powered by LeetCode's GraphQL API.

---

## Preview

> Place a screenshot of your widget here once you have one.

---

## Features

- 📊 **Contribution Graph** — month-aware activity grid for the last 4 months, matching LeetCode's visual style with rounded cells, month-boundary column splitting, and centered month labels
- 🔥 **Streak Counter** — current solving streak displayed at a glance
- 🧩 **Solved Stats** — total, easy, medium, and hard problem counts
- 🔄 **Auto Refresh** — background data refresh every 3 hours via WorkManager
- 📦 **Offline Support** — last fetched data persisted via DataStore, widget stays visible even without internet
- ⚙️ **Easy Setup** — enter your LeetCode username once when placing the widget, done

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Widget Framework | Jetpack Glance |
| Graph Rendering | Android Canvas & Bitmap API |
| Networking | Retrofit + OkHttp |
| API | LeetCode GraphQL (`leetcode.com/graphql`) |
| Background Jobs | WorkManager |
| Local Storage | Jetpack DataStore |
| Config Screen | Jetpack Compose |

---

## How It Works

### Data Flow
```
LeetCode GraphQL API
        ↓
  Retrofit (OkHttp)
        ↓
  LeetCodeRepository  ──→  DataStore (cache)
        ↓
  CalendarParser
        ↓
  Canvas / Bitmap
        ↓
  Jetpack Glance Widget
```

### Contribution Graph Logic
- Shows exactly **120 days** ending on today
- Grid starts on the **Sunday on or before** the start date (matching LeetCode's layout)
- Week columns that span two months are **split into two columns** with a gap spacer between them — no day is suppressed or misattributed
- Month labels appear **below the grid**, centered on their column span
- Months spanning only 1 column have their label omitted to avoid overlap
- Grid is sized for the **proven worst case** (22 data columns + 4 gap spacers = 26 total slots) so cells are always the same size day-to-day

### Background Refresh
- Scheduled via `WorkManager` as a `PeriodicWorkRequest` repeating every **3 hours**
- Only runs when network is available (`NetworkType.CONNECTED` constraint)
- Starts automatically when the widget is placed, stops when the last widget is removed
- Survives app close, device reboot, and Android Studio disconnects

---

## Project Structure

```
app/src/main/
├── java/com/example/myapplication/
│   ├── api/
│   │   ├── Models.kt               # GraphQL request/response data classes
│   │   └── LeetCodeApiClient.kt    # Retrofit singleton
│   ├── data/
│   │   ├── LeetCodeRepository.kt   # API calls + DataStore caching
│   │   ├── CalendarParser.kt       # JSON → month-aware grid builder
│   │   └── RefreshWorker.kt        # WorkManager background job
│   ├── widget/
│   │   └── LeetCodeWidget.kt       # Glance UI + Canvas rendering + Receiver
│   └── ui/
│       └── WidgetConfigActivity.kt # Username entry screen (Compose)
└── res/
    ├── xml/widget_info.xml         # Widget metadata
    ├── drawable/ic_leetcode.png    # LeetCode logo
    └── values/
        ├── strings.xml
        └── themes.xml
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android device or emulator running API 26+

### Setup
1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/LeetGlance.git
   ```
2. Open in Android Studio
3. Let Gradle sync complete
4. Run on your device

### Adding the Widget
1. Long-press your home screen
2. Tap **Widgets**
3. Find **LeetGlance** and drag it to your home screen
4. Enter your LeetCode username in the config screen
5. Tap **Save & Add Widget**

---

## GraphQL Query

The app fetches data directly from LeetCode's GraphQL endpoint with no backend intermediary:

```graphql
query getUserProfile($username: String!) {
  allQuestionsCount { difficulty count }
  matchedUser(username: $username) {
    submissionCalendar
    submitStats {
      acSubmissionNum { difficulty count submissions }
      totalSubmissionNum { difficulty count submissions }
    }
    profile { ranking reputation }
    contributions { points }
  }
}
```

---

## License

MIT License — feel free to use, modify, and distribute.

---

## Author

Built by [Purva](https://leetcode.com/purva__017) — because LeetCode deserved a better mobile widget.