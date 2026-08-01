# Panduan Menjalankan Kardiolin-AI (.EXE)

Aplikasi versi `.exe` dapat langsung dijalankan tanpa perlu mengompilasi kode via NetBeans atau memasang Python virtual environment.

---

## ⚠️ Syarat Wajib (Path Harus Sama)

Semua file dan folder berikut **HARUS berada dalam 1 folder / direktori yang sama**:

```text
Kardiolin-AI/
├── Kardiolin-AI.exe    # Aplikasi Utama (GUI Java Swing)
├── modelekg.exe        # Backend Server (Flask REST API & Model AI)
├── .env                # File Konfigurasi API Key & Database MySQL
├── ecgmodel.onnx       # File Model Neural Network AI
├── ScpMaker/           # Folder Utilitas Converter SCP-ECG
└── ScpReader/          # Folder Utilitas Reader SCP-ECG
```

> **PENTING**: Jangan memindahkan `Kardiolin-AI.exe` atau `modelekg.exe` ke folder lain agar koneksi dan pembacaan berkas tidak error.

---

## 🚀 Cara Menjalankan

1. **Pastikan MySQL Aktif**: Jalankan service MySQL (misal via XAMPP).
2. **Jalankan Backend**: Double-click **`modelekg.exe`** (biarkan jendela terminal tetap terbuka).
3. **Jalankan Aplikasi**: Double-click **`Kardiolin-AI.exe`**.
