package projectecg;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaveAnnotationPainter {

    // Warna Tiap Gelombang
    private static final Color COLOR_R = new Color(228, 75, 141);   // Pink / Magenta Khas Dashboard (R Peak)
    private static final Color COLOR_P = new Color(79, 179, 135);   // Mint Green (P Peak)
    private static final Color COLOR_T = new Color(63, 114, 175);   // Dusty Blue (T Peak)
    private static final Color COLOR_Q = new Color(255, 126, 103);  // Coral Red (Q Peak)
    private static final Color COLOR_S = new Color(248, 164, 136);  // Soft Peach (S Peak)
    private static final Color COLOR_ANOMALY = new Color(255, 0, 0); // Red (Anomaly Peak)

    private static final int DOT_RADIUS = 5;

    // Metode gambar anotasi
    public static void paint(
            Graphics2D g2,
            ECGAnalyzer analyzer,
            double[] data,
            int currentDataIdx,
            int visibleSamples,
            double xStart,
            int panelWidth,
            double yBase,
            double gainFactor,
            double sampleWidth) {

        if (analyzer == null || data == null || data.length == 0) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int dataLen = data.length;
        Map<Integer, Character> waveMap = new HashMap<>();

        // Prioritas: R > P > T > Q > S > A (jika tabrakan di satu indeks, R menang)
        for (int idx : safeList(analyzer.getAnomalyIndices())) waveMap.put(idx, 'A');
        for (int idx : safeList(analyzer.getQIndices())) waveMap.put(idx, 'Q');
        for (int idx : safeList(analyzer.getSIndices())) waveMap.put(idx, 'S');
        for (int idx : safeList(analyzer.getTIndices())) waveMap.put(idx, 'T');
        for (int idx : safeList(analyzer.getPIndices())) waveMap.put(idx, 'P');
        for (int idx : safeList(analyzer.getRIndices())) waveMap.put(idx, 'R');

        for (int j = 0; j < visibleSamples; j++) {
            int sampleIdx = (currentDataIdx + j) % dataLen;

            if (!waveMap.containsKey(sampleIdx)) continue;

            char waveLabel = waveMap.get(sampleIdx);

            double px = xStart + (j + 1) * sampleWidth;
            if (px < xStart || px > panelWidth) continue; // di luar layar

            double sampleVal = data[sampleIdx] * -gainFactor;
            double py = yBase + sampleVal;

            Color c = getWaveColor(waveLabel);
            int radius = (waveLabel == 'A') ? 4 : DOT_RADIUS;

            // Gambar lingkaran marker (hanya lingkaran/titik saja, tanpa teks/label huruf)
            g2.setColor(c);
            double dotX = px - radius;
            double dotY = py - radius;
            
            // Gambar shadow/border putih agar terlihat kontras dan premium
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(dotX - 1, dotY - 1, (radius + 1) * 2, (radius + 1) * 2));
            
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(dotX, dotY, radius * 2, radius * 2));
        }
    }

    private static Color getWaveColor(char wave) {
        switch (wave) {
            case 'R': return COLOR_R;
            case 'P': return COLOR_P;
            case 'T': return COLOR_T;
            case 'Q': return COLOR_Q;
            case 'S': return COLOR_S;
            case 'A': return COLOR_ANOMALY;
            default:  return Color.WHITE;
        }
    }

    private static List<Integer> safeList(List<Integer> list) {
        return (list != null) ? list : java.util.Collections.emptyList();
    }
}
