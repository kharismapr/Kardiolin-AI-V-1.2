/*
 * CSV Exporter Utility Class
 * Purpose: Export ECG analysis results to CSV file with proper formatting
 * Columns: Patient Info, Classification Result, Confidence, Risk Level, Signal Metrics
 */
package projectecg;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CSVExporter: Utility class to export ECG analysis results to CSV format
 * ROLE: Handles CSV file creation with patient data and analysis results
 * WHY: Centralized CSV generation prevents code duplication and ensures consistent formatting
 */
public class CSVExporter {
    
    /**
     * Export analysis results to CSV file
     * 
     * @param file - Target file path
     * @param patientName - Patient's name
     * @param patientID - Patient's ID
     * @param classification - ECG classification result (e.g., "Normal", "Abnormal")
     * @param confidence - Confidence percentage (0-100)
     * @param riskLevel - Risk level (Low, Medium, High)
     * @param heartRate - Heart rate in BPM
     * @param rPeaks - Number of R peaks detected
     * @param pPeaks - Number of P peaks detected
     * @param tPeaks - Number of T peaks detected
     * @param rrInterval - RR interval in milliseconds
     * @return true if export successful, false otherwise
     */
    public static boolean exportToCSV(
            File file,
            String patientName,
            String patientID,
            String classification,
            double confidence,
            String riskLevel,
            int heartRate,
            int rPeaks,
            int pPeaks,
            int tPeaks,
            double rrInterval) {
        
        return exportToCSVWithDemographics(
            file, patientName, patientID, "N/A", "N/A", "0", "0", "0",
            classification, confidence, riskLevel,
            heartRate, rPeaks, pPeaks, tPeaks, rrInterval, "",
            0, 0, 0  // Default values for PR, QT, QTc
        );
    }
    
