/*
 * ECGPythonClient.java
 * Handles communication with Python Flask backend for ECG model classification
 */
package projectecg;

import projectecg.config.ApiConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * ECGPythonClient handles all communication with the Python Flask server.
 * It sends ECG signal data and RR intervals to the Python backend,
 * receives classification results, and handles errors gracefully.
 * 
 * Supports two input modes:
 * - CSV Import: Process pre-recorded ECG files
 * - Real-time Monitoring: Process streaming ECG data from devices
 */
public class ECGPythonClient {
    
    // Base URL dan API key dibaca dari ApiConfig (api_config.properties)
    private static final String PYTHON_SERVER_URL = ApiConfig.getBaseUrl();
    private static final String PREDICT_ENDPOINT = "/predict";
    private static final int SIGNAL_LENGTH = 216; // Model expects 216 samples
    private static final int TIMEOUT = ApiConfig.getReadTimeoutMs();
    
    /**
     * Sends ECG signal data to Python backend for classification.
     * 
     * @param signalData Array of ECG samples (should be 216 samples)
     * @param rrPrev Previous RR interval
     * @param rrNext Next RR interval
     * @return PredictionResult object containing classification label and confidence
     * @throws Exception if connection fails or classification fails
     */
    public static PredictionResult predictECG(double[] signalData, double rrPrev, double rrNext) throws Exception {
        
        // Validate input length
        if (signalData == null || signalData.length != SIGNAL_LENGTH) {
            throw new IllegalArgumentException(
                "Signal data must be exactly " + SIGNAL_LENGTH + " samples. Received: " + 
                (signalData == null ? "null" : signalData.length)
            );
        }
        
        // Build JSON payload manually
        StringBuilder jsonPayload = new StringBuilder();
        jsonPayload.append("{\"signal\": [");
        
        // Add signal data
        for (int i = 0; i < signalData.length; i++) {
            if (i > 0) jsonPayload.append(", ");
            jsonPayload.append(signalData[i]);
        }
        
        // Add RR intervals
        jsonPayload.append("], \"rr\": [");
        jsonPayload.append(rrPrev).append(", ").append(rrNext);
        jsonPayload.append("]}");
        
        // Send HTTP POST request
        return sendPostRequest(jsonPayload.toString());
    }
    
