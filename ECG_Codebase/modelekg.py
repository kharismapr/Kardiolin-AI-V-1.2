import os
import sys
import logging
import functools
from datetime import datetime

import numpy as np
import onnxruntime as ort
import requests
from flask import Flask, request, jsonify
from dotenv import load_dotenv

# -----------------------------------------------------------------------------
# 1) Load Environment Variables
# -----------------------------------------------------------------------------
if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

ENV_PATH = os.path.join(BASE_DIR, ".env")
load_dotenv(dotenv_path=ENV_PATH)

# -----------------------------------------------------------------------------
# 2) Logger
# -----------------------------------------------------------------------------
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

app = Flask(__name__)

# -----------------------------------------------------------------------------
# 3) API Key Authentication
# -----------------------------------------------------------------------------
API_KEY = os.getenv("FLASK_API_KEY", "")

def require_api_key(f):
    """Decorator: validasi header X-API-Key dari setiap request Java FE."""
    @functools.wraps(f)
    def decorated(*args, **kwargs):
        # Health endpoint tidak butuh auth (untuk debug koneksi awal)
        if not API_KEY:
            # Jika API_KEY belum dikonfigurasi di .env, lewati auth (dev mode)
            return f(*args, **kwargs)
        client_key = request.headers.get("X-API-Key", "")
        if client_key != API_KEY:
            logger.warning("Unauthorized request from %s", request.remote_addr)
            return jsonify({"ok": False, "error": "Unauthorized: invalid API key"}), 401
        return f(*args, **kwargs)
    return decorated

# -----------------------------------------------------------------------------
# 4) Database Connection Pool
# -----------------------------------------------------------------------------
import mysql.connector
from mysql.connector import pooling

DB_HOST     = os.getenv("DB_HOST", "localhost")
DB_PORT     = int(os.getenv("DB_PORT", "3306"))
DB_NAME     = os.getenv("DB_NAME", "ecg_db")
DB_USER     = os.getenv("DB_USER", "")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

db_pool = None

def init_db_pool():
    global db_pool
    try:
        db_pool = pooling.MySQLConnectionPool(
            pool_name="ecg_pool",
            pool_size=5,
            pool_reset_session=True,
            host=DB_HOST,
            port=DB_PORT,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD,
            charset="utf8mb4",
            autocommit=True,
        )
        logger.info("✓ MySQL connection pool initialized (host=%s, db=%s)", DB_HOST, DB_NAME)
    except Exception as e:
        logger.error("✗ Failed to initialize MySQL pool: %s", e)
        db_pool = None

init_db_pool()

def get_conn():
    """Ambil koneksi dari pool. Raise jika pool tidak tersedia."""
    if db_pool is None:
        raise RuntimeError("Database pool not available. Check DB credentials in .env")
    return db_pool.get_connection()

# -----------------------------------------------------------------------------
# 5) Konfigurasi OpenRouter AI
# -----------------------------------------------------------------------------
OPENROUTER_KEY   = os.getenv("OPENROUTER_API_KEY")
OPENROUTER_MODEL = os.getenv("OPENROUTER_MODEL", "openai/gpt-oss-20b:free")
OPENROUTER_URL   = "https://openrouter.ai/api/v1/chat/completions"

ai_enabled = bool(
    OPENROUTER_KEY
    and OPENROUTER_KEY.strip()
    and "REPLACE" not in OPENROUTER_KEY
)

if ai_enabled:
    logger.info("✓ OpenRouter AI initialized (model=%s)", OPENROUTER_MODEL)
else:
    logger.warning("✗ OPENROUTER_API_KEY not found. AI advice disabled.")

# -----------------------------------------------------------------------------
# 6) Load ONNX Model
# -----------------------------------------------------------------------------
session = None
input_name_signal = None
input_name_rr = None
output_name = None

ONNX_PATH = os.path.join(BASE_DIR, "ecgmodel.onnx")

try:
    session = ort.InferenceSession(ONNX_PATH)
    input_name_signal = session.get_inputs()[0].name
    input_name_rr = session.get_inputs()[1].name
    output_name = session.get_outputs()[0].name
    logger.info("✓ ONNX model loaded (%s)", ONNX_PATH)
