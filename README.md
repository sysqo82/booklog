# BookLog - Minimalist Book Inventory Android App

A sleek, minimalist Android app for cataloguing and discovering books. Powered by Google Books API. No authentication required.

## Features

- **📱 Android-First**: Optimized for Android devices (API 21+)
- **🔍 Smart Search**: Full-text search across Google Books database
- **📸 Barcode Scanning**: One-tap ISBN scanner with device camera
- **∞ Infinite Grid**: Smooth infinite scrolling with lazy loading
- **🎨 Minimalist UI**: Clean dark-blue theme, zero clutter
- **⚡ Fast**: 50KB average API response, optimized for 4G/WiFi

## Quick Start

### Installation
```bash
npm install
```

### Development
```bash
npm start              # Start Expo dev server
npm run android        # Build & run on Android
npm run dev            # Reset cache + rebuild
```

### Production
```bash
npm run build:android  # Create production APK
```

## Requirements

- **Node.js** 16+
- **Android SDK** 21+ (via Android Studio or EAS)
- **Expo CLI** (`npm install -g expo-cli`)
- **Emulator** or **Physical Device** with USB Debugging

## Tech Stack

- **React Native** 0.71 - Cross-platform framework
- **Expo** 49 - Development & build platform
- **Google Books API** v1 - Book data
- **TypeScript** - Type safety
- **Async Storage** - Local persistence

## Architecture

```
src/
├── screens/         # Full-screen components
├── components/      # Reusable UI components
├── services/        # API integrations
└── utils/           # Helpers & constants
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `HomeScreen` | Main dashboard & layout |
| `SearchBar` | Search input + camera button |
| `BookGrid` | Infinite scroll grid |
| `BookTile` | Individual book card |
| `BarcodeScanner` | Camera interface |
| `FAB` | Floating action button |

## API Integration

- **Endpoint**: Google Books API v1
- **Key**: Provided (free public tier)
- **Rate Limit**: 1,000 requests/day
- **Features**: Full-text search, ISBN lookup

## Design

- **Primary Color**: `#1a2847` (Dark Blue)
- **Secondary**: `#ffffff` (White)
- **Layout**: 2-column grid, responsive
- **Icons**: Material Design via Expo Icons

## Security

- ✅ No login required
- ✅ Public API key (safe to share)
- ✅ No personal data collection
- ✅ HTTPS only
- ✅ Permissions: Camera only

## Building & Deployment

### Development APK
```bash
npm run build:android-preview
```

### Production APK (Signed)
```bash
npm run build:android
```

### Google Play Store
1. Build production APK
2. Create developer account
3. Upload to Play Console
4. Follow release checklist

## Customization

### App Name
`app.json` → `expo.name`

### Colors
`src/utils/theme.ts` → `colors` object

### Package Name
`app.json` → `android.package`

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Module errors | `npm install && npm start -- --reset-cache` |
| Camera not working | Grant permissions in Settings |
| Emulator slow | Use physical device instead |
| Port 8081 in use | Kill process: `lsof -i :8081; kill -9 <PID>` |

See [SETUP.md](SETUP.md) for detailed Android setup instructions.

## License

MIT - Free to use and modify

## Credits

- [Google Books API](https://developers.google.com/books)
- [React Native](https://reactnative.dev)
- [Expo](https://expo.dev)

---

**Ready to start?** → [SETUP.md](SETUP.md)  
**Need help?** → [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
