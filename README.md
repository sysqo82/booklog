# BookLog 📚

**BookLog** is a native Android application designed for bibliophiles to effortlessly catalog and manage their personal book collections. It combines the speed of barcode scanning with intelligent metadata enrichment to build a comprehensive digital library.

<img src="booklog.png" width="128" alt="BookLog Logo">

## Key Features

### 🔍 Effortless Cataloging
- **Barcode Scanner**: Quickly add books to your collection by scanning their ISBN barcodes using the integrated CameraX and Google ML Kit scanner.
- **Manual Search**: Search the Google Books and Open Library databases directly to find and add books manually.

### ✨ Intelligent Metadata Enrichment
- **Automatic Details**: The app automatically fetches high-quality cover art, synopses, and ISBN data.
- **Hybrid Source Logic**: Utilizes both Google Books and Open Library APIs to ensure the best possible data coverage.
- **Pseudonym Handling**: Smart matching logic that connects different author names (e.g., pen names vs. real names like Edward Marston/Keith Miles) to ensure your collection stays accurate.

### 🛠 Background Maintenance
- **Auto-Update Worker**: A background process runs every 12 hours to check for better cover images or more detailed descriptions, keeping your library up-to-date even when the app is closed.

### 📖 Collection Management
- **Offline Access**: Your entire collection is stored locally using a Room database, meaning you can browse your library without an internet connection.
- **Search & Filter**: Quickly find any book in your collection with a responsive search bar and author-based filtering.
- **Detailed View**: View full synopses and metadata for every book in an elegant, modern bottom sheet interface.

## How It Looks

BookLog features a clean, minimalist design following modern Android UI principles:
- **Main Dashboard**: A scrollable list of your books with high-resolution thumbnails and clear typography.
- **Interactive FAB**: A single-tap Floating Action Button to launch the scanner immediately.
- **Light & Airy**: A white-themed interface with light status bars for a modern look and feel.

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM / Repository Pattern
- **Local Storage**: Room Persistence Library
- **Networking**: Retrofit + OkHttp
- **Imaging**: Glide for smooth image loading and caching
- **Scanning**: CameraX + Google ML Kit Barcode Scanning
- **Background Tasks**: WorkManager for periodic enrichment

## Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 24+ (Min SDK)
- JDK 17

### Setup
1. **Clone the repository.**
2. **Open in Android Studio.**
3. **API Key (Optional)**: 
   Add `google.books.api.key=YOUR_KEY` to your `local.properties` file to avoid rate-limiting on Google Books requests.
4. **Run**: Deploy to your physical device or emulator.

---
*Developed for Android with ☕ and ❤️.*
