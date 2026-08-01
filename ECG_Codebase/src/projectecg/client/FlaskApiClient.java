package projectecg.client;

import projectecg.config.ApiConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FlaskApiClient — HTTP client generik untuk berkomunikasi dengan Flask BE.
 *
 * Semua akses database (yang tadinya JDBC) sekarang lewat class ini.
 * Base URL dan API key dibaca dari ApiConfig (→ api_config.properties).
 *
 * Tidak ada ketergantungan library JSON eksternal: request/response
 * menggunakan raw String JSON karena org.json sudah ada di lib/.
 */
public class FlaskApiClient {

    private static final Logger logger = Logger.getLogger(FlaskApiClient.class.getName());

    private final String baseUrl;
    private final String apiKey;
    private final int connectTimeout;
    private final int readTimeout;

    /** Singleton instance */
    private static volatile FlaskApiClient instance;

    private FlaskApiClient() {
        this.baseUrl        = ApiConfig.getBaseUrl();
        this.apiKey         = ApiConfig.getApiKey();
        this.connectTimeout = ApiConfig.getConnectTimeoutMs();
        this.readTimeout    = ApiConfig.getReadTimeoutMs();
        logger.info("✓ FlaskApiClient ready. Base URL: " + baseUrl);
    }

    public static FlaskApiClient getInstance() {
        if (instance == null) {
            synchronized (FlaskApiClient.class) {
                if (instance == null) {
                    instance = new FlaskApiClient();
                }
            }
        }
        return instance;
    }

    // =========================================================================
    // Public HTTP helpers
    // =========================================================================

    /** HTTP GET — returns response body as String, or null on error. */
    public String get(String path) {
        return get(path, readTimeout);
    }

    public String get(String path, int overrideReadTimeout) {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(baseUrl + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            addCommonHeaders(conn);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(overrideReadTimeout);

            int code = conn.getResponseCode();
            String body = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (code >= 400) {
                logger.warning("GET " + path + " → HTTP " + code + ": " + body);
            }
            return body;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GET " + path + " failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** HTTP POST — body is a JSON string, returns response body. */
    public String post(String path, String jsonBody) {
        return sendWithBody("POST", path, jsonBody);
    }

    /** HTTP PUT — body is a JSON string, returns response body. */
    public String put(String path, String jsonBody) {
        return sendWithBody("PUT", path, jsonBody);
    }

    /** HTTP DELETE — returns response body. */
    public String delete(String path) {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(baseUrl + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("DELETE");
            addCommonHeaders(conn);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            int code = conn.getResponseCode();
            String body = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (code >= 400) {
                logger.warning("DELETE " + path + " → HTTP " + code + ": " + body);
            }
            return body;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DELETE " + path + " failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String sendWithBody(String method, String path, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(baseUrl + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod(method);
            addCommonHeaders(conn);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            if (jsonBody != null && !jsonBody.isEmpty()) {
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            int code = conn.getResponseCode();
            String body = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (code >= 400) {
                logger.warning(method + " " + path + " → HTTP " + code + ": " + body);
            }
            return body;
        } catch (Exception e) {
            logger.log(Level.SEVERE, method + " " + path + " failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void addCommonHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-API-Key", apiKey);
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /** Quick connectivity check — returns true if Flask /health responds. */
    public boolean testConnection() {
        try {
            String resp = get("/health", 5000);
            return resp != null && resp.contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        }
    }
}
