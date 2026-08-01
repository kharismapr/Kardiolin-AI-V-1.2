package projectecg.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class EnvConfig {
    private static final Logger logger = Logger.getLogger(EnvConfig.class.getName());
    private static final Map<String, String> envVars = new HashMap<>();
    private static boolean isLoaded = false;

    /**
     * Load environment variables from .env file
     * File location: ECG_Codebase/.env
     */
    public static void loadEnv() {
        if (isLoaded) {
            return; // Already loaded, don't reload
        }

        try {
            // Try multiple possible locations for .env file
            String[] possiblePaths = {
                ".env",
                "ECG_Codebase/.env",
                "../../../.env"
            };

            String envFilePath = null;
            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    envFilePath = path;
                    break;
                }
            }

            if (envFilePath == null) {
                logger.warning("⚠ .env file not found in any expected location");
                return;
            }

            // Read .env file
            Files.readAllLines(Paths.get(envFilePath)).forEach(line -> {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    return;
                }

                // Parse KEY=VALUE format
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    envVars.put(key, value);
                }
            });

            isLoaded = true;
            logger.info("✓ Environment variables loaded from: " + envFilePath);
            logger.info("✓ Loaded " + envVars.size() + " environment variables");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "✗ Error loading .env file", e);
        }
    }

    public static String get(String key) {
        if (!isLoaded) {
            loadEnv();
        }
        return envVars.getOrDefault(key, null);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public static void printAll() {
        if (!isLoaded) {
            loadEnv();
        }
        logger.info("=== Loaded Environment Variables ===");
        envVars.forEach((key, value) -> {
            // Mask sensitive values
            String displayValue = key.toLowerCase().contains("password") || key.toLowerCase().contains("key")
                    ? "***MASKED***"
                    : value;
            logger.info(key + " = " + displayValue);
        });
    }
}
