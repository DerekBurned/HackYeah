# Know Before You Go 

A community-powered Android travel safety app that lets users report and discover safety conditions in cities around the world. Built with Google Maps, Firebase, and Room for offline-first functionality.

---

## Features

- **City Safety Search** — Search for any city and instantly see crowd-sourced safety reports on the map
- **Safety Zone Reporting** — Long-press anywhere on the map to submit a safety report with a coverage radius, safety level, and description
- **Four Safety Levels** — Safe, Be Cautious, Unsafe, and Dangerous, each color-coded on the map
- **Interactive Map** — Clustered markers, colored overlay circles, zoom/pan controls, and multiple map types (Normal, Satellite, Terrain, Hybrid)
- **Community Voting** — Upvote or downvote reports to surface the most reliable information
- **Reports Bottom Sheet** — View and sort all nearby reports by danger level, date, or vote count
- **Offline Support** — Reports are cached locally via Room so the app works without an internet connection
- **Anonymous Auth** — Automatic anonymous Firebase authentication; no sign-up required

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Maps | Google Maps SDK, Maps Utils (clustering) |
| Places Autocomplete | Google Places API |
| Backend / Auth | Firebase Firestore, Firebase Authentication |
| Local Cache | Room (SQLite) |
| Architecture | MVVM (ViewModel + LiveData + Repository) |
| UI | Material Design 3, ViewBinding, BottomSheetDialog |
| Geocoding | Android Geocoder API |
| Location | Fused Location Provider |

---

## Project Structure

```
app/src/main/java/
├── Activities/
│   ├── MainActivity.kt          # City search entry screen
│   └── MapsActivity.kt          # Main map screen
├── Adapters/
│   └── SafetyReportAdapter.kt   # RecyclerView adapter for reports list
├── ViewModel/
│   └── SafetyViewModel.kt       # MVVM ViewModel
├── com/example/travelnow/
│   ├── MyApplication.kt         # Application class, ViewModel singleton
│   └── database/
│       └── AppDatabase.kt       # Room database setup
├── helpers/
│   ├── DialogManager.kt         # All dialog/bottom sheet builders
│   ├── LocationHelper.kt        # GPS, geocoding, distance utilities
│   ├── MapManagerHelper.kt      # Map rendering, clusters, circles
│   ├── PlacesSearchHelper.kt    # Google Places autocomplete
│   ├── ReportSorter.kt          # Sort logic for reports
│   └── SafetyReportClusterRenderer.kt  # Custom cluster marker rendering
├── local/
│   ├── SafetyReportDao.kt       # Room DAO
│   └── SafetyReportEntity.kt    # Room entity
├── models/
│   ├── SafetyLevel.kt           # Enum: SAFE, BE_CAUTIOUS, UNSAFE, DANGEROUS
│   ├── SafetyReport.kt          # Firestore/domain model
│   ├── SafetyReportClusterItem.kt
│   └── SortOptions.kt
├── repository/
│   ├── ISafetyRepository.kt
│   └── SafetyRepository.kt      # Firestore + Room data layer
└── utils/
    └── GeoUtils.kt              # Haversine distance, geohash encoding
```

---

## Setup

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK API 32+
- A Google Cloud project with the following APIs enabled:
  - Maps SDK for Android
  - Places API
- A Firebase project with:
  - Firestore Database
  - Anonymous Authentication enabled

### Configuration

1. **Clone the repository**

2. **Add your `google-services.json`** to `app/` (from Firebase Console → Project Settings → Android app)

3. **Set your API keys** in `local.properties`:
   ```
   MAPS_API_KEY=your_google_maps_api_key
   PLACES_API_KEY=your_google_places_api_key
   ```

4. **Firestore Security Rules** — The app uses anonymous authentication. Recommended rules:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /safety_reports/{reportId} {
         allow read: if request.auth != null;
         allow create: if request.auth != null;
         allow update: if request.auth != null;
         allow delete: if request.auth.uid == resource.data.userId;
       }
       match /votes/{voteId} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

5. **Build and run** on a device or emulator with API 32+

---

## How It Works

### Submitting a Report
1. Open the app and search for a city
2. On the map, long-press any location within 100km of your current position
3. Choose a safety level, adjust the coverage radius, and write a comment
4. Tap the safety level button to submit

### Viewing Reports
- Colored circles on the map show reported safety zones
- Tap a marker to see report details and vote on it
- Tap the orange FAB (ℹ) to open a scrollable list of all nearby reports, sortable by danger level, time, or votes

### Geohash-Based Querying
Reports are stored with a [geohash](https://en.wikipedia.org/wiki/Geohash) field. Firestore range queries on the geohash prefix are used to efficiently fetch reports within a radius, with a secondary Haversine distance filter applied client-side.

---

## Safety Levels

| Level | Color | Meaning |
|---|---|---|
| ✅ Safe | Green | Area is generally safe |
| ⚠️ Be Cautious | Yellow | Minor concerns; stay alert |
| ⚡ Unsafe | Orange | Notable safety risks present |
| ✕ Dangerous | Red | Serious safety concerns; avoid if possible |

---

## Offline Behavior

- All fetched reports are cached in a local Room database
- Reports submitted while offline are saved locally with `syncedWithFirebase = false`
- On next launch, unsynced reports are automatically pushed to Firestore
- Reports older than 30 days are automatically purged from the local cache

---

## License

This project is for educational and personal use. 
