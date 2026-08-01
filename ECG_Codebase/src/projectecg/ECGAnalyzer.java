/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package projectecg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ECGAnalyzer {
    
    private double[] signalData;
    private double fs;
    private String selectedFormula = "-"; // Sampling Frequency (Hz)
    
    // Lists untuk menyimpan lokasi index (urutan data ke-berapa)
    private List<Integer> rIndices = new ArrayList<>();
    private List<Integer> pIndices = new ArrayList<>();
    private List<Integer> tIndices = new ArrayList<>();
    private List<Integer> qIndices = new ArrayList<>();
    private List<Integer> sIndices = new ArrayList<>();
    private List<Integer> anomalyIndices = new ArrayList<>();
    
    // List untuk Interval (dalam detik)
    private List<Double> rrIntervals = new ArrayList<>();

    private boolean usePanTompkins = false;

    public ECGAnalyzer(double[] signalData, double fs, String formula) {
        this(signalData, fs, formula, false);
    }
    public ECGAnalyzer(double[] signalData, double fs, String formula, boolean usePanTompkins) {
        this.signalData = signalData;
        this.fs = fs;
        this.usePanTompkins = usePanTompkins;
        
        if (formula != null) {
            if (formula.contains("Bazett")) this.selectedFormula = "Bazett";
            else if (formula.contains("Fridericia")) this.selectedFormula = "Fridericia";
            else if (formula.contains("Framingham")) this.selectedFormula = "Framingham";
            else if (formula.contains("Hodges")) this.selectedFormula = "Hodges";
            else this.selectedFormula = "-"; 
        }
        
        analyzeSignal(); 
    }

    private void analyzeSignal() {
        if (usePanTompkins) {
            detectRPeaksPanTompkins();
            detectOtherWavesAdvanced();
        } else {
            detectRPeaks(); // Naive
            detectOtherWaves(); // Naive
        }
        calculateRR();
        detectAnomalyPeaks();
    }

    // ==========================================
    // 1. METODE NAIVE (Default / Visual Accuracy)
    // ==========================================
    private void detectRPeaks() {
        double maxVal = -Double.MAX_VALUE;
        double minVal = Double.MAX_VALUE;
        for (double val : signalData) {
            if (val > maxVal) maxVal = val;
            if (val < minVal) minVal = val;
        }
        double range = maxVal - minVal;
        if (range <= 0) return;
        
        // Threshold dinamis berbasis 60% tinggi dari nilai minimum
        double threshold = minVal + range * 0.6; 
        int minDistance = (int) (0.35 * fs); 
        int lastPeak = -minDistance;
        
        for (int i = 1; i < signalData.length - 1; i++) {
            if (signalData[i] > threshold && 
                signalData[i] > signalData[i-1] && 
                signalData[i] > signalData[i+1]) {
                
                if ((i - lastPeak) > minDistance) {
                    rIndices.add(i);
                    lastPeak = i;
                }
            }
        }
    }

    private void detectOtherWaves() {
        // Window pencarian default dalam sampel (P: 200ms, T: 400ms)
        int searchWindowP = (int)(0.20 * fs); 
        int searchWindowT = (int)(0.40 * fs); 
        
        qIndices.clear();
        sIndices.clear();
        pIndices.clear();
        tIndices.clear();
        
        for (int rIdx : rIndices) {
            int rIdxPos = rIndices.indexOf(rIdx);
            int prevR = (rIdxPos > 0) ? rIndices.get(rIdxPos - 1) : -1;
            int nextR = (rIdxPos < rIndices.size() - 1) ? rIndices.get(rIdxPos + 1) : -1;
            
            // --- CARI Q (Minimum lokal sebelum R) ---
            int qIdx = rIdx;
            double minQ = signalData[rIdx];
            // Batasi pencarian Q agar tidak melewati batas tengah dari beat sebelumnya
            int limitQ = (prevR != -1) ? (prevR + rIdx) / 2 : 0;
            int qStart = Math.max(limitQ, rIdx - (int)(0.08 * fs));
            for (int i = rIdx; i >= qStart; i--) { 
                if (signalData[i] < minQ) {
                    minQ = signalData[i];
                    qIdx = i;
                }
            }
            qIndices.add(qIdx);

            // --- CARI S (Minimum lokal setelah R) ---
            int sIdx = rIdx;
            double minS = signalData[rIdx];
            // Batasi pencarian S agar tidak melewati batas tengah dari beat selanjutnya
            int limitS = (nextR != -1) ? (rIdx + nextR) / 2 : signalData.length - 1;
            int sEnd = Math.min(limitS, rIdx + (int)(0.08 * fs));
            for (int i = rIdx; i <= sEnd; i++) { 
                if (signalData[i] < minS) {
                    minS = signalData[i];
                    sIdx = i;
                }
            }
            sIndices.add(sIdx);

            // --- CARI P (Puncak sebelum Q) ---
            int pIdx = -1;
            double maxP = -Double.MAX_VALUE;
            // Batasi agar pencarian P tidak menyeberang ke T-wave dari beat sebelumnya
            int limitP = (prevR != -1) ? (prevR + rIdx) / 2 : 0;
            int startP = Math.max(limitP, qIdx - searchWindowP);
            int endP = qIdx - (int)(0.02 * fs); // Jeda 20ms agar tidak mendeteksi lereng QRS
            if (endP > startP) {
                double baseline = signalData[startP]; // baseline diambil di awal jendela (isoelektrik)
                for (int i = startP; i < endP; i++) {
                    double dev = Math.abs(signalData[i] - baseline);
                    if (dev > maxP) {
                        maxP = dev;
                        pIdx = i;
                    }
                }
            }
            if (pIdx != -1) pIndices.add(pIdx);

            // --- CARI T (Puncak setelah S) ---
            int tIdx = -1;
            double maxT = -Double.MAX_VALUE;
            // Batasi agar pencarian T tidak menyeberang ke P-wave dari beat selanjutnya
            int limitT = (nextR != -1) ? (rIdx + nextR) / 2 : signalData.length - 1;
            int endT = Math.min(limitT, sIdx + searchWindowT);
            int startT = sIdx + (int)(0.04 * fs); // Jeda 40ms agar tidak mendeteksi lereng QRS
            if (endT > startT) {
                double baseline = signalData[endT]; // baseline diambil di akhir jendela (isoelektrik)
                for (int i = startT; i < endT; i++) {
                    double dev = Math.abs(signalData[i] - baseline);
                    if (dev > maxT) {
                        maxT = dev;
                        tIdx = i;
                    }
                }
            }
            if (tIdx != -1) tIndices.add(tIdx);
        }
    }

    // ==========================================
    // 2. METODE PAN-TOMPKINS DSP (Anti-Noise)
    // ==========================================
    private void detectRPeaksPanTompkins() {
        int n = signalData.length;
        if (n < 100) return;

        double targetFs = 200.0;
        int m = (int) (n * (targetFs / fs));
        if (m < 100) return;

        // 1. Resample sinyal input ke 200 Hz (frekuensi kerja desain filter Pan-Tompkins)
        double[] resampledSignal = new double[m];
        for (int i = 0; i < m; i++) {
            double origIdx = i * (fs / targetFs);
            int idx1 = (int) Math.floor(origIdx);
            int idx2 = Math.min(n - 1, idx1 + 1);
            double frac = origIdx - idx1;
            resampledSignal[i] = (1 - frac) * signalData[idx1] + frac * signalData[idx2];
        }

        // 2. DC-offset cancellation (Zero-Mean) untuk menjamin stabilitas filter low-pass (mencegah infinity/NaN)
        double rMean = 0;
        for (double val : resampledSignal) rMean += val;
        rMean /= m;
        for (int i = 0; i < m; i++) {
            resampledSignal[i] -= rMean;
        }

        double[] lpf = new double[m];
        double[] hpf = new double[m];
        double[] der = new double[m];
        double[] sqr = new double[m];
        double[] mwi = new double[m];

        // Low Pass Filter (Pan-Tompkins, cutoff ~11 Hz, gain 36)
        for (int i = 12; i < m; i++) {
            lpf[i] = 2 * lpf[i - 1] - lpf[i - 2] + resampledSignal[i] - 2 * resampledSignal[i - 6] + resampledSignal[i - 12];
        }

        // High Pass Filter (Pan-Tompkins, cutoff ~5 Hz, gain 1)
        for (int i = 32; i < m; i++) {
            hpf[i] = hpf[i - 1] - (lpf[i] / 32.0) + lpf[i - 16] - lpf[i - 17] + (lpf[i - 32] / 32.0);
        }

        // Derivative (5-point derivative untuk mendeteksi kemiringan QRS)
        for (int i = 4; i < m; i++) {
            der[i] = (2 * hpf[i] + hpf[i - 1] - hpf[i - 3] - 2 * hpf[i - 4]) / 8.0;
        }

        // Squaring (Pengkuadratan sinyal derivative untuk menonjolkan QRS)
        for (int i = 0; i < m; i++) {
            sqr[i] = der[i] * der[i];
        }

        // Moving Window Integrator (150ms window = 30 sampel pada 200 Hz)
        int windowSize = 30;
        double sum = 0;
        for (int i = 0; i < windowSize && i < m; i++) {
            sum += sqr[i];
            mwi[i] = sum / windowSize;
        }
        for (int i = windowSize; i < m; i++) {
            sum += sqr[i] - sqr[i - windowSize];
            mwi[i] = sum / windowSize;
        }

        // Adaptive Thresholding pada MWI
        double spk = 0; 
        double npk = 0; 
        
        // Estimasi awal dari 2 detik pertama (400 sampel)
        double maxMwi = 0;
        int initLimit = Math.min(m, 400);
        for (int i = 0; i < initLimit; i++) {
            if (mwi[i] > maxMwi) maxMwi = mwi[i];
        }
        spk = maxMwi * 0.5;
        npk = maxMwi * 0.1;
        double threshold1 = npk + 0.25 * (spk - npk);

        int minDistanceResampled = 50; // 250ms refractory period pada 200 Hz
        int lastPeakIdx = -minDistanceResampled;
        List<Integer> mwiPeaks = new ArrayList<>();
        
        for (int i = 1; i < m - 1; i++) {
            if (mwi[i] > mwi[i - 1] && mwi[i] > mwi[i + 1]) {
                double peakVal = mwi[i];
                if (peakVal > threshold1) {
                    if ((i - lastPeakIdx) > minDistanceResampled) {
                        mwiPeaks.add(i);
                        lastPeakIdx = i;
                        spk = 0.125 * peakVal + 0.875 * spk;
                    }
                } else {
                    npk = 0.125 * peakVal + 0.875 * npk;
                }
                threshold1 = npk + 0.25 * (spk - npk);
            }
        }

        // Backtrack ke sinyal asli (mengubah indeks resampled kembali ke indeks asli)
        rIndices.clear();
        int searchWindowResampled = 50; // 250ms search window pada 200 Hz
        for (int mwiPeak : mwiPeaks) {
            int start = Math.max(0, mwiPeak - searchWindowResampled);
            int end = Math.min(m - 1, mwiPeak);
            
            // Cari puncak di sinyal SQR
            int bestSqrIdx = start;
            double maxSqr = -1;
            for (int i = start; i <= end; i++) {
                if (sqr[i] > maxSqr) {
                    maxSqr = sqr[i];
                    bestSqrIdx = i;
                }
            }
            
            // Kompensasi delay filter di 200 Hz: LPF (5) + HPF (16) + Derivative (2) = 23 sampel (~115ms)
            int delaySqr = 23;
            int estimatedRResampled = Math.max(0, bestSqrIdx - delaySqr);
            
            // Konversi indeks resampled kembali ke indeks sampel original
            int estimatedROrig = (int) Math.round(estimatedRResampled * (fs / targetFs));
            
            // Cari puncak lokal maksimum (positif R-wave) di sekitar estimasi awal (+/- 100ms) pada sinyal asli
            int exactR = estimatedROrig;
            int localSearch = (int) (0.10 * fs);
            int localStart = Math.max(0, estimatedROrig - localSearch);
            int localEnd = Math.min(n - 1, estimatedROrig + localSearch);
            double maxVal = -Double.MAX_VALUE;
            
            for (int i = localStart; i <= localEnd; i++) {
                if (signalData[i] > maxVal) {
                    maxVal = signalData[i];
                    exactR = i;
                }
            }
            
            // Cegah duplikasi peak
            int minDistanceOrig = (int) (0.25 * fs);
            if (rIndices.isEmpty() || (exactR - rIndices.get(rIndices.size() - 1)) > minDistanceOrig) {
                rIndices.add(exactR);
            }
        }
    }

    private void detectOtherWavesAdvanced() {
        // Window search dalam sampel (P: 250ms, T: 450ms)
        int searchWindowP = (int)(0.25 * fs); 
        int searchWindowT = (int)(0.45 * fs); 
        
        qIndices.clear();
        sIndices.clear();
        pIndices.clear();
        tIndices.clear();
        
        for (int rIdx : rIndices) {
            int rIdxPos = rIndices.indexOf(rIdx);
            int prevR = (rIdxPos > 0) ? rIndices.get(rIdxPos - 1) : -1;
            int nextR = (rIdxPos < rIndices.size() - 1) ? rIndices.get(rIdxPos + 1) : -1;
            
            // --- CARI Q (Minimum lokal sebelum R) ---
            int qIdx = rIdx;
            double minQ = signalData[rIdx];
            int limitQ = (prevR != -1) ? (prevR + rIdx) / 2 : 0;
            int qStart = Math.max(limitQ, rIdx - (int)(0.1 * fs));
            for (int i = rIdx; i >= qStart; i--) { 
                if (signalData[i] < minQ) {
                    minQ = signalData[i];
                    qIdx = i;
                }
            }
            qIndices.add(qIdx);

            // --- CARI S (Minimum lokal setelah R) ---
            int sIdx = rIdx;
            double minS = signalData[rIdx];
            int limitS = (nextR != -1) ? (rIdx + nextR) / 2 : signalData.length - 1;
            int sEnd = Math.min(limitS, rIdx + (int)(0.1 * fs));
            for (int i = rIdx; i <= sEnd; i++) { 
                if (signalData[i] < minS) {
                    minS = signalData[i];
                    sIdx = i;
                }
            }
            sIndices.add(sIdx);

            // --- CARI P (Puncak lokal sebelum Q) ---
            // Gunakan referensi baseline lokal dan batasan beat untuk mencegah crossover
            int pIdx = -1;
            int limitP = (prevR != -1) ? (prevR + rIdx) / 2 : 0;
            int startP = Math.max(limitP, qIdx - searchWindowP);
            int endP = qIdx - (int)(0.02 * fs); // Jeda 20ms dari Q
            if (endP > startP) {
                double baseP = signalData[startP]; // anggap ujung kiri/isoelektrik sebagai baseline
                double maxP = -1;
                for (int i = startP; i < endP; i++) { 
                    double val = Math.abs(signalData[i] - baseP);
                    if (val > maxP) {
                        maxP = val;
                        pIdx = i;
                    }
                }
            }
            if (pIdx != -1) pIndices.add(pIdx);

            // --- CARI T (Puncak lokal setelah S) ---
            // Gunakan referensi baseline lokal dan batasan beat untuk mencegah crossover
            int tIdx = -1;
            int limitT = (nextR != -1) ? (rIdx + nextR) / 2 : signalData.length - 1;
            int endT = Math.min(limitT, sIdx + searchWindowT);
            int startT = sIdx + (int)(0.04 * fs); // Jeda 40ms dari S
            if (endT > startT) {
                double baseT = signalData[endT]; // anggap ujung kanan/isoelektrik sebagai baseline
                double maxT = -1;
                for (int i = startT; i < endT; i++) { 
                    double val = Math.abs(signalData[i] - baseT);
                    if (val > maxT) {
                        maxT = val;
                        tIdx = i;
                    }
                }
            }
            if (tIdx != -1) tIndices.add(tIdx);
        }
    }

    private void calculateRR() {
        rrIntervals.clear();
        for (int i = 1; i < rIndices.size(); i++) {
            double diff = (rIndices.get(i) - rIndices.get(i-1)) / fs;
            rrIntervals.add(diff);
        }
    }

    private void detectAnomalyPeaks() {
        anomalyIndices.clear();
        if (rIndices.size() < 2) return;
        
        // Calculate threshold based on max R peak amplitude
        double maxR = 0;
        for (int r : rIndices) {
            if (signalData[r] > maxR) maxR = signalData[r];
        }
        double threshold = maxR * 0.12; // 12% of max R peak
        if (threshold < 0.05) threshold = 0.05; // safety minimum
        
        int searchOffset = (int) (0.08 * fs); // ~80ms QRS width margin
        int tolerance = (int) (0.05 * fs); // ~50ms tolerance around P and T peaks
        
        for (int k = 0; k < rIndices.size() - 1; k++) {
            int r1 = rIndices.get(k);
            int r2 = rIndices.get(k + 1);
            
            int start = r1 + searchOffset;
            int end = r2 - searchOffset;
            
            if (start >= end || start < 0 || end >= signalData.length) continue;
            
            // Find detected T in this interval (usually belongs to r1)
            int tPeak = -1;
            for (int t : tIndices) {
                if (t >= r1 && t <= r2) {
                    tPeak = t;
                    break;
                }
            }
            
            // Find detected P in this interval (usually belongs to r2)
            int pPeak = -1;
            for (int p : pIndices) {
                if (p >= r1 && p <= r2) {
                    pPeak = p;
                    break;
                }
            }
            
            for (int i = start; i <= end; i++) {
                if (i <= 0 || i >= signalData.length - 1) continue;
                
                // Local maximum check
                if (signalData[i] > signalData[i-1] && signalData[i] > signalData[i+1] && signalData[i] > threshold) {
                    // Check if it is the T peak or close to it
                    boolean isT = (tPeak != -1 && Math.abs(i - tPeak) <= tolerance);
                    // Check if it is the P peak or close to it
                    boolean isP = (pPeak != -1 && Math.abs(i - pPeak) <= tolerance);
                    
                    // Check if it's also close to any Q or S
                    boolean isQRS = false;
                    for (int q : qIndices) {
                        if (Math.abs(i - q) <= tolerance) { isQRS = true; break; }
                    }
                    for (int s : sIndices) {
                        if (Math.abs(i - s) <= tolerance) { isQRS = true; break; }
                    }
                    
                    if (!isT && !isP && !isQRS) {
                        anomalyIndices.add(i);
                    }
                }
            }
        }
    }

    // --- GETTERS & HASIL PERHITUNGAN ---

    // 1. COUNTS (JUMLAH)
    public int getRPeakCount() { return rIndices.size(); }
    public int getPPeakCount() { return pIndices.size(); }
    public int getTPeakCount() { return tIndices.size(); }

    // 2. HEART RATE
    public double getHRAvg() {
        if (rrIntervals.isEmpty()) return 0;
        double total = 0;
        for (double val : rrIntervals) total += val;
        double avgRR = total / rrIntervals.size();
        return (avgRR == 0) ? 0 : 60.0 / avgRR;
    }

    public double getHRCurrent() {
        if (rrIntervals.isEmpty()) return 0;
        return 60.0 / rrIntervals.get(rrIntervals.size() - 1);
    }

    // 3. RR INTERVAL STATS
    public double getRRAvg() {
        if (rrIntervals.isEmpty()) return 0;
        double total = 0;
        for (double val : rrIntervals) total += val;
        return total / rrIntervals.size();
    }
    public double getRRMin() {
        if (rrIntervals.isEmpty()) return 0;
        return Collections.min(rrIntervals);
    }
    public double getRRMax() {
        if (rrIntervals.isEmpty()) return 0;
        return Collections.max(rrIntervals);
    }

    // 4. COMPLEX INTERVALS (PR, QT, QTc, QRS, PR Segment, ST Segment, ST Interval)

    // PR Interval: dari puncak P ke puncak R (rata-rata per detak)
    // Referensi: NaivePtDetector – P wave dicari di 30% RR sebelum R, berakhir 30ms sebelum R.
    public double getPRAvg() {
        if (pIndices.isEmpty() || rIndices.isEmpty()) return 0;
        double total = 0;
        int count = Math.min(pIndices.size(), rIndices.size());
        for (int i = 0; i < count; i++) {
            total += (rIndices.get(i) - pIndices.get(i)) / fs;
        }
        return total / count;
    }

    // QRS Interval: dari lembah Q ke lembah S (rata-rata per detak)
    // Referensi: ElgendiFastQrsDetector – qrsStart (onset) dan qrsEnd (offset)
    public double getQRSAvg() {
        if (qIndices.isEmpty() || sIndices.isEmpty()) return 0;
        double total = 0;
        int count = Math.min(qIndices.size(), sIndices.size());
        for (int i = 0; i < count; i++) {
            // Durasi QRS = indeks S dikurangi indeks Q, lalu bagi fs (sampel → detik)
            total += (sIndices.get(i) - qIndices.get(i)) / fs;
        }
        return total / count;
    }

    // PR Segment: dari akhir P (P offset) ke awal QRS (Q onset)
    // Referensi: NaivePtDetector – P offset diestimasi sebagai pPeakIdx + 10 sampel
    public double getPRSegmentAvg() {
        if (pIndices.isEmpty() || qIndices.isEmpty()) return 0;
        double total = 0;
        int count = Math.min(pIndices.size(), qIndices.size());
        int pOffsetMargin = 10; // 10 sampel setelah puncak P = estimasi P offset (ref: NaivePtDetector)
        for (int i = 0; i < count; i++) {
            int pOffset = pIndices.get(i) + pOffsetMargin; // akhir gelombang P
            int qOnset  = qIndices.get(i);                 // awal kompleks QRS
            double seg  = (qOnset - pOffset) / fs;
            if (seg > 0) total += seg; // abaikan jika P offset > Q (anomali)
        }
        return total / count;
    }

    // ST Segment (amplitudo): nilai sinyal di J-point (tepat setelah S) 
    // Referensi: NaivePtDetector – T wave onset = tPeakIdx - 10 sampel.
    //            J-point (awal ST segment) diestimasi = sIdx + 10 sampel
    // Return: amplitudo rata-rata di J-point (dalam satuan data sinyal, bukan mV).
    public double getSTSegmentAvg() {
        if (sIndices.isEmpty()) return 0;
        double total = 0;
        int count = 0;
        int jPointMargin = 10; // sampel setelah S = J-point (ref: NaivePtDetector T onset)
        for (int sIdx : sIndices) {
            int jPoint = sIdx + jPointMargin;
            if (jPoint < signalData.length) {
                total += signalData[jPoint]; // amplitudo sinyal di J-point
                count++;
            }
        }
        return (count > 0) ? total / count : 0;
    }

    // ST Interval: dari lembah S ke puncak T (rata-rata per detak)
    // Referensi: NaivePtDetector – T wave dicari dari 40ms setelah R
    //            hingga 50% RR interval berikutnya. 
    public double getSTIntervalAvg() {
        if (sIndices.isEmpty() || tIndices.isEmpty()) return 0;
        double total = 0;
        int count = Math.min(sIndices.size(), tIndices.size());
        for (int i = 0; i < count; i++) {
            // Durasi ST interval = indeks T peak dikurangi indeks S, dibagi fs
            double interval = (tIndices.get(i) - sIndices.get(i)) / fs;
            if (interval > 0) total += interval; // abaikan jika terbalik (anomali)
        }
        return total / count;
    }

    // QT Interval: dari Q ke T peak (rata-rata). Normal: 350–440 ms.
    public double getQTAvg() {
        if (qIndices.isEmpty() || tIndices.isEmpty()) return 0;
        double total = 0;
        int count = Math.min(qIndices.size(), tIndices.size());
        for (int i = 0; i < count; i++) {
            total += (tIndices.get(i) - qIndices.get(i)) / fs;
        }
        return total / count;
    }

    // QTc (Corrected QT): koreksi QT terhadap denyut jantung menggunakan rumus dinamis.
    public double getQTcAvg() {
        double qt = getQTAvg(); // dalam detik
        double rr = getRRAvg(); // dalam detik
        double hr = getHRAvg(); // BPM
        
        if (rr <= 0) return 0;

        switch (selectedFormula) {
            case "Fridericia":
                return qt / Math.cbrt(rr);       // QT / RR^(1/3)
            case "Framingham":
                return qt + 0.154 * (1.0 - rr);  // QT + 0.154*(1-RR)
            case "Hodges":
                return qt + 0.00175 * (hr - 60); // QT + 1.75*(HR-60) (konversi ke detik)
            case "Bazett":
            default:
                return qt / Math.sqrt(rr);        // QT / sqrt(RR)
        }
    }
    
    public double getFs() {
        return this.fs;
    }
    
    public double getDataWindow(){
        if (fs == 0) return 0;
        return signalData.length / fs;
        
    }
    
    /**
     * Get the QTc formula name being used for calculations
     * WHY: Display in analyzepanel which formula was selected in Settings
     * @return The formula name (e.g., "Bazett", "Fridericia", "Framingham", "Hodges", or "-")
     */
    public String getFormulaName() {
        return this.selectedFormula;
    }
    
    public List<Integer> getRIndices() { return rIndices; }
    public List<Integer> getPIndices() { return pIndices; }
    public List<Integer> getTIndices() { return tIndices; }
    public List<Integer> getQIndices() { return qIndices; }
    public List<Integer> getSIndices() { return sIndices; }
    public List<Integer> getAnomalyIndices() { return anomalyIndices; }

    // HRV TIME DOMAIN

    // SDNN: standar deviasi semua RR interval → indikator variabilitas ANS keseluruhan
    // Normal: > 50 ms sehat; < 20 ms risiko tinggi
    public double getHRVsdnn() {
        if (rrIntervals.size() < 2) return 0;
        double mean = getRRAvg();
        double sumSq = 0;
        for (double rr : rrIntervals) sumSq += Math.pow(rr - mean, 2);
        return Math.sqrt(sumSq / rrIntervals.size()) * 1000; // detik → ms
    }

    // RMSSD: root mean square of successive differences → aktivitas parasimpatis (relaksasi)
    // Normal istirahat: 20–50 ms; olahraga/stres: lebih rendah
    public double getHRVrmssd() {
        int n = rrIntervals.size();
        if (n < 2) return 0;
        double sumSq = 0;
        for (int i = 1; i < n; i++) {
            double diff = rrIntervals.get(i) - rrIntervals.get(i - 1);
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (n - 1)) * 1000; // detik → ms
    }

    // NN50: jumlah pasangan RR yang perbedaannya > 50 ms
    public int getHRVnn50() {
        int count = 0;
        int n = rrIntervals.size();
        for (int i = 1; i < n; i++) {
            double diffMs = Math.abs(rrIntervals.get(i) - rrIntervals.get(i - 1)) * 1000;
            if (diffMs > 50) count++;
        }
        return count;
    }

    // pNN50: persentase NN50 dari total pasangan → 0–100%
    // Normal istirahat: > 20%; nilai rendah = stres / aktivitas simpatis dominan
    public double getHRVpnn50() {
        int n = rrIntervals.size();
        if (n < 2) return 0;
        return (double) getHRVnn50() / (n - 1) * 100.0;
    }

    // SD1: variabilitas jangka pendek (Poincaré plot, sumbu minor)
    // SD1 = SDSD / sqrt(2), SDSD = std dari successive differences
    public double getHRVsd1() {
        int n = rrIntervals.size();
        if (n < 2) return 0;
        double[] diffs = new double[n - 1];
        for (int i = 1; i < n; i++) diffs[i - 1] = (rrIntervals.get(i) - rrIntervals.get(i - 1)) * 1000;
        double mean = 0;
        for (double d : diffs) mean += d;
        mean /= diffs.length;
        double sumSq = 0;
        for (double d : diffs) sumSq += Math.pow(d - mean, 2);
        double sdsd = Math.sqrt(sumSq / diffs.length);
        return sdsd / Math.sqrt(2);
    }

    // SD2: variabilitas jangka panjang (Poincaré plot, sumbu mayor)
    // SD2 = sqrt(2*SDNN² - 0.5*SDSD²)
    public double getHRVsd2() {
        double sdnn = getHRVsdnn();
        double sd1  = getHRVsd1();
        double sdsd = sd1 * Math.sqrt(2);
        double val  = 2 * Math.pow(sdnn, 2) - 0.5 * Math.pow(sdsd, 2);
        return val > 0 ? Math.sqrt(val) : 0;
    }
}