except Exception as e:
    logger.error("✗ Failed to load ONNX (%s): %s", ONNX_PATH, e)
    session = None

CLASS_NAMES = ["N","L","R","A","V","F","/","f","j","E","Other"]
CLASS_DESCRIPTIONS = {
    "N":     "Normal Sinus Beat",
    "L":     "Left Bundle Branch Block Beat",
    "R":     "Right Bundle Branch Block Beat",
    "A":     "Atrial Premature Beat (PAC)",
    "V":     "Ventricular Premature Beat (PVC)",
    "F":     "Fusion Beat (Normal + Ventricular)",
    "/":     "Paced Beat",
    "f":     "Fusion of Paced and Normal Beat",
    "j":     "Junctional Escape Beat",
    "E":     "Ventricular Escape Beat",
    "Other": "Unclassified Beat"
}

def preprocess_signal(data):
    sig = np.array(data, dtype=np.float32)
    mu, sd = float(np.mean(sig)), float(np.std(sig))
    if sd == 0:
        sd = 1e-8
    sig = (sig - mu) / sd
    sig = sig.reshape(216, 1)
    sig = np.concatenate([sig, sig], axis=1)
    return np.expand_dims(sig, axis=0)

def make_gemini_prompt(result_label, description, confidence, patient_context=""):
    ctx = (patient_context or "").strip()
    ctx_text = f" Konteks pasien: {ctx}." if ctx else ""
    return (
        f"Kamu adalah asisten kesehatan jantung. "
        f"Hasil klasifikasi ECG pasien: {result_label} ({description}), "
        f"tingkat kepercayaan model: {confidence*100:.1f}%.{ctx_text} "
        f"Berikan SATU KALIMAT saran kesehatan dalam Bahasa Indonesia "
        f"(maksimal 150 karakter) berdasarkan hasil tersebut. "
        f"Jangan sebut dirimu AI atau dokter. Langsung ke poinnya."
    )

def generate_ai_advice(result_label, confidence, description, patient_context=""):
    if not ai_enabled:
        return ""
    prompt = make_gemini_prompt(result_label, description, confidence, patient_context)
    try:
        response = requests.post(
            OPENROUTER_URL,
            headers={
                "Authorization": f"Bearer {OPENROUTER_KEY}",
                "Content-Type":  "application/json",
                "HTTP-Referer":  "http://localhost",
                "X-Title":       "ECG Classifier"
            },
            json={
                "model":       OPENROUTER_MODEL,
                "messages":    [{"role": "user", "content": prompt}],
                "max_tokens":  200,
                "temperature": 0.3
            },
            timeout=15
        )
        response.raise_for_status()
        data = response.json()
        text = data["choices"][0]["message"]["content"]
        return (text or "").strip()
    except requests.exceptions.Timeout:
        logger.error("OpenRouter timeout")
        return ""
    except requests.exceptions.HTTPError as e:
        logger.error("OpenRouter HTTP error: %s | body: %s", e, response.text)
        return ""
    except Exception as e:
        logger.error("OpenRouter error: %s", e)
        return ""

# =============================================================================
# HELPER: row dict dari cursor
# =============================================================================
def row_to_dict(cursor, row):
    cols = [d[0] for d in cursor.description]
    d = {}
    for k, v in zip(cols, row):
        if isinstance(v, datetime):
            d[k] = v.isoformat()
        elif hasattr(v, 'isoformat'):          # date
            d[k] = v.isoformat()
        else:
            d[k] = v
    return d

# =============================================================================
# ENDPOINTS — HEALTH & ECG MODEL
# =============================================================================

@app.route("/health", methods=["GET"])
def health():
    db_ok = False
    try:
        if db_pool:
            conn = get_conn()
            conn.close()
            db_ok = True
    except Exception:
        pass
    return jsonify({
        "ok": True,
        "service": "modelekg-backend",
        "onnx_loaded": session is not None,
        "ai_enabled": ai_enabled,
        "ai_model": OPENROUTER_MODEL,
        "db_pool_ok": db_ok,
        "time": datetime.utcnow().isoformat() + "Z"
    })

