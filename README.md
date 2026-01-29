# Home Dashboard

A home dashboard application designed to be displayed as a wall calendar. Available as both a web app and an Android app.

## Project Structure

```
home-dashboard/
├── apps/
│   ├── web/          # React + Vite web application
│   └── android/      # Android (Kotlin + Jetpack Compose) application
└── README.md
```

## Web App

The web application is built with React, Vite, TypeScript, and Tailwind CSS. It integrates with Microsoft Graph API for calendar functionality.

### Prerequisites

- Node.js (see `apps/web/.nvmrc` for version)
- npm

### Development

```bash
cd apps/web
npm install
npm run dev
```

### Build

```bash
cd apps/web
npm run build
```

## Android App

The Android application is built with Kotlin and Jetpack Compose.

### Prerequisites

- Android Studio (Arctic Fox or later)
- JDK 17

### Development

1. Open `apps/android` in Android Studio
2. Sync Gradle files
3. Run on emulator or device

### Build

```bash
cd apps/android
./gradlew assembleDebug
```

## Features

- Calendar integration (Microsoft 365)
- Weather display
- Notes
- Budget tracking
- Clock and notifications
