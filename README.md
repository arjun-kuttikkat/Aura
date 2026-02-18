<div align="center">
  <img src="logo.png" alt="Aura Logo" width="200"/>
  
  # Aura
  
  **P2P marketplace with trust & escrow**
  
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://www.android.com/)
  [![Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-orange.svg)](https://developer.android.com/jetpack/compose)
  [![Solana](https://img.shields.io/badge/Solana-Blockchain-purple.svg)](https://solana.com/)
</div>

## 📱 About

Aura is a peer-to-peer marketplace Android application built with Kotlin and Jetpack Compose. It enables secure transactions using Solana blockchain with built-in escrow functionality, ensuring trust and security for both buyers and sellers.

## ✨ Features

- 🏪 **Marketplace**: Browse and discover listings in a beautiful grid layout
- 📝 **Create Listings**: List items for sale with photos, descriptions, and pricing
- 🔐 **Escrow Payments**: Secure transactions with Solana blockchain escrow system
- 💼 **Wallet Integration**: Connect and manage Solana wallets seamlessly
- 📷 **QR Code Scanning**: Scan QR codes for quick meetups and verification
- ✅ **Item Verification**: Verify items before completing transactions
- 🎁 **Rewards System**: Earn rewards for successful trades
- 👤 **User Profile**: Manage your profile and trading history
- ⚙️ **Settings**: Customize your app experience

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Compose Navigation
- **Blockchain**: Solana
- **Image Loading**: Coil
- **QR Code**: ZXing
- **Camera**: CameraX

## 📋 Requirements

- Android Studio Hedgehog | 2023.1.1 or later
- JDK 11 or later
- Android SDK 26+ (Android 8.0+)
- Gradle 8.0+

## 🚀 Getting Started

### Prerequisites

1. Clone the repository:
```bash
git clone https://github.com/yourusername/aura-android.git
cd aura-android
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Build and run the app on an emulator or physical device

### Building the App

```bash
./gradlew assembleDebug
```

For release build:
```bash
./gradlew assembleRelease
```

## 📁 Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/aura/app/
│   │   │   ├── data/           # Data models and backend
│   │   │   ├── model/          # Domain models
│   │   │   ├── navigation/     # Navigation setup
│   │   │   ├── ui/
│   │   │   │   ├── components/ # Reusable UI components
│   │   │   │   ├── screen/     # Screen composables
│   │   │   │   └── theme/      # App theming
│   │   │   └── wallet/         # Wallet integration
│   │   ├── res/                # Resources (drawables, strings, etc.)
│   │   └── AndroidManifest.xml
│   └── test/                   # Unit tests
└── build.gradle.kts
```

## 🔧 Configuration

### Wallet Setup

The app uses Solana wallet integration. Make sure you have:
- A Solana wallet configured
- Mobile Wallet Adapter (MWA) support (when connecting to real wallets)

### Environment Variables

Currently using mock backend. For production, configure:
- Solana RPC endpoint
- Wallet adapter settings
- API endpoints

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **Your Name** - *Initial work*

## 🙏 Acknowledgments

- Solana Foundation for blockchain infrastructure
- Android Jetpack team for Compose framework
- Open source community

## 📞 Support

For issues and questions, please open an issue on GitHub.

---

<div align="center">
  Made with ❤️ using Kotlin and Jetpack Compose
</div>
