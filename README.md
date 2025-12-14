# Monitoring Sampah

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-API%2015%2B-green.svg)](https://developer.android.com)
[![Build](https://img.shields.io/badge/Build-Gradle-blue.svg)](https://gradle.org)

Aplikasi Android untuk petugas yang mengelola incinerator pembakar sampah. Memungkinkan absen harian, menghidupkan incinerator, dan memantau lokasi incinerator untuk operasi pembakaran sampah yang efisien dan aman.

## ✨ Fitur Utama

- **Absen Harian**: Petugas dapat melakukan absen masuk dan keluar dengan lokasi GPS.
- **Kontrol Incinerator**: Menghidupkan dan memantau status incinerator pembakar sampah.
- **Peta Lokasi Incinerator**: Lihat titik lokasi incinerator di peta untuk navigasi dan verifikasi.
- **Pelacakan Operasi**: Catat waktu pembakaran dan status incinerator.
- **Notifikasi Sistem**: Alert untuk maintenance atau masalah teknis incinerator.

## 🚀 Quick Start

### Persyaratan
- JDK 11+
- Android SDK (API 15+)
- Android Studio (versi terbaru direkomendasikan)

### Langkah Instalasi

1. **Clone Repository**
   ```bash
   git clone https://github.com/your-repo/monitoring_sampah.git
   cd monitoring_sampah
   ```

2. **Buka di Android Studio**
   - File > Open > Pilih folder `monitoring_sampah`

3. **Konfigurasi**
   - Pastikan `local.properties` berisi `sdk.dir=/path/to/android/sdk`
   - Tambahkan `app/google-services.json` untuk Firebase (jika diperlukan)

4. **Build & Run**
   ```powershell
   .\gradlew.bat assembleDebug
   .\gradlew.bat installDebug
   ```

## 📱 UI & Alur Pengguna

<details>
<summary>🎯 Klik untuk Lihat Alur Pengguna</summary>

### Alur Utama
1. **Absen Masuk**: Petugas absen dengan verifikasi lokasi GPS.
2. **Navigasi ke Incinerator**: Lihat peta lokasi incinerator dan navigasi.
3. **Menghidupkan Incinerator**: Kontrol on/off incinerator dengan konfirmasi.
4. **Pantau Operasi**: Lihat status real-time incinerator dan log pembakaran.
5. **Absen Keluar**: Catat waktu selesai dan kirim laporan harian.

### Desain UI
- **Minimalis**: Fokus pada tugas operasional dengan antarmuka sederhana.
- **Responsif**: Mendukung berbagai ukuran layar dan orientasi.
- **Aksesibilitas**: Dukungan untuk screen reader dan kontrol suara.

</details>

## 🛠️ Development

### Struktur Proyek
```
monitoring_sampah/
├── app/
│   ├── src/main/
│   │   ├── java/          # Kode sumber Kotlin/Java
│   │   ├── res/           # Resource (layout, drawable, dll)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts   # Konfigurasi aplikasi
├── gradle/
└── README.md
```

### Testing
```powershell
# Unit Tests
.\gradlew.bat testDebugUnitTest

# Instrumentation Tests
.\gradlew.bat connectedDebugAndroidTest
```

### Arsitektur
- **MVVM**: Model-View-ViewModel untuk pemisahan concern.
- **Coroutines**: Untuk operasi asynchronous.
- **Room**: Database lokal untuk penyimpanan offline.

## 📞 Kontak

- **Maintainer**: Tim Riset SEA
- **Issues**: [GitHub Issues](https://github.com/your-repo/monitoring_sampah/issues)

## 📄 Lisensi

Distributed under the MIT License. See `LICENSE` for more information.

---

*Terima kasih telah menggunakan Monitoring Sampah! Mari bersama-sama jaga lingkungan bersih.* 🌍
