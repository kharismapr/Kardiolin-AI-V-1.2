package projectecg.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * ApiConfig — Satu-satunya tempat konfigurasi komunikasi ke Flask BE.
 *
 * Cara kerja:
 *  1. Load dari file "api_config.properties" di classpath (src/projectecg/config/).
 *  2. Jika tidak ditemukan, gunakan nilai default hardcoded (untuk dev lokal).
 *
 * Untuk mengubah base URL atau API key: edit api_config.properties, bukan file ini.
 */
public class ApiConfig {

    private static final Logger logger = Logger.getLogger(ApiConfig.class.getName());
    private static final String CONFIG_FILE = "/projectecg/config/api_config.properties";

    // -----------------------------------------------------------------------
    // Default values (fallback jika properties tidak ditemukan)
    // -----------------------------------------------------------------------
    private static final String DEFAULT_BASE_URL = "http://localhost:5000";
    private static final String DEFAULT_API_KEY  = "ecg-kardiolin-secret-2026";

    private static final Properties props = new Properties();

    static {
        try (InputStream is = ApiConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                logger.info("✓ ApiConfig loaded from " + CONFIG_FILE);
            } else {
                logger.warning("⚠ " + CONFIG_FILE + " not found — using defaults.");
            }
        } catch (IOException e) {
            logger.warning("⚠ Failed to load ApiConfig: " + e.getMessage());
        }
    }

    /** Base URL Flask backend. Contoh: http://localhost:5000 atau http://192.168.1.10:5000 */
    public static String getBaseUrl() {
        return props.getProperty("flask.base_url", DEFAULT_BASE_URL).trim();
    }

    /** API key yang dikirim di header X-API-Key setiap request. */
    public static String getApiKey() {
        return props.getProperty("flask.api_key", DEFAULT_API_KEY).trim();
    }

    /** Timeout koneksi dalam milidetik. */
    public static int getConnectTimeoutMs() {
        try {
            return Integer.parseInt(props.getProperty("flask.connect_timeout_ms", "5000").trim());
        } catch (NumberFormatException e) {
            return 5000;
        }
    }

    /** Timeout baca response dalam milidetik. */
    public static int getReadTimeoutMs() {
        try {
            return Integer.parseInt(props.getProperty("flask.read_timeout_ms", "15000").trim());
        } catch (NumberFormatException e) {
            return 15000;
        }
    }

    private ApiConfig() { /* utility class */ }
}