    /**
     * Sends HTTP POST request to Flask server and parses the response.
     * 
     * @param jsonPayload JSON string containing the request data
     * @return PredictionResult object with prediction and confidence
     * @throws Exception if request fails or response cannot be parsed
     */
    private static PredictionResult sendPostRequest(String jsonPayload) throws Exception {
        
        // Use URI instead of deprecated URL constructor
        java.net.URI uri = new java.net.URI(PYTHON_SERVER_URL + PREDICT_ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        
        try {
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-API-Key", ApiConfig.getApiKey());
            connection.setConnectTimeout(ApiConfig.getConnectTimeoutMs());
            connection.setReadTimeout(TIMEOUT);
            connection.setDoOutput(true);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            // Handle response
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Success: parse JSON response
                String response = readInputStream(connection.getInputStream());
                return parseResponse(response);
            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                // Bad request: read error message
                String errorResponse = readInputStream(connection.getErrorStream());
                String errorMsg = extractJsonField(errorResponse, "error");
                throw new Exception("Bad Request (400): " + errorMsg);
            } else if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                // Server error
                String errorResponse = readInputStream(connection.getErrorStream());
                String errorMsg = extractJsonField(errorResponse, "error");
                throw new Exception("Server Error (500): " + errorMsg);
            } else {
                throw new Exception("Unexpected HTTP response code: " + responseCode);
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Reads entire InputStream and converts to String.
     * 
     * @param inputStream the stream to read
     * @return String containing the stream contents
     * @throws IOException if reading fails
     */
    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
    
    /**
     * Parses JSON response from Python server.
     * Expected format: {"classification": "N", "confidence": 0.95, "class_label": "Normal", "mode": "model_classification"}
     * 
     * @param jsonResponse the JSON string from server
     * @return PredictionResult object
     * @throws Exception if JSON parsing fails
     */
    private static PredictionResult parseResponse(String jsonResponse) throws Exception {
        // Manual JSON parsing (no org.json library needed)
        // Try to read 'classification' field (new terminology)
        String classification = null;
        try {
            classification = extractJsonField(jsonResponse, "classification");
        } catch (Exception e) {
            // Fallback to 'prediction' for backward compatibility
            classification = extractJsonField(jsonResponse, "prediction");
        }
        
        String confidenceStr = extractJsonField(jsonResponse, "confidence");
        double confidence = Double.parseDouble(confidenceStr);
        
        return new PredictionResult(classification, confidence);
    }
    
    /**
     * Extracts value from simple JSON string.
     * Works for both string values (quoted) and numeric values.
     * 
     * @param jsonStr JSON string to parse
     * @param fieldName Field name to extract
     * @return String value of the field
     */
    private static String extractJsonField(String jsonStr, String fieldName) throws Exception {
        String searchKey = "\"" + fieldName + "\":";
        int keyIndex = jsonStr.indexOf(searchKey);
        
        if (keyIndex == -1) {
            throw new Exception("Field '" + fieldName + "' not found in JSON response");
        }
        
        int startIndex = keyIndex + searchKey.length();
        
        // Skip whitespace
        while (startIndex < jsonStr.length() && Character.isWhitespace(jsonStr.charAt(startIndex))) {
            startIndex++;
        }
        
        // Check if value is a string (quoted)
        if (jsonStr.charAt(startIndex) == '"') {
            // Extract quoted string
            startIndex++; // Skip opening quote
            int endIndex = startIndex;
            while (endIndex < jsonStr.length() && jsonStr.charAt(endIndex) != '"') {
                if (jsonStr.charAt(endIndex) == '\\') {
                    endIndex++; // Skip escaped character
                }
                endIndex++;
            }
            return jsonStr.substring(startIndex, endIndex);
        } else {
            // Extract numeric value
            int endIndex = startIndex;
            while (endIndex < jsonStr.length() && 
                   (Character.isDigit(jsonStr.charAt(endIndex)) || jsonStr.charAt(endIndex) == '.' || jsonStr.charAt(endIndex) == '-')) {
                endIndex++;
            }
            return jsonStr.substring(startIndex, endIndex);
        }
    }
    
    /**
     * Tests connection to Python server.
     * This method makes a simple request to verify server is running.
     * 
     * @return true if server is reachable, false otherwise
     */
    public static boolean testConnection() {
        try {
            // Use URI instead of deprecated URL constructor
            java.net.URI uri = new java.net.URI(PYTHON_SERVER_URL + "/health");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-API-Key", ApiConfig.getApiKey());
            connection.setConnectTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
            
        } catch (Exception e) {
            System.err.println("Python server connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts a segment of 216 samples from a longer signal.
     * Used when you have continuous ECG recording and need to extract
     * a window for prediction.
     * 
     * @param fullSignal The complete signal array
     * @param startIndex Starting index for extraction
     * @return Array of exactly 216 samples, or null if not possible
     */
    public static double[] extractSignalWindow(double[] fullSignal, int startIndex) {
        if (fullSignal == null || startIndex < 0) {
            return null;
        }
        
        if (startIndex + SIGNAL_LENGTH > fullSignal.length) {
            System.err.println("Not enough samples to extract window. Need at least " + 
                             (startIndex + SIGNAL_LENGTH) + ", have " + fullSignal.length);
            return null;
        }
        
        double[] window = new double[SIGNAL_LENGTH];
        System.arraycopy(fullSignal, startIndex, window, 0, SIGNAL_LENGTH);
        return window;
    }
    
    /**
     * Calculates average RR interval from signal using detected beats.
     * This is a simplified calculation. In production, use proper beat detection.
     * 
     * @param signalData ECG signal
     * @param fs Sampling frequency (Hz)
     * @return Array [rr_prev, rr_next] in seconds
     */

    public static double[] calculateRRIntervals(double[] signalData, double fs) {
        //  ECGAnalyzer untuk hitung RR interval sebenarnya
        ECGAnalyzer analyzer = new ECGAnalyzer(signalData, fs, "Bazett");
    
        double rrAvg = analyzer.getRRAvg();
        double rrMin = analyzer.getRRMin();
        double rrMax = analyzer.getRRMax();
    
        // Return [rr_prev, rr_next] dengan nilai REAL dari signal
    return new double[] { rrAvg, rrAvg };
}
    
    /**
     * Batch prediction for multiple ECG windows (useful for continuous monitoring).
     * Sends multiple 216-sample windows for prediction.
     * 
     * @param windows List of 216-sample windows
     * @param rrIntervals List of [rr_prev, rr_next] for each window
     * @return List of PredictionResult objects
     */
    public static List<PredictionResult> batchPredict(List<double[]> windows, List<double[]> rrIntervals) throws Exception {
        List<PredictionResult> results = new ArrayList<>();
        
        if (windows.size() != rrIntervals.size()) {
            throw new IllegalArgumentException("Windows and RR intervals count mismatch");
        }
        
        for (int i = 0; i < windows.size(); i++) {
            double[] rr = rrIntervals.get(i);
            PredictionResult result = predictECG(windows.get(i), rr[0], rr[1]);
            results.add(result);
        }
        
        return results;
    }
    
    // ==================== INNER CLASS: PredictionResult ====================
    
    /**
     * Holds the prediction result from Python backend.
     */
    public static class PredictionResult {
        public String prediction; // e.g., "N", "L", "R", "A", "V", "F", "/", "Other"
        public double confidence;  // 0.0 to 1.0
        
        public PredictionResult(String prediction, double confidence) {
            this.prediction = prediction;
            this.confidence = confidence;
        }
        
        @Override
        public String toString() {
            return String.format("Prediction: %s (Confidence: %.2f%%)", 
                               prediction, confidence * 100);
        }
        
        /**
         * Gets human-readable label for the prediction.
         * 
         * @return Descriptive label
         */
        public String getLabel() {
            switch (prediction) {
                case "N": return "Normal";
                case "L": return "Left Ventricular Hypertrophy";
                case "R": return "Right Ventricular Hypertrophy";
                case "A": return "Atrial Fibrillation";
                case "V": return "Ventricular Fibrillation";
                case "F": return "Flutter";
                case "/": return "Paced";
                case "Other": return "Other";
                default: return "Unknown";
            }
        }
        
        /**
         * Gets risk level based on prediction type.
         * 
         * @return Risk level: "Low", "Medium", "High", or "Critical"
         */
        public String getRiskLevel() {
            switch (prediction) {
                case "N": return "Low";
                case "L": return "Medium";
                case "R": return "Medium";
                case "A": return "High";
                case "V": return "Critical";
                case "F": return "Critical";
                case "/": return "Medium";
                default: return "Unknown";
            }
        }
    }
}
