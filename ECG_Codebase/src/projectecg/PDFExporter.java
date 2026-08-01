package projectecg;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

public class PDFExporter {

    private static final float MM = 2.83465f;
    private static final float MARGIN = 36f;
    private static final float MARGIN_T = 50f;
    private static final float MARGIN_B = 40f;

    public static boolean generateReport(String dest, Map<String, String> info, List<double[]> leadsData, double fs) {
        try {
            Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN_T, MARGIN_B);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(dest));
            document.open();

            // JUDUL
            addHeader(document, writer);

            // TABEL 1: DATA PASIEN (Format 2 Kolom Vertikal)
            addPatientTable(document, info);

            // TABEL 2: HASIL KLASIFIKASI MODEL (Format 4 Kolom)
            addAnalysisTable(document, info);

            // SARAN KESEHATAN (Format Kotak Tabel Center)
            addAdviceTable(document, info.getOrDefault("advice", ""));

            // GRAFIK KLASIFIKASI (Lead Target) 
            float classHeight = (25 * MM) + (5 * MM);
            drawSignalSection(writer, document, "Grafik Klasifikasi (Lead Target)", leadsData, 1, fs, 1, 1, classHeight, info, true);

            // TABEL 3: METRIKS (RR, Peak, Interval)
            addMetricsTable(document, info);

            // GRAFIK MONITORING (12-Lead) 
            float monHeight = (6 * 20 * MM) + (5 * MM); 
            drawSignalSection(writer, document, "Grafik Monitoring 12-Lead", leadsData, 12, fs, 2, 6, monHeight, info, true);

            addFooter(writer, document);
            document.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Gmabra grafik
    private static void drawSignalSection(PdfWriter writer, Document document, String title,
                                          List<double[]> leadsData, int maxLeads, double fs,
                                          int cols, int rows, float totalHeight, 
                                          Map<String, String> info, boolean hasFooter) throws DocumentException {
        
        float estimatedTitleHeight = 30f; 
        float currentY = writer.getVerticalPosition(true);
        
        if (currentY - estimatedTitleHeight - totalHeight < document.bottom() + MARGIN_B) {
            document.newPage(); 
        }

        Font titleFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, new BaseColor(165, 42, 42));
        Paragraph pTitle = new Paragraph(title, titleFont);
        pTitle.setSpacingAfter(4);
        pTitle.setSpacingBefore(10);
        document.add(pTitle);

        float yTop = writer.getVerticalPosition(true);
        float boxY = yTop - totalHeight;

        PdfContentByte cb = writer.getDirectContent();
        float canvasWidth = document.getPageSize().getWidth() - (MARGIN * 2);
        
