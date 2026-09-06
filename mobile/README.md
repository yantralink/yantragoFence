# YantraGO Mobile App

Flutter mobile app for the YantraGO machine management platform.

## Tech Stack

- **Flutter 3.10+** / Dart 3.0+
- **Riverpod** for state management
- **GoRouter** for declarative routing with auth guards
- **Dio** for HTTP with auth interceptors
- **STOMP WebSocket** for real-time machine state updates
- **Flutter Secure Storage** for JWT token storage
- **Google Maps Flutter** for GPS visualization

## Setup

```powershell
cd mobile
flutter pub get
flutter run
```

## Build

```powershell
flutter build apk --release       # Android
flutter build ios --release        # iOS
```

## Structure

```
lib/
  main.dart, app.dart
  core/           # Config, network, auth, storage, utils
  features/       # Feature-based modules (auth, dashboard, machines, etc.)
  models/         # Data models
  routing/        # GoRouter configuration
```

## Features

- **Auth**: Login, logout, token refresh, auth guards
- **Dashboard**: Machine status overview, battery/voltage/GSM widgets
- **Machines**: List, detail, ON/OFF control
- **Map**: GPS locations on Google Maps
- **Commands**: Command status tracking
- **Alerts**: Alert notifications
- **Settings**: App and machine settings
- **History**: Activity history
- **Profile**: User profile
