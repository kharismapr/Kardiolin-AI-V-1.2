package projectecg;

import java.util.logging.Logger;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import projectecg.config.ApiConfig;

/**
 * GeminiHealthAdvisor (Offline Fallback Version)
 * Revisi: Modul ini tidak lagi menghubungi API Google secara langsung.
 * API Gemini sekarang diproses di Backend (Python).
 * Kelas ini berfungsi sebagai fallback jika backend gagal,
 * namun dapat mencoba mengambil saran dari backend jika tersedia.
 */
public class GeminiHealthAdvisor {
    private static final Logger logger = Logger.getLogger(GeminiHealthAdvisor.class.getName());

    // Backend endpoint (bukan API key). Ini aman ada di frontend.
    // Sesuaikan port/path dengan server Python kamu.
    private static final String BACKEND_GEMINI_ENDPOINT = "/gemini/advice";

    /**
     * Coba ambil advice dari backend Python.
     * Jika gagal (server mati/timeout/error), return null agar caller bisa fallback.
     *
     * NOTE: Method ini TIDAK menyimpan API key.
     */
    private static String tryGetAdviceFromBackend(String classification, double confidence, String patientContext) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ApiConfig.getBaseUrl() + BACKEND_GEMINI_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("X-API-Key", ApiConfig.getApiKey());
            conn.setConnectTimeout(1500);  // cepat fallback kalau backend mati
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);

            // JSON manual biar tanpa dependency tambahan
            // Backend bisa gunakan field ini untuk bikin prompt Gemini.
            String safeContext = (patientContext == null) ? "" : patientContext.replace("\"", "\\\"");
            String jsonBody =
                    "{"
                    + "\"classification\":\"" + classification + "\","
                    + "\"confidence\":" + confidence + ","
                    + "\"patientContext\":\"" + safeContext + "\""
                    + "}";

            byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String resp = sb.toString();
            logger.info("Backend raw response: " + resp);


            if (status < 200 || status >= 300) {
                logger.warning("Backend Gemini error HTTP " + status + ": " + resp);
                return null;
            }

            // Kamu bisa parse JSON di sini kalau mau ambil field "advice".
            // Tapi supaya minimal & kompatibel, return raw JSON dulu.
            // Nanti kita rapikan parse-nya setelah backend fix format responsenya.
            
            return resp;

        } catch (Exception e) {
            logger.warning("Tidak dapat menghubungi backend Gemini, fallback dipakai. Reason: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractAdviceFromJson(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"advice\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int firstQuote = json.indexOf("\"", colon + 1);
        if (firstQuote == -1) return null;
        int secondQuote = json.indexOf("\"", firstQuote + 1);
        if (secondQuote == -1) return null;
        
        String rawText = json.substring(firstQuote + 1, secondQuote);
        
        // Bersihkan karakter Unicode dari AI menjadi teks normal (Unescape)
        return rawText.replace("\\n", "\n")
                      .replace("\\\"", "\"")
                      .replace("\\u2011", "-")  // Ubah non-breaking hyphen jadi strip biasa
                      .replace("\\u202f", " ")  // Ubah narrow space jadi spasi biasa
                      .replace("\\u00a0", " "); // Ubah non-breaking space jadi spasi biasa
    }


    /**
     * Mendapatkan saran kesehatan cadangan (Static/Offline).
     * Gunakan ini HANYA jika response 'advice' dari Backend Python kosong atau error.
     */
    public static String getFallbackAdvice(String classificationLabel, double confidence) {
        StringBuilder advice = new StringBuilder();

        switch (classificationLabel) {
            case "N":
                advice.append("Detak jantung normal terdeteksi. Pertahankan gaya hidup sehat dan olahraga teratur.");
                break;
            case "L":
                advice.append("Left Bundle Branch Block terdeteksi. Kondisi ini memerlukan evaluasi kardiologis rutin.");
                break;
            case "R":
                advice.append("Right Bundle Branch Block terdeteksi. Pantau secara berkala dengan dokter spesialis jantung.");
                break;
            case "A":
                advice.append("Detak prematur atrium (PAC) terdeteksi. Kurangi kafein, alkohol, dan kelola stres.");
                break;
            case "V":
                advice.append("Detak prematur ventrikel (PVC) terdeteksi. Segera konsultasikan ke kardiolog jika sering terjadi.");
                break;
            case "F":
                advice.append("Fusion beat terdeteksi — kombinasi detak normal dan ventrikel. Disarankan pemantauan lanjut.");
                break;
            case "/":
                advice.append("Irama alat pacu jantung terdeteksi. Pastikan perangkat pacu jantung dalam kondisi baik.");
                break;
            case "f":
                advice.append("Fusion beat dari pacu jantung dan detak normal terdeteksi. Periksa fungsi alat pacu jantung.");
                break;
            case "j":
                advice.append("Junctional escape beat terdeteksi — detak pengganti dari AV node. Perlu evaluasi medis.");
                break;
            case "E":
                advice.append("Ventricular escape beat terdeteksi. Ini bisa mengindikasikan gangguan konduksi — segera periksakan.");
                break;
            default:
                advice.append("Terdeteksi pola detak tidak biasa. Segera jadwalkan pemeriksaan dengan dokter.");
        }

        // if (confidence < 0.70) {
        //     advice.append(" (Tingkat kepercayaan rendah — disarankan pemeriksaan ulang).");
        // }

        return advice.toString();
    }

    public static String getClassificationDescription(String label) {
    switch (label) {
        case "N":  return "Normal Sinus Beat";
        case "L":  return "Left Bundle Branch Block Beat";
        case "R":  return "Right Bundle Branch Block Beat";
        case "A":  return "Atrial Premature Beat";
        case "V":  return "Ventricular Premature Beat";
        case "F":  return "Fusion Beat";
        case "/":  return "Paced Beat";
        case "f":  return "Fusion of Paced and Normal Beat";
        case "j":  return "Junctional Escape Beat";
        case "E":  return "Ventricular Escape Beat";
        case "Other": return "Unclassified Beat";
        default:   return "Unknown Classification";
    }
}

    public static String generateHealthAdvice(String classification, double confidence, String patientContext) {
        String backendResp = tryGetAdviceFromBackend(classification, confidence, patientContext);
         if (backendResp != null && !backendResp.trim().isEmpty()) {
        return backendResp; // sementara untuk debug
    }
        return getFallbackAdvice(classification, confidence);
    }

    /**
     * Sekarang: coba backend dulu, kalau gagal baru fallback.
     * Ini menjaga placeholder/fallback tetap ada.
     */
    public static String generateAdvancedHealthAdvice(String classification, double confidence, String patientContext) {
    String backendResp = tryGetAdviceFromBackend(classification, confidence, patientContext);
    if (backendResp != null && !backendResp.trim().isEmpty()) {
        String advice = extractAdviceFromJson(backendResp);
        if (advice != null && !advice.trim().isEmpty()) {
            return advice;
        }
    }
    return getFallbackAdvice(classification, confidence);
}

    public static String calculateRiskLevel(String classification) {
        switch (classification) {
            case "N":
                return "Low";
            case "A":
            case "L":
            case "R":
            case "/":
            case "f":
            case "j":
                return "Medium";
            case "V":
            case "F":
            case "E":
                return "High";
            case "Other":
                return "Unknown";
            default:
                return "Unknown";
        }
    }
}
