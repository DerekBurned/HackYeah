# 🗺️ Safety Map Application

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Google Maps](https://img.shields.io/badge/Google%20Maps-4285F4?style=for-the-badge&logo=google-maps&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)

**A community-driven mobile application for sharing and viewing safety reports on an interactive map**

[Features](#-features) • [Installation](#-installation--setup) • [Usage](#-usage) • [Architecture](#-architecture) • [Technologies](#-technologies) • [Contributing](#-contributing)

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Installation & Setup](#-installation--setup)
- [Usage](#-usage)
- [Architecture](#-architecture)
- [Technologies](#-technologies)
- [Permissions](#-permissions)
- [Future Enhancements](#-future-enhancements)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌟 Overview

**Safety Map** is an Android application designed to help users make informed decisions about their safety by providing a community-driven platform for sharing and viewing safety reports at various locations. The app leverages Google Maps to display interactive markers indicating safety levels in different areas, enabling travelers and locals to contribute to and benefit from collective safety awareness.

### Key Objectives

- 🛡️ **Enhance Public Safety** - Provide real-time safety information for various locations
- 🤝 **Community Collaboration** - Enable users to share and update safety reports
- 📍 **Location Intelligence** - Visualize safety data on an interactive map
- 🔒 **Privacy-First** - Anonymous reporting with Firebase authentication

---

## ✨ Features

### 🗺️ Interactive Map

- **Google Maps Integration** with full navigation support
- Multiple map types: Standard, Satellite, Hybrid, Terrain
- Custom zoom controls and location centering
- Smooth map interactions and gestures

### 📍 Safety Reports

- **Add Reports** - Long-press on any location to create a safety report
- **View Reports** - Tap on markers to see detailed information
- **Clustered Markers** - Automatically groups nearby reports for better visibility
- **Community Voting** - Rate and validate existing reports
- **Real-time Sync** - Instant updates across all users via Firebase

### 🔍 Search & Navigation

- **Location Search** powered by Google Places API
- Search for cities, addresses, and points of interest
- **Auto-location** - Automatically detect and center on your current position
- View nearby safety reports instantly

### 💬 User Interface

- **Bottom Sheet** - View all reports in the current area as a list
- **Dialog Windows** - Interactive prompts for adding reports, enabling GPS, and more
- **Intuitive Controls** - Easy-to-use interface with minimal learning curve
- **Responsive Design** - Optimized for various Android screen sizes

---

## 📥 Installation & Setup

### Prerequisites

- Android device running **Android 12.0 (API 32)** or higher
- Active internet connection
- Location services enabled (optional but recommended)

### Step 1: Download the APK

Download the latest APK file from this git repository "Know Where You Go.apk".

### Step 2: Enable Installation from Unknown Sources

Since this app is not from Google Play Store, you need to allow installation from unknown sources:

#### For Android 8.0 and above:

1. Open **Settings** on your Android device
2. Navigate to **Apps & notifications** (or **Apps**)
3. Tap on **Advanced** → **Special app access**
4. Select **Install unknown apps**
5. Choose your **browser** or **file manager** (whichever you use to download the APK)
6. Toggle **Allow from this source** to ON

#### For Android 7.1 and below:

1. Open **Settings** on your Android device
2. Navigate to **Security** (or **Lock screen and security**)
3. Find **Unknown sources** option
4. Toggle it to **ON**
5. Confirm the warning by tapping **OK**

### Step 3: Install the Application

1. Locate the downloaded APK file (usually in your **Downloads** folder)
2. Tap on the APK file to begin installation
3. Review the permissions required by the app
4. Tap **Install** to proceed
5. Wait for the installation to complete
6. Tap **Open** to launch the app, or **Done** to exit

### Step 4: Grant Required Permissions

When you first open the app, you'll be asked to grant the following permissions:

#### Essential Permissions:

- **📍 Location** - Required to show your current position and nearby reports
  - Tap **Allow** → Choose **While using the app** or **Always**
  
- **🌐 Internet** - Required to load maps and sync reports with Firebase
  - Granted automatically

#### Optional Permissions:

- **🗺️ Fine Location** - Provides more accurate positioning on the map
  - Tap **Allow** for better experience

> **Note:** You can change these permissions later in your device's Settings → Apps → Safety Map → Permissions

###  Initial Setup:

1. **Launch the app** - Open Safety Map from your app drawer
2. **Wait for Firebase initialization** - The app will authenticate anonymously
3. **Accept location prompt** - Allow the app to access your location
4. **Enable GPS** (if prompted) - For optimal functionality
5. **Start exploring** - The map will load with existing safety reports

### Troubleshooting

**App won't install?**
- Ensure you have enough storage space (minimum 50MB free)
- Check that your Android version meets the minimum requirement (Android 6.0+)
- Try downloading the APK again if it's corrupted

**Permissions not working?**
- Go to Settings → Apps → Safety Map → Permissions
- Manually enable required permissions

**Map not loading?**
- Check your internet connection
- Ensure Google Play Services is installed and updated
- Restart the app

---

## 🚀 Usage

### Adding a Safety Report

1. **Long-press** on any location around you (100km max) on the map
2. A dialog will appear prompting you to add a report
3. Fill in the safety information
4. Tap one from four buttons to share your report
5. The report will appear as a marker on the map

### Viewing Safety Reports

1. **Tap on any marker** to view the safety report details
2. Read the information provided by other users
3. **Upvote or downvote** the report if you agree with the assessment


### Searching for Locations

1. Tap on the **search bar** at the top of the screen
2. Enter a city name, address, or place
3. Select from the suggested results
4. The map will center on your chosen location

### Viewing Nearby Reports

1. Tap the **list icon** to open the Bottom Sheet
2. Browse all safety reports in the current map view
3. Tap on any report to center the map on that location
4. **Swipe down** to close the list

### Changing Map Type

1. Tap the **layers icon** (usually in the top-right corner)
2. Select your preferred map type:
   - 🗺️ Standard
   - 🛰️ Satellite
   - 🏞️ Hybrid
   - ⛰️ Terrain

---

## 🏗️ Architecture

### Design Pattern

The application follows the **MVVM (Model-View-ViewModel)** architecture pattern

```


### Key Components

- **MapsActivity** -  hosting the map fragment
- **MapHelper** - Handles map interactions and marker display
- **SafetyReportRepository** - Manages data operations with Firebase
- **LocationManager** - Handles location services and permissions
- **ClusterManager** - Groups nearby markers for better UX

---

## 🛠️ Technologies

### Frontend

| Technology | Purpose |
|------------|---------|
| **Kotlin** | Primary programming language |
| **View Binding** | Type-safe UI component access |
| **Material Design** | Modern Android UI components |
| **Coroutines** | Asynchronous programming |

### Google Services

| Service | Purpose |
|---------|---------|
| **Google Maps SDK** | Interactive map display |
| **Google Places API** | Location search functionality |
| **Location Services** | User location detection |
| **Maps Utils** | Marker clustering |

### Backend & Database

| Service | Purpose |
|---------|---------|
| **Firebase Authentication** | Anonymous user authentication |
| **Cloud Firestore** | Real-time database for safety reports |
| **Firebase Analytics** | Usage tracking (optional) |

### Build Tools

- **Gradle** - Build automation
- **Android SDK** - API Level 32+
- **Target SDK** - API Level 34

---

## 🔐 Permissions

The app requires the following permissions:

### Required Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### Optional Permissions

```xml
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

> **Privacy Notice:** All location data is used only for displaying your position on the map and finding nearby reports. No personal information is collected or stored.

---

## 🔮 Future Enhancements

### Planned Features

- [ ] **Report Categories** - Classify reports (theft, assault, lighting issues, etc.)
- [ ] **Photo Attachments** - Add images to safety reports
- [ ] **Push Notifications** - Alerts for nearby incidents
- [ ] **Statistics Dashboard** - Safety trends and analytics
- [ ] **Offline Mode** - Download maps for offline use
- [ ] **User Profiles** - Track contributions and reputation
- [ ] **Report Expiration** - Automatic removal of outdated reports
- [ ] **Multi-language Support** - Localization for global users
- [ ] **Integration with Authorities** - Direct reporting to local police
- [ ] **Heat Map View** - Visualize safety density across areas

---

## 🤝 Contributing

We welcome contributions from the community! Here's how you can help:

### How to Contribute

1. **Fork** the repository
2. **Create** a new branch (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'Add some AmazingFeature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. **Open** a Pull Request

### Guidelines

- Follow Kotlin coding conventions
- Write clear commit messages
- Add comments for complex logic
- Test your changes thoroughly
- Update documentation if needed

---



## 👥 Authors

- **Danylo Lukianiuk** - *Student* - [DerekBurned](https://github.com/DerekBurned)

---

## 🙏 Acknowledgments

- Google Maps Platform for mapping services
- Firebase for backend infrastructure
- The Android open-source community
- All contributors who help improve this app

---

## 📞 Support

If you encounter any issues or have questions:



- 📧 **Contact** - danillolka2@gmail.com

---

<div align="center">



⭐ Star this repository if you find it helpful!

</div>