        drawGridAndSignal(cb, MARGIN, boxY, canvasWidth, totalHeight, leadsData, maxLeads, fs, cols, rows, info, hasFooter);
        Paragraph spacer = new Paragraph(" ");
        spacer.setLeading(totalHeight + 5); 
        document.add(spacer);
    }

    // TABEL 1: DATA PASIEN 
    private static void addPatientTable(Document document, Map<String, String> info) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setWidths(new float[]{1.5f, 4f});

        Font headerF = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        Font labelF = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(40, 40, 40));
        Font valueF = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, new BaseColor(40, 40, 40));
        
        BaseColor headerBg = new BaseColor(165, 42, 42); // Merah tua
        BaseColor labelBg = new BaseColor(255, 240, 240); // Merah muda
        BaseColor valueBg = BaseColor.WHITE;

        addHeaderCell(table, "DATA PASIEN", headerF, headerBg, 2);
        addLabelCell(table, "Nama Pasien", labelF, labelBg);   
        addValueCell(table, info.getOrDefault("name", "-"), valueF, valueBg);
        
        addLabelCell(table, "ID Pasien", labelF, labelBg);     
        addValueCell(table, info.getOrDefault("id", "-"), valueF, valueBg);
        
        addLabelCell(table, "Jenis Kelamin", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("sex", "-"), valueF, valueBg);
        
        addLabelCell(table, "Tanggal Lahir", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("dob", "-"), valueF, valueBg);

        String age = info.getOrDefault("age", "-");
        addLabelCell(table, "Umur", labelF, labelBg);          
        addValueCell(table, age.equals("-") ? "-" : age + " Tahun", valueF, valueBg);

        document.add(table);
    }

    // TABEL 2: HASIL KLASIFIKASI MODEL 
    private static void addAnalysisTable(Document document, Map<String, String> info) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setWidths(new float[]{1.5f, 2f, 1.5f, 2f});

        Font headerF = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        Font labelF = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(40, 40, 40));
        Font valueF = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, new BaseColor(40, 40, 40));
        
        BaseColor headerBg = new BaseColor(165, 42, 42); // Merah tua
        BaseColor labelBg = new BaseColor(255, 240, 240); // Merah muda
        BaseColor valueBg = BaseColor.WHITE;

        addHeaderCell(table, "HASIL KLASIFIKASI MODEL", headerF, headerBg, 4);

        addLabelCell(table, "Diagnosis", labelF, labelBg);  
        addDiagnosisCell(table, info.getOrDefault("diagnosis", "-"), valueBg);
        
        String hr = info.getOrDefault("hr", "-");
        addLabelCell(table, "Heart Rate", labelF, labelBg); 
        addValueCell(table, hr.equals("-") ? "-" : hr + " bpm", valueF, valueBg);

        String conf = info.getOrDefault("confidence", "-");
        addLabelCell(table, "Confidence", labelF, labelBg); 
        addValueCell(table, conf.equals("-") ? "-" : conf + " %", valueF, valueBg);
        
        addLabelCell(table, "Risk Level", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("risk", "-"), valueF, valueBg);

        document.add(table);
    }

    // SARAN KESEHATAN (Tabel Box Single Cell)
    private static void addAdviceTable(Document document, String advice) throws DocumentException {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(165, 42, 42));
        Paragraph adviceTitle = new Paragraph("SARAN KESEHATAN", sectionFont);
        adviceTitle.setSpacingBefore(10);
        adviceTitle.setSpacingAfter(4);
        document.add(adviceTitle);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(12);
        cell.setBorderColor(BaseColor.BLACK);
        cell.setBorderWidth(0.7f);

        Font bodyFont = new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC, BaseColor.BLACK);

        String[] lines = advice.split("\\r?\\n");
        boolean hasContent = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            
            String checkLine = trimmed.toUpperCase();
            if (checkLine.contains("ECG ANALYSIS") || 
                checkLine.contains("CLASSIFICATION RESULTS") || 
                checkLine.contains("CLASSIFICATION") ||    
                checkLine.contains("DESCRIPTION") ||       
                checkLine.contains("CONFIDENCE") ||        
                checkLine.contains("RISK LEVEL") ||        
                checkLine.contains("AGE GROUP") ||        
                checkLine.contains("BMI") ||
                checkLine.contains("PATIENT HEALTH CONTEXT") ||
                checkLine.contains("HEALTH ADVICE") || 
                checkLine.contains("DISCLAIMER") || 
                checkLine.contains("PURPOSES ONLY") || 
                checkLine.contains("PLEASE CONSULT")) { 
                continue; 
            }

            if (trimmed.startsWith("•") || trimmed.startsWith("-")) {
                trimmed = trimmed.substring(1).trim();
            }

            Paragraph p = new Paragraph(trimmed, bodyFont);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(4);
            cell.addElement(p);
            hasContent = true;
        }
        
        if (!hasContent) {
            Paragraph p = new Paragraph("Tidak ada saran kesehatan khusus tercatat.", bodyFont);
            p.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p);
        }
        
        table.addCell(cell);
        document.add(table);
    }

    // TABEL 3: METRIKS (RR, Peak, Interval, HRV) 
    private static void addMetricsTable(Document document, Map<String, String> info) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSpacingAfter(15);
        // Unified symmetrical widths for 3 label-value pairs:
        table.setWidths(new float[]{1.2f, 1f, 1.2f, 1f, 1.2f, 1f});

        Font headerF = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, BaseColor.WHITE);
        Font labelF = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, new BaseColor(40, 40, 40));
        Font valueF = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, new BaseColor(40, 40, 40));
        
        BaseColor headerBg = new BaseColor(165, 42, 42); 
        BaseColor labelBg = new BaseColor(255, 240, 240); 
        BaseColor valueBg = BaseColor.WHITE;

        String formulaStr = info.getOrDefault("formula", "Bazett");

        // --- ROW 1 HEADERS ---
        addHeaderCell(table, "RR INTERVALS", headerF, headerBg, 2);
        addHeaderCell(table, "PEAK COUNT", headerF, headerBg, 2);
        addHeaderCell(table, "ECG SEGMENTS", headerF, headerBg, 2);

        // --- ROW 1 DATA ---
        addLabelCell(table, "RR Avg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("rrAvg", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "R Peaks", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("rPeaks", "-"), valueF, valueBg);
        addLabelCell(table, "PR Seg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("prSeg", "-") + " ms", valueF, valueBg);

        // --- ROW 2 DATA ---
        addLabelCell(table, "RR Min", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("rrMin", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "P Peaks", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("pPeaks", "-"), valueF, valueBg);
        addLabelCell(table, "ST Int", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("stInt", "-") + " ms", valueF, valueBg);

        // --- ROW 3 DATA ---
        addLabelCell(table, "RR Max", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("rrMax", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "T Peaks", labelF, labelBg); 
        addValueCell(table, info.getOrDefault("tPeaks", "-"), valueF, valueBg);
        addLabelCell(table, "ST Seg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("stSeg", "-") + " mV", valueF, valueBg);

        // BLANK ROW SEPARATOR
        PdfPCell blank = new PdfPCell(new Phrase(" "));
        blank.setColspan(6);
        blank.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        blank.setFixedHeight(10f);
        table.addCell(blank);

        // --- ROW 2 HEADERS ---
        addHeaderCell(table, "INTERVAL MEASUREMENTS (" + formulaStr + ")", headerF, headerBg, 2);
        addHeaderCell(table, "HRV TIME DOMAIN", headerF, headerBg, 4);

        // --- ROW 4 DATA ---
        addLabelCell(table, "PR Avg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("prAvg", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "SDNN", labelF, labelBg);
        addValueCell(table, info.getOrDefault("sdnn", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "NN50", labelF, labelBg);
        addValueCell(table, info.getOrDefault("nn50", "-"), valueF, valueBg);

        // --- ROW 5 DATA ---
        addLabelCell(table, "QRS Avg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("qrsAvg", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "RMSSD", labelF, labelBg);
        addValueCell(table, info.getOrDefault("rmssd", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "pNN50", labelF, labelBg);
        addValueCell(table, info.getOrDefault("pnn50", "-") + " %", valueF, valueBg);

        // --- ROW 6 DATA ---
        addLabelCell(table, "QT Avg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("qtAvg", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "SD1", labelF, labelBg);
        addValueCell(table, info.getOrDefault("sd1", "-") + " ms", valueF, valueBg);
        addLabelCell(table, "SD2", labelF, labelBg);
        addValueCell(table, info.getOrDefault("sd2", "-") + " ms", valueF, valueBg);

        // --- ROW 7 DATA ---
        addLabelCell(table, "QTc Avg", labelF, labelBg);  
        addValueCell(table, info.getOrDefault("qtcAvg", "-") + " ms", valueF, valueBg);
        // Empty cells for the rest of the row
        addLabelCell(table, "", labelF, valueBg);
        addValueCell(table, "", valueF, valueBg);
        addLabelCell(table, "", labelF, valueBg);
        addValueCell(table, "", valueF, valueBg);

        document.add(table);
    }

    // ── Komponen Pembantu Tabel & Desain ──
    private static void addHeader(Document document, PdfWriter writer) throws DocumentException {
        PdfContentByte cb = writer.getDirectContent();
        float pageWidth = document.getPageSize().getWidth();

        cb.setLineWidth(1.5f);
        cb.setColorStroke(new BaseColor(165, 42, 42)); // Merah tua
        cb.moveTo(MARGIN, document.top() - 5);
        cb.lineTo(pageWidth - MARGIN, document.top() - 5);
        cb.stroke();

        Font titleFont = new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD, new BaseColor(165, 42, 42));
        Paragraph title = new Paragraph("LAPORAN ANALISIS ELEKTROKARDIOGRAM (ECG)", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(20);
        title.setSpacingAfter(10);
        document.add(title);

        cb.setLineWidth(0.5f);
        float afterSubY = writer.getVerticalPosition(true) - 4;
        cb.moveTo(MARGIN, afterSubY);
        cb.lineTo(pageWidth - MARGIN, afterSubY);
        cb.stroke();
    }

    private static void addFooter(PdfWriter writer, Document document) {
        try {
            PdfContentByte cb = writer.getDirectContent();
            float pageWidth = document.getPageSize().getWidth();
            float footerY = document.bottom() - 20;

            cb.setLineWidth(0.5f);
            cb.setColorStroke(new BaseColor(180, 180, 180));
            cb.moveTo(MARGIN, footerY + 12);
            cb.lineTo(pageWidth - MARGIN, footerY + 12);
            cb.stroke();
        } catch (Exception ignored) {}
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font, BaseColor bg, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setColspan(colspan);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new BaseColor(180, 180, 200));
        table.addCell(cell);
    }

    private static void addLabelCell(PdfPTable table, String text, Font font, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new BaseColor(180, 180, 200));
        table.addCell(cell);
    }

    private static void addValueCell(PdfPTable table, String text, Font font, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new BaseColor(180, 180, 200));
        table.addCell(cell);
    }

    private static void addDiagnosisCell(PdfPTable table, String label, BaseColor bg) {
        String lc = label.toLowerCase();
        BaseColor textColor;
        if (lc.contains("normal") || lc.contains("sinus")) {
            textColor = new BaseColor(0, 130, 60); 
        } else if (lc.contains("afib") || lc.contains("flutter") || lc.contains("vt") || lc.contains("other")) {
            textColor = new BaseColor(180, 120, 0); 
        } else {
            textColor = new BaseColor(200, 0, 0); 
        }
        Font f = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, textColor);
        PdfPCell cell = new PdfPCell(new Phrase(label, f));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new BaseColor(180, 180, 200));
        table.addCell(cell);
    }

    // ── Fungsi Penggambar Vektor Sinyal dan Grid Berserta Footer Teks ──
    private static void drawGridAndSignal(PdfContentByte cb, float x, float y, float w, float h,
                                          List<double[]> leadsData, int maxLeads, double fs, int cols, int rows,
                                          Map<String, String> info, boolean hasFooter) {

        cb.saveState(); cb.setColorFill(BaseColor.WHITE); cb.rectangle(x, y, w, h); cb.fill(); cb.restoreState();
        cb.saveState(); cb.setLineWidth(0.5f); cb.setColorStroke(new BaseColor(80, 80, 80)); cb.rectangle(x, y, w, h); cb.stroke(); cb.restoreState();

        // Grid Kecil (1mm)
        cb.saveState(); cb.setLineWidth(0.1f); cb.setColorStroke(new BaseColor(255, 204, 204));
        for (float i = MM; i < w; i += MM) { cb.moveTo(x + i, y); cb.lineTo(x + i, y + h); }
        for (float i = MM; i < h; i += MM) { cb.moveTo(x, y + i); cb.lineTo(x + w, y + i); }
        cb.stroke(); cb.restoreState();

        // Grid Besar (5mm)
        cb.saveState(); cb.setLineWidth(0.4f); cb.setColorStroke(new BaseColor(230, 100, 100));
        for (float i = 0; i <= w; i += MM * 5) { cb.moveTo(x + i, y); cb.lineTo(x + i, y + h); }
        for (float i = 0; i <= h; i += MM * 5) { cb.moveTo(x, y + i); cb.lineTo(x + w, y + i); }
        cb.stroke(); cb.restoreState();

        // Garis Pembatas Kolom
        if (cols > 1) {
            cb.saveState(); cb.setLineWidth(0.5f); cb.setColorStroke(new BaseColor(140, 140, 140));
            float colWidth = w / cols;
            for (int c = 1; c < cols; c++) { float lx = x + c * colWidth; cb.moveTo(lx, y); cb.lineTo(lx, y + h); }
            cb.stroke(); cb.restoreState();
        }

        if (leadsData == null || leadsData.isEmpty()) return;
        
        // --- HITUNG AREA SINYAL (Sisihkan 5mm di bawah jika ada footer info) ---
        float actualH = hasFooter ? h - (5 * MM) : h; 
        float signalStartY = y + (hasFooter ? 5 * MM : 0); 
        
        String[] names = {"I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6"};
        float colWidth = w / cols, rowHeight = actualH / rows;

        for (int i = 0; i < Math.min(maxLeads, leadsData.size()); i++) {
            int targetIndex = i;
            if (maxLeads == 1) targetIndex = (leadsData.size() > 1) ? 1 : 0;
            double[] signal = leadsData.get(targetIndex);
            if (signal == null || signal.length == 0) continue;

            int colIndex = i / rows, rowIndex = i % rows;
            float xBaseRaw = x + (colIndex * colWidth);
            float yBaseRaw = signalStartY + actualH - (rowIndex * rowHeight) - (rowHeight / 2f);
            
            // Align xBase and yBase perfectly to the nearest 5mm (major grid line)
            float xBase = x + Math.round((xBaseRaw - x) / (MM * 5f)) * (MM * 5f);
            float yBase = y + Math.round((yBaseRaw - y) / (MM * 5f)) * (MM * 5f);

            // Label Lead
            cb.saveState();
            try {
                cb.beginText();
                cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false), 7);
                cb.setColorFill(new BaseColor(60, 60, 60));
                cb.setTextMatrix(xBase + 3, yBase + (rowHeight / 2f) - 10);
                cb.showText((maxLeads == 1) ? "Lead " + names[targetIndex] : names[i]);
                cb.endText();
            } catch (Exception ignored) {}
            cb.restoreState();

            // Garis Isoelektrik
            cb.saveState(); cb.setLineWidth(0.3f); cb.setColorStroke(new BaseColor(180, 180, 180));
            cb.moveTo(xBase, yBase); cb.lineTo(xBase + colWidth, yBase); cb.stroke(); cb.restoreState();

            // Calibration square pulse: 2mm flat, 10mm (1mV) high, 5mm (0.2s) wide, 2mm flat
            float calH = 10f * MM; // 1mV = 10mm
            float calW = 5f * MM;  // 0.2s * 25mm/s = 5mm
            float flatMargin = 2f * MM;
            cb.saveState(); cb.setColorStroke(new BaseColor(0, 0, 180)); cb.setLineWidth(0.7f);
            cb.moveTo(xBase, yBase);
            cb.lineTo(xBase + flatMargin, yBase);
            cb.lineTo(xBase + flatMargin, yBase + calH);
            cb.lineTo(xBase + flatMargin + calW, yBase + calH);
            cb.lineTo(xBase + flatMargin + calW, yBase);
            cb.lineTo(xBase + flatMargin + calW + flatMargin, yBase);
            cb.stroke(); cb.restoreState();

            // Plot Sinyal EKG (mulai setelah calibration pulse)
            float signalStartX = xBase + flatMargin + calW + flatMargin;
            cb.saveState(); cb.setColorStroke(new BaseColor(0, 0, 180)); cb.setLineWidth(0.7f);
            float dx = (float) ((25f * MM) / fs), gain = 10f * MM, halfRow = rowHeight / 2f;
            boolean first = true;
            for (int j = 0; j < signal.length; j++) {
                float px = signalStartX + (j * dx);
                if (px > xBase + colWidth) break;
                float pyRaw = yBase + (float) (signal[j] * gain);
                float py = Math.max(yBase - halfRow, Math.min(yBase + halfRow, pyRaw));
                if (first) { cb.moveTo(px, py); first = false; } else { cb.lineTo(px, py); }
            }
            cb.stroke(); cb.restoreState();
        }
        
        // Teks di bawah grafik monitoring 
        if (hasFooter && info != null) {
            cb.saveState();
            try {
                cb.beginText();
                cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false), 8);
                cb.setColorFill(new BaseColor(20, 20, 20));
                
                // Menyusun teks informasi
                String footerStr = String.format("%s                %s              %s              Layout: %d by %d", 
                    info.getOrDefault("filter", "40 Hz"),
                    info.getOrDefault("speed", "25 mm/s"),
                    info.getOrDefault("gain", "10 mm/mV"),
                    cols, rows);
                
                // Menulis di kotak paling bawah, X bergeser sedikit ke kanan, Y bergeser sedikit ke atas dari batas
                cb.setTextMatrix(x + 5, y + 4); 
                cb.showText(footerStr);
                cb.endText();
            } catch (Exception ignored) {}
            cb.restoreState();
        }
    }
}