/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package projectecg;
public class ECGFilters {
    // 1. Moving Average (Berfungsi sebagai Low-Pass Filter sederhana)
    // Bagus untuk menghilangkan noise otot (EMG) frekuensi tinggi
    public static double[] applyMovingAverage(double[] data, int windowSize) {
        double[] output = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            double sum = 0;
            int count = 0;
            for (int j = i; j > i - windowSize; j--) {
                if (j >= 0) {
                    sum += data[j]; 
                    count++;
                }

                
            }
            output[i] = sum / count;
        }
        return output;
    }

    // 2. High-Pass Filter sederhana (Menghilangkan Baseline Wander)
    // Rumus: y[i] = x[i] - MovingAverage(x[i])
    public static double[] applyHighPass(double[] data, double fs) {
        // Window besar (misal 0.5 detik) untuk mengambil baseline
        int window = (int) (0.5 * fs); 
        double[] baseline = applyMovingAverage(data, window);
        double[] output = new double[data.length];
        for(int i=0; i<data.length; i++) {
            output[i] = data[i] - baseline[i];
        }
        return output;
    }

    // 3. Bandpass Filter (Gabungan Low-Pass + High-Pass)
    // Standar ECG monitor: 0.5 Hz - 40 Hz
    public static double[] applyBandpass(double[] data, double fs) {
        // Langkah 1: Hilangkan noise frekuensi sangat tinggi (Lowpass pakai Moving Avg kecil)
        double[] lowPassed = applyMovingAverage(data, 5); 
        // Langkah 2: Hilangkan baseline wander (Highpass)
        return applyHighPass(lowPassed, fs);
    }

    // EMG Filter: Removes muscle artifact noise (20-500 Hz) while preserving QRS morphology
    public static double[] applyEMGFilter(double[] data, int windowSize) {
        double[] output = new double[data.length];
        
        // Generate triangular weights (better than uniform for EMG)
        double[] weights = generateTriangularWeights(windowSize);
        
        for (int i = 0; i < data.length; i++) {
            double weightedSum = 0;
            double weightTotal = 0;
            
            int startIdx = Math.max(0, i - windowSize + 1);
            
            for (int j = startIdx; j <= i; j++) {
                int weightIdx = j - startIdx;
                if (weightIdx < weights.length) {
                    weightedSum += data[j] * weights[weightIdx];
                    weightTotal += weights[weightIdx];
                }
            }
            
            output[i] = (weightTotal > 0) ? weightedSum / weightTotal : data[i];
        }
        
        return output;
    }
    
    // Helper: Generate triangular weights for EMG filter
    private static double[] generateTriangularWeights(int windowSize) {
        double[] weights = new double[windowSize];
        
        for (int i = 0; i < windowSize; i++) {
            // Triangular window: increases linearly toward most recent sample
            weights[i] = i + 1;
        }
        
        return weights;
    }

    // 4. Notch Filter (Menghilangkan gangguan listrik 50Hz/60Hz)
    // Implementasi sederhana: Recursive filter
public static double[] applyNotchFilter(double[] data, double fs, int notchFreq) {
    double[] output = new double[data.length];
    
    // Calculate period in samples
    double period = fs / notchFreq;
    
    // Simple 3-tap notch approximation
    // y[n] = x[n] - 2*cos(2πf₀/fs)*x[n-1] + x[n-2]
    double omega = 2 * Math.PI * notchFreq / fs;
    double coeff = 2 * Math.cos(omega);
    
    for(int i = 0; i < data.length; i++) {
        if(i < 2) {
            output[i] = data[i];
        } else {
            output[i] = data[i] - coeff * data[i-1] + data[i-2];
        }
    }
    
    // Normalize to prevent amplitude changes
    double[] normalized = new double[output.length];
    double scale = 1.0 / (1 - coeff + 1); // Approx gain correction
    for(int i = 0; i < output.length; i++) {
        normalized[i] = output[i] * scale;
    }
    return normalized;
    }

// 5. Butterworth filter
public static double[] applyButterworthLowPass(double[] data, double fs, double cutoff) {
        double omega = 2 * Math.PI * cutoff / fs;
        double k = Math.tan(omega / 2.0);
        double q = 1.0 / Math.sqrt(2.0); // faktor redaman Butterworth
 
        double a0 = k * k + k / q + 1;
        double b0 = (k * k) / a0;
        double b1 = 2 * b0;
        double b2 = b0;
        double a1 = 2 * (k * k - 1) / a0;
        double a2 = (k * k - k / q + 1) / a0;
 
        return runBiquad(data, b0, b1, b2, a1, a2);
    }
 
    /**
     * @param data    sinyal ECG mentah
     * @param fs      sampling rate (Hz)
     * @param cutoff  frekuensi cutoff (Hz), misal 0.5 Hz untuk ECG
     */
    public static double[] applyButterworthHighPass(double[] data, double fs, double cutoff) {
        double omega = 2 * Math.PI * cutoff / fs;
        double k = Math.tan(omega / 2.0);
        double q = 1.0 / Math.sqrt(2.0); // faktor redaman Butterworth
 
        double a0 = k * k + k / q + 1;
        double b0 = 1.0 / a0;
        double b1 = -2 * b0;
        double b2 = b0;
        double a1 = 2 * (k * k - 1) / a0;
        double a2 = (k * k - k / q + 1) / a0;
 
        return runBiquad(data, b0, b1, b2, a1, a2);
    }
 
    /**
     * @param data       sinyal ECG mentah
     * @param fs         sampling rate (Hz)
     * @param lowCutoff  cutoff bawah (Hz), misal 0.5 Hz
     * @param highCutoff cutoff atas (Hz), misal 40 Hz
     */
    public static double[] applyButterworthBandpass(double[] data, double fs, double lowCutoff, double highCutoff) {
        double[] highPassed = applyButterworthHighPass(data, fs, lowCutoff);
        return applyButterworthLowPass(highPassed, fs, highCutoff);
    }
 
    // Filter biquad IIR orde-2 standar
    private static double[] runBiquad(double[] data, double b0, double b1, double b2, double a1, double a2) {
        double[] output = new double[data.length];
        for (int n = 0; n < data.length; n++) {
            double xn = data[n];
            double xn1 = (n - 1 >= 0) ? data[n - 1] : 0;
            double xn2 = (n - 2 >= 0) ? data[n - 2] : 0;
            double yn1 = (n - 1 >= 0) ? output[n - 1] : 0;
            double yn2 = (n - 2 >= 0) ? output[n - 2] : 0;
 
            output[n] = b0 * xn + b1 * xn1 + b2 * xn2 - a1 * yn1 - a2 * yn2;
        }
        return output;
    }

}
/**
 *
 * @author fizel
 */

    