    /**
     * ENHANCED: Export with complete patient demographics
     * WHY: Clinical reports need comprehensive patient context
     * 
     * @param patientSex - Patient's sex
     * @param patientBirthdate - Patient's birthdate
     * @param patientAge - Patient's age in years
     * @param patientHeight - Patient's height in cm
     * @param patientWeight - Patient's weight in kg
     * @param healthAdvice - Generated health advice (can be from GenAI)
     * @param prInterval - PR interval in ms (P-R segment duration)
     * @param qtInterval - QT interval in ms (Q-T segment duration)
     * @param qtcInterval - QTc interval in ms (corrected QT using Bazett formula)
     */
    public static boolean exportToCSVWithDemographics(
            File file,
            String patientName,
            String patientID,
            String patientSex,
            String patientBirthdate,
            String patientAge,
            String patientHeight,
            String patientWeight,
            String classification,
            double confidence,
            String riskLevel,
            int heartRate,
            int rPeaks,
            int pPeaks,
            int tPeaks,
            double rrInterval,
            String healthAdvice,
            double prInterval,
            double qtInterval,
            double qtcInterval) {
        
        try (FileWriter writer = new FileWriter(file)) {
            // Write header with metadata
            writer.append("ECG ANALYSIS REPORT\n");
            writer.append("Export Date,").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
            writer.append("\n");
            
            // SECTION 1: PATIENT INFORMATION (ENHANCED)
            writer.append("PATIENT INFORMATION\n");
            writer.append("Patient Name,").append(patientName != null ? patientName : "N/A").append("\n");
            writer.append("Patient ID,").append(patientID != null ? patientID : "N/A").append("\n");
            writer.append("Sex,").append(patientSex != null ? patientSex : "N/A").append("\n");
            writer.append("Birthdate,").append(patientBirthdate != null ? patientBirthdate : "N/A").append("\n");
            
            // Parse and display age
            int age = 0;
            try {
                age = Integer.parseInt(patientAge);
            } catch (NumberFormatException e) {
                age = 0;
            }
            if (age > 0) {
                writer.append("Age (years),").append(String.valueOf(age)).append("\n");
            }
            
            // Parse and display height/weight
            int height = 0;
            int weight = 0;
            try {
                height = Integer.parseInt(patientHeight);
            } catch (NumberFormatException e) {
                height = 0;
            }
            try {
                weight = Integer.parseInt(patientWeight);
            } catch (NumberFormatException e) {
                weight = 0;
            }
            
            if (height > 0) {
                writer.append("Height (cm),").append(String.valueOf(height)).append("\n");
            }
            if (weight > 0) {
                writer.append("Weight (kg),").append(String.valueOf(weight)).append("\n");
            }
            if (height > 0 && weight > 0) {
                double bmi = (weight * 10000.0) / (height * height);
                writer.append("BMI,").append(String.format(java.util.Locale.US, "%.2f", bmi)).append("\n");
            }
            writer.append("\n");
            
            // SECTION 2: CLASSIFICATION RESULTS
            writer.append("CLASSIFICATION RESULT\n");
            writer.append("Classification,").append(classification != null ? classification : "N/A").append("\n");
            writer.append("Confidence Rate (%)").append(",").append(String.format(java.util.Locale.US, "%.2f", confidence)).append("\n");
            // Use GeminiHealthAdvisor for consistent risk level calculation
            String calculatedRiskLevel = GeminiHealthAdvisor.calculateRiskLevel(classification);
            writer.append("Risk Level,").append(calculatedRiskLevel).append("\n");
            writer.append("\n");
            
            // SECTION 3: SIGNAL MEASUREMENTS (Task 3: Removed Heart Rate, Added Intervals)
            writer.append("SIGNAL MEASUREMENTS\n");
            writer.append("R Peaks Count,").append(String.valueOf(rPeaks)).append("\n");
            writer.append("P Peaks Count,").append(String.valueOf(pPeaks)).append("\n");
            writer.append("T Peaks Count,").append(String.valueOf(tPeaks)).append("\n");
            writer.append("RR Interval (ms),").append(String.format(java.util.Locale.US, "%.2f", rrInterval)).append("\n");
            
            // SECTION 3.5: INTERVAL MEASUREMENTS (Task 3: Added QTc and Intervals)
            writer.append("\n");
            writer.append("INTERVAL MEASUREMENTS\n");
            if (prInterval > 0) {
                writer.append("PR Interval (ms),").append(String.format(java.util.Locale.US, "%.2f", prInterval)).append("\n");
            }
            if (qtInterval > 0) {
                writer.append("QT Interval (ms),").append(String.format(java.util.Locale.US, "%.2f", qtInterval)).append("\n");
            }
            if (qtcInterval > 0) {
                writer.append("QTc Interval (ms) [Bazett],").append(String.format(java.util.Locale.US, "%.2f", qtcInterval)).append("\n");
                // QTc interpretation
                String qtcInterpretation = "";
                if (qtcInterval < 340) {
                    qtcInterpretation = "Short (< 340 ms)";
                } else if (qtcInterval <= 460) {
                    qtcInterpretation = "Normal (340-460 ms)";
                } else if (qtcInterval <= 500) {
                    qtcInterpretation = "Borderline (460-500 ms)";
                } else {
                    qtcInterpretation = "Prolonged (> 500 ms) - Risk of arrhythmia";
                }
                writer.append("QTc Interpretation,").append(qtcInterpretation).append("\n");
            }
            
            // Calculate QRS Duration estimate (R-Q interval, typically 80-120ms)
            double qrsDuration = rrInterval * 0.15;  // Rough estimate: ~15% of RR interval
            writer.append("QRS Duration (ms) [estimated],").append(String.format(java.util.Locale.US, "%.2f", qrsDuration)).append("\n");
            writer.append("\n");
            
            // SECTION 4: HEALTH ADVICE
            writer.append("HEALTH ADVICE\n");
            String finalAdvice;
            if (healthAdvice != null && !healthAdvice.isEmpty()) {
                finalAdvice = healthAdvice;
            } else {
                // Use GeminiHealthAdvisor for AI-powered advice if not provided
                finalAdvice = GeminiHealthAdvisor.generateHealthAdvice(classification, confidence / 100.0, healthAdvice);
            }
            // Replace newlines with pipe characters for CSV compatibility
            String adviceForCSV = finalAdvice.replace("\n", " | ");
            writer.append("Recommendation,").append(adviceForCSV).append("\n");
            
            writer.flush();
            return true;
            
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════════════════
    // NEW METHOD: Export Raw ECG Signal Data to CSV
    // PURPOSE: Allows downloading recorded ECG data (12-lead) from real-time recording or file
    // FORMAT: Time(ms), Lead_I, Lead_II, Lead_III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6
    // ════════════════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Export raw 12-lead ECG signal data to CSV file
     * 
     * @param file - Target file path
     * @param leadsData - List of 12 double arrays containing ECG signal data
     * @param samplingRate - Sampling frequency in Hz (e.g., 125.0, 250.0, 500.0)
     * @param patientID - Patient's ID (for header info)
     * @param patientName - Patient's name (for header info)
     * @return true if export successful, false otherwise
     */
    public static boolean exportSignalToCSV(
            File file,
            java.util.List<double[]> leadsData,
            double samplingRate,
            String patientID,
            String patientName) {
        
        if (leadsData == null || leadsData.isEmpty()) {
            System.err.println("CSVExporter: No ECG data to export.");
            return false;
        }
        
        // Validate we have at least 3 leads (minimum for basic ECG)
        if (leadsData.size() < 3) {
            System.err.println("CSVExporter: Expected at least 3 leads, got " + leadsData.size());
            return false;
        }
        
        try (FileWriter writer = new FileWriter(file)) {

            // === HEADER SECTION ===
            writer.append("ECG SIGNAL DATA EXPORT\n");
            writer.append("Export Date,").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
            writer.append("Patient ID,").append(patientID != null && !patientID.isEmpty() ? patientID : "N/A").append("\n");
            writer.append("Patient Name,").append(patientName != null && !patientName.isEmpty() ? patientName : "N/A").append("\n");
            writer.append("Sampling Rate (Hz),").append(String.valueOf(samplingRate)).append("\n");
            
            int numSamples = leadsData.get(0).length;
            writer.append("Total Samples,").append(String.valueOf(numSamples)).append("\n");
            
            double durationSeconds = numSamples / samplingRate;
            writer.append("Duration (seconds),").append(String.format(java.util.Locale.US, "%.2f", durationSeconds)).append("\n");
            writer.append("\n");
            
            // === COLUMN HEADERS ===
            // Dynamic lead naming based on actual data size
            String[] allLeadNames = {"Lead_I", "Lead_II", "Lead_III", "aVR", "aVL", "aVF", 
                                  "V1", "V2", "V3", "V4", "V5", "V6"};
            int numLeads = leadsData.size();
            
            writer.append("Time_ms");
            for (int i = 0; i < numLeads; i++) {
                String leadName = (i < allLeadNames.length) ? allLeadNames[i] : "Lead_" + (i + 1);
                writer.append(",").append(leadName);
            }
            writer.append("\n");
            
            // === DATA ROWS ===
            // Calculate time interval between samples in milliseconds
            double intervalMs = 1000.0 / samplingRate;
            
            for (int sampleIdx = 0; sampleIdx < numSamples; sampleIdx++) {
                // Time in milliseconds
                double timeMs = sampleIdx * intervalMs;
                writer.append(String.format(java.util.Locale.US, "%.2f", timeMs));
                
                // Write each lead's value (dynamic based on actual lead count)
                for (int leadIdx = 0; leadIdx < numLeads; leadIdx++) {
                    double[] leadData = leadsData.get(leadIdx);
                    if (sampleIdx < leadData.length) {
                        writer.append(",").append(String.format(java.util.Locale.US, "%.6f", leadData[sampleIdx]));
                    } else {
                        writer.append(",0.0"); // Fallback if data is shorter
                    }
                }
                writer.append("\n");
            }
            
            writer.flush();
            System.out.println("CSVExporter: Successfully exported " + numSamples + " samples to " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            System.err.println("CSVExporter: Error writing signal CSV file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * OVERLOADED: Export with both raw and processed (filtered) data
     * WHY: Clinicians may want to compare raw vs filtered signals
     * 
     * @param file - Target file path
     * @param rawData - List of 12 double arrays containing raw ECG signal
     * @param processedData - List of 12 double arrays containing filtered ECG signal
     * @param samplingRate - Sampling frequency in Hz
     * @param patientID - Patient's ID
     * @param patientName - Patient's name
     * @param exportProcessed - If true, exports processed data; if false, exports raw data
     * @return true if export successful, false otherwise
     */
    public static boolean exportSignalToCSV(
            File file,
            java.util.List<double[]> rawData,
            java.util.List<double[]> processedData,
            double samplingRate,
            String patientID,
            String patientName,
            boolean exportProcessed) {
        
        // Choose which data to export based on flag
        java.util.List<double[]> dataToExport = exportProcessed ? processedData : rawData;
        return exportSignalToCSV(file, dataToExport, samplingRate, patientID, patientName);
    }
}
