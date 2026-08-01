# Kardiolin AI — Clinical ECG Monitoring & AI Diagnostic Platform

`Kardiolin-AI` is a clinical-grade medical Electrocardiogram (ECG) monitoring, signal processing, and AI-assisted diagnostic platform. It combines a **Java Swing Desktop Application** for real-time signal visualization, a **Python Flask REST & ML Backend** running ONNX deep learning models, **Generative AI Health Consultation** via OpenRouter / Gemini API, an **iText PDF Clinical Report Generator**, and a **C# .NET SCP-ECG Interoperability Suite** for international medical data standards (EN 1064 / ISO standard).

## Project Origin

This repository originated from the open-source project **visualisasiECG** by Viefay:
https://github.com/Viefay/visualisasiECG

While preserving the original foundation, this repository has evolved into an extended ECG platform with significant additional development, including:

- AI-assisted arrhythmia diagnosis improvement
- MySQL-backed patient management
- Clinical PDF report generation
- Enhanced ECG visualization and annotations
- SCP-ECG interoperability
- Continuous maintenance and feature expansion

---

## Quick Start Guide for Developers

If you are new to this repository or want to extend its capabilities, follow this execution sequence:

1. **Review Documentation**:
   - `projects.md` — Comprehensive technical architecture, REST API reference, DB configuration, and troubleshooting guide.
   - `ECG_Codebase/AGENT.md` — Main codebase directory navigation guide.
   - `ECG_Codebase/src/projectecg/AGENT.md` — Java package architecture guide.

2. **Quick Run Steps**:
   - **Step 1**: Create `.env` in `ECG_Codebase/` (set `FLASK_API_KEY`, `OPENROUTER_API_KEY`, and MySQL credentials).
   - **Step 2**: Start Python Flask backend:
     ```bash
     cd ECG_Codebase
     python3 -m venv .venv
     source .venv/bin/activate
     pip install -r requirements.txt
     python modelekg.py
     ```
   - **Step 3**: Launch Java Swing application via NetBeans or Ant:
     ```bash
     cd ECG_Codebase
     ant -f build.xml clean build
     java -cp build/projectECG.jar:lib/jSerialComm.jar projectecg.ProjectECG
     ```
   - **Step 4**: Upload demo CSV dataset `ECG_Codebase/demo_ecg_normal.csv`, click **Analyze** for AI diagnosis, and click **Export PDF** to generate an A4 clinical report.

---

## Key Platform Features

- **📈 Signal Processing & Visual Annotations**:
  - Digital filters: 50Hz/60Hz Notch filter, Moving Average EMG filter, Butterworth Lowpass/Highpass/Bandpass (0.5Hz–40Hz), and Baseline Wander Highpass filter.
  - Feature extraction: Pan-Tompkins R-peak detection, Heart Rate calculation, PR interval, QRS duration, and QTc interval measurement (Bazett).
  - Wave annotations: Color-coded visual markers for P, Q, R, S, T peaks and anomaly dots (`WaveAnnotationPainter`).
- **🤖 ONNX Deep Learning Arrhythmia Classifier**:
  - 11-class beat & rhythm classification using `ecgmodel.onnx`.
- **💡 Generative AI Clinical Consultation**:
  - Contextual clinical health advice powered by OpenRouter / Gemini API tailored to patient demographics (age, gender, heart rate, diagnosis).
- **🏥 Patient Management Layer (MVC + DAO)**:
  - Layered architecture (MVC + Repository Pattern + Service Layer).
  - Database persistence via RESTful Flask API (`/db/patients/*`) backed by MySQL connection pooling.
  - Patient search panel (`FindPatientPanel`) and historical recording browser (`PatientHistory`).
- **📄 Medical PDF Report Generation**:
  - A4 clinical PDF exporter (`PDFExporter`) featuring patient data, 12-lead grid plots, diagnostic summaries, and AI recommendations.
- **🔄 SCP-ECG Standard Interoperability**:
  - C# .NET conversion tools (`ScpMaker` & `ScpReader` in `ECG_Codebase/ScpMaker/` & `ECG_Codebase/ScpReader/`) for standard binary SCP-ECG files (EN 1064).

---

## Repository Directory Layout

