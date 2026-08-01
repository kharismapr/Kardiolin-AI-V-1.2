# Kardiolin AI - System Execution Guide

## 1. System Architecture Overview

1. **Frontend (Java Swing Desktop)**: Primary user interface handling real-time ECG signal visualization, patient management, digital signal filtering, P-Q-R-S-T wave peak annotations, and A4 clinical PDF report exports.
2. **Backend (Python Flask REST API & ML)**: Backend gateway executing ONNX machine learning inference (`ecgmodel.onnx`), Generative AI consultation (OpenRouter / Gemini API), and RESTful database operations.
3. **Database Layer (MySQL via Flask REST)**: Patient persistence layer managed through a MySQL Connection Pool served over REST API endpoints (`/db/patients/*`).
4. **Medical Interoperability (C# .NET SCP-ECG)**: Utility suite for encoding and parsing international standard binary SCP-ECG files (EN 1064 / ISO standard).
---

## 2. Prerequisites & Environment Setup

### 2.1 Software Prerequisites

| Component | Minimum Version | Recommended | Notes |
| :--- | :--- | :--- | :--- |
| **Java JDK** | OpenJDK 17 | OpenJDK 25 | Required for Java Swing UI |
| **NetBeans IDE** | 12.0+ | NetBeans 19 / 20 | Recommended for editing `.form` GUI builder files |
| **Build Tool** | Apache Ant 1.10+ | Integrated in NetBeans | Used by `build.xml` |
| **Python** | 3.10+ | Python 3.11 / 3.13 | Backend server runtime |
| **Database** | MySQL Server 8.0+ | MySQL 8.0 | Backend storage engine |
| **.NET SDK** | .NET 6.0+ | .NET 6.0 / 8.0 SDK | Optional: Required to compile `ScpMaker` tool |

### 2.2 Dependencies & Libraries
- **Java**:
  - `ECG_Codebase/lib/jSerialComm.jar` (v2.10.4): Serial hardware communication.
  - `iText PDF` (`com.itextpdf`): A4 medical report layout renderer.
  - `org.json`: Lightweight JSON parsing library.
- **Python** (`ECG_Codebase/requirements.txt`):
  - `numpy`, `onnxruntime`, `flask`, `google-generativeai`, `python-dotenv`, `requests`, `mysql-connector-python`, `matplotlib`.

---

## 3. Execution Workflow & Step-by-Step Setup

Follow this exact sequence to start and run the application:

### Step 3.1: Configure Backend Environment Variables (`.env`)
Create a `.env` file inside `ECG_Codebase/`:

```bash
# Security Gateway API Key
FLASK_API_KEY=your_secret_api_key_here

# OpenRouter / Gemini AI Consultation
OPENROUTER_API_KEY=your_openrouter_api_key
OPENROUTER_MODEL=openai/gpt-oss-20b:free

# MySQL Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ecg_db
DB_USER=root
DB_PASSWORD=your_mysql_password
```

> ⚠️ **Important**: The `FLASK_API_KEY` value in `.env` **must match** `api.key` in the Java configuration file.

### Step 3.2: Configure Java Client Settings (`api_config.properties`)
Verify `ECG_Codebase/src/projectecg/config/api_config.properties`:

```properties
api.base_url=http://localhost:5000
api.key=your_secret_api_key_here
api.connect_timeout_ms=5000
api.read_timeout_ms=10000
```

### Step 3.3: Start Python Flask Backend Server
The Python backend **must be running** before launching the Java application:

```bash
cd ECG_Codebase

# 1. Create and activate a Virtual Environment
python3 -m venv .venv
source .venv/bin/activate      # On Windows: .venv\Scripts\activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Start Flask server
python modelekg.py
```

Verify server health:
```bash
curl http://127.0.0.1:5000/health
```
*Expected response*: `{"status": "OK", "onnx_loaded": true, "ai_enabled": true}`

### Step 3.4: Launch Java Swing Application
Using NetBeans IDE:
1. Open NetBeans IDE.
2. Select `File > Open Project` -> Choose `ECG_Codebase`.
3. Confirm `lib/jSerialComm.jar` is present in **Libraries**.
4. Click **Clean and Build Project** (`Shift + F11`).
5. Click **Run Project** (`F6`) or run the main class: `projectecg.MainDashboard`.

Using Terminal (Apache Ant):
```bash
cd ECG_Codebase
ant -f build.xml clean build
java -cp build/projectECG.jar:lib/jSerialComm.jar projectecg.MainDashboard
```

### Step 3.5: Testing Workflow with Demo Data
1. In the Java Dashboard, click **Upload csv**.
2. Select demo dataset: `ECG_Codebase/demo_ecg_normal.csv`.
3. Confirm 12-lead ECG waveform rendering on the canvas.
4. Click **Analyze**:
   - The app extracts a 216-sample signal window.
   - Java issues an HTTP POST request to Python Flask `/predict`.
   - The backend runs ONNX model inference and Gemini AI consultation.
   - Diagnosis result (e.g., *Normal - 96% Confidence*) and health advice are displayed on screen.
5. Click **Download** to generate PDF/CSV medical report.

---

## 4. Java Codebase Package Layout (`src/projectecg/`)

An overview of the Java package structure for code maintainability:

```text
src/projectecg/
├── client/
│   └── FlaskApiClient.java       # Singleton HTTP REST client (GET/POST/PUT/DELETE) with X-API-Key auth
├── config/
│   ├── ApiConfig.java            # API URL & configuration properties loader
│   └── api_config.properties     # Property configuration file for Flask backend
├── models/
│   └── Patient.java              # Patient domain model (Biometrics, Demographics, ECG Metrics)
├── repositories/
│   └── PatientRepository.java    # Data Access Object (DAO) querying Flask REST API /db/patients
├── services/
│   └── PatientService.java       # Business logic layer for patient history and searching
├── utils/
│   └── EnvConfig.java            # Java .env environment file parser
├── CSVExporter.java              # 12-lead signal & metric CSV export utility
├── ECGAnalyzer.java              # Feature extraction: Pan-Tompkins R-peak, HR, PR, QRS, QTc
├── ECGFilters.java               # Digital filters: Notch (50/60Hz), Moving Average, Butterworth
├── FindPatientPanel.java/.form   # Patient search dialog UI
├── GeminiHealthAdvisor.java      # AI consultation fallback & adapter
├── MainDashboard.java/.form      # Primary ECG monitor dashboard UI & canvas
├── NewPatientFrame.java/.form    # Patient registration dialog UI
├── PDFExporter.java              # A4 medical PDF report generator engine (iText)
├── PatientHistory.java/.form     # Historical ECG record viewer UI
├── ProjectECG.java               # Main launcher class
├── SerialCommBiner.java          # Serial hardware interface wrapper (jSerialComm)
├── Settings.java/.form           # Digital filter & chart settings dialog UI
├── ThrdParsingECG.java           # Multi-threaded real-time stream parser
├── WaveAnnotationPainter.java    # Visual P-Q-R-S-T & anomaly peak annotation painter
└── analyzepanel.java/.form       # ML arrhythmia diagnosis & feature panel UI
```

---

## 5. Backend REST API Reference (`modelekg.py`)

### 5.1 Diagnostic & AI Endpoints
- `GET /health`: Healthcheck status for server, ONNX model, and AI integration.
- `POST /predict`: Receives 216 signal samples & RR intervals, returns 8-class arrhythmia prediction (N, L, R, A, V, /, F, Other) & AI advice.
- `POST /gemini/advice`: Receives patient context & classification label, returns Gemini AI health consultation advice.

### 5.2 Patient Database REST Endpoints (`/db/patients/*`)
- `GET /db/patients` — Retrieve all patient records.
- `POST /db/patients` — Register new patient.
- `GET /db/patients/<id>` — Retrieve patient profile & ECG history.
- `PUT /db/patients/<id>` — Update patient demographics.
- `DELETE /db/patients/<id>` — Remove patient record.
- `GET /db/patients/search?name=...` — Search patient by name.
- `GET /db/patients/abnormal` — Filter patients with abnormal ECG findings.
- `GET /db/patients/by-date?start=...&end=...` — Filter patient records by date range.
- `PUT /db/patients/<id>/analysis` — Update patient ECG diagnostic metrics.

---

## 6. C# .NET SCP-ECG Interoperability Suite (`ScpMaker` & `ScpReader`)

The project includes two C# .NET CLI tools for medical international standard binary SCP-ECG files (EN 1064):

- **ScpMaker** (`ECG_Codebase/ScpMaker/Program.cs`):
  - Compilation: `dotnet build ScpMaker/ScpMaker.csproj -c Release`
  - Usage:
    ```bash
    ScpMaker.exe <input_csv> <sample_rate> <output_directory> <patient_id>
    ```
  - Converts raw 12-lead CSV data into standard binary `.scp` format with embedded demographic headers (`FTUI-ESP32`).

- **ScpReader** (`ECG_Codebase/ScpReader/Program.cs`):
  - CLI utility to inspect, parse, and verify binary `.scp` file headers and voltage data.

---

## 7. Developer Troubleshooting Guide

| Issue / Error | Probable Cause | Resolution |
| :--- | :--- | :--- |
| **Java Compilation Error (`package jSerialComm does not exist`)** | Missing or broken JAR library path in NetBeans | Go to Project Properties > Libraries > Remove old path > Add `lib/jSerialComm.jar` (located inside `ECG_Codebase/lib/`) |
| **Analyze Error (`HTTP 401 Unauthorized`)** | API Key mismatch between Java and Python | Ensure `FLASK_API_KEY` in `.env` matches `api.key` in `api_config.properties` |
| **Analyze Error (`Connection Refused`)** | Flask backend server is not running | Start backend with `python modelekg.py` prior to running analysis in Java GUI |
| **Backend `/health` Error (`"onnx_loaded": false`)** | Missing `ecgmodel.onnx` file | Ensure `ecgmodel.onnx` exists in `ECG_Codebase/` root |
| **Empty AI Advice** | Missing or invalid `OPENROUTER_API_KEY` | Set API key in `.env` and restart Flask backend server |
| **SCP Converter Error** | C# binary not compiled | Run `dotnet build ScpMaker/ScpMaker.csproj` |
