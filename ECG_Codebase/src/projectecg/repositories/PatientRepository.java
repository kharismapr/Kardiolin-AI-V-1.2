package projectecg.repositories;

import projectecg.client.FlaskApiClient;
import projectecg.models.Patient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PatientRepository — Data Access Layer for Patient entity.
 *
 * Semua operasi database sekarang dikirim ke Flask REST API
 * (tidak lagi konek langsung ke MySQL via JDBC).
 *
 * Mapping endpoint:
 *  savePatient()             → POST  /db/patients
 *  getPatientById()          → GET   /db/patients/{id}
 *  getPatientsByName()       → GET   /db/patients/search?name=...
 *  getAllPatients()           → GET   /db/patients
 *  getAbnormalPatients()     → GET   /db/patients/abnormal
 *  getPatientsByLabel()      → GET   /db/patients/by-label?label=...
 *  getPatientsByDateRange()  → GET   /db/patients/by-date?start=...&end=...
 *  updateAnalysisResults()   → PUT   /db/patients/{id}/analysis
 *  updatePatient()           → PUT   /db/patients/{id}
 *  deletePatient()           → DELETE /db/patients/{id}
 *  getTotalPatientCount()    → GET   /db/patients/count
 */
public class PatientRepository {

    private static final Logger logger = Logger.getLogger(PatientRepository.class.getName());
    private final FlaskApiClient http = FlaskApiClient.getInstance();

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Simpan pasien baru ke database melalui Flask API.
     * @return Generated patient ID, atau -1 jika gagal.
     */
    public int savePatient(Patient patient) {
        try {
            JSONObject body = new JSONObject();
            body.put("name",     patient.getName());
            body.put("sex",      patient.getSex());
            body.put("birthdate", patient.getBirthdate());
            body.put("age",      patient.getAge());
            body.put("tempat_lahir", patient.getTempatLahir());
            body.put("nik", patient.getNik());
            body.put("alamat", patient.getAlamat());
            body.put("gol_darah", patient.getGolDarah());
            body.put("pekerjaan", patient.getPekerjaan());
            body.put("kewarganegaraan", patient.getKewarganegaraan());
            body.put("status_kawin", patient.getStatusKawin());
            body.put("agama", patient.getAgama());

            if (patient.getArrhythmiaLabelCode() != null) {
                body.put("arrhythmia_label",  patient.getArrhythmiaLabelCode());
                body.put("confidence_score",  patient.getConfidenceScore());
                body.put("ai_recommendation", patient.getAiRecommendation() != null ? patient.getAiRecommendation() : "");
                body.put("heart_rate",        patient.getHeartRate());
                body.put("pr_interval",       patient.getPrInterval());
                body.put("qrs_duration",      patient.getQrsDuration());
                body.put("qtc_interval",      patient.getQtcInterval());
                body.put("st_deviation",      patient.getStDeviation());
            }

            String resp = http.post("/db/patients", body.toString());
            if (resp == null) return -1;

            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                int id = json.getInt("id");
                logger.info("✓ Patient saved via API. ID: " + id);
                return id;
            } else {
                logger.warning("✗ savePatient API error: " + json.optString("error"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error saving patient via API", e);
        }
        return -1;
    }

    // =========================================================================
    // READ
    // =========================================================================

    public Patient getPatientById(int patientId) {
        try {
            String resp = http.get("/db/patients/" + patientId);
            if (resp == null) return null;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                return mapJsonToPatient(json.getJSONObject("data"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving patient by ID: " + patientId, e);
        }
        return null;
    }

    public List<Patient> getPatientsByName(String name) {
        List<Patient> patients = new ArrayList<>();
        try {
            String encoded = java.net.URLEncoder.encode(name, "UTF-8");
            String resp = http.get("/db/patients/search?name=" + encoded);
            if (resp == null) return patients;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    patients.add(mapJsonToPatient(arr.getJSONObject(i)));
                }
            }
            logger.info("✓ Found " + patients.size() + " patient(s) with name: " + name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving patients by name", e);
        }
        return patients;
    }

    public List<Patient> getAllPatients() {
        List<Patient> patients = new ArrayList<>();
        try {
            String resp = http.get("/db/patients");
            if (resp == null) return patients;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    patients.add(mapJsonToPatient(arr.getJSONObject(i)));
                }
            }
            logger.info("✓ Retrieved " + patients.size() + " patient(s)");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving all patients", e);
        }
        return patients;
    }

    public List<Patient> getAbnormalPatients() {
        List<Patient> patients = new ArrayList<>();
        try {
            String resp = http.get("/db/patients/abnormal");
            if (resp == null) return patients;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    patients.add(mapJsonToPatient(arr.getJSONObject(i)));
                }
            }
            logger.info("✓ Found " + patients.size() + " abnormal patient(s)");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving abnormal patients", e);
        }
        return patients;
    }

    public List<Patient> getPatientsByLabel(String label) {
        List<Patient> patients = new ArrayList<>();
        try {
            String encoded = java.net.URLEncoder.encode(label, "UTF-8");
            String resp = http.get("/db/patients/by-label?label=" + encoded);
            if (resp == null) return patients;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    patients.add(mapJsonToPatient(arr.getJSONObject(i)));
                }
            }
            logger.info("✓ Found " + patients.size() + " patient(s) with label: " + label);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving patients by label", e);
        }
        return patients;
    }

    public List<Patient> getPatientsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Patient> patients = new ArrayList<>();
        try {
            String resp = http.get("/db/patients/by-date?start=" + startDate + "&end=" + endDate);
            if (resp == null) return patients;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                JSONArray arr = json.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    patients.add(mapJsonToPatient(arr.getJSONObject(i)));
                }
            }
            logger.info("✓ Found " + patients.size() + " patient(s) in date range");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error retrieving patients by date range", e);
        }
        return patients;
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    public boolean updateAnalysisResults(int patientId, String arrhythmiaLabel,
                                         double confidenceScore, String aiRecommendation,
                                         int heartRate, double prInterval, double qrsDuration,
                                         double qtcInterval, double stDeviation) {
        try {
            JSONObject body = new JSONObject();
            body.put("arrhythmia_label",  arrhythmiaLabel);
            body.put("confidence_score",  confidenceScore);
            body.put("ai_recommendation", aiRecommendation != null ? aiRecommendation : "");
            body.put("heart_rate",        heartRate);
            body.put("pr_interval",       prInterval);
            body.put("qrs_duration",      qrsDuration);
            body.put("qtc_interval",      qtcInterval);
            body.put("st_deviation",      stDeviation);

            String resp = http.put("/db/patients/" + patientId + "/analysis", body.toString());
            if (resp == null) return false;

            JSONObject json = new JSONObject(resp);
            boolean ok = json.optBoolean("ok", false);
            if (ok) {
                logger.info("✓ Patient analysis updated. ID: " + patientId);
            } else {
                logger.warning("✗ updateAnalysisResults: " + json.optString("error"));
            }
            return ok;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error updating patient analysis", e);
        }
        return false;
    }

    /** Backward-compatible: tanpa parameter klinis. */
    public boolean updateAnalysisResults(int patientId, String arrhythmiaLabel,
                                         double confidenceScore, String aiRecommendation) {
        return updateAnalysisResults(patientId, arrhythmiaLabel, confidenceScore, aiRecommendation,
                                     0, 0.0, 0.0, 0.0, 0.0);
    }

    public boolean updatePatient(Patient patient) {
        try {
            JSONObject body = new JSONObject();
            body.put("name",     patient.getName());
            body.put("sex",      patient.getSex());
            body.put("birthdate", patient.getBirthdate());
            body.put("age",      patient.getAge());
            body.put("tempat_lahir", patient.getTempatLahir());
            body.put("nik", patient.getNik());
            body.put("alamat", patient.getAlamat());
            body.put("gol_darah", patient.getGolDarah());
            body.put("pekerjaan", patient.getPekerjaan());
            body.put("kewarganegaraan", patient.getKewarganegaraan());
            body.put("status_kawin", patient.getStatusKawin());
            body.put("agama", patient.getAgama());

            String resp = http.put("/db/patients/" + patient.getId(), body.toString());
            if (resp == null) return false;

            JSONObject json = new JSONObject(resp);
            boolean ok = json.optBoolean("ok", false);
            if (ok) logger.info("✓ Patient updated. ID: " + patient.getId());
            return ok;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error updating patient", e);
        }
        return false;
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    public boolean deletePatient(int patientId) {
        try {
            String resp = http.delete("/db/patients/" + patientId);
            if (resp == null) return false;

            JSONObject json = new JSONObject(resp);
            boolean ok = json.optBoolean("ok", false);
            if (ok) logger.info("✓ Patient deleted. ID: " + patientId);
            return ok;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error deleting patient", e);
        }
        return false;
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    public int getTotalPatientCount() {
        try {
            String resp = http.get("/db/patients/count");
            if (resp == null) return 0;
            JSONObject json = new JSONObject(resp);
            if (json.optBoolean("ok", false)) {
                return json.getInt("count");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error getting patient count", e);
        }
        return 0;
    }

    /** printLabelStatistics() — dipertahankan untuk kompatibilitas, log ke console. */
    public void printLabelStatistics() {
        try {
            List<Patient> all = getAllPatients();
            java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
            for (Patient p : all) {
                String lbl = p.getArrhythmiaLabelCode();
                if (lbl != null) {
                    stats.merge(lbl, 1L, Long::sum);
                }
            }
            logger.info("=== Arrhythmia Label Statistics ===");
            stats.forEach((k, v) -> logger.info(k + ": " + v + " patient(s)"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "✗ Error getting label statistics", e);
        }
    }

    // =========================================================================
    // HELPER: map JSON object → Patient
    // =========================================================================

    private Patient mapJsonToPatient(JSONObject obj) {
        Patient p = new Patient();
        p.setId(obj.optInt("id", 0));
        p.setName(obj.optString("name", ""));
        p.setSex(obj.optString("sex", ""));

        String bd = obj.optString("birthdate", null);
        if (bd != null && !bd.isEmpty() && !bd.equals("null")) {
            p.setBirthdate(bd);
        }

        p.setAge(obj.optInt("age", 0));
        p.setWeight(obj.optDouble("weight", 0.0));
        p.setHeight(obj.optDouble("height", 0.0));
        
        // Map new demographics
        p.setTempatLahir(obj.optString("tempat_lahir", null));
        p.setNik(obj.optString("nik", null));
        p.setAlamat(obj.optString("alamat", null));
        p.setGolDarah(obj.optString("gol_darah", null));
        p.setPekerjaan(obj.optString("pekerjaan", null));
        p.setKewarganegaraan(obj.optString("kewarganegaraan", null));
        p.setStatusKawin(obj.optString("status_kawin", null));
        p.setAgama(obj.optString("agama", null));
        p.setArrhythmiaLabel(obj.optString("arrhythmia_label", null));
        p.setConfidenceScore(obj.optDouble("confidence_score", 0.0));
        p.setAiRecommendation(obj.optString("ai_recommendation", ""));
        p.setHeartRate(obj.optInt("heart_rate", 0));
        p.setPrInterval(obj.optDouble("pr_interval", 0.0));
        p.setQrsDuration(obj.optDouble("qrs_duration", 0.0));
        p.setQtcInterval(obj.optDouble("qtc_interval", 0.0));
        p.setStDeviation(obj.optDouble("st_deviation", 0.0));

        String ad = obj.optString("analysis_date", null);
        if (ad != null && !ad.isEmpty() && !ad.equals("null")) {
            try { p.setAnalysisDate(Timestamp.valueOf(ad.replace("T", " ").replace("Z", ""))); }
            catch (Exception ignored) {}
        }

        String ca = obj.optString("created_at", null);
        if (ca != null && !ca.isEmpty() && !ca.equals("null")) {
            try { p.setCreatedAt(Timestamp.valueOf(ca.replace("T", " ").replace("Z", ""))); }
            catch (Exception ignored) {}
        }

        return p;
    }
}