@app.route("/predict", methods=["POST"])
@require_api_key
def predict():
    try:
        if session is None:
            return jsonify({"ok": False, "error": "ONNX model not loaded."}), 500

        req_data = request.get_json(silent=True) or {}
        if "signal" not in req_data:
            return jsonify({"ok": False, "error": "Missing 'signal' data"}), 400
        if "rr" not in req_data:
            return jsonify({"ok": False, "error": "Missing 'rr' data"}), 400

        processed_signal = preprocess_signal(req_data["signal"])
        processed_rr = np.array(req_data["rr"], dtype=np.float32).reshape(1, -1)

        outputs = session.run([output_name], {
            input_name_signal: processed_signal,
            input_name_rr: processed_rr
        })

        prediction = outputs[0][0]
        class_idx = int(np.argmax(prediction))
        result_label = CLASS_NAMES[class_idx]
        confidence = float(prediction[class_idx])
        description = CLASS_DESCRIPTIONS.get(result_label, "Unknown")

        patient_context = req_data.get("patientContext", "")
        ai_advice = generate_ai_advice(result_label, confidence, description, patient_context)

        return jsonify({
            "ok": True,
            "classification": result_label,
            "confidence": confidence,
            "class_label": description,
            "advice": ai_advice,
            "mode": "hybrid_cloud"
        })

    except Exception as e:
        logger.error("Prediction Error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

@app.route("/gemini/advice", methods=["POST"])
@require_api_key
def gemini_advice():
    logger.info("HIT /gemini/advice with payload: %s", request.get_json(silent=True))
    try:
        req_data = request.get_json(silent=True) or {}
        classification = req_data.get("classification", "")
        confidence = float(req_data.get("confidence", 0.0))
        patient_context = req_data.get("patientContext", "")

        description = CLASS_DESCRIPTIONS.get(classification, "Unknown")
        advice = generate_ai_advice(classification, confidence, description, patient_context)

        if not advice:
            return jsonify({
                "ok": False,
                "error": "Gemini advice unavailable (disabled or error).",
                "advice": ""
            }), 200

        return jsonify({
            "ok": True,
            "advice": advice,
            "model": OPENROUTER_MODEL
        }), 200

    except Exception as e:
        logger.error("Gemini Advice Error: %s", e)
        return jsonify({"ok": False, "error": str(e), "advice": ""}), 500

# =============================================================================
# ENDPOINTS — DATABASE (patients table)
# =============================================================================

# ---------------------------------------------------------------------------
# GET /db/patients        → getAllPatients()
# ---------------------------------------------------------------------------
@app.route("/db/patients", methods=["GET"])
@require_api_key
def db_get_all_patients():
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM patients ORDER BY created_at DESC")
        rows = cursor.fetchall()
        patients = [row_to_dict(cursor, r) for r in rows]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": patients, "count": len(patients)})
    except Exception as e:
        logger.error("db_get_all_patients error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/<id>   → getPatientById()
# ---------------------------------------------------------------------------
@app.route("/db/patients/<int:patient_id>", methods=["GET"])
@require_api_key
def db_get_patient_by_id(patient_id):
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM patients WHERE id = %s", (patient_id,))
        row = cursor.fetchone()
        if row is None:
            cursor.close()
            conn.close()
            return jsonify({"ok": False, "error": "Patient not found"}), 404
        data = row_to_dict(cursor, row)
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": data})
    except Exception as e:
        logger.error("db_get_patient_by_id error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/search?name=...  → getPatientsByName()
# ---------------------------------------------------------------------------
@app.route("/db/patients/search", methods=["GET"])
@require_api_key
def db_search_patients():
    name = request.args.get("name", "")
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM patients WHERE LOWER(name) LIKE LOWER(%s) ORDER BY created_at DESC",
            (f"%{name}%",)
        )
        rows = cursor.fetchall()
        patients = [row_to_dict(cursor, r) for r in rows]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": patients, "count": len(patients)})
    except Exception as e:
        logger.error("db_search_patients error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/abnormal  → getAbnormalPatients()
# ---------------------------------------------------------------------------
@app.route("/db/patients/abnormal", methods=["GET"])
@require_api_key
def db_get_abnormal_patients():
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM patients WHERE arrhythmia_label IS NOT NULL "
            "AND arrhythmia_label != 'N' ORDER BY created_at DESC"
        )
        rows = cursor.fetchall()
        patients = [row_to_dict(cursor, r) for r in rows]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": patients, "count": len(patients)})
    except Exception as e:
        logger.error("db_get_abnormal_patients error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/by-label?label=V  → getPatientsByLabel()
# ---------------------------------------------------------------------------
@app.route("/db/patients/by-label", methods=["GET"])
@require_api_key
def db_get_patients_by_label():
    label = request.args.get("label", "")
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM patients WHERE arrhythmia_label = %s ORDER BY created_at DESC",
            (label,)
        )
        rows = cursor.fetchall()
        patients = [row_to_dict(cursor, r) for r in rows]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": patients, "count": len(patients)})
    except Exception as e:
        logger.error("db_get_patients_by_label error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/by-date?start=YYYY-MM-DD&end=YYYY-MM-DD
# ---------------------------------------------------------------------------
@app.route("/db/patients/by-date", methods=["GET"])
@require_api_key
def db_get_patients_by_date():
    start = request.args.get("start", "")
    end   = request.args.get("end", "")
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM patients WHERE DATE(analysis_date) BETWEEN %s AND %s "
            "ORDER BY analysis_date DESC",
            (start, end)
        )
        rows = cursor.fetchall()
        patients = [row_to_dict(cursor, r) for r in rows]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "data": patients, "count": len(patients)})
    except Exception as e:
        logger.error("db_get_patients_by_date error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# GET /db/patients/count  → getTotalPatientCount()
# ---------------------------------------------------------------------------
@app.route("/db/patients/count", methods=["GET"])
@require_api_key
def db_get_patient_count():
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM patients")
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        return jsonify({"ok": True, "count": count})
    except Exception as e:
        logger.error("db_get_patient_count error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# POST /db/patients  → savePatient() / registerNewPatient()
# Body JSON: {name, sex, birthdate, age, weight, height,
#             arrhythmia_label?, confidence_score?, ai_recommendation?,
#             heart_rate?, pr_interval?, qrs_duration?, qtc_interval?, st_deviation?}
# ---------------------------------------------------------------------------
@app.route("/db/patients", methods=["POST"])
@require_api_key
def db_save_patient():
    data = request.get_json(silent=True) or {}
    required = ["name", "sex", "birthdate", "age"]
    for field in required:
        if field not in data:
            return jsonify({"ok": False, "error": f"Missing required field: {field}"}), 400
    try:
        sql = (
            "INSERT INTO patients (name, sex, birthdate, age, "
            "arrhythmia_label, confidence_score, ai_recommendation, analysis_date, "
            "heart_rate, pr_interval, qrs_duration, qtc_interval, st_deviation, "
            "tempat_lahir, nik, alamat, gol_darah, pekerjaan, kewarganegaraan, "
            "status_kawin, agama) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
        )
        vals = (
            data["name"],
            data["sex"],
            data["birthdate"],
            int(data["age"]),
            data.get("arrhythmia_label"),
            data.get("confidence_score"),
            data.get("ai_recommendation"),
            data.get("analysis_date"),
            data.get("heart_rate", 0),
            data.get("pr_interval", 0.0),
            data.get("qrs_duration", 0.0),
            data.get("qtc_interval", 0.0),
            data.get("st_deviation", 0.0),
            data.get("tempat_lahir"),
            data.get("nik"),
            data.get("alamat"),
            data.get("gol_darah"),
            data.get("pekerjaan"),
            data.get("kewarganegaraan"),
            data.get("status_kawin"),
            data.get("agama"),
        )
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(sql, vals)
        new_id = cursor.lastrowid
        cursor.close()
        conn.close()
        logger.info("✓ Patient saved. ID=%d", new_id)
        return jsonify({"ok": True, "id": new_id}), 201
    except Exception as e:
        logger.error("db_save_patient error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# PUT /db/patients/<id>/analysis  → updateAnalysisResults()
# Body JSON: {arrhythmia_label, confidence_score, ai_recommendation,
#             heart_rate?, pr_interval?, qrs_duration?, qtc_interval?, st_deviation?}
# ---------------------------------------------------------------------------
@app.route("/db/patients/<int:patient_id>/analysis", methods=["PUT"])
@require_api_key
def db_update_analysis(patient_id):
    data = request.get_json(silent=True) or {}
    try:
        sql = (
            "UPDATE patients SET arrhythmia_label = %s, confidence_score = %s, "
            "ai_recommendation = %s, heart_rate = %s, pr_interval = %s, "
            "qrs_duration = %s, qtc_interval = %s, st_deviation = %s, "
            "analysis_date = CURRENT_TIMESTAMP WHERE id = %s"
        )
        vals = (
            data.get("arrhythmia_label"),
            float(data.get("confidence_score", 0.0)),
            data.get("ai_recommendation", ""),
            int(data.get("heart_rate", 0)),
            float(data.get("pr_interval", 0.0)),
            float(data.get("qrs_duration", 0.0)),
            float(data.get("qtc_interval", 0.0)),
            float(data.get("st_deviation", 0.0)),
            patient_id,
        )
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(sql, vals)
        affected = cursor.rowcount
        cursor.close()
        conn.close()
        if affected == 0:
            return jsonify({"ok": False, "error": "Patient not found or no change"}), 404
        logger.info("✓ Analysis updated for patient ID=%d", patient_id)
        return jsonify({"ok": True, "updated": affected})
    except Exception as e:
        logger.error("db_update_analysis error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# PUT /db/patients/<id>  → updatePatient() (data dasar)
# Body JSON: {name, sex, birthdate, age, weight, height}
# ---------------------------------------------------------------------------
@app.route("/db/patients/<int:patient_id>", methods=["PUT"])
@require_api_key
def db_update_patient(patient_id):
    data = request.get_json(silent=True) or {}
    try:
        sql = (
            "UPDATE patients SET name = %s, sex = %s, birthdate = %s, "
            "age = %s, tempat_lahir = %s, "
            "nik = %s, alamat = %s, gol_darah = %s, pekerjaan = %s, "
            "kewarganegaraan = %s, status_kawin = %s, agama = %s WHERE id = %s"
        )
        vals = (
            data.get("name"),
            data.get("sex"),
            data.get("birthdate"),
            int(data.get("age", 0)),
            data.get("tempat_lahir"),
            data.get("nik"),
            data.get("alamat"),
            data.get("gol_darah"),
            data.get("pekerjaan"),
            data.get("kewarganegaraan"),
            data.get("status_kawin"),
            data.get("agama"),
            patient_id,
        )
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute(sql, vals)
        affected = cursor.rowcount
        cursor.close()
        conn.close()
        if affected == 0:
            return jsonify({"ok": False, "error": "Patient not found or no change"}), 404
        return jsonify({"ok": True, "updated": affected})
    except Exception as e:
        logger.error("db_update_patient error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# ---------------------------------------------------------------------------
# DELETE /db/patients/<id>  → deletePatient()
# ---------------------------------------------------------------------------
@app.route("/db/patients/<int:patient_id>", methods=["DELETE"])
@require_api_key
def db_delete_patient(patient_id):
    try:
        conn = get_conn()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM patients WHERE id = %s", (patient_id,))
        affected = cursor.rowcount
        cursor.close()
        conn.close()
        if affected == 0:
            return jsonify({"ok": False, "error": "Patient not found"}), 404
        logger.info("✓ Patient deleted. ID=%d", patient_id)
        return jsonify({"ok": True, "deleted": affected})
    except Exception as e:
        logger.error("db_delete_patient error: %s", e)
        return jsonify({"ok": False, "error": str(e)}), 500

# =============================================================================
if __name__ == "__main__":
    print("REGISTERED ROUTES:")
    for r in app.url_map.iter_rules():
        print(r, r.methods)
    app.run(host="127.0.0.1", port=5000, debug=True)