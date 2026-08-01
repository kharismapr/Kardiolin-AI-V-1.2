package projectecg.services;

import projectecg.models.Patient;
import projectecg.repositories.PatientRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * PatientService: Business Logic Layer
 * Provides high-level operations for patient management
 * Handles validation, business rules, and coordinates with repository
 */
public class PatientService {
    private static final Logger logger = Logger.getLogger(PatientService.class.getName());
    private final PatientRepository patientRepository;

    public PatientService() {
        this.patientRepository = new PatientRepository();
    }

    // ========== PATIENT REGISTRATION ==========

    /**
     * Register a new patient with basic information
     * @param patient Patient model containing all data
     * @return Patient ID if successful, -1 if failed
     */
    public int registerNewPatient(Patient patient) {
        // Validation
        if (patient == null || !validatePatientData(patient.getName(), patient.getSex(), patient.getBirthdate(), patient.getWeight(), patient.getHeight())) {
            return -1;
        }

        // Save to database
        int patientId = patientRepository.savePatient(patient);

        if (patientId > 0) {
            logger.info("✓ New patient registered. ID: " + patientId + ", Name: " + patient.getName());
        } else {
            logger.warning("✗ Failed to register patient: " + (patient != null ? patient.getName() : "null"));
        }

        return patientId;
    }

    // ========== ANALYSIS RESULTS ==========

    /**
     * Save ECG analysis results for a patient
     * Called from MainDashboard after analysis is complete
     * 
     * @param name Patient name
     * @param sex Patient sex
     * @param birthdate Patient birthdate
     * @param age Patient age
     * @param weight Patient weight
     * @param height Patient height
     * @param arrhythmiaLabel ECG classification label (N, A, V, L, R, etc.)
     * @param confidenceScore AI model confidence (0-1)
     * @param aiRecommendation AI health advice
     * @return Patient ID if successful, -1 if failed
     */
    /**
     * Save ECG analysis results for a patient with complete clinical parameters
     */
    public int savePatientWithAnalysis(String name, String sex, String birthdate,
                                       int age, double weight, double height,
                                       String arrhythmiaLabel, double confidenceScore,
                                       String aiRecommendation, int heartRate,
                                       double prInterval, double qrsDuration,
                                       double qtcInterval, double stDeviation) {
        // Validation
        if (!validatePatientData(name, sex, birthdate, weight, height)) {
            return -1;
        }

        if (!validateAnalysisData(arrhythmiaLabel, confidenceScore)) {
            return -1;
        }

        // Create patient with analysis results and clinical parameters
        Patient patient = new Patient(name, sex, birthdate, age, weight, height,
                                     arrhythmiaLabel, confidenceScore, aiRecommendation,
                                     heartRate, prInterval, qrsDuration, qtcInterval, stDeviation);

        // Save to database
        int patientId = patientRepository.savePatient(patient);

        if (patientId > 0) {
            logger.info("✓ Patient analysis saved. ID: " + patientId);
            logger.info("  - Classification: " + arrhythmiaLabel);
            logger.info("  - Confidence: " + String.format("%.2f%%", confidenceScore * 100));
        }

        return patientId;
    }

    /**
     * Backward-compatible save ECG analysis results (without clinical parameters)
     */
    public int savePatientWithAnalysis(String name, String sex, String birthdate,
                                       int age, double weight, double height,
                                       String arrhythmiaLabel, double confidenceScore,
                                       String aiRecommendation) {
        return savePatientWithAnalysis(name, sex, birthdate, age, weight, height,
                                      arrhythmiaLabel, confidenceScore, aiRecommendation,
                                      0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Update existing patient with new analysis results and clinical parameters
     * @param patientId Patient ID
     * @param arrhythmiaLabel New classification label
     * @param confidenceScore New confidence score
     * @param aiRecommendation New health advice
     * @param heartRate Heart rate (BPM)
     * @param prInterval PR interval (ms)
     * @param qrsDuration QRS duration (ms)
     * @param qtcInterval QTc interval (ms)
     * @param stDeviation ST segment deviation (mV)
     * @return true if update successful
     */
    public boolean updatePatientAnalysis(int patientId, String arrhythmiaLabel,
                                         double confidenceScore, String aiRecommendation,
                                         int heartRate, double prInterval, double qrsDuration,
                                         double qtcInterval, double stDeviation) {
        if (!validateAnalysisData(arrhythmiaLabel, confidenceScore)) {
            return false;
        }

        return patientRepository.updateAnalysisResults(patientId, arrhythmiaLabel,
                                                       confidenceScore, aiRecommendation,
                                                       heartRate, prInterval, qrsDuration,
                                                       qtcInterval, stDeviation);
    }

    /**
     * Backward-compatible update existing patient with new analysis results (without clinical parameters)
     */
    public boolean updatePatientAnalysis(int patientId, String arrhythmiaLabel,
                                         double confidenceScore, String aiRecommendation) {
        return updatePatientAnalysis(patientId, arrhythmiaLabel, confidenceScore, aiRecommendation,
                                     0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Delete patient by ID
     * @param patientId Patient ID
     * @return true if deletion successful
     */
    public boolean deletePatient(int patientId) {
        return patientRepository.deletePatient(patientId);
    }

    // ========== RETRIEVAL ==========

    /**
     * Get patient by ID
     * @param patientId Patient ID
     * @return Patient object or null
     */
    public Patient getPatient(int patientId) {
        return patientRepository.getPatientById(patientId);
    }

    /**
     * Search patients by name
     * @param name Patient name (partial match)
     * @return List of matching patients
     */
    public List<Patient> searchPatientsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warning("⚠ Search name is empty");
            return List.of();
        }
        return patientRepository.getPatientsByName(name.trim());
    }

    /**
     * Get all patients
     * @return List of all patients
     */
    public List<Patient> getAllPatients() {
        return patientRepository.getAllPatients();
    }

    /**
     * Get patients with abnormal ECG (non-Normal classification)
     * @return List of abnormal patients
     */
    public List<Patient> getAbnormalPatients() {
        return patientRepository.getAbnormalPatients();
    }

    /**
     * Get patients by ECG classification
     * @param label Classification label (N, A, V, L, R, F, /)
     * @return List of patients with that classification
     */
    public List<Patient> getPatientsByClassification(String label) {
        if (label == null || label.trim().isEmpty()) {
            logger.warning("⚠ Classification label is empty");
            return List.of();
        }
        return patientRepository.getPatientsByLabel(label.trim());
    }

    /**
     * Get patients analyzed within a date range
     * @param startDate Start date
     * @param endDate End date
     * @return List of patients analyzed in the range
     */
    public List<Patient> getPatientsByDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            logger.warning("⚠ Invalid date range");
            return List.of();
        }
        return patientRepository.getPatientsByDateRange(startDate, endDate);
    }