```text
visualisasiECG/
├── README.md                      # Primary project README (This guide)
├── projects.md                    # Detailed developer onboarding & technical guide
├── projects_improve.md            # Extended architectural reference
├── README_new.md                  # Project structure reference
└── ECG_Codebase/                  # Core application codebase directory
    ├── SYSTEM_VERIFICATION.txt    # System deployment verification log
    ├── build.xml                  # Apache Ant build script for Java Swing
    ├── build_and_run.sh           # Helper build & run script
    ├── demo_ecg_normal.csv        # Synthetic 12-lead ECG demo CSV dataset
    ├── ecgmodel.onnx              # ONNX deep learning classification model
    ├── generate_plot.py           # Standalone Python signal filter & Matplotlib 12-lead plotter
    ├── modelekg.py                # Python Flask REST API & ML backend server
    ├── requirements.txt           # Python dependencies manifest
    │
    ├── lib/                       # Java JAR dependencies directory
    │   └── jSerialComm.jar        # Serial hardware interface library (v2.10.4)
    │
    ├── ScpMaker/                  # C# .NET CSV to binary SCP-ECG (.scp) converter
    │   ├── Program.cs
    │   └── ScpMaker.csproj
    │
    ├── ScpReader/                 # C# .NET binary SCP-ECG (.scp) decoder
    │   ├── Program.cs
    │   └── ScpReader.csproj
    │
    └── src/
        └── projectecg/            # Core Java Swing application package
            ├── AGENT.md           # Java package architecture guide
            │
            ├── client/            # REST API Client Layer
            │   └── FlaskApiClient.java      # Singleton HTTP client with X-API-Key auth
            │
            ├── config/            # Application Configuration
            │   ├── ApiConfig.java           # Configuration properties manager
            │   └── api_config.properties    # REST API endpoint configuration file
            │
            ├── models/            # Domain Entities
            │   └── Patient.java             # Entity model for patient data & metrics
            │
            ├── repositories/      # Data Access Layer (DAO)
            │   └── PatientRepository.java   # REST API DAO querying Flask /db/patients
            │
            ├── services/          # Business Logic Layer
            │   └── PatientService.java      # Patient lifecycle & history service
            │
            ├── utils/             # Helper Utilities
            │   └── EnvConfig.java           # Java .env environment file loader
            │
            ├── CSVExporter.java              # 12-lead signal CSV exporter
            ├── ECGAnalyzer.java              # R-peak detection, HR, PR, QRS, QTc
            ├── ECGFilters.java               # Notch, Moving Average, Butterworth digital filters
            ├── FindPatientPanel.java/.form   # Patient search dialog UI
            ├── GeminiHealthAdvisor.java      # AI recommendation fallback & adapter
            ├── MainDashboard.java/.form      # Primary ECG monitor dashboard UI & canvas
            ├── NewPatientFrame.java/.form    # Patient registration dialog UI
            ├── PDFExporter.java              # A4 medical PDF report generator
            ├── PatientHistory.java/.form     # Historical ECG record viewer UI
            ├── ProjectECG.java               # Main entry point class
            ├── SerialCommBiner.java          # Serial hardware communication wrapper
            ├── Settings.java/.form           # Digital filter & chart settings dialog UI
            ├── ThrdParsingECG.java           # Real-time stream parsing thread
            ├── WaveAnnotationPainter.java    # Visual P-Q-R-S-T peak annotation painter
            └── analyzepanel.java/.form       # ML arrhythmia diagnosis & feature panel UI
```

---

## 🗺 System Workflow & Architecture Diagram

```text
  [ Signal / Sensor Input ]
     │ (Serial COM / CSV File Upload)
     ▼
  [ Java Swing Dashboard ] ──► (Digital Filtering & Pan-Tompkins Peak Detection)
     │                     ──► (2D Visual Canvas + P-Q-R-S-T Peak Annotations)
     │
     │ HTTP POST (Payload: 216 Signal Samples + Patient Context + X-API-Key Header)
     ▼
  [ Python Flask Backend ]
     ├─► ONNX Runtime (ecgmodel.onnx) ──► Arrhythmia Classification Prediction
     ├─► OpenRouter / Gemini API     ──► Medical & Lifestyle Consultation Advice
     └─► MySQL Connection Pool       ──► Patient Data & History Persistence (/db/patients)
     │
     ▼
  [ Output Deliverables ]
     ├─► A4 Clinical Medical PDF Diagnostics Reports (PDFExporter.java)
     ├─► 12-Lead Signal & Metric CSV Data Exports (CSVExporter.java)
     └─► International Standard Binary SCP-ECG Files (ScpMaker.exe / C# .NET)
```

---

