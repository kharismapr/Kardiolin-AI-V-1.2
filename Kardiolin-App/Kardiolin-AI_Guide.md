# Kardiolin-AI Execution Guide (.EXE)

The `.exe` version of the application can be launched directly without compiling code via NetBeans or setting up a Python virtual environment.

---

## Mandatory Requirements (Same Path Required)

All of the following files and folders **MUST be located in the same directory / path**:

```text
Kardiolin-AI/
├── Kardiolin-AI.exe    # Main Application (Java Swing GUI)
├── modelekg.exe        # Backend Server (Flask REST API & AI Model)
├── .env                # Configuration File (API Keys & MySQL Database)
├── ecgmodel.onnx       # AI Neural Network Model File
├── ScpMaker/           # SCP-ECG Converter Utility Folder
└── ScpReader/          # SCP-ECG Reader Utility Folder
```

> **IMPORTANT**: Do not move `Kardiolin-AI.exe` or `modelekg.exe` to another folder to prevent connection and file reading errors.

---

## How to Run

1. **Ensure MySQL is Running**: Start the MySQL service.
2. **Launch Application**: Double-click **`Kardiolin-AI.exe`**.