    // ========== STATISTICS ==========

    /**
     * Get total patient count
     * @return Total number of patients
     */
    public int getTotalPatientCount() {
        return patientRepository.getTotalPatientCount();
    }

    /**
     * Get count of abnormal patients
     * @return Number of abnormal patients
     */
    public int getAbnormalPatientCount() {
        return patientRepository.getAbnormalPatients().size();
    }

    /**
     * Print ECG classification statistics
     */
    public void printStatistics() {
        logger.info("=== Patient Database Statistics ===");
        logger.info("Total Patients: " + getTotalPatientCount());
        logger.info("Abnormal Cases: " + getAbnormalPatientCount());
        patientRepository.printLabelStatistics();
    }

    // ========== VALIDATION ==========

    /**
     * Validate patient basic data
     * @return true if valid
     */
    private boolean validatePatientData(String name, String sex, String birthdate,
                                        double weight, double height) {
        // Name validation
        if (name == null || name.trim().isEmpty()) {
            logger.warning("✗ Patient name is empty");
            return false;
        }

        if (name.length() > 100) {
            logger.warning("✗ Patient name too long (max 100 characters)");
            return false;
        }

        // Sex validation
        if (sex == null || (!sex.equalsIgnoreCase("Female") && !sex.equalsIgnoreCase("Male") && 
                            !sex.equalsIgnoreCase("Laki-Laki") && !sex.equalsIgnoreCase("Perempuan"))) {
            logger.warning("✗ Invalid sex value: " + sex);
            return false;
        }

        // Birthdate validation
        if (birthdate == null || birthdate.trim().isEmpty()) {
            logger.warning("✗ Invalid birthdate");
            return false;
        }

        return true;
    }

    /**
     * Validate analysis data
     * @return true if valid
     */
    private boolean validateAnalysisData(String arrhythmiaLabel, double confidenceScore) {
        // Label validation
        if (arrhythmiaLabel == null || arrhythmiaLabel.trim().isEmpty()) {
            logger.warning("✗ Arrhythmia label is empty");
            return false;
        }

        String[] validLabels = {"N","L","R","A","V","F","/","f","j","E","Other"};
        boolean isValidLabel = false;
        for (String label : validLabels) {
            if (arrhythmiaLabel.equals(label)) {
                isValidLabel = true;
                break;
            }
        }

        if (!isValidLabel) {
            logger.warning("✗ Invalid arrhythmia label: " + arrhythmiaLabel);
            return false;
        }

        // Confidence validation (0-1)
        if (confidenceScore < 0 || confidenceScore > 1) {
            logger.warning("✗ Invalid confidence score: " + confidenceScore);
            return false;
        }

        return true;
    }



    // ========== UTILITY ==========

    /**
     * Get risk level based on arrhythmia classification
     * @param label Arrhythmia label
     * @return Risk level (Low, Medium, High)
     */
    public String getRiskLevel(String label) {
        if (label == null) {
            return "Unknown";
        }

        switch (label) {
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
            default:
                return "Unknown";
        }
    }
    /**
     * Get classification description
     * @param label Classification label
     * @return Human-readable description
     */
    public String getClassificationDescription(String label) {
        if (label == null) {
            return "Unknown";
        }

        switch (label) {
            case "N":
                return "Normal Sinus Rhythm";
            case "L":
                return "Left Bundle Branch Block";
            case "R":
                return "Right Bundle Branch Block";
            case "A":
                return "Atrial Premature Beat";
            case "V":
                return "Ventricular Premature Beat";
            case "F":
                return "Fusion Beat";
            case "/":
                return "Paced Beat";
            case "f":
                return "Fusion of Paced and Normal Beat";
            case "j":
                return "Junctional Escape Beat";
            case "E":
                return "Ventricular Escape Beat";
            case "Other":
            default:
                return "Other Abnormality";
        }
    }
}
