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

    // 4. Notch Filter (Menghilangkan gangguan listrik 50Hz/60Hz)
    // Implementasi sederhana: Recursive filter
    public static double[] applyNotchFilter(double[] data, double fs, int notchFreq) {
        double[] output = new double[data.length];
        // Koefisien filter (Rumus IIR Notch sederhana)
        // Ini adalah pendekatan simplifikasi agar tidak terlalu berat codingnya
        // Untuk hasil medis akurat butuh koefisien Butterworth, tapi ini cukup untuk simulasi visual.
        
        // Kita gunakan trik "3-Point Moving Average" khusus frekuensi tertentu untuk simplifikasi
        // Atau biarkan raw dulu jika implementasi IIR terlalu rumit untukmu saat ini.
        // Guna mempermudah, kita pakai Moving Average window kecil yg mendekati periode gelombang noise
        int period = (int) (fs / notchFreq);
        if(period < 1) period = 1;
        
        // Sederhana: Rata-ratakan titik kini dengan titik satu periode lalu untuk cancel noise
        for(int i = 0; i < data.length; i++) {
            if(i >= period) {
                output[i] = (data[i] + data[i-period]) / 2.0; 
            } else {
                output[i] = data[i];
            }
        }
        return output;
    }
}
/**
 *
 * @author fizel
 */

    

