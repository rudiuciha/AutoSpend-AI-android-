# 🤖 AutoSpend AI - Android

Aplikasi Android untuk mencatat pengeluaran **secara otomatis** dari notifikasi HP.
Mendukung GoPay, OVO, DANA, BCA, Mandiri, BNI, BRI, dan semua SMS bank.

---

## ✨ Fitur

- 📩 **Baca Notifikasi Otomatis** — tidak perlu input manual
- 🤖 **AI Parser** — menggunakan Claude AI untuk ekstrak nominal, merchant, kategori
- 📊 **Dashboard** — total pengeluaran, breakdown kategori, filter minggu/bulan
- 📋 **Riwayat** — semua transaksi dengan fitur search
- 💾 **Database Lokal** — data tersimpan di HP, privasi terjaga
- 🔌 **Fallback Regex** — tetap bisa parse tanpa internet/API key

---

## 🚀 Cara Build APK via GitHub (tanpa Android Studio)

### 1. Upload ke GitHub
```
1. Buat repo baru di github.com
2. Upload semua file ini ke repo
3. GitHub Actions otomatis build APK
```

### 2. Download APK
```
1. Buka tab "Actions" di repo GitHub
2. Klik workflow run terbaru
3. Scroll ke bawah → "Artifacts"
4. Download "AutoSpend-AI-Debug"
5. Install APK di HP ✅
```

---

## ⚙️ Setup di HP setelah Install

### Wajib: Izin Notifikasi
```
Pengaturan HP → Aplikasi → Akses Khusus → 
Akses Notifikasi → AutoSpend AI → Aktifkan
```

### Opsional: Claude API Key (untuk parsing lebih akurat)
```
Buka app → ⚙️ Settings → Masukkan API key Claude
Tanpa API key, app tetap bisa parse menggunakan regex
```

---

## 📱 Aplikasi yang Didukung (otomatis terbaca)

| App | Package |
|-----|---------|
| GoPay / GoJek | com.gojek.gopay |
| OVO | id.co.ovo.android |
| DANA | id.dana |
| ShopeePay | com.shopee.id |
| BCA Mobile | id.co.bca.mybca |
| Mandiri Online | id.co.mandiri.internet |
| BNI Mobile | com.bni.android |
| BRI Mobile | id.co.bri.brimo |
| SMS Bank | com.google.android.apps.messaging |

---

## 🏗️ Struktur Project

```
autospend-ai/
├── .github/workflows/build.yml    ← Auto build APK
├── app/src/main/
│   ├── java/com/autospend/ai/
│   │   ├── MainActivity.java      ← UI utama
│   │   ├── NotificationService.java ← Baca notif HP ⭐
│   │   ├── AiParser.java          ← Parse dengan Claude AI
│   │   ├── Transaction.java       ← Model data
│   │   ├── AppDatabase.java       ← Room database
│   │   └── TransactionAdapter.java
│   ├── res/layout/                ← Tampilan XML
│   └── AndroidManifest.xml
└── build.gradle
```

---

## 🔒 Privasi

- Semua data tersimpan **lokal di HP** (Room Database)
- API key tersimpan di SharedPreferences lokal
- Tidak ada server eksternal selain Claude API (opsional)
