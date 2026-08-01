/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package projectecg;
import projectecg.services.PatientService;
import projectecg.services.MqttService;


import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
/**
 *
 * @author fizel
 */
public class MainDashboard extends javax.swing.JFrame {
    private BlockingQueue<Byte> queueByte = new LinkedBlockingQueue<>();
    private BlockingQueue<String> queueString = new LinkedBlockingQueue<>();
    private SerialCommBiner serialComm;
    private Thread parsingThread;
    private ThrdParsingECG parserRunnable;
    private javax.swing.Timer consumerTimer;
    private projectecg.services.MqttService mqttService;
    private java.io.BufferedWriter streamFileWriter = null; // Data stream file writer


    private PatientService patientService;
    private FindPatientPanel findPatientWindow;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MainDashboard.class.getName());

    private String currentMode = "Monitoring"; 
    private Timer ecgTimer; 
    private boolean isMqttActive   = false; 
    private boolean isMqttCaptured = false; 
    private static final int MQTT_CAPTURE_THRESHOLD = 2500;
    private int xGraph = 0; 
    private int[] lastY = new int[12]; 
    private java.awt.image.BufferedImage ecgBufferImage;
    
    public String filterType = "-"; // Bandpass, Low-pass, High-pass
    public boolean isNotchOn = false;
    public boolean isEmgOn = false;
    public boolean isPanTompkinsOn = false;
    public int notchFreq = 50;
    public String qtcFormula = "-";
    // 50 atau 60 Hz
    // --- VARIABLES FOR WAVE VISUALIZATION ---
    public boolean showPWave = false;
    public boolean showTWave = false;
    public boolean showRWave = false;
    
    // Penampung lokasi index hasil analisis background
    private java.util.List<Integer> cachedPIndices = new java.util.ArrayList<>();
    private java.util.List<Integer> cachedTIndices = new java.util.ArrayList<>();
    private java.util.List<Integer> cachedRIndices = new java.util.ArrayList<>();
    
    // Cache ECGAnalyzer untuk anotasi gelombang pada Mode Classification
    private ECGAnalyzer classificationAnalyzer = null;
    
    private java.util.List<double[]> leadsData = new java.util.ArrayList<>();
    private java.util.List<double[]> processedData = new java.util.ArrayList<>();// Menampung data dari file
    private int currentDataIndex = 0;         // Pointer untuk animasi (sedang baca data ke berapa)
    private boolean isFileLoaded = false;     // true = CSV/SCP file has been uploaded (enables playback)
    private boolean hasRecordedData = false;  // true = live recording was stopped (enables Analyze, no playback)
    
    
    // serial I/O is handled entirely by SerialCommBiner (jSerialComm)
    private String serialBuffer = ""; // Untuk menampung potongan data string
    private int writeIndex = 0;       // Posisi index data saat merekam real-time
    private final int MAX_SAMPLE = 50000; // Kapasitas rekam (misal cukup utk bbrp menit)
    private int currentSpeedStep = 2;
    
    private double currentGainMultiplier = 2.0;
    private int layoutRows = 6; // Default 2x6 (6 Baris)
    private int layoutCols = 2;  // Default 2x6 (2 Kolom)
    
    private double currentFs = 125.0;
    private int refreshCounter = 0;
    public int rhythmTimeSeconds = 10;
    private Process pythonProcess;

    
    public MainDashboard() {
        initComponents();

        jon.setSelected(true);
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                resetBackground();
            }
        });

        jPanel_ecgsignal.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                resetBackground();
            }
            public void componentShown(java.awt.event.ComponentEvent evt) {
                resetBackground();
            }
        });
        
        jPanelDropzone.setBorder(javax.swing.BorderFactory.createDashedBorder(java.awt.Color.LIGHT_GRAY, 2.0f, 5.0f, 2.0f, false));
        jPanelDropzone.setBackground(java.awt.Color.WHITE);

        leadsData = new ArrayList<>();
        processedData = new ArrayList<>();
        
        // Siapkan 12 tempat (untuk 12 Lead) dengan ukuran MAX_SAMPLE
        for (int i = 0; i < 12; i++) {
            leadsData.add(new double[MAX_SAMPLE]);
            processedData.add(new double[MAX_SAMPLE]);
        }

        findPatientWindow = new FindPatientPanel(this, true);
        findPatientWindow.setLocationRelativeTo(this);

        // Matikan tombol Analyze di awal (karena default Monitoring)
        btnAnalyze.setEnabled(false); 
        
        // Setup Timer untuk Animasi Grafik (Jalan setiap 50 milidetik)
        ecgTimer = new Timer(50, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                drawLiveECG(); // Method menggambar (kita buat di poin C)
            }
        });
        ecgTimer.start(); 
        
        // Auto-scan port COM sistem
        refreshPortComboBox();
        
        try {
            patientService = new PatientService();
            logger.info("✓ PatientService initialized");
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "✗ Failed to init PatientService", e);
        }

        // Koneksi MQTT
        mqttService = new projectecg.services.MqttService(queueString, this);
        mqttService.connect();

        // Nyalakan mesin pembaca data MQTT agar grafik bisa langsung berjalan
        if (consumerTimer == null) {
             consumerTimer = new javax.swing.Timer(10, e -> processParsedData());
             consumerTimer.start();
        }
        isMqttActive = true; // MQTT selalu aktif setelah koneksi

        // Jalankan Python di Background
        startPythonBackend();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Mengeksekusi command CMD rahasia dari dalam Java
                Runtime.getRuntime().exec("taskkill /f /im modelekg.exe /t");
                System.out.println("Backend berhasil dimatikan.");
            } catch (Exception e) {
                System.err.println("Gagal mematikan backend: " + e.getMessage());
            }
        }));

        // Action listener for Yes button in dialogEndSession
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dialogEndSession.setVisible(false);
                executeEndSession();
            }
        });
    }

    // Method khusus untuk memanggil modelekg.exe
    private void startPythonBackend() {
        try {
            String flaskExePath = "modelekg.exe"; 
            
            ProcessBuilder pb = new ProcessBuilder(flaskExePath);
            pythonProcess = pb.start();
            System.out.println("Backend Python sedang berjalan di background...");
            
        } catch (Exception e) {
            System.err.println("Gagal menjalankan backend Python: " + e.getMessage());
        }
    }

    // Getter method untuk NewPatientFrame bisa akses
    public PatientService getPatientService() {
        return patientService;
    }

    private void refreshPortComboBox() {
        // Use jSerialComm to enumerate available COM ports
        com.fazecast.jSerialComm.SerialPort[] ports = com.fazecast.jSerialComm.SerialPort.getCommPorts();
        System.out.println("Scan port: ditemukan " + ports.length + " port.");
        javax.swing.DefaultComboBoxModel<String> model = new javax.swing.DefaultComboBoxModel<>();
        for (com.fazecast.jSerialComm.SerialPort p : ports) {
            System.out.println("  -> " + p.getSystemPortName());
            model.addElement(p.getSystemPortName());
        }
        if (ports.length == 0) {
            model.addElement("(No Port)");
            System.out.println("[!] Tidak ada port COM terdeteksi!");
        }
        jComboBox_port.setModel(model);
    }

    // Inisialisasi SerialCommBiner untuk baca data serial dan parsing
    public java.util.List<double[]> getProcessedLeadsData() {
        return this.processedData;
        }
        
    // Getter untuk currentFs agar bisa diakses dari AnalyzePanel
    public double getCurrentFs() {
        return this.currentFs;
    }

    public String getSpeedSetting() {
        if (jComboBox_cbSpeed != null && jComboBox_cbSpeed.getSelectedItem() != null) {
            return jComboBox_cbSpeed.getSelectedItem().toString();
        }
        return "25 mm/s";
    }
    
    public String getGainSetting() {
        if (jComboBox_cbGain != null && jComboBox_cbGain.getSelectedItem() != null) {
            return jComboBox_cbGain.getSelectedItem().toString();
        }
        return "5 mm/mV";
    }
    
    public String getFilterSetting() {
        String f = filterType;
        if (isNotchOn) f = notchFreq + " Hz " + (f.equals("-") ? "" : "+ " + f);
        if (isEmgOn) f += " + EMG";
        return f.equals("-") ? "40 Hz (Default)" : f; // Standar EKG jika tanpa filter khusus
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dialogPatient = new javax.swing.JDialog();
        btnNew = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jtxtID = new javax.swing.JTextField();
        jlblHR = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        btnFind = new javax.swing.JButton();
        jPanel_panelpatient2 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jLabel28 = new javax.swing.JLabel();
        jtxtBirthdate2 = new javax.swing.JTextField();
        jScrollPane4 = new javax.swing.JScrollPane();
        jtxtNotes2 = new javax.swing.JTextArea();
        jtxtAge2 = new javax.swing.JTextField();
        txtsex = new javax.swing.JTextField();
        jLabel29 = new javax.swing.JLabel();
        txtname = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jLabel10 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jTextField5 = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        jTextField6 = new javax.swing.JTextField();
        dialogUpload = new javax.swing.JDialog();
        jPanel1 = new javax.swing.JPanel();
        jPanelDropzone = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        btnChooseFile = new javax.swing.JButton();
        dialogEndSession = new javax.swing.JDialog();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jLabel18 = new javax.swing.JLabel();
        jPanel_ecgsignal = new javax.swing.JPanel();
        jPanelButton = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        jComboBox_port = new javax.swing.JComboBox<>();
        jrecord = new javax.swing.JToggleButton();
        btnAnalyze = new javax.swing.JButton();
        btnsettings = new javax.swing.JButton();
        jbtnfreeze = new javax.swing.JButton();
        jbtnDownload = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        jComboBox_cbSpeed = new javax.swing.JComboBox<>();
        jLabel14 = new javax.swing.JLabel();
        jComboBox_cbGain = new javax.swing.JComboBox<>();
        jLabel17 = new javax.swing.JLabel();
        jon = new javax.swing.JToggleButton();
        jComboBox1 = new javax.swing.JComboBox<>();
        btnUploadFile = new javax.swing.JButton();
        btnPatientInput = new javax.swing.JButton();
        btnEndSession = new javax.swing.JButton();
        jPanelHeader = new javax.swing.JPanel();
        lblTitlePatient = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        lblTitleDashboard = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();

        dialogPatient.setBackground(new java.awt.Color(255, 234, 227));

        btnNew.setText("New");
        btnNew.addActionListener(this::btnNewActionPerformed);

        jLabel2.setText("ID :");

        jtxtID.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jtxtID.addActionListener(this::jtxtIDActionPerformed);

        jlblHR.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jlblHR.setText("--");
        jlblHR.addActionListener(this::jlblHRActionPerformed);

        jLabel3.setText("HR:");

        jLabel4.setText("♥");

        btnFind.setText("Find");
        btnFind.addActionListener(this::btnFindActionPerformed);

        jPanel_panelpatient2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, " Patient Information", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.DEFAULT_POSITION));
        jPanel_panelpatient2.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel23.setText("Jenis Kelamin        :");

        jLabel24.setText("Tempat/Tgl Lahir  : ");

        jLabel25.setText("Umur                    : ");

        jLabel28.setText("Saran Kesehatan     :");

        jtxtBirthdate2.addActionListener(this::jtxtBirthdate2ActionPerformed);

        jtxtNotes2.setColumns(20);
        jtxtNotes2.setLineWrap(true);
        jtxtNotes2.setRows(5);
        jtxtNotes2.setWrapStyleWord(true);
        jScrollPane4.setViewportView(jtxtNotes2);

        jtxtAge2.addActionListener(this::jtxtAge2ActionPerformed);

        txtsex.addActionListener(this::txtsexActionPerformed);

        jLabel29.setText("Nama                   :");

        txtname.addActionListener(this::txtnameActionPerformed);

        jLabel1.setText("NIK                       :");

        jLabel8.setText("Agama                  :");

        jLabel9.setText("Alamat                 :");

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        jLabel10.setText("Status Perkawinan :");

        jTextField1.addActionListener(this::jTextField1ActionPerformed);

        jLabel11.setText("Pekerjaan               :");

        jLabel12.setText("Kewarganegaraan  :");

        jLabel19.setText("Gol. Darah            :");

        jTextField6.addActionListener(this::jTextField6ActionPerformed);

        javax.swing.GroupLayout jPanel_panelpatient2Layout = new javax.swing.GroupLayout(jPanel_panelpatient2);
        jPanel_panelpatient2.setLayout(jPanel_panelpatient2Layout);
        jPanel_panelpatient2Layout.setHorizontalGroup(
            jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel23)
                    .addComponent(jLabel24))
                .addGap(12, 12, 12)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtsex)
                    .addComponent(jtxtBirthdate2, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                .addGap(6, 6, 6))
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jTextField5)
                .addContainerGap())
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel8)
                            .addComponent(jLabel10))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField2)
                            .addComponent(jTextField1)))
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextField3))
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addComponent(jLabel12)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextField4))
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel25)
                            .addComponent(jLabel9)
                            .addComponent(jLabel19))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField6)
                            .addComponent(jtxtAge2)
                            .addComponent(jScrollPane1))))
                .addContainerGap())
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel29, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(txtname))
                    .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel28)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel_panelpatient2Layout.setVerticalGroup(
            jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel_panelpatient2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel29)
                    .addComponent(txtname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel24)
                    .addComponent(jtxtBirthdate2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel23)
                    .addComponent(txtsex, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel19))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtxtAge2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel25))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel9)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel_panelpatient2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel28)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout dialogPatientLayout = new javax.swing.GroupLayout(dialogPatient.getContentPane());
        dialogPatient.getContentPane().setLayout(dialogPatientLayout);
        dialogPatientLayout.setHorizontalGroup(
            dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogPatientLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dialogPatientLayout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jtxtID, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jlblHR, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(dialogPatientLayout.createSequentialGroup()
                            .addComponent(btnNew, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(btnFind, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(jPanel_panelpatient2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        dialogPatientLayout.setVerticalGroup(
            dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogPatientLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jtxtID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(jlblHR, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(dialogPatientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnNew, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnFind, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel_panelpatient2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        dialogUpload.setTitle("Upload File");

        jPanelDropzone.setBackground(new java.awt.Color(255, 255, 255));
        jPanelDropzone.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel6.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel6.setText("Select File here");

        jLabel7.setBackground(new java.awt.Color(204, 204, 204));
        jLabel7.setForeground(new java.awt.Color(153, 153, 153));
        jLabel7.setText("File supported: CSV, SCP");

        btnChooseFile.setBackground(new java.awt.Color(242, 242, 242));
        btnChooseFile.setText("Choose File");
        btnChooseFile.addActionListener(this::btnChooseFileActionPerformed);

        javax.swing.GroupLayout jPanelDropzoneLayout = new javax.swing.GroupLayout(jPanelDropzone);
        jPanelDropzone.setLayout(jPanelDropzoneLayout);
        jPanelDropzoneLayout.setHorizontalGroup(
            jPanelDropzoneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelDropzoneLayout.createSequentialGroup()
                .addGap(120, 120, 120)
                .addGroup(jPanelDropzoneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(btnChooseFile)
                    .addComponent(jLabel7)
                    .addComponent(jLabel6))
                .addContainerGap(117, Short.MAX_VALUE))
        );
        jPanelDropzoneLayout.setVerticalGroup(
            jPanelDropzoneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelDropzoneLayout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnChooseFile)
                .addContainerGap(53, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(jPanelDropzone, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(15, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addComponent(jPanelDropzone, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(16, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout dialogUploadLayout = new javax.swing.GroupLayout(dialogUpload.getContentPane());
        dialogUpload.getContentPane().setLayout(dialogUploadLayout);
        dialogUploadLayout.setHorizontalGroup(
            dialogUploadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogUploadLayout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        dialogUploadLayout.setVerticalGroup(
            dialogUploadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogUploadLayout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        dialogEndSession.setTitle("Konfirmasi End Session");

        jLabel15.setText("Apakah Anda yakin ingin mengakhir sesi pemeriksaan saat ini?");

        jLabel16.setText("Semua data hasil analisis dan rekaman yang belum tersimpan akan dibersihkan.");

        jButton1.setText("Yes");

        jButton3.setText("No");
        jButton3.addActionListener(this::jButton3ActionPerformed);

        jLabel18.setIcon(new javax.swing.ImageIcon(getClass().getResource("/projectecg/icons/warning.png"))); // NOI18N

        javax.swing.GroupLayout dialogEndSessionLayout = new javax.swing.GroupLayout(dialogEndSession.getContentPane());
        dialogEndSession.getContentPane().setLayout(dialogEndSessionLayout);
        dialogEndSessionLayout.setHorizontalGroup(
            dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogEndSessionLayout.createSequentialGroup()
                .addGroup(dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dialogEndSessionLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(jLabel18)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel16)
                            .addComponent(jLabel15)))
                    .addGroup(dialogEndSessionLayout.createSequentialGroup()
                        .addGap(184, 184, 184)
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton3)))
                .addContainerGap(16, Short.MAX_VALUE))
        );
        dialogEndSessionLayout.setVerticalGroup(
            dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dialogEndSessionLayout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addGroup(dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dialogEndSessionLayout.createSequentialGroup()
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel16))
                    .addComponent(jLabel18, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(dialogEndSessionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton3))
                .addContainerGap(10, Short.MAX_VALUE))
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Kardiolin-AI  V. 1.2");
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setPreferredSize(new java.awt.Dimension(1920, 980));

        jPanel_ecgsignal.setBackground(new java.awt.Color(255, 255, 255));
        jPanel_ecgsignal.setForeground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout jPanel_ecgsignalLayout = new javax.swing.GroupLayout(jPanel_ecgsignal);
        jPanel_ecgsignal.setLayout(jPanel_ecgsignalLayout);
        jPanel_ecgsignalLayout.setHorizontalGroup(
            jPanel_ecgsignalLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1920, Short.MAX_VALUE)
        );
        jPanel_ecgsignalLayout.setVerticalGroup(
            jPanel_ecgsignalLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 716, Short.MAX_VALUE)
        );

        getContentPane().add(jPanel_ecgsignal, java.awt.BorderLayout.CENTER);
        jPanel_ecgsignal.getAccessibleContext().setAccessibleName("");

        jLabel5.setText("PORT:");

        jComboBox_port.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "COM5", "COM10", "COM12", "COM4" }));

        jrecord.setText("Record/Stop");
        jrecord.addActionListener(this::jrecordActionPerformed);

        btnAnalyze.setText("Analyze");
        btnAnalyze.addActionListener(this::btnAnalyzeActionPerformed);

        btnsettings.setText("Settings");
        btnsettings.addActionListener(this::btnsettingsActionPerformed);

        jbtnfreeze.setText("Freeze");
        jbtnfreeze.addActionListener(this::jbtnfreezeActionPerformed);

        jbtnDownload.setText("Download");
        jbtnDownload.addActionListener(this::jbtnDownloadActionPerformed);

        jLabel13.setText("Speed:");

        jComboBox_cbSpeed.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "25mm/s", "100 mm/s", "50 mm/s", "12.5 mm/s" }));
        jComboBox_cbSpeed.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jComboBox_cbSpeed.addActionListener(this::jComboBox_cbSpeedActionPerformed);

        jLabel14.setText("Gain:");

        jComboBox_cbGain.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "20 mm/mV", "10 mm/mV", "5 mm/mV" }));
        jComboBox_cbGain.addActionListener(this::jComboBox_cbGainActionPerformed);

        jLabel17.setText("Grid:");

        jon.setBackground(new java.awt.Color(204, 204, 204));
        jon.setText("✓");
        jon.setToolTipText("");
        jon.addActionListener(this::jonActionPerformed);

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "2 x  6", "1 x 12", "3 x 4" }));
        jComboBox1.addActionListener(this::jComboBox1ActionPerformed);

        btnUploadFile.setText("Upload File");
        btnUploadFile.addActionListener(this::btnUploadFileActionPerformed);

        btnPatientInput.setIcon(new javax.swing.ImageIcon(getClass().getResource("/projectecg/icons/btn patient input.png"))); // NOI18N
        btnPatientInput.setToolTipText("");
        btnPatientInput.setBorder(null);
        btnPatientInput.setContentAreaFilled(false);
        btnPatientInput.setFocusPainted(false);
        btnPatientInput.addActionListener(this::btnPatientInputActionPerformed);

        btnEndSession.setText("End Session");
        btnEndSession.addActionListener(this::btnEndSessionActionPerformed);

        javax.swing.GroupLayout jPanelButtonLayout = new javax.swing.GroupLayout(jPanelButton);
        jPanelButton.setLayout(jPanelButtonLayout);
        jPanelButtonLayout.setHorizontalGroup(
            jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelButtonLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnPatientInput, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelButtonLayout.createSequentialGroup()
                        .addComponent(jComboBox_port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jrecord))
                    .addGroup(jPanelButtonLayout.createSequentialGroup()
                        .addComponent(jComboBox_cbSpeed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(23, 23, 23)
                        .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox_cbGain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel17, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jon)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 983, Short.MAX_VALUE)
                .addComponent(btnUploadFile)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jbtnfreeze, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jbtnDownload, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnsettings, javax.swing.GroupLayout.DEFAULT_SIZE, 90, Short.MAX_VALUE))
                .addGap(8, 8, 8)
                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnEndSession, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAnalyze, javax.swing.GroupLayout.DEFAULT_SIZE, 98, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelButtonLayout.setVerticalGroup(
            jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelButtonLayout.createSequentialGroup()
                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelButtonLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel5)
                            .addComponent(jComboBox_port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jrecord))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel17)
                            .addComponent(jon)
                            .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanelButtonLayout.createSequentialGroup()
                        .addGap(38, 38, 38)
                        .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel13)
                            .addComponent(jComboBox_cbSpeed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jComboBox_cbGain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanelButtonLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelButtonLayout.createSequentialGroup()
                                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(btnAnalyze, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jbtnfreeze)
                                    .addComponent(btnsettings)
                                    .addComponent(btnUploadFile))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanelButtonLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jbtnDownload)
                                    .addComponent(btnEndSession))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelButtonLayout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(btnPatientInput, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(12, 12, 12))
        );

        getContentPane().add(jPanelButton, java.awt.BorderLayout.SOUTH);

        jPanelHeader.setLayout(new java.awt.BorderLayout());

        lblTitlePatient.setFont(new java.awt.Font("Segoe UI", 2, 14)); // NOI18N
        lblTitlePatient.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitlePatient.setText("Patient Monitoring: -  ID: -");
        jPanelHeader.add(lblTitlePatient, java.awt.BorderLayout.PAGE_END);

        jPanel2.setBackground(new java.awt.Color(153, 153, 153));
        jPanel2.setLayout(new java.awt.BorderLayout());

        lblTitleDashboard.setBackground(new java.awt.Color(153, 153, 153));
        lblTitleDashboard.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblTitleDashboard.setForeground(new java.awt.Color(255, 255, 255));
        lblTitleDashboard.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitleDashboard.setText("Kardiolin-AI  V. 1.2");
        lblTitleDashboard.setOpaque(true);
        jPanel2.add(lblTitleDashboard, java.awt.BorderLayout.CENTER);

        jButton2.setBackground(new java.awt.Color(153, 153, 153));
        jButton2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/projectecg/icons/btn history.png"))); // NOI18N
        jButton2.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10));
        jButton2.setBorderPainted(false);
        jButton2.setContentAreaFilled(false);
        jButton2.setFocusPainted(false);
        jButton2.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jButton2.addActionListener(this::jButton2ActionPerformed);
        jPanel2.add(jButton2, java.awt.BorderLayout.WEST);

        jPanelHeader.add(jPanel2, java.awt.BorderLayout.PAGE_START);

        getContentPane().add(jPanelHeader, java.awt.BorderLayout.NORTH);
        jPanelHeader.getAccessibleContext().setAccessibleName("");

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void jtxtIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jtxtIDActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jtxtIDActionPerformed

    private void jlblHRActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jlblHRActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jlblHRActionPerformed

    private void btnAnalyzeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event-btnAnalyzeActionPerformed
    // ════════════════════════════════════════════════════════════════════════════════════════════
    // NAVIGATION: btnAnalyze → Triggers Python Analysis directly (REFACTORED)
    // PURPOSE: Runs Python ML Model Classification and shows Health Report immediately
    // FLOW: Click Analyze → Python Analysis → Health Report Dialog → OK → Detailed Metrics Panel
    // ════════════════════════════════════════════════════════════════════════════════════════════
    
    // 1. VALIDATE: Check if ECG data is loaded
        if ((!isFileLoaded && !hasRecordedData) || leadsData == null || leadsData.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Please upload a CSV file or record data first!");
            return;
        }

        // 2. DIRECT EXECUTION: Run Python analysis immediately (NO choice dialog)
        analyzewithPython();
    }//GEN-LAST:event-btnAnalyzeActionPerformed

    private void btnsettingsActionPerformed(java.awt.event.ActionEvent evt) {                                            
        // Navigation: Open Settings Dialog
        // WHY: .setEnabled() is used to disable ECG settings when in Monitoring mode
        // This prevents the user from changing filters when just viewing live data
        Settings settingsWindow = new Settings(this, true);
        
        settingsWindow.setCurrentSelection(
                this.currentMode, 
                this.filterType, 
                this.isNotchOn,
                this.notchFreq,
                this.isEmgOn,
                this.qtcFormula,
                this.rhythmTimeSeconds,
                this.showPWave, 
                this.showTWave, 
                this.showRWave,
                this.isPanTompkinsOn
        );
        
        // Display the Settings window
        settingsWindow.setLocationRelativeTo(this);
        settingsWindow.setVisible(true);
        
        // NEW: After settings dialog closes, enforce button state based on current mode
        enforceButtonState();
    }                                           

    private void jbtnfreezeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event-jbtnfreezeActionPerformed
        // Cek apakah Timer sedang jalan atau mati
        if (ecgTimer.isRunning()) {
            // JIKA SEDANG JALAN -> MATIKAN (FREEZE)
            ecgTimer.stop();
            jbtnfreeze.setText("Resume"); // Ubah teks tombol jadi Resume
            jbtnfreeze.setBackground(Color.GREEN); // Opsional: Beri warna merah biar jelas
        } else {
            // JIKA SEDANG MATI -> JALANKAN LAGI (RESUME)
            ecgTimer.start();
            jbtnfreeze.setText("Freeze"); // Kembalikan teks jadi Freeze
            jbtnfreeze.setBackground(null); // Kembalikan warna asli
        }        // TODO add your handling code here:
    }//GEN-LAST:event-jbtnfreezeActionPerformed

    private void jbtnDownloadActionPerformed(java.awt.event.ActionEvent evt) {
        // 1. VALIDATE: Check if ECG data exists
        if (leadsData == null || leadsData.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, 
                "No ECG data to download!\n\nPlease either:\n• Upload a CSV file, or\n• Record data from serial port first.",
                "No Data Available", 
                javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int sampleCount = leadsData.get(0).length;
        if (sampleCount == 0) {
            javax.swing.JOptionPane.showMessageDialog(this, 
                "ECG data is empty. Please record or upload data first.",
                "Empty Data", 
                javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 2. ASK USER: Export raw, processed, or SCP?
        String[] options = {"Raw Signal", "Filtered Signal", "SCP File", "Cancel"};
        int choice = javax.swing.JOptionPane.showOptionDialog(this,
            "Which data would you like to export?\n\n" +
            "• Raw Signal: Original unfiltered ECG data (.csv)\n" +
            "• Filtered Signal: Data with applied filters (.csv)\n" +
            "• SCP File: Standard Communication Protocol for ECG (.scp)",
            "Select Export Type",
            javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE,
            null, options, options[2]); // Default pilih SCP
        
        if (choice == 3 || choice == javax.swing.JOptionPane.CLOSED_OPTION) {
            return; // User cancelled
        }
        
        // 3. FILE CHOOSER: Let user select save location
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(choice == 2 ? "Save Data as SCP-ECG" : "Save ECG Data as CSV");
        
        String patientID = jtxtID.getText().trim();
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String baseFilename = "ECG_" + (patientID.isEmpty() ? "Unknown" : patientID) + "_" + timestamp;
        
        // Tentukan ekstensi default berdasarkan pilihan user
        String defaultFilename = (choice == 2) ? baseFilename + ".scp" : baseFilename + ".csv";
        fileChooser.setSelectedFile(new File(defaultFilename));
        
        if (choice == 2) {
            FileNameExtensionFilter scpFilter = new FileNameExtensionFilter("SCP-ECG Files (*.scp)", "scp");
            fileChooser.setFileFilter(scpFilter);
        } else {
            FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV Files (*.csv)", "csv");
            fileChooser.setFileFilter(csvFilter);
        }
        
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return; 
        }
        
        File fileToSave = fileChooser.getSelectedFile();
        String filePath = fileToSave.getAbsolutePath();
        String patientName = txtname.getText().trim();
        boolean success = false;
        String resultMessage = "";
        
        // 4. EXPORT BASED ON CHOICE
        if (choice == 2) {
            // =========================================================
            // JALUR SCP FILE (Jembatan ke C#)
            // =========================================================
            
            // 1. Pastikan Ekstensi Tujuan adalah .scp
            String finalPath = fileToSave.getAbsolutePath();
            if (finalPath.toLowerCase().endsWith(".csv")) {
                finalPath = finalPath.substring(0, finalPath.length() - 4);
            }
            if (!finalPath.toLowerCase().endsWith(".scp")) {
                finalPath += ".scp";
            }
            fileToSave = new File(finalPath);
            
            // Dapatkan folder tujuan dan ID pasien
            String outputDir = fileToSave.getParent();
            String rawFileName = fileToSave.getName().replace(".scp", "");
            
            // Jika ID kosong (belum klik Find Patient), pakai nama file
            String finalPatientID = patientID.isEmpty() ? rawFileName : patientID; 
            
            // 2. Buat File Temp CSV untuk dilempar ke C#
            File tempCsv = new File(outputDir, "temp_ecg.csv");
            
            try {
                // Tulis data 12 Lead ke file temp
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(tempCsv));
                
                // Menentukan batas data (Bisa dari upload CSV atau record langsung)
                int actualLength = isFileLoaded ? leadsData.get(0).length : writeIndex;
                
                // Looping per baris data (waktu)
                for (int i = 0; i < actualLength; i++) {
                    StringBuilder rowData = new StringBuilder();
                    
                    // Looping per kolom (12 Lead)
                    for (int lead = 0; lead < 12; lead++) {
                        // Ambil angka jika lead tersedia, jika tidak isi dengan 0.0
                        double value = (lead < leadsData.size()) ? leadsData.get(lead)[i] : 0.0;
                        
                        // Masukkan angka dengan format desimal Amerika (titik)
                        rowData.append(String.format(java.util.Locale.US, "%f", value));
                        
                        // Tambahkan koma sebagai pemisah antar lead (kecuali di lead terakhir)
                        if (lead < 11) {
                            rowData.append(",");
                        }
                    }
                    // Tulis baris tersebut ke file
                    pw.println(rowData.toString());
                }
                pw.close();
                
                // 3. PANGGIL SCPMAKER.EXE
                java.io.File exeFile = new java.io.File("ScpMaker" + java.io.File.separator + "bin" + java.io.File.separator + "Release" + java.io.File.separator + "net9.0" + java.io.File.separator + "ScpMaker.exe");
                String exePath = exeFile.getAbsolutePath();
                
                if (!exeFile.exists()) {
                    throw new Exception("ScpMaker.exe tidak ditemukan di: " + exePath);
                }
                
                ProcessBuilder pb = new ProcessBuilder(
                    exePath, 
                    tempCsv.getAbsolutePath(), 
                    String.valueOf((int)currentFs), 
                    outputDir, 
                    finalPatientID
                );
                
                Process process = pb.start();
                
                // BACA PESAN ERROR/LOG DARI TERMINAL C#
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                StringBuilder csharpOutput = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    csharpOutput.append(line).append("\n");
                }
                
                process.waitFor(); 
                
                // 4. Hapus file temp CSV
                if (tempCsv.exists()) {
                    tempCsv.delete();
                }
                
                // 5. Cek apakah file SCP berhasil tercipta
                File generatedScpFile = new File(outputDir, finalPatientID + ".scp");
                
                if (generatedScpFile.exists() && !generatedScpFile.getAbsolutePath().equals(fileToSave.getAbsolutePath())) {
                    generatedScpFile.renameTo(fileToSave);
                }
                
                if (fileToSave.exists()) {
                    javax.swing.JOptionPane.showMessageDialog(this, 
                        "File SCP-ECG berhasil dibuat!\nLokasi: " + fileToSave.getAbsolutePath(), 
                        "Export SCP Success", 
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } else {
                    // JIKA GAGAL, TAMPILKAN PESAN ERROR ASLI DARI C#
                    throw new Exception("Pesan Error dari ScpMaker:\n" + csharpOutput.toString());
                }
                
            } catch (Exception ex) {
                javax.swing.JOptionPane.showMessageDialog(this, 
                    "Gagal membuat file SCP:\n" + ex.getMessage(), 
                    "SCP Export Error", 
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            }
            
            return; // Selesai untuk jalur SCP
            
        } else {
            // =========================================================
            // JALUR CSV FILE (Raw atau Filtered)
            // =========================================================
            if (!filePath.toLowerCase().endsWith(".csv")) {
                filePath += ".csv";
                fileToSave = new File(filePath);
            }
            
            if (fileToSave.exists()) {
                int overwrite = javax.swing.JOptionPane.showConfirmDialog(this,
                    "File already exists. Overwrite?", "Confirm Overwrite",
                    javax.swing.JOptionPane.YES_NO_OPTION);
                if (overwrite != javax.swing.JOptionPane.YES_OPTION) return;
            }
            
            boolean exportProcessed = (choice == 1); 
            java.util.List<double[]> sourceData = exportProcessed ? processedData : leadsData;
            java.util.List<double[]> dataToExport = generate12LeadData(sourceData);
            
            success = CSVExporter.exportSignalToCSV(
                fileToSave, dataToExport, currentFs, patientID, patientName);
            
            String dataType = exportProcessed ? "Filtered" : "Raw";
            resultMessage = "ECG data exported successfully!\n\n" +
                "File: " + fileToSave.getAbsolutePath() + "\n" +
                "Type: " + dataType + " Signal\n" +
                "Samples: " + sampleCount;
                
            if (success) {
                javax.swing.JOptionPane.showMessageDialog(this, resultMessage, "Export Successful", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Failed to export ECG data.", "Export Failed", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void jComboBox_cbSpeedActionPerformed(java.awt.event.ActionEvent evt) {
        String selected = jComboBox_cbSpeed.getSelectedItem().toString();
        
        if (selected.contains("12.5")) {
            currentSpeedStep = 1; // SANGAT RAPAT (Cocok untuk melihat ritme panjang)
        } else if (selected.contains("25")) {
            currentSpeedStep = 2; // NORMAL / STANDAR MEDIS
        } else if (selected.contains("50")) {
            currentSpeedStep = 4; // RENGGANG (Lebar)
        } else if (selected.contains("100")) {
            currentSpeedStep = 8; // SANGAT RENGGANG (Sangat lebar/zoom)
        } else {
            currentSpeedStep = 2; // Default fallback
        }
        
        // Reset layar agar grafik dan kalibrasi tidak menumpuk
        xGraph = 0;
        jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
        jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
        resetBackground();
    }                                                
    

    private void jonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jonActionPerformed
        if (jon.isSelected()) {
            jon.setText("✓");
        } else {
            jon.setText("x");
        }
        resetBackground();
    }//GEN-LAST:event_jonActionPerformed

    private void btnFindActionPerformed(java.awt.event.ActionEvent evt) {
        // Ambil semua data pasien dari MySQL lewat PatientService
        if (this.patientService != null) {
            java.util.List<projectecg.models.Patient> allPatients = this.patientService.getAllPatients();
            findPatientWindow.populateTable(allPatients);
        }
        findPatientWindow.setLocationRelativeTo(this);
        findPatientWindow.setVisible(true);
    }

    private void btnNewActionPerformed(java.awt.event.ActionEvent evt) {
        javax.swing.table.DefaultTableModel model = findPatientWindow.getTableModel();
        NewPatientFrame newPatient = new NewPatientFrame(this, true, model, this.patientService);
        newPatient.setLocationRelativeTo(this);
        newPatient.setVisible(true);
    }

    private void btnUploadFileActionPerformed(java.awt.event.ActionEvent evt) {
    dialogUpload.pack(); 
    dialogUpload.setLocationRelativeTo(this); 
    dialogUpload.getContentPane().setBackground(java.awt.Color.WHITE); 
    dialogUpload.setVisible(true); 
}

    private void jrecordActionPerformed(java.awt.event.ActionEvent evt) {
        Object selectedItem = jComboBox_port.getSelectedItem();
        if (selectedItem == null) return;
        String portName = selectedItem.toString();

        if (jrecord.isSelected()) {
            // === START RECORDING ===
            try {
                // 1. Clear Queues & Data
                queueByte.clear();
                queueString.clear();
                leadsData.clear();
                processedData.clear();
                for (int i = 0; i < 12; i++) {
                    leadsData.add(new double[MAX_SAMPLE]);
                    processedData.add(new double[MAX_SAMPLE]);
                }
                hasRecordedData = false;
                isFileLoaded = false;
                leadAvailabilityKnown = false;
                writeIndex = 0;
                currentDataIndex = 0;
                xGraph = 0;

                int h = jPanel_ecgsignal.getHeight();
                int rows = layoutRows;
                int rowHeight = (rows > 0) ? h / rows : h;
                for (int i = 0; i < 12; i++) {
                    int rowIndex = (layoutCols == 1) ? i : i % rows;
                    lastY[i] = (rowIndex * rowHeight) + (rowHeight / 2);
                }

                // Bersihkan kanvas dan restart timer untuk sesi rekam baru
                jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
                jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
                resetBackground();
                if (ecgTimer != null && !ecgTimer.isRunning()) {
                    ecgTimer.start();
                }

                // 2. Init & Open Serial Comm (The Ear)
                // Uses jSerialComm polling thread — no addDataListener, no handshake needed.
                // Hardware (EG12000) auto-streams binary data the moment the port is opened.
                serialComm = new SerialCommBiner(portName, 115200, queueByte);
                boolean opened = serialComm.openPort(); // openPort() sets params & starts reader thread

                if (opened && serialComm.isOpen()) {
                    // 3. Wait 1 second for the hardware to stabilize its data stream
                    //    This runs on the EDT, so we use a SwingWorker to avoid UI freeze.
                    final SerialCommBiner commRef = serialComm;
                    new javax.swing.SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            Thread.sleep(1000); // Hardware stabilization delay
                            return null;
                        }
                        @Override
                        protected void done() {
                            // 4. Init & Start Parser (The Brain)
                            parserRunnable = new ThrdParsingECG(MainDashboard.this, queueByte, queueString);
                            parsingThread = new Thread(parserRunnable, "ECG-Parser");
                            parsingThread.start();

                            // === INITIALIZE DATA STREAM ===
                            try {
                                String targetDir = "./recordings/";
                                java.io.File folder = new java.io.File(targetDir);
                                if (!folder.exists()) {
                                    folder.mkdirs();
                                }
                                String patientID = jtxtID.getText().trim();
                                String patientName = txtname.getText().trim();
                                String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                                String fileName = "ECG_Stream_" + (patientID.isEmpty() ? "Unknown" : patientID) + "_" + timeStamp + ".csv";
                                java.io.File streamFile = new java.io.File(folder, fileName);
                                streamFileWriter = new java.io.BufferedWriter(new java.io.FileWriter(streamFile));
                                
                                // Write CSV Header matching CSVExporter format
                                streamFileWriter.write("ECG SIGNAL DATA EXPORT"); streamFileWriter.newLine();
                                streamFileWriter.write("Export Date," + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())); streamFileWriter.newLine();
                                streamFileWriter.write("Patient ID," + (patientID.isEmpty() ? "N/A" : patientID)); streamFileWriter.newLine();
                                streamFileWriter.write("Patient Name," + (patientName.isEmpty() ? "N/A" : patientName)); streamFileWriter.newLine();
                                streamFileWriter.write("Sampling Rate (Hz)," + currentFs); streamFileWriter.newLine();
                                streamFileWriter.newLine();
                                streamFileWriter.write("Time_ms,Lead_I,Lead_II,Lead_III,aVR,aVL,aVF,V1,V2,V3,V4,V5,V6");
                                streamFileWriter.newLine();
                                
                                System.out.println("[STREAM] Started streaming to: " + streamFile.getAbsolutePath());
                            } catch (java.io.IOException e) {
                                System.err.println("[STREAM] Error initializing stream file: " + e.getMessage());
                            }

                            // 5. Start Consumer Timer (reads queueString every 10ms)
                            if (consumerTimer != null) consumerTimer.stop();
                            consumerTimer = new javax.swing.Timer(10, e -> processParsedData());
                            consumerTimer.start();

                            // Pastikan ecgTimer jalan agar grafik terus digambar
                            if (ecgTimer != null && !ecgTimer.isRunning()) {
                                ecgTimer.start();
                            }

                            System.out.println("[RECORD] Connected & parser started: " + portName);
                            jrecord.setText("STOP");
                            btnAnalyze.setEnabled(false);
                            // JANGAN set isFileLoaded=true di sini — live mode pakai writeIndex bukan currentDataIndex
                        }
                    }.execute();
                } else {
                    jrecord.setSelected(false);
                    javax.swing.JOptionPane.showMessageDialog(MainDashboard.this,
                        "Failed to open port: " + portName + "\n" +
                        "Check that the port is correct and not in use by another app.",
                        "Port Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                jrecord.setSelected(false);
            }

        } else {
            // === STOP RECORDING ===
            // 1. Stop Serial
            try {
                if (serialComm != null) serialComm.closePort();
            } catch (Exception e) {}

            // 2. Stop Parser
            if (parserRunnable != null) parserRunnable.setExit();

            // 3. Stop Consumer Timer
            if (consumerTimer != null) consumerTimer.stop();

            // === CLOSE DATA STREAM ===
            if (streamFileWriter != null) {
                try {
                    streamFileWriter.flush();
                    streamFileWriter.close();
                    System.out.println("[STREAM] Stream file closed successfully.");
                } catch (java.io.IOException e) {
                    System.err.println("[STREAM] Error closing stream file: " + e.getMessage());
                } finally {
                    streamFileWriter = null;
                }
            }

            jrecord.setText("Record");
            System.out.println("[RECORD] Recording Stopped.");

            try {
                Thread.sleep(100); // Tunggu 100ms untuk data terakhir
            } catch (InterruptedException e) {}

            trimArrays();
            hasRecordedData = true;
            isFileLoaded = false;

            enforceButtonState();

            // Freeze sinyal di state terakhir — hentikan ecgTimer
            if (ecgTimer != null && ecgTimer.isRunning()) {
                ecgTimer.stop();
            }
        }
    }


    private void jComboBox_cbGainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox_cbGainActionPerformed
    // Ambil teks yang dipilih user
    String selected = jComboBox_cbGain.getSelectedItem().toString();
    
    // Tentukan pengali berdasarkan pilihan
    if (selected.contains("5 mm/mV")) {
        currentGainMultiplier = 0.5; // Perkecil setengah
    } else if (selected.contains("20 mm/mV")) {
        currentGainMultiplier = 2.0; // Perbesar dua kali
    } else {
        currentGainMultiplier = 1.0; // Normal (10 mm/mV)
    }
    
    // Reset layar agar grafik dan kalibrasi tidak menumpuk
    xGraph = 0;
    jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
    jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
    resetBackground();
    }//GEN-LAST:event_jComboBox_cbGainActionPerformed

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
    String selected = jComboBox1.getSelectedItem().toString();
        
        // Atur baris dan kolom berdasarkan pilihan
        if (selected.contains("2 x")) { 
            // Layout 2 x 6 (2 Kolom, 6 Baris per kolom)
            layoutCols = 2;
            layoutRows = 6;
        } else if (selected.contains("3 x")) {
            // Layout 3 x 4 (3 Kolom, 4 Baris per kolom)
            layoutCols = 3;
            layoutRows = 4;
        } else {
            // Default 1 x 12
            layoutCols = 1;
            layoutRows = 12;
        }
        
        // Reset grafik agar layout baru tergambar bersih dan tidak menumpuk
        xGraph = 0;
        jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
        jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
        resetBackground();
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void btnPatientInputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPatientInputActionPerformed
        dialogPatient.pack(); 
        dialogPatient.setLocationRelativeTo(this); 
        dialogPatient.getContentPane().setBackground(new java.awt.Color(242,242,242));
        dialogPatient.setVisible(true); 
    }//GEN-LAST:event_btnPatientInputActionPerformed

    private void btnChooseFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChooseFileActionPerformed
    // Pilih file
    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
    chooser.setDialogTitle("Pilih File Rekam Medis (.csv / .scp)");

    // Kunci format file hanya untuk CSV dan SCP
    javax.swing.filechooser.FileNameExtensionFilter filter = 
    new javax.swing.filechooser.FileNameExtensionFilter("ECG Data Files (*.csv, *.scp)", "csv", "scp");
    chooser.setFileFilter(filter);
    chooser.setAcceptAllFileFilterUsed(false); 

    int returnVal = chooser.showOpenDialog(this);

    if (returnVal == javax.swing.JFileChooser.APPROVE_OPTION) {
        java.io.File file = chooser.getSelectedFile();
        String filePath = file.getAbsolutePath();

        // Jika file scp
        if (filePath.toLowerCase().endsWith(".scp")) {
            System.out.println("File SCP dipilih: " + filePath);
            
            try {
                // Path ScpReader.exe (Jembatan untuk ekstraksi SCP ke CSV)
                java.io.File exeFile = new java.io.File("ScpReader" + java.io.File.separator + "bin" + java.io.File.separator + "Release" + java.io.File.separator + "net9.0" + java.io.File.separator + "ScpReader.exe");
                String exePath = exeFile.getAbsolutePath();
                
                if (!exeFile.exists()) {
                    throw new Exception("ScpReader.exe tidak ditemukan di:\n" + exePath);
                }

                // buat file CSV sementara di folder temporary bawaan Windows/OS
                java.io.File tempCsvOut = new java.io.File(System.getProperty("java.io.tmpdir"), "temp_extracted.csv");
                String tempCsvPath = tempCsvOut.getAbsolutePath();

                // C# MEMBONGKAR BRANKAS SCP
                System.out.println("Mengekstrak SCP...");
                ProcessBuilder pb = new ProcessBuilder(exePath, filePath, tempCsvPath);
                Process process = pb.start();

                // Dengar laporan dari C#
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                StringBuilder csharpOutput = new StringBuilder();
                String exeLine;
                boolean isSuccess = false;
                
                while ((exeLine = reader.readLine()) != null) {
                    csharpOutput.append(exeLine).append("\n");
                    if (exeLine.contains("SUCCESS")) {
                        isSuccess = true;
                    }
                }
                process.waitFor();

                if (!isSuccess) {
                    throw new Exception("C# Gagal Mengekstrak:\n" + csharpOutput.toString());
                }

                // 3. JIKA SUKSES, BACA FILE TEMP CSV HASIL BONGKARAN
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(tempCsvOut));
                String line;
                java.util.ArrayList<java.util.ArrayList<Double>> tempColumns = new java.util.ArrayList<>();

                String header = br.readLine();
                if (header != null) {
                    String[] cols = header.split(",");
                    // BEDA DENGAN CSV BIASA: Mulai dari i = 0 karena tidak ada Time_ms
                    for (int i = 0; i < cols.length; i++) { 
                        tempColumns.add(new java.util.ArrayList<>());
                    }
                }

                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    // BEDA DENGAN CSV BIASA: Mulai dari i = 0
                    for (int i = 0; i < values.length; i++) {
                        if (i < tempColumns.size()) {
                            try {
                                double val = Double.parseDouble(values[i]);
                                tempColumns.get(i).add(val);
                            } catch (NumberFormatException e) {
                                tempColumns.get(i).add(0.0);
                            }
                        }
                    }
                }
                br.close();

                if (tempCsvOut.exists()) {
                    tempCsvOut.delete();
                }
                leadsData.clear();
                for (java.util.ArrayList<Double> colData : tempColumns) {
                    double[] primitiveArr = new double[colData.size()];
                    for (int i = 0; i < colData.size(); i++) {
                        primitiveArr[i] = colData.get(i);
                    }
                    leadsData.add(primitiveArr);
                }
                isFileLoaded = true;
                currentDataIndex = 0; 
                xGraph = 0;           

                int h = jPanel_ecgsignal.getHeight();
                for (int i = 0; i < 12; i++) {
                    lastY[i] = h / 2; 
                }

                jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
                jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
                
                resetBackground(); 

                if (btnAnalyze != null) {
                    btnAnalyze.setToolTipText("Click to run Model Classification");
                }

                // Jalankan Engine Analisis Background
                recalculateFilters(); 
                updateWaveMarkers();  
                enforceButtonState(); 
                updateAutoHR();       

                System.out.println("Berhasil memuat dan mengekstrak file SCP!");

            } catch (Exception ex) {
                ex.printStackTrace();
                javax.swing.JOptionPane.showMessageDialog(this, 
                    "Error saat membaca file SCP:\n" + ex.getMessage(), 
                    "SCP Extraction Error", 
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }

        // Jika file csv
        else if (filePath.toLowerCase().endsWith(".csv")) {
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                String line;
                
                // Variabel sementara untuk menampung data per kolom
                java.util.ArrayList<java.util.ArrayList<Double>> tempColumns = new java.util.ArrayList<>();
                java.util.ArrayList<String> dataLines = new java.util.ArrayList<>();
                String header = null;
                boolean foundHeader = false;
                while ((line = br.readLine()) != null) {
                    if (!foundHeader) {
                        if (line.trim().toLowerCase().startsWith("time")) {
                            header = line;
                            foundHeader = true;
                        }
                    } else {
                        dataLines.add(line);
                    }
                }

                // Fallback jika tidak ditemukan header yang dimulai dengan "time" (CSV format lama)
                if (header == null) {
                    br.close();
                    br = new java.io.BufferedReader(new java.io.FileReader(file));
                    header = br.readLine();
                    dataLines.clear();
                    while ((line = br.readLine()) != null) {
                        dataLines.add(line);
                    }
                }
                br.close();

                int numLeadsExpected = 0;
                if (header != null) {
                    String[] cols = header.split(",");
                    numLeadsExpected = cols.length - 1;
                    for (int i = 1; i < cols.length; i++) {
                        tempColumns.add(new java.util.ArrayList<>());
                    }
                }

                for (String dataLine : dataLines) {
                    String[] values = dataLine.split(",");
                    
                    if (numLeadsExpected > 0 && values.length >= (numLeadsExpected + 1) * 2 && values.length % 2 == 0) {
                        String[] cleanValues = new String[values.length / 2];
                        for (int k = 0; k < cleanValues.length; k++) {
                            cleanValues[k] = values[2 * k].trim() + "." + values[2 * k + 1].trim();
                        }
                        values = cleanValues;
                    }
                    
                    for (int i = 1; i < values.length; i++) {
                        if ((i - 1) < tempColumns.size()) {
                            try {
                                double val = Double.parseDouble(values[i]);
                                tempColumns.get(i - 1).add(val);
                            } catch (NumberFormatException e) {
                                tempColumns.get(i - 1).add(0.0); // Default jika error
                            }
                        }
                    }
                }

                leadsData.clear();
                for (java.util.ArrayList<Double> colData : tempColumns) {
                    double[] primitiveArr = new double[colData.size()];
                    for (int i = 0; i < colData.size(); i++) {
                        primitiveArr[i] = colData.get(i);
                    }
                    leadsData.add(primitiveArr);
                }

                // === STATE MANAGEMENT & UI UPDATE ===
                isFileLoaded = true;
                currentDataIndex = 0; 
                xGraph = 0;           

                int h = jPanel_ecgsignal.getHeight();
                for (int i = 0; i < 12; i++) {
                    lastY[i] = h / 2; 
                }

                jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
                jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
                
                resetBackground(); 

                if (btnAnalyze != null) {
                    btnAnalyze.setToolTipText("Click to run Model Classification");
                }

                // Jalankan Engine Analisis Background
                recalculateFilters(); 
                updateWaveMarkers();  
                enforceButtonState(); 
                updateAutoHR();       

                System.out.println("Berhasil memuat file CSV: " + file.getName());

            } catch (Exception e) {
                System.out.println("Error reading CSV: " + e.getMessage());
                e.printStackTrace();
                javax.swing.JOptionPane.showMessageDialog(this, 
                    "Gagal membaca file CSV!\n" + e.getMessage(), 
                    "Error", 
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }
        dialogUpload.dispose();
    }
    }//GEN-LAST:event_btnChooseFileActionPerformed

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField1ActionPerformed

    private void txtnameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtnameActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtnameActionPerformed

    private void txtsexActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtsexActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtsexActionPerformed

    private void jtxtAge2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jtxtAge2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jtxtAge2ActionPerformed

    private void jtxtBirthdate2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jtxtBirthdate2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jtxtBirthdate2ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        PatientHistory historyFrame = new PatientHistory(this.patientService);
        historyFrame.setLocationRelativeTo(this);
        historyFrame.setVisible(true);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void btnEndSessionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEndSessionActionPerformed
        dialogEndSession.pack();
        dialogEndSession.setLocationRelativeTo(this);
        dialogEndSession.setVisible(true);
    }//GEN-LAST:event_btnEndSessionActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        dialogEndSession.setVisible(false);
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jTextField6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField6ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField6ActionPerformed

    private void executeEndSession() {
        // 1. Hentikan Serial & Parser jika sedang berjalan
        try {
            if (serialComm != null) {
                serialComm.closePort();
            }
        } catch (Exception e) {
            // abaikan
        }
        if (parserRunnable != null) {
            parserRunnable.setExit();
        }
        if (consumerTimer != null) {
            consumerTimer.stop();
        }

        // 2. Tutup File Stream Perekaman jika masih terbuka
        if (streamFileWriter != null) {
            try {
                streamFileWriter.flush();
                streamFileWriter.close();
            } catch (java.io.IOException e) {
                // abaikan
            } finally {
                streamFileWriter = null;
            }
        }

        // 3. Reset Data Perekaman & Buffer Grafik
        queueByte.clear();
        queueString.clear();
        
        if (leadsData != null) {
            leadsData.clear();
            processedData.clear();
            for (int i = 0; i < 12; i++) {
                leadsData.add(new double[MAX_SAMPLE]);
                processedData.add(new double[MAX_SAMPLE]);
            }
        }

        writeIndex = 0;
        xGraph = 0;
        isFileLoaded = false;
        hasRecordedData = false;
        leadAvailabilityKnown = false;

        // 4. Kosongkan Form Identitas Pasien
        jtxtID.setText("");
        txtname.setText("");
        txtsex.setText("");
        jtxtBirthdate2.setText("");
        jtxtAge2.setText("");
        jTextField5.setText("");
        jTextArea1.setText("");
        jTextField6.setText("");
        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");
        

        // 5. Bersihkan Label Hasil Klasifikasi & Kotak Saran Kesehatan
        jLabel28.setText("Prediction Result : -");
        jtxtNotes2.setText(""); // Saran Kesehatan
        jlblHR.setText("0"); // Heart Rate label

        // 6. Reset Header Title Dashboard
        lblTitlePatient.setText("Patient Monitoring: -  ID: -");

        // 7. Kembalikan Mode ke Monitoring
        setAppMode("Monitoring");
    }
   
    public void loadPatientData(String id, String name, String sex, String dob, String age, String weight, String height) {
        // Parameter di harus lengkap semuanya String.
        
        jtxtID.setText(id);
        txtname.setText(name); 
        
        if ("Male".equalsIgnoreCase(sex) || "Laki-Laki".equalsIgnoreCase(sex)) {
            txtsex.setText("Laki-Laki");
        } else if ("Female".equalsIgnoreCase(sex) || "Perempuan".equalsIgnoreCase(sex)) {
            txtsex.setText("Perempuan");
        } else {
            txtsex.setText(sex);
        }
        
        jtxtBirthdate2.setText(dob);
        jtxtAge2.setText(age);

        // Reset new demographic fields
        jTextField5.setText("");
        jTextArea1.setText("");
        jTextField6.setText("");
        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");

        if (lblTitlePatient != null) {
            lblTitlePatient.setText("Patient Monitoring: " + name + " (ID: " + id + ")");
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════════════════
    // PUBLIC SETTER: Allows external classes to update the Health Advice text area
    // PURPOSE: Data Sync - analyzepanel and other classes can push health advice to MainDashboard
    // USAGE: mainDashboard.updateHealthAdviceBox(adviceString);
    // ════════════════════════════════════════════════════════════════════════════════════════════
    /**
     * Updates the Health Advice text box on the main dashboard.
     * This method allows other classes (like analyzepanel) to send health advice
     * to be displayed in the patient information panel.
     * 
     * @param advice The health advice text to display
     */
    public void updateHealthAdviceBox(String advice) {
        if (advice == null || advice.trim().isEmpty()) {
            jtxtNotes2.setText("No health advice available.");
        } else {
            jtxtNotes2.setText(advice);
        }
        jtxtNotes2.setCaretPosition(0); // Scroll to top
    }
    
    public String getHealthAdviceText() {
        return jtxtNotes2 != null ? jtxtNotes2.getText() : "";
    }
    
    // --- METHOD 1: MENERIMA SETTING DARI PANEL SETTINGS ---
    public void setAppMode(String mode) {
        this.currentMode = mode;
        xGraph = 0; // Reset garis kembali ke paling kiri

        // Gambar grid
        jPanel_ecgsignal.putClientProperty("GridRendered_Monitoring", false);
        jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
        resetBackground();

        int h = jPanel_ecgsignal.getHeight();

        if ("Monitoring".equals(mode)) {
            // --- MODE MONITORING: TAMPILKAN 12 BARIS (RAW DATA) ---
            int rows = 12; // Tampilkan 12 lead
            int gap = h / rows;
            for (int i = 0; i < rows; i++) {
                lastY[i] = (i * gap) + (gap / 2);
            }

            // UI LOGIC FOR MONITORING MODE:
            btnAnalyze.setEnabled(false);
            btnAnalyze.setToolTipText("Switch to Classification Mode to enable analysis");
            jrecord.setVisible(true);        
            jrecord.setText("Record");       
            jComboBox1.setEnabled(true);     
            //jlblStatus.setText("MONITORING - 12 Leads");

        } else {
            // --- MODE CLASSIFICATION: tampilkan 1 lead (Lead II) ---
            // Snap baseline ke major grid terdekat (major grid = setiap 50px)
            int majorGrid = 50;
            lastY[0] = Math.round((float)(h / 2) / majorGrid) * majorGrid;

            // Aktifkan Analyze jika ada data (file ATAU rekaman)
            if (isFileLoaded || hasRecordedData) {
                btnAnalyze.setEnabled(true);
                btnAnalyze.setToolTipText("Click to run Model Classification");
            } else {
                btnAnalyze.setEnabled(false);
                btnAnalyze.setToolTipText("Upload CSV or record data first");
            }

            // Reset playback ke awal agar rekaman diputar ulang dari sample pertama
            currentDataIndex = 0;
            xGraph = 0;

            jrecord.setVisible(true);
            jComboBox1.setEnabled(false);

            // Pastikan ecgTimer jalan agar grafik Classification langsung bergerak
            if (ecgTimer != null && !ecgTimer.isRunning()) {
                ecgTimer.start();
            }
        }
    }

    // --- METHOD 2: MENGGAMBAR GRAFIK (VISUALISASI) ---
    private void drawLiveECG() {
        Graphics g = jPanel_ecgsignal.getGraphics();
        if (g == null) return;
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        int totalW = jPanel_ecgsignal.getWidth();
        int totalH = jPanel_ecgsignal.getHeight();
        
        boolean isClassification = !"Monitoring".equals(currentMode);
        int actualCols = isClassification ? 1 : layoutCols;
        int actualRows = isClassification ? 1 : layoutRows;
        
        int samplesPerFrame = (int)(currentFs / 20.0);
        if (samplesPerFrame < 1) samplesPerFrame = 1;
        
        int colWidth = totalW / actualCols;
        int rowHeight = totalH / actualRows;
        
        if (xGraph >= colWidth) {
            xGraph = 0; 
        }
        
        // Gambar Grid
        Color minorGridColor = new Color(255, 230, 235); // Pink sangat muda (solid)
        Color majorGridColor = new Color(255, 180, 200); // Pink sedang (solid)

        Boolean isGridRendered = (Boolean) jPanel_ecgsignal.getClientProperty("GridRendered_" + currentMode);
        if (isGridRendered == null || !isGridRendered) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, totalW, totalH);
            
            if (jon.isSelected()) {
                int gridStep = 10;
                g2.setStroke(new java.awt.BasicStroke(1.0f));
                
                for (int y = 0; y < totalH; y += gridStep) {
                    g2.setColor(((y / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                    g2.drawLine(0, y, totalW, y);
                }
                for (int x = 0; x < totalW; x += gridStep) {
                    g2.setColor(((x / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                    g2.drawLine(x, 0, x, totalH);
                }
            }
            jPanel_ecgsignal.putClientProperty("GridRendered_" + currentMode, true);
        }

        // =================================================================
        // OPTIMASI SWEEP BAR (Kotak Penghapus)
        // =================================================================
        int eraseWidth = isClassification ? (samplesPerFrame * currentSpeedStep + 10) : 10;
        for (int c = 0; c < actualCols; c++) {
            int colStartX = c * colWidth;
            int eraseX = colStartX + xGraph;
            
            g2.setColor(Color.WHITE);
            g2.fillRect(eraseX, 0, eraseWidth, totalH);
            
            if (jon.isSelected()) {
                int gridStep = 10; 
                g2.setStroke(new java.awt.BasicStroke(1.0f));
                
                for (int y = 0; y < totalH; y += gridStep) {
                    g2.setColor(((y / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                    g2.drawLine(eraseX, y, eraseX + eraseWidth, y);
                }
                
                int startGridX = (eraseX / gridStep) * gridStep;
                for (int x = startGridX; x < eraseX + eraseWidth; x += gridStep) {
                    if (x >= eraseX) {
                        g2.setColor(((x / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                        g2.drawLine(x, 0, x, totalH);
                    }
                }
            }
        }
        
        // isLiveMode: true saat port Serial aktif (Record ditekan) ATAU MQTT belum di-capture
        // Setelah isMqttCaptured=true, mode beralih ke playback (seperti setelah Record berhenti)
        boolean hasLiveData  = (writeIndex > 0);
        boolean isSerialLive = !isFileLoaded && jrecord.isSelected() && hasLiveData;
        boolean isMqttLive   = !isFileLoaded && isMqttActive && !isMqttCaptured && hasLiveData;
        boolean isLiveMode   = isSerialLive || isMqttLive;
        
        java.awt.Stroke solidStroke = new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        Color solidColor = Color.DARK_GRAY;

        if ("Monitoring".equals(currentMode)) {
            // --- Determine which leads are available ---
            // In live mode: before the first FC/FF status packet (once/sec), treat all leads
            // as available so the signal draws immediately. After first FC/FF, use real status.
            boolean[] effectiveLeadAvailable = new boolean[12];
            for (int i = 0; i < 12; i++) {
                if (leadAvailabilityKnown) {
                    effectiveLeadAvailable[i] = leadAvailable[i];
                } else {
                    // Sebelum paket FC/FF pertama, anggap semua lead aktif
                    effectiveLeadAvailable[i] = true;
                }
            }

            // --- Determine the sample index to read ---
            // File mode  → use currentDataIndex (loop playback)
            // Live mode  → gunakan sample TERBARU agar tampilan real-time
            int readIndex;
            if (isLiveMode) {
                readIndex = Math.max(0, writeIndex - 1);
            } else {
                readIndex = currentDataIndex;
            }

            for (int i = 0; i < 12; i++) {
                int colIndex = (actualCols == 1) ? 0 : i / actualRows;
                int rowIndex = (actualCols == 1) ? i : i % actualRows;
                int xOffset = colIndex * colWidth;
                int yBase = (rowIndex * rowHeight) + (rowHeight / 2);

                int signalY = 0;
                boolean shouldDraw = false;

                if (isFileLoaded || isLiveMode || hasRecordedData) {
                    if (effectiveLeadAvailable[i]) {
                        // --- Lead has direct data: read from array ---
                        if (i < processedData.size() && processedData.get(i) != null
                                && readIndex < processedData.get(i).length) {
                            signalY = (int) (processedData.get(i)[readIndex] * -25.0 * currentGainMultiplier);
                            shouldDraw = true;
                        }
                    } else {
                        // --- Lead is derived (FC/FF said it's not directly streamed) ---
                        // Compute augmented / chest leads from I and II
                        Double valI = null, valII = null, valIII = null;
                        if (processedData.size() > 0 && processedData.get(0) != null
                                && processedData.get(0).length > 0)
                            valI  = processedData.get(0)[readIndex % processedData.get(0).length];
                        if (processedData.size() > 1 && processedData.get(1) != null
                                && processedData.get(1).length > 0)
                            valII = processedData.get(1)[readIndex % processedData.get(1).length];
                        if (processedData.size() > 2 && processedData.get(2) != null
                                && processedData.get(2).length > 0)
                            valIII = processedData.get(2)[readIndex % processedData.get(2).length];
                        if (valIII == null && valI != null && valII != null) valIII = valII - valI;

                        if (valI != null && valII != null) {
                            if (i == 0) {
                                signalY = (int) (valI  * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } else if (i == 1) {
                                signalY = (int) (valII * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } else if (i == 2 && valIII != null) {
                                signalY = (int) (valIII * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } 
                            else if (i == 3) {
                                signalY = (int) (-(valI + valII) / 2.0 * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } else if (i == 4 && valIII != null) {
                                signalY = (int) ((valI - valIII) / 2.0 * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } else if (i == 5 && valIII != null) {
                                signalY = (int) ((valII + valIII) / 2.0 * -25.0 * currentGainMultiplier); shouldDraw = true;
                            } else if (i >= 6 && i <= 11) {
                                double wct = (valI + valII) / 3.0;
                                double scale = 0.7 + 0.05 * (i - 6);
                                signalY = (int) ((valII - wct) * -25.0 * currentGainMultiplier * scale); shouldDraw = true;
                            }
                        }
                        if (!shouldDraw) { signalY = 0; shouldDraw = true; }
                    }
                }
                
                if (!shouldDraw) signalY = 0;
                int currentY = yBase + signalY;
                
                double speedVal = 25.0;
                if (getSpeedSetting().contains("12.5")) speedVal = 12.5;
                else if (getSpeedSetting().contains("50")) speedVal = 50.0;
                else if (getSpeedSetting().contains("100")) speedVal = 100.0;
                
                double pixelsPerSecond = ((double) currentSpeedStep * currentFs) / samplesPerFrame;
                int width_px = (int) (0.2 * pixelsPerSecond);
                int height_px = (int) (2.5 * currentGainMultiplier * 10);
                int flatMargin = (int) (0.08 * pixelsPerSecond);
                if (flatMargin < 5) flatMargin = 5;
                int calWidth = flatMargin + width_px + flatMargin;

                // Draw calibration pulse statically
                g2.setStroke(solidStroke);
                g2.setColor(solidColor);
                g2.drawLine(xOffset, yBase, xOffset + flatMargin, yBase);
                g2.drawLine(xOffset + flatMargin, yBase, xOffset + flatMargin, yBase - height_px);
                g2.drawLine(xOffset + flatMargin, yBase - height_px, xOffset + flatMargin + width_px, yBase - height_px);
                g2.drawLine(xOffset + flatMargin + width_px, yBase - height_px, xOffset + flatMargin + width_px, yBase);
                g2.drawLine(xOffset + flatMargin + width_px, yBase, xOffset + calWidth, yBase);

                int drawX_Now = xGraph + xOffset;
                if (xGraph >= calWidth) {
                    int drawX_Prev = (xGraph - currentSpeedStep) + xOffset;
                    if (drawX_Prev < xOffset + calWidth) {
                        drawX_Prev = xOffset + calWidth;
                    }
                    g2.setStroke(solidStroke);
                    g2.setColor(solidColor);
                    g2.drawLine(drawX_Prev, lastY[i], drawX_Now, currentY);
                    lastY[i] = currentY;
                } else {
                    lastY[i] = yBase;
                }

                g2.setStroke(new java.awt.BasicStroke(1.0f));
                
                int targetLead = (leadsData.size() > 1) ? 1 : 0;
                if (i == targetLead && isFileLoaded && xGraph >= calWidth) {
                    if (showRWave && cachedRIndices.contains(currentDataIndex)) {
                        g2.setColor(Color.WHITE); g2.fillOval(drawX_Now - 4, currentY - 4, 8, 8); g2.drawString("R", drawX_Now, currentY - 10);
                    }
                    else if (showPWave && cachedPIndices.contains(currentDataIndex)) {
                        g2.setColor(Color.YELLOW); g2.fillOval(drawX_Now - 3, currentY - 3, 6, 6); g2.drawString("P", drawX_Now, currentY - 15);
                    }
                    else if (showTWave && cachedTIndices.contains(currentDataIndex)) {
                        g2.setColor(Color.CYAN); g2.fillOval(drawX_Now - 3, currentY - 3, 6, 6); g2.drawString("T", drawX_Now, currentY - 15);
                    }
                }
                
                String label = getLeadName(i);
                boolean showAsterisk = leadAvailabilityKnown && !effectiveLeadAvailable[i] && !isLiveMode;
                g2.setColor(showAsterisk ? Color.RED : Color.BLACK);
                g2.drawString(label + (showAsterisk ? "*" : ""), xOffset + 5, yBase - 28);
            }
        } else {
            // --- MODE CLASSIFICATION (1 lead, Lead II) ---
            double effectiveGain = currentGainMultiplier;
            if (effectiveGain == 2.0) {
                effectiveGain = 1.0;
            }
            
            int majorGrid = 50;
            int yBase = Math.round((float)(totalH / 2) / majorGrid) * majorGrid + majorGrid;

            double speedVal = 25.0;
            if (getSpeedSetting().contains("12.5")) speedVal = 12.5;
            else if (getSpeedSetting().contains("50")) speedVal = 50.0;
            else if (getSpeedSetting().contains("100")) speedVal = 100.0;
            
            double pixelsPerSecond = ((double) currentSpeedStep * currentFs) / samplesPerFrame;
            int width_px = (int) (0.2 * pixelsPerSecond);
            int height_px = (int) (10 * effectiveGain * 10);
            int flatMargin = (int) (0.08 * pixelsPerSecond);
            if (flatMargin < 5) flatMargin = 5;
            int calWidth = flatMargin + width_px + flatMargin;

            // Draw calibration pulse statically
            g2.setStroke(solidStroke);
            g2.setColor(solidColor);
            g2.drawLine(0, yBase, flatMargin, yBase);
            g2.drawLine(flatMargin, yBase, flatMargin, yBase - height_px);
            g2.drawLine(flatMargin, yBase - height_px, flatMargin + width_px, yBase - height_px);
            g2.drawLine(flatMargin + width_px, yBase - height_px, flatMargin + width_px, yBase);
            g2.drawLine(flatMargin + width_px, yBase, calWidth, yBase);

            boolean hasData = isFileLoaded || isLiveMode || hasRecordedData;
            
            if (xGraph >= calWidth) {
                g2.setStroke(solidStroke);
                g2.setColor(solidColor);
                
                double prevX = xGraph;
                double prevY = lastY[0];
                
                // Menggambar seluruh titik sampel secara berurutan dengan presisi sub-piksel (Line2D.Double)
                // Dan dengan rasio lebar piksel (sampleWidth = currentSpeedStep) agar sinyal melebar dan peak-peak EKG terlihat jelas oleh dokter
                double sampleWidth = currentSpeedStep;
                for (int j = 0; j < samplesPerFrame; j++) {
                    double px = xGraph + (j + 1) * sampleWidth;
                    
                    double sampleY = 0;
                    if (hasData && !processedData.isEmpty()) {
                        int targetIndex = (processedData.size() > 1) ? 1 : 0;
                        if (targetIndex < processedData.size()) {
                            double[] data = processedData.get(targetIndex);
                            if (data != null && data.length > 0) {
                                int idx;
                                if (isLiveMode) {
                                    idx = (writeIndex - samplesPerFrame + j + data.length) % data.length;
                                } else {
                                    idx = (currentDataIndex + j) % data.length;
                                }
                                sampleY = data[idx] * -100.0 * effectiveGain;
                            }
                        }
                    }
                    double py = yBase + sampleY;
                    g2.draw(new java.awt.geom.Line2D.Double(prevX, prevY, px, py));
                    prevX = px;
                    prevY = py;
                }
                lastY[0] = (int) prevY;

                // --- ANOTASI PUNCAK GELOMBANG (P, Q, R, S, T) ---
                if (classificationAnalyzer != null && (isFileLoaded || hasRecordedData)) {
                    int targetIndex = (processedData.size() > 1) ? 1 : 0;
                    double[] drawData = (targetIndex < processedData.size()) ? processedData.get(targetIndex) : null;
                    if (drawData != null) {
                        WaveAnnotationPainter.paint(
                            g2,
                            classificationAnalyzer,
                            drawData,
                            currentDataIndex,
                            samplesPerFrame,
                            xGraph,
                            totalW,
                            yBase,
                            100.0 * effectiveGain,
                            sampleWidth
                        );
                    }
                }
            } else {
                lastY[0] = yBase;
            }

            // --- STYLE & BACKGROUND UNTUK TITLE BADGE ---
            String titleText = "Lead II (Signal Classification View)";
            g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
            java.awt.FontMetrics fmTitle = g2.getFontMetrics();
            int titleBadgeW = fmTitle.stringWidth(titleText) + 20;
            g2.setColor(new java.awt.Color(0,0,0)); // balck
            g2.drawString(titleText, 20, 28);

            // Note anotasi
            int legendW = 460;
            int legendH = 26;
            int legendX = 10;
            int legendY = totalH - legendH - 10; // 10px margin dari bawah
            
            // Background Legend Card
            g2.setColor(new java.awt.Color(255, 255, 255, 210));
            g2.drawRoundRect(legendX, legendY, legendW, legendH, 6, 6);
            
            // Text Header
            g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
            g2.setColor(java.awt.Color.DARK_GRAY);
            g2.drawString("Note:", legendX + 10, legendY + 17);
            
            // Definisikan item legenda (Label & Warna)
            Object[][] legendItems = {
                {"P Wave", new java.awt.Color(79, 179, 135)},   // Mint Green
                {"Q Wave", new java.awt.Color(255, 126, 103)},  // Coral Red
                {"R Peak", new java.awt.Color(228, 75, 141)},  // Signature Pink
                {"S Wave", new java.awt.Color(248, 164, 136)},  // Soft Peach
                {"T Wave", new java.awt.Color(63, 114, 175)}   // Dusty Blue
            };
            
            g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
            int currentX = legendX + 60;
            for (Object[] item : legendItems) {
                String labelStr = (String) item[0];
                java.awt.Color dotColor = (java.awt.Color) item[1];
                
                g2.setColor(java.awt.Color.WHITE);
                g2.fillOval(currentX - 1, legendY + 8, 10, 10);
                g2.setColor(dotColor);
                g2.fillOval(currentX, legendY + 9, 8, 8);
                g2.setColor(java.awt.Color.DARK_GRAY);
                g2.drawString(labelStr, currentX + 14, legendY + 17);
                
                currentX += 80;
            }
        }
        
        if (!leadsData.isEmpty() && leadsData.get(0) != null) {
                if (isFileLoaded) {
                // Loop saat file playback
                int fileLen = leadsData.get(0).length;
                if (fileLen > 0) {
                    currentDataIndex = (currentDataIndex + samplesPerFrame) % fileLen;
                }
            } else if (hasRecordedData && !isLiveMode) {
                // Playback rekaman live yang sudah di-stop
                int recLen = writeIndex; 
                if (recLen > 0) {
                    currentDataIndex = (currentDataIndex + samplesPerFrame) % recLen;
                }
            }
            // isLiveMode: readIndex pakai writeIndex-1 langsung — tidak perlu update di sini.
        }
        
        if (isClassification) {
            xGraph += samplesPerFrame * currentSpeedStep;
        } else {
            xGraph += currentSpeedStep;
        }
        refreshCounter++;
        
        if (refreshCounter >= 20) {
            updateAutoHR();
            refreshCounter = 0;
        }
    }
    
// --- WINNING SOLUTION: OTOMATISASI HR ---
private void updateAutoHR() {
    // 1. Cek Ketersediaan Data
    if (leadsData == null || leadsData.isEmpty()) return;

    // Default Lead II
    int targetIndex = 1; 
    if (leadsData.size() == 1) targetIndex = 0;
    
    double[] sourceSignal = leadsData.get(targetIndex); // Ambil raw data
    double[] signalToAnalyze;
    
    // 2. Logika Pengambilan Data (File vs Live)
    if (isFileLoaded) {
        // A. MODE FILE: Gunakan seluruh data yang ada
        signalToAnalyze = sourceSignal.clone();
        if (isNotchOn && notchFreq > 0) {
            signalToAnalyze = ECGFilters.applyNotchFilter(signalToAnalyze, currentFs, notchFreq);
        }
        
    } else if (jrecord.isSelected() || isMqttActive) {
        // B. MODE LIVE (Serial Record ATAU MQTT): Gunakan "Sliding Window" (10 Detik Terakhir)
        // Agar ringan dan real-time.
        
        int windowSeconds = 10;
        int windowSamples = (int) (windowSeconds * currentFs);
        
        // Pastikan data minimal 3 detik agar bisa deteksi puncak
        if (writeIndex < (3 * currentFs)) return; 
        
        // Tentukan titik potong (Start - End)
        int endIndex = writeIndex;
        int startIndex = Math.max(0, endIndex - windowSamples);
        int length = endIndex - startIndex;
        
        signalToAnalyze = new double[length];
        System.arraycopy(sourceSignal, startIndex, signalToAnalyze, 0, length);
        
        // Filter Notch Quick (Agar tidak berat di CPU saat live)
        if (isNotchOn && notchFreq > 0) {
            signalToAnalyze = ECGFilters.applyNotchFilter(signalToAnalyze, currentFs, notchFreq);
        }
        
    } else {
        // Tidak ada file dan tidak sedang merekam
        return; 
    }

    // 3. Hitung HR Menggunakan Analyzer
    try {
        ECGAnalyzer liveAnalyzer = new ECGAnalyzer(signalToAnalyze, currentFs, qtcFormula, isPanTompkinsOn);
        double bpm = liveAnalyzer.getHRAvg(); // Dapatkan BPM rata-rata dari window tsb
        
        // 4. Update Tampilan
        if (bpm > 0) {
            jlblHR.setText(String.format("%.0f", bpm));
            
            // Indikator Warna
            if (bpm < 60) jlblHR.setForeground(Color.BLUE);
            else if (bpm > 100) jlblHR.setForeground(Color.RED);
            else jlblHR.setForeground(new Color(0, 153, 0));
        }
    } catch (Exception e) {
        System.out.println("Auto HR Error: " + e.getMessage());
    }
}

// --- METHOD: BACKGROUND ANALYZER FOR VISUALIZATION ---
// Dipanggil saat: 1. File Uploaded, 2. Record Stop, 3. Filter Berubah
private void updateWaveMarkers() {
    // Safety Check
    if (leadsData == null || leadsData.isEmpty()) return;
    
    // Pilih Lead II (Index 1) atau Lead I (Index 0) sebagai referensi marker
    int targetIndex = (leadsData.size() > 1) ? 1 : 0;
    
    // Ambil data yang sudah ter-filter (processedData)
    if (processedData.isEmpty() || processedData.size() <= targetIndex) return;
    double[] signal = processedData.get(targetIndex);

    // Jalankan Analyzer 
    // Gunakan Fs dan Formula yang sedang aktif
    ECGAnalyzer visualAnalyzer = new ECGAnalyzer(signal, currentFs, qtcFormula, isPanTompkinsOn);
    
    // Simpan hasilnya ke memori dashboard
    cachedPIndices = visualAnalyzer.getPIndices();
    cachedTIndices = visualAnalyzer.getTIndices();
    cachedRIndices = visualAnalyzer.getRIndices();
    
    System.out.println("Visual Markers Updated -> P: " + cachedPIndices.size() + 
                       ", T: " + cachedTIndices.size() + ", R: " + cachedRIndices.size());
}

// --- METHOD HELPER: RESET LAYAR & GAMBAR GRID ---
    private void resetBackground() {
        Graphics g = jPanel_ecgsignal.getGraphics();
        if (g == null) return;
        
        int w = jPanel_ecgsignal.getWidth();
        int h = jPanel_ecgsignal.getHeight();

        g.setColor(Color.WHITE); 
        g.fillRect(0, 0, w, h);

        // Jika tombol Grid ON, gambar grid
        if (jon.isSelected()) {
            int gridStep = 10; // JARAK KOTAK KECIL (Minor Grid)
            Color minorGridColor = new Color(255, 192, 203, 100); // Pink Muda (Lebih transparan)
            Color majorGridColor = new Color(255, 105, 180, 180); // Pink Tua

            if (g instanceof Graphics2D) {
                ((Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            }

            // Gambar Garis Horizontal
            for (int y = 0; y < h; y += gridStep) {
                g.setColor(((y / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                g.drawLine(0, y, w, y);
            }

            // Gambar Garis Vertikal
            for (int x = 0; x < w; x += gridStep) {
                g.setColor(((x / gridStep) % 5 == 0) ? majorGridColor : minorGridColor);
                g.drawLine(x, 0, x, h);
            }
            
            // Gambar Garis Batas antar Lead
            // if ("Monitoring".equals(currentMode)) {
            //     g.setColor(Color.BLACK); 
            //     int colWidth = w / layoutCols; 
            //     for (int c = 1; c < layoutCols; c++) {
            //         g.drawLine(c * colWidth, 0, c * colWidth, h);
            //     }
            // }
        }
    }


  private String getLeadName(int i) {
    if (i == 0) return "I";
    if (i == 1) return "II";
    if (i == 2) return "III";
    if (i == 3) return "aVR";
    if (i == 4) return "aVL";
    if (i == 5) return "aVF";
    if (i == 6) return "V1";
    if (i == 7) return "V2";
    if (i == 8) return "V3";
    if (i == 9) return "V4";
    if (i == 10) return "V5";
    if (i == 11) return "V6";
    return "Unknown";
}

// LOGIKA FILTERING (Method recalculateFilters)
public void recalculateFilters() {
    // Cek Data Kosong
    if (leadsData == null || leadsData.isEmpty()) return;

    // Bersihkan penampung data
    processedData.clear();

    double fs = this.currentFs; 

    // f single-lead CSV (Lead II data), insert empty placeholder at index 0 (Lead I)
    //    so the actual data goes to index 1 (Lead II position on display)
    if (leadsData.size() == 1) {
        processedData.add(new double[0]); // Empty placeholder for Lead I
    }

    // 4. Loop Proses Filter
    for (double[] rawSignal : leadsData) {
        double[] signalToProcess = rawSignal.clone();
        // 1. Notch Filter
        // Hanya jalan jika Toggle ON DAN Frekuensi bukan 0/Invalid
        if (isNotchOn && notchFreq > 0) {
            signalToProcess = ECGFilters.applyNotchFilter(signalToProcess, fs, notchFreq);
        }

        // 2. Filter Utama (Bandpass / Low / High / None)
        if ("Low-pass".equals(filterType)) {
            signalToProcess = ECGFilters.applyMovingAverage(signalToProcess, 5);
        } else if ("High-pass".equals(filterType)) {
            signalToProcess = ECGFilters.applyHighPass(signalToProcess, fs);
        } else if ("Bandpass".equals(filterType) || "Band-pass".equals(filterType)) {
            signalToProcess = ECGFilters.applyBandpass(signalToProcess, fs);
        } else if ("Low-pass Butterworth".equals(filterType)) {
            signalToProcess = ECGFilters.applyButterworthLowPass(signalToProcess, fs, 40.0);
        } else if ("High-pass Butterworth".equals(filterType)) {
            signalToProcess = ECGFilters.applyButterworthHighPass(signalToProcess, fs, 0.5);
        } else if ("Bandpass Butterworth".equals(filterType)) {
            signalToProcess = ECGFilters.applyButterworthBandpass(signalToProcess, fs, 0.5, 40.0);
        } else {
            // JIKA PILIHANNYA "-" (STRIPE), MAKA TIDAK ADA FILTER (RAW)
            // Do Nothing (Signal tetap original/raw)
        }

        // 3. Filter EMG
        if (isEmgOn) {
            signalToProcess = ECGFilters.applyEMGFilter(signalToProcess, 7);
        }

        // Simpan Hasil
        processedData.add(signalToProcess);
    }

    System.out.println("Filters Recalculated! Fs: " + fs + ", Mode: " + filterType + ", Leads: " + processedData.size());
    updateWaveMarkers();

    rebuildClassificationAnnotations();
}

private void rebuildClassificationAnnotations() {
    int targetIdx = (processedData.size() > 1) ? 1 : 0;
    if (targetIdx >= processedData.size()) {
        classificationAnalyzer = null;
        return;
    }
    double[] leadData = processedData.get(targetIdx);
    if (leadData == null || leadData.length == 0) {
        classificationAnalyzer = null;
        return;
    }
    classificationAnalyzer = new ECGAnalyzer(leadData, currentFs, "-", isPanTompkinsOn);
}

// ============================================================
// ========= 12-LEAD DATA GENERATION FOR EXPORT ===============
// ============================================================

/**
 * Generates complete 12-lead ECG data from available leads (I, II, III).
 * Computes derived leads: aVR, aVL, aVF, V1-V6 using standard ECG formulas.
 * Used for CSV export to ensure all 12 leads have valid data.
 * 
 * @param sourceData - Source data (leadsData or processedData)
 * @return List of 12 double arrays containing all ECG leads
 */
public java.util.List<double[]> generate12LeadData(java.util.List<double[]> sourceData) {
    java.util.List<double[]> fullData = new java.util.ArrayList<>();
    
    if (sourceData == null || sourceData.isEmpty()) {
        return fullData;
    }
    
    if (sourceData.size() >= 12) {
        for (int i = 0; i < 12; i++) {
            if (i < sourceData.size() && sourceData.get(i) != null) {
                fullData.add(sourceData.get(i).clone());
            } else {
                fullData.add(new double[0]);
            }
        }
        return fullData;
    }
    
    // Get sample count from first available lead
    int numSamples = 0;
    for (double[] lead : sourceData) {
        if (lead != null && lead.length > 0) {
            numSamples = lead.length;
            break;
        }
    }
    
    if (numSamples == 0) return fullData;
    
    // Extract available leads (handle empty placeholder at index 0 for single-lead CSV)
    double[] leadI = null;
    double[] leadII = null;
    double[] leadIII = null;
    
    // Check each index for valid data
    if (sourceData.size() > 0 && sourceData.get(0) != null && sourceData.get(0).length > 0) {
        leadI = sourceData.get(0);
    }
    if (sourceData.size() > 1 && sourceData.get(1) != null && sourceData.get(1).length > 0) {
        leadII = sourceData.get(1);
    }
    if (sourceData.size() > 2 && sourceData.get(2) != null && sourceData.get(2).length > 0) {
        leadIII = sourceData.get(2);
    }
    
    // If only Lead II available (single-column CSV), use it as reference
    if (leadI == null && leadII != null) {
        numSamples = leadII.length;
    }
    
    // Compute Lead III if not available: III = II - I
    if (leadIII == null && leadI != null && leadII != null) {
        leadIII = new double[numSamples];
        for (int i = 0; i < numSamples; i++) {
            leadIII[i] = leadII[i] - leadI[i];
        }
    }
    
    // Initialize all 12 leads
    double[] aVR = new double[numSamples];
    double[] aVL = new double[numSamples];
    double[] aVF = new double[numSamples];
    double[] v1 = new double[numSamples];
    double[] v2 = new double[numSamples];
    double[] v3 = new double[numSamples];
    double[] v4 = new double[numSamples];
    double[] v5 = new double[numSamples];
    double[] v6 = new double[numSamples];
    
    // Compute derived leads for each sample
    for (int i = 0; i < numSamples; i++) {
        double valI = (leadI != null) ? leadI[i] : 0.0;
        double valII = (leadII != null) ? leadII[i] : 0.0;
        double valIII = (leadIII != null) ? leadIII[i] : 0.0;
        
        // Augmented limb leads (standard ECG formulas)
        if (leadI != null && leadII != null) {
            aVR[i] = -(valI + valII) / 2.0;           // aVR = -(I + II)/2
            aVL[i] = (valI - valIII) / 2.0;           // aVL = (I - III)/2
            aVF[i] = (valII + valIII) / 2.0;          // aVF = (II + III)/2
            
            // Precordial leads (V1-V6) - approximation using Wilson Central Terminal
            double wct = (valI + valII) / 3.0;
            double chestProxy = valII;
            
            v1[i] = (chestProxy - wct) * 0.70;
            v2[i] = (chestProxy - wct) * 0.75;
            v3[i] = (chestProxy - wct) * 0.80;
            v4[i] = (chestProxy - wct) * 0.85;
            v5[i] = (chestProxy - wct) * 0.90;
            v6[i] = (chestProxy - wct) * 0.95;
        } else if (leadII != null) {
            // Only Lead II available - generate approximations
            aVR[i] = -valII / 2.0;
            aVL[i] = valII / 4.0;
            aVF[i] = valII * 0.75;
            
            v1[i] = valII * 0.50;
            v2[i] = valII * 0.55;
            v3[i] = valII * 0.60;
            v4[i] = valII * 0.65;
            v5[i] = valII * 0.70;
            v6[i] = valII * 0.75;
        }
    }
    
    // Build the 12-lead list in standard order
    fullData.add(leadI != null ? leadI : new double[numSamples]);   // 0: Lead I
    fullData.add(leadII != null ? leadII : new double[numSamples]); // 1: Lead II
    fullData.add(leadIII != null ? leadIII : new double[numSamples]); // 2: Lead III
    fullData.add(aVR);  // 3: aVR
    fullData.add(aVL);  // 4: aVL
    fullData.add(aVF);  // 5: aVF
    fullData.add(v1);   // 6: V1
    fullData.add(v2);   // 7: V2
    fullData.add(v3);   // 8: V3
    fullData.add(v4);   // 9: V4
    fullData.add(v5);   // 10: V5
    fullData.add(v6);   // 11: V6
    
    return fullData;
}

// ============================================================
// ========= PYTHON INTEGRATION METHODS (NEW SECTION) =========
// ============================================================

/**
 * Analyzes ECG data using Python backend and displays results.
 * This method extracts 216-sample windows, sends them to the Python server,
 * and displays prediction results.
 */
public void analyzewithPython() {
    // 1. Validasi data
    if ((!isFileLoaded && !hasRecordedData) || leadsData == null || leadsData.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(this,
            "No ECG data available. Upload a CSV file or record data first.");
        return;
    }

    // 2. Ambil Lead II (index 1), fallback Lead I (index 0)
    int targetIndex = (leadsData.size() > 1) ? 1 : 0;
    double[] sourceSignal = leadsData.get(targetIndex);

    // 3. Terapkan filter sesuai Settings (konsisten dengan tampilan grafik)
    double[] filteredSignal = sourceSignal.clone();
    if (isNotchOn && notchFreq > 0)
        filteredSignal = ECGFilters.applyNotchFilter(filteredSignal, currentFs, notchFreq);
    if ("Low-pass".equals(filterType))
        filteredSignal = ECGFilters.applyMovingAverage(filteredSignal, 5);
    else if ("High-pass".equals(filterType))
        filteredSignal = ECGFilters.applyHighPass(filteredSignal, currentFs);
    else if ("Bandpass".equals(filterType) || "Band-pass".equals(filterType))
        filteredSignal = ECGFilters.applyBandpass(filteredSignal, currentFs);
    else if ("Low-pass Butterworth".equals(filterType))
        filteredSignal = ECGFilters.applyButterworthLowPass(filteredSignal, currentFs, 40.0);
    else if ("High-pass Butterworth".equals(filterType))
        filteredSignal = ECGFilters.applyButterworthHighPass(filteredSignal, currentFs, 0.5);
    else if ("Bandpass Butterworth".equals(filterType))
        filteredSignal = ECGFilters.applyButterworthBandpass(filteredSignal, currentFs, 0.5, 40.0);

    if (isEmgOn) {
        filteredSignal = ECGFilters.applyEMGFilter(filteredSignal, 7);
    }

    // PILIH 1 BEAT TERBAIK (DARI AREA TENGAH SINYAL)
    final int WINDOW_SIZE  = 216;
    final int HALF_WIN     = WINDOW_SIZE / 2;
    final int SKIP_SAMPLES = (int)(2.0 * currentFs);

    // Potong bagian stabilisasi awal
    double[] stableSignal;
    if (filteredSignal.length > SKIP_SAMPLES + WINDOW_SIZE) {
        stableSignal = new double[filteredSignal.length - SKIP_SAMPLES];
        System.arraycopy(filteredSignal, SKIP_SAMPLES, stableSignal, 0, stableSignal.length);
    } else {
        stableSignal = filteredSignal; // terlalu pendek, skip diabaikan
        System.out.println("[WARN] Sinyal terlalu pendek untuk skip 2s. Gunakan full signal.");
    }

    // Deteksi R-peaks & RR interval
    ECGAnalyzer beatAnalyzer = new ECGAnalyzer(stableSignal, currentFs, qtcFormula, isPanTompkinsOn);
    java.util.List<Integer> rPeaks = beatAnalyzer.getRIndices();
    double[] rrFull = ECGPythonClient.calculateRRIntervals(stableSignal, currentFs);

    // Pilih 1 R-peak yang paling dekat ke tengah sinyal (area paling stabil)
    int midPoint   = stableSignal.length / 2;
    double[] bestWindow = null;
    double[] bestRR     = rrFull;

    if (!rPeaks.isEmpty()) {
        int bestRi   = 0;
        int bestDist = Math.abs(rPeaks.get(0) - midPoint);
        for (int ri = 1; ri < rPeaks.size(); ri++) {
            int dist = Math.abs(rPeaks.get(ri) - midPoint);
            if (dist < bestDist) { bestDist = dist; bestRi = ri; }
        }

        int rIdx  = rPeaks.get(bestRi);
        int start = rIdx - HALF_WIN;
        int end   = rIdx + HALF_WIN;

        if (start >= 0 && end <= stableSignal.length) {
            bestWindow = new double[WINDOW_SIZE];
            System.arraycopy(stableSignal, start, bestWindow, 0, WINDOW_SIZE);

            double rrPrev = (bestRi > 0)
                ? (rPeaks.get(bestRi) - rPeaks.get(bestRi - 1)) / currentFs
                : rrFull[0];
            double rrNext = (bestRi < rPeaks.size() - 1)
                ? (rPeaks.get(bestRi + 1) - rPeaks.get(bestRi)) / currentFs
                : rrFull[rrFull.length > 1 ? 1 : 0];
            bestRR = new double[]{rrPrev, rrNext};
            System.out.printf("[ANALYZE] R-peak ke-%d (idx=%d) dipilih dari %d peak%n",
                bestRi + 1, rIdx, rPeaks.size());
        }
    }

    // Fallback: tidak ada R-peak valid → slice 216 dari tengah mentah
    if (bestWindow == null) {
        System.out.println("[WARN] Tidak ada R-peak valid. Fallback ke slice tengah.");
        int center = Math.max(0, midPoint - HALF_WIN);
        bestWindow = ECGPythonClient.extractSignalWindow(stableSignal, center);
        if (bestWindow == null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Sinyal terlalu pendek! Butuh minimal " + WINDOW_SIZE + " sampel.");
            return;
        }
    }

    final double[] finalWindow = bestWindow;
    final double[] finalRR     = bestRR;

    // 5. Kirim 1 request ke Flask server di background thread
    Thread pythonThread = new Thread(() -> {
        try {
            if (!ECGPythonClient.testConnection()) {
                javax.swing.SwingUtilities.invokeLater(() ->
                    javax.swing.JOptionPane.showMessageDialog(MainDashboard.this,
                        "Python server tidak berjalan.\nJalankan: python modelekg.py",
                        "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE));
                return;
            }

            ECGPythonClient.PredictionResult result =
                ECGPythonClient.predictECG(finalWindow, finalRR[0], finalRR[1]);

            System.out.printf("[RESULT] %s (conf=%.3f)%n", result.prediction, result.confidence);

            javax.swing.SwingUtilities.invokeLater(() -> displayPredictionResult(result));

        } catch (Exception e) {
            javax.swing.SwingUtilities.invokeLater(() ->
                javax.swing.JOptionPane.showMessageDialog(MainDashboard.this,
                    "Python prediction error:\n" + e.getMessage(),
                    "Prediction Failed", javax.swing.JOptionPane.ERROR_MESSAGE));
        }
    });
    pythonThread.setDaemon(true);
    pythonThread.start();
}

    private void displayPredictionResult(ECGPythonClient.PredictionResult result) {
        // --- 1. Ambil Data Pasien dari Layar ---
        String patientName = txtname.getText();
        String patientID = jtxtID.getText();
        String patientSex = txtsex.getText();
        String patientBirthdate = jtxtBirthdate2.getText();
        String patientAge = jtxtAge2.getText();
        


        // --- 2. PENERJEMAH ANTI-JEBOL (Menyaring Data dari Python) ---
        String rawLabel = result.getLabel() != null ? result.getLabel() : "Unknown";
        String rawCode = result.prediction != null ? result.prediction : "?";
        
        String classificationCode = "Other";
        String checkStr = (rawLabel + " " + rawCode).toLowerCase(); 
        
        if (checkStr.contains("fusion of paced")) classificationCode = "f";
        else if (checkStr.contains("fusion")) classificationCode = "F";
        else if (checkStr.contains("ventricular premature") || checkStr.contains("pvc")) classificationCode = "V";
        else if (checkStr.contains("atrial premature") || checkStr.contains("pac")) classificationCode = "A";
        else if (checkStr.contains("left bundle")) classificationCode = "L";
        else if (checkStr.contains("right bundle")) classificationCode = "R";
        else if (checkStr.contains("junctional")) classificationCode = "j";
        else if (checkStr.contains("ventricular escape")) classificationCode = "E";
        else if (checkStr.contains("paced") || checkStr.contains("/")) classificationCode = "/";
        else if (checkStr.contains("normal") || checkStr.equals("n")) classificationCode = "N";
        else if (rawCode.length() == 1) classificationCode = rawCode; 
        
        String classificationLabel = projectecg.GeminiHealthAdvisor.getClassificationDescription(classificationCode);
        String riskLevel = projectecg.GeminiHealthAdvisor.calculateRiskLevel(classificationCode);
        double confidence = result.confidence;
        
        // UPDATE TEKS KLASIFIKASI
        jLabel28.setText("Prediction Result : " + classificationCode + " (" + classificationLabel + ")");

        // Generate Health Advice via AI (Atau Fallback)
        String patientContext = buildPatientContext(patientAge, patientSex, riskLevel);
        String healthAdvice = projectecg.GeminiHealthAdvisor.generateAdvancedHealthAdvice(
            classificationCode,
            confidence,
            patientContext
        );
        
        StringBuilder healthReport = new StringBuilder();
        healthReport.append(healthAdvice).append("\n\n");
        healthReport.append("⚠ Consult a cardiologist for clinical evaluation.");
        
        // UPDATE KOTAK SARAN KESEHATAN
        updateHealthAdviceBox(healthReport.toString());

        //  Update Database MySQL 
        try {
            int pId = Integer.parseInt(patientID.trim());
            if (this.patientService != null) {
                // Instansiasi ECGAnalyzer untuk mendapatkan parameter klinis
                int heartRate = 0;
                double prInterval = 0;
                double qrsDuration = 0;
                double qtcInterval = 0;
                double stDeviation = 0;
                
                try {
                    int targetIndex = (leadsData.size() > 1) ? 1 : 0;
                    double[] signal = leadsData.get(targetIndex);
                    ECGAnalyzer tempAnalyzer = new ECGAnalyzer(signal, currentFs, qtcFormula, isPanTompkinsOn);
                    heartRate = (int) tempAnalyzer.getHRAvg();
                    prInterval = tempAnalyzer.getPRAvg() * 1000.0;     // konversi ke ms
                    qrsDuration = tempAnalyzer.getQRSAvg() * 1000.0;   // konversi ke ms
                    qtcInterval = tempAnalyzer.getQTcAvg() * 1000.0;   // konversi ke ms
                    stDeviation = tempAnalyzer.getSTSegmentAvg();      // amplitudo mV
                } catch (Exception e) {
                    System.out.println("Error calculating metrics for database save: " + e.getMessage());
                }

                // DATABASE MENERIMA KODE 1 HURUF dan parameter klinis
                boolean isUpdated = this.patientService.updatePatientAnalysis(
                    pId, 
                    classificationCode, 
                    confidence, 
                    healthAdvice,
                    heartRate,
                    prInterval,
                    qrsDuration,
                    qtcInterval,
                    stDeviation
                );
                
                if (isUpdated) {
                    System.out.println("Hasil analisis berhasil diupdate ke MySQL untuk Pasien ID: " + pId);
                } else {
                    System.err.println("Gagal update ke MySQL (ID tidak ditemukan).");
                }
            }
        } catch (NumberFormatException ex) {
            System.out.println("⚠ Analisis berjalan tanpa ID pasien (Mode Guest). Data tidak disimpan ke DB.");
        }
        
        // Tampilkan Pop-up & Buka Panel Detail
        int userChoice = javax.swing.JOptionPane.showOptionDialog(
            this,
            healthReport.toString(),
            "Patient Health Report - ECG Analysis Complete",
            javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.INFORMATION_MESSAGE,
            null,
            new String[]{"OK - View Detailed Metrics"},
            "OK - View Detailed Metrics"
        );
        
        if (userChoice == 0 || userChoice == javax.swing.JOptionPane.CLOSED_OPTION) {
            analyzepanel analyzeWindow = new analyzepanel(this, true);
            analyzeWindow.setLocationRelativeTo(this);
            
            // KIRIM KODE BERSIH KE ANALYZEPANEL
            analyzeWindow.setPatientInfoComplete(
                patientName, patientID, patientSex, patientBirthdate,
                patientAge,
                classificationCode, confidence, riskLevel
            );
            
            try {
                int targetIndex = (leadsData.size() > 1) ? 1 : 0;
                double[] signal = leadsData.get(targetIndex);
                double dataWindow = signal.length / currentFs;
                
                ECGAnalyzer realAnalyzer = new ECGAnalyzer(signal, currentFs, qtcFormula, isPanTompkinsOn);
                
                int realRPeaks = realAnalyzer.getRPeakCount();
                int realPPeaks = realAnalyzer.getPPeakCount();
                int realTPeaks = realAnalyzer.getTPeakCount();
                int realHrAvg = (int) realAnalyzer.getHRAvg(); 
                double realRrAvg = realAnalyzer.getRRAvg();    
                
                analyzeWindow.setPythonResults(
                    realRPeaks, realPPeaks, realTPeaks,
                    realHrAvg, realRrAvg,
                    classificationCode, confidence * 100, dataWindow,
                    qtcFormula,
                    realAnalyzer
                );
            } catch (Exception e) {
                System.out.println("Error calculating metrics: " + e.getMessage());
            }
            
            analyzeWindow.setVisible(true);
        }
    }

    public void autoFillPatientData(int patientId) {
    // Memanggil MySQL melalui PatientService 
    projectecg.models.Patient p = patientService.getPatient(patientId);
    
    if (p != null) {
        // Jika ketemu, ubah datanya jadi String
        String idStr = String.valueOf(p.getId());
        String name = p.getName();
        String sex = p.getSex();
        String dob = p.getBirthdate() != null ? p.getBirthdate().toString() : "-";
        String age = String.valueOf(p.getAge());
        String weight = String.valueOf(p.getWeight());
        String height = String.valueOf(p.getHeight());
        
        // Lempar ke fungsi bawaanmu yang mengubah tulisan di layar
        loadPatientData(idStr, name, sex, dob, age, weight, height);

        // Load new demographic fields directly
        jTextField5.setText(p.getNik() != null ? p.getNik() : "");
        jTextArea1.setText(p.getAlamat() != null ? p.getAlamat() : "");
        jTextField6.setText(p.getGolDarah() != null ? p.getGolDarah() : "");
        jTextField1.setText(p.getAgama() != null ? p.getAgama() : "");
        jTextField2.setText(p.getStatusKawin() != null ? p.getStatusKawin() : "");
        jTextField3.setText(p.getPekerjaan() != null ? p.getPekerjaan() : "");
        jTextField4.setText(p.getKewarganegaraan() != null ? p.getKewarganegaraan() : "");

        System.out.println("Data pasien berhasil di-load dari MySQL!");
    } else {
        System.out.println("Pasien dengan ID " + patientId + " tidak ditemukan di Database.");
    }
}
    
    /**
     * Helper method to build patient context string for Gemini health advice
     */
    private String buildPatientContext(String ageStr, String sex, String riskLevel) {
        int age = 0;
        try { age = (int) Double.parseDouble(ageStr.trim()); } catch (Exception e) { /* ignore */ }
        
        return "Risk level: " + riskLevel +
               ", Age: " + age +
               ", Sex: " + (sex != null && !sex.isEmpty() ? sex : "N/A");
    }

/**
 * Batch analysis of multiple ECG windows using Python backend.
 * Useful for continuous ECG monitoring with predictions at regular intervals.
 * 
 * @param windowSize Size of each window in samples (typically 216)
 * @param overlapPercent Overlap between windows in percentage (0-90)
 */
public void batchAnalyzeWithPython(int windowSize, int overlapPercent) {
    if (!isFileLoaded || leadsData == null || leadsData.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(this, 
            "Please upload ECG CSV file first!");
        return;
    }
    
    if (windowSize != 216) {
        javax.swing.JOptionPane.showMessageDialog(this,
            "Window size must be 216 samples for the model!");
        return;
    }
    
    // Get target lead
    int targetIndex = (leadsData.size() > 1) ? 1 : 0;
    double[] sourceSignal = leadsData.get(targetIndex);
    
    // Calculate window stride
    int stride = (int) (windowSize * (1 - overlapPercent / 100.0));
    if (stride < 1) stride = windowSize / 2;
    
    // Extract windows
    java.util.List<double[]> windows = new java.util.ArrayList<>();
    java.util.List<double[]> rrIntervals = new java.util.ArrayList<>();
    
    for (int i = 0; i + windowSize <= sourceSignal.length; i += stride) {
        double[] window = ECGPythonClient.extractSignalWindow(sourceSignal, i);
        if (window != null) {
            windows.add(window);
            double[] rr = ECGPythonClient.calculateRRIntervals(window, currentFs);
            rrIntervals.add(rr);
        }
    }
    
    if (windows.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(this,
            "Cannot extract 216-sample windows from signal!");
        return;
    }
    
    // Run batch prediction in background
    Thread batchThread = new Thread(() -> {
        try {
            if (!ECGPythonClient.testConnection()) {
                throw new Exception("Python server not running");
            }
            
            java.util.List<ECGPythonClient.PredictionResult> results = 
                ECGPythonClient.batchPredict(windows, rrIntervals);
            
            // Count predictions by type
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (ECGPythonClient.PredictionResult r : results) {
                counts.put(r.prediction, counts.getOrDefault(r.prediction, 0) + 1);
            }
            
            // Display summary
            javax.swing.SwingUtilities.invokeLater(() -> {
                StringBuilder summary = new StringBuilder();
                summary.append("Batch Analysis Results (").append(results.size()).append(" windows):\n\n");
                counts.forEach((pred, count) -> {
                    summary.append(pred).append(": ").append(count).append(" windows\n");
                });
                
                javax.swing.JOptionPane.showMessageDialog(this, summary.toString(),
                    "Batch Prediction Summary", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            });
            
        } catch (Exception e) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JOptionPane.showMessageDialog(MainDashboard.this,
                    "Batch analysis error:\n" + e.getMessage(),
                    "Analysis Failed",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            });
        }
    });
    batchThread.setDaemon(true);
    batchThread.start();
}

private void captureMqttSnapshot() {
    if (writeIndex < 2) return;

    // Gunakan trimArrays() yang sudah ada: compact leadsData[0..writeIndex-1] ke array baru
    // dan panggil recalculateFilters() + updateWaveMarkers() secara internal
    trimArrays();

    isMqttCaptured   = true;
    hasRecordedData  = true;
    isFileLoaded     = false;
    currentDataIndex = 0;
    xGraph           = 0;

    if (streamFileWriter != null) {
        try { streamFileWriter.flush(); streamFileWriter.close(); }
        catch (java.io.IOException e) { System.err.println("[MQTT-CAPTURE] " + e.getMessage()); }
        finally { streamFileWriter = null; }
    }

    enforceButtonState();

    System.out.printf("[MQTT-CAPTURE] %d sample (%.1fs) siap untuk Analysis.%n",
                       writeIndex, writeIndex / currentFs);

    javax.swing.SwingUtilities.invokeLater(() ->
        javax.swing.JOptionPane.showMessageDialog(this,
            String.format(
                "<html><b>✓ Data MQTT berhasil di-capture!</b><br>" +
                "%d sample (%.1f detik)<br>" +
                "Ganti ke mode <b>Classification</b> → klik <b>Analyze</b>.</html>",
                writeIndex, writeIndex / currentFs),
            "MQTT Auto-Capture", javax.swing.JOptionPane.INFORMATION_MESSAGE)
    );
}

private void trimArrays() {
        if (writeIndex == 0) return;
        
        // Buat list baru untuk hasil potong
        java.util.List<double[]> trimmedLeads = new java.util.ArrayList<>();
        java.util.List<double[]> trimmedProcessed = new java.util.ArrayList<>();
        
        for (int i = 0; i < 12; i++) {
            double[] fullData = leadsData.get(i);
            double[] realData = new double[writeIndex];
            // Copy hanya data yang terisi (sampai writeIndex)
            System.arraycopy(fullData, 0, realData, 0, writeIndex);
            
            trimmedLeads.add(realData);
            trimmedProcessed.add(realData.clone()); // Copy ke processed juga
        }
        
        // Replace list utama
        leadsData = trimmedLeads;
        processedData = trimmedProcessed;
        
        // Jalankan filter ulang terhadap data yang baru saja direkam
        recalculateFilters();
        updateWaveMarkers();
    }

// Method penghubung untuk dipanggil dari Settings.java
public void applySettingsFromDialog() {
    // Hitung ulang filter dengan pengaturan baru dari dialog
    recalculateFilters();

    // Reset playback pointer ke awal agar rekaman diputar dari sample pertama
    // setelah user memilih filter baru di Classification mode
    if (!"Monitoring".equals(currentMode)) {
        currentDataIndex = 0;
        xGraph = 0;
        jPanel_ecgsignal.putClientProperty("GridRendered_Classification", false);
        resetBackground();
        if (ecgTimer != null && !ecgTimer.isRunning()) {
            ecgTimer.start();
        }
    }
}

/**
 * Enforces the correct state of the Analyze button based on current mode and data availability.
 * RULES:
 * - Monitoring Mode: btnAnalyze ALWAYS disabled
 * - Classification Mode + Data Loaded: btnAnalyze enabled
 * - Classification Mode + No Data: btnAnalyze disabled
 */
private void enforceButtonState() {
   // Tombol Analyze HANYA boleh aktif jika:
    // Mode = Classification DAN Data sudah ada DAN tidak sedang merekam
    boolean isClassification = !"Monitoring".equals(currentMode);
    boolean hasData = (isFileLoaded || hasRecordedData) && leadsData != null && !leadsData.isEmpty();
    boolean isNotRecording = !jrecord.isSelected();

    if (isClassification && hasData && isNotRecording) {
        btnAnalyze.setEnabled(true);
        btnAnalyze.setToolTipText("Click to run analysis");
    } else {
        btnAnalyze.setEnabled(false);
        // Berikan feedback ke user kenapa tombol mati
        if (!isClassification) {
            btnAnalyze.setToolTipText("Switch to Classification Mode to enable analysis");
        } else {
            btnAnalyze.setToolTipText("Upload CSV or record data first");
        }
    }
}

private void processParsedData() {
    while (!queueString.isEmpty()) {
        String data = queueString.poll();
        if (data == null) continue;

        String[] parts = data.split(";");
        // if (!jrecord.isSelected()) continue;

        try {
            // ═══════════════════════════════════════════════════════════════
            // MODE A: SINGLE LEAD (ECG 3 CLICK / MAX30003)
            // ═══════════════════════════════════════════════════════════════
            if (parts[0].equals("1-LEAD") && parts.length >= 2) {
                if (writeIndex < MAX_SAMPLE) {
                    double val = Double.parseDouble(parts[1].replace(',', '.'));
                    // Kita masukkan datanya ke grafik Lead II (index 1) sebagai default tampilan
                    System.out.println("Data masuk keranjang Lead II: " + val);
                    leadsData.get(1)[writeIndex] = val; 
                    processedData.get(1)[writeIndex] = val;
                    
                    // Stream row: Lead II is at index 1, others are 0.0
                    double[] streamVals = new double[12];
                    streamVals[1] = val;
                    writeStreamRowAt(writeIndex, streamVals);
                    
                    writeIndex++;
                }
            }
            // ═══════════════════════════════════════════════════════════════
            // MODE B: 12-LEAD (EG12000 MEDLAB)
            // ═══════════════════════════════════════════════════════════════
            else if (parts[0].equals("12-LEAD") && parts.length >= 13) {
                // Jika auto-capture sudah dipicu, berhenti menulis ke buffer
                // agar data snapshot tidak tertimpa oleh data baru
                if (isMqttCaptured) break;

                if (writeIndex < MAX_SAMPLE) {
                    double[] streamVals = new double[12];
                    for (int i = 0; i < 12; i++) {
                        double val = Double.parseDouble(parts[i + 1]);
                        leadsData.get(i)[writeIndex] = val;
                        processedData.get(i)[writeIndex] = val;
                        streamVals[i] = val;
                    }
                    if (writeIndex % 10 == 0) { // Print tiap 10 sample agar konsol tidak spam
                        System.out.println("Data 12-Lead masuk! Lead I: " + leadsData.get(0)[writeIndex] + " | writeIndex: " + writeIndex);
                    }
                    writeStreamRowAt(writeIndex, streamVals);
                    writeIndex++;

                    // ── AUTO-CAPTURE: picu snapshot setelah cukup data terkumpul ──
                    // Threshold default = 2500 sample (~10 detik @ 250 Hz)
                    if (isMqttActive && !isMqttCaptured && writeIndex >= MQTT_CAPTURE_THRESHOLD) {
                        captureMqttSnapshot();
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // PROTOCOL F8: F8;I;II;III;aVR;aVL;aVF;C1;Resp;StatusError;NoUrut
            // parts[0] = "F8"
            // parts[1-8] = data untuk 8 lead
            // parts[9] = StatusError
            // parts[10] = NoUrut (counter)
            // ═══════════════════════════════════════════════════════════════
            else if (parts[0].equals("F8") && parts.length >= 9) { // F8: F8;I;II;III;aVR;aVL;aVF;C1;Resp;StatusError;NoUrut (min 9 non-empty fields)
                if (writeIndex < MAX_SAMPLE) {
                    // Update Leads 0-7 (I, II, III, aVR, aVL, aVF, C1, Respiration)
                    for (int i = 0; i < 8; i++) {
                        String valString = parts[i + 1]; // parts[1] s/d parts[8]
                        double val = 0.0;
                        
                        if (valString != null && !valString.isEmpty() && !valString.equals("")) {
                            try {
                                double raw = Double.parseDouble(valString);
                                // Normalisasi: PS410 mengirim 0-255, baseline 127
                                val = (raw - 127.0) / 40.0; 
                            } catch (NumberFormatException ex) {
                                val = 0.0; 
                            }
                        }
                        
                        // Mapping ke array leadsData:
                        // Index 0-6: I, II, III, aVR, aVL, aVF, C1 (V1)
                        // Index 7: Respiration (kita skip atau simpan terpisah)
                        if (i < 7) { 
                            // Simpan data mentah asli (TETAP AMAN)
                            leadsData.get(i)[writeIndex] = val;
                            
                            // Terapkan Live Smoothing Filter (EMA) 
                            if (writeIndex > 0) {
                                // alpha adalah tingkat kehalusan (0.0 sampai 1.0)
                                // 0.25 artinya 25% data baru, 75% data lama (sangat mulus tapi tidak delay)
                                double alpha = 0.25; 
                                double prevVal = processedData.get(i)[writeIndex - 1];
                                processedData.get(i)[writeIndex] = (alpha * val) + ((1.0 - alpha) * prevVal);
                            } else {
                                // Untuk data pertama kali masuk
                                processedData.get(i)[writeIndex] = val;
                            }
                        }
                    }
                    writeIndex++; 
                }
            }
            // ═══════════════════════════════════════════════════════════════
            // PROTOCOL FE: FE;C2;C3;C4;C5;C6;StatusError
            // parts[0] = "FE"
            // parts[1-5] = data untuk 5 chest leads (V2-V6)
            // parts[6] = StatusError
            // ═══════════════════════════════════════════════════════════════
            else if (parts[0].equals("FE") && parts.length >= 7) { // Perlu 7 field (0-6)
                // FE datang SETELAH F8, jadi kita tulis ke index SEBELUMNYA
                int targetIndex = (writeIndex > 0) ? writeIndex - 1 : 0;
                
                // C2-C6 adalah lead index 7-11 (V2-V6)
                for (int i = 0; i < 5; i++) {
                    String valString = parts[i + 1]; // parts[1] s/d parts[5]
                    double val = 0.0;
                    
                    if (valString != null && !valString.isEmpty() && !valString.equals("")) {
                        try {
                            double raw = Double.parseDouble(valString);
                            val = (raw - 127.0) / 40.0;
                        } catch (NumberFormatException ex) { 
                            val = 0.0; 
                        }
                    }
                    
                    // Simpan ke index 7-11 (V2-V6)
                    leadsData.get(7 + i)[targetIndex] = val;
                    if (targetIndex > 0) {
                        double alpha = 0.25;
                        double prevVal = processedData.get(7 + i)[targetIndex - 1];
                        processedData.get(7 + i)[targetIndex] = (alpha * val) + ((1.0 - alpha) * prevVal);
                    } else {
                        processedData.get(7 + i)[targetIndex] = val;
                    }
                }
                
                // Stream row: both F8 and FE are completed at targetIndex
                if (streamFileWriter != null) {
                    double[] streamVals = new double[12];
                    for (int i = 0; i < 12; i++) {
                        streamVals[i] = leadsData.get(i)[targetIndex];
                    }
                    writeStreamRowAt(targetIndex, streamVals);
                }
            }
            // ═══════════════════════════════════════════════════════════════
            // PROTOCOL FC: Status dan Channel Info (skip untuk data plotting)
            // ═══════════════════════════════════════════════════════════════
            else if (parts[0].equals("FC")) {
                // FC berisi info status channel, bukan data waveform
                // Untuk sekarang kita skip
                System.out.println("FC Status received");
            }
            // ═══════════════════════════════════════════════════════════════
            // PROTOCOL FF: Electrode status untuk chest leads (skip)
            // ═══════════════════════════════════════════════════════════════
            else if (parts[0].equals("FF")) {
                // FF berisi info electrode chest, bukan data waveform
                System.out.println("FF Electrode status received");
            }
            
        } catch (Exception e) {
            System.out.println("Parse error for: " + data + " | " + e.getMessage());
        }
    }
}

private void writeStreamRowAt(int index, double[] values) {
    if (streamFileWriter == null) return;
    try {
        double timeMs = index * (1000.0 / currentFs);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.US, "%.2f", timeMs));
        for (int i = 0; i < 12; i++) {
            double val = (i < values.length) ? values[i] : 0.0;
            sb.append(",").append(String.format(java.util.Locale.US, "%.6f", val));
        }
        streamFileWriter.write(sb.toString());
        streamFileWriter.newLine();
    } catch (java.io.IOException e) {
        System.err.println("[STREAM] Error writing row: " + e.getMessage());
    }
}

public void setIndentity(String id) {
    // This is called when the device sends device info (0xFD)
    System.out.println("Device ID: " + id); 
}

private final boolean[] leadAvailable = new boolean[12];
private boolean leadAvailabilityKnown = false;
public void setStatusChannels(int[] channels) {

    // Status lead real-time dari paket FC/FF
    // channels index:
    // 0..6 = I, II, III, aVR, aVL, aVF, C1(V1)
    // 8..12 = C2..C6 (V2..V6)  [lihat ThrdParsingECG]
    if (channels == null || channels.length < 13) return;

    leadAvailable[0] = channels[0] == 1;  // I
    leadAvailable[1] = channels[1] == 1;  // II
    leadAvailable[2] = channels[2] == 1;  // III
    leadAvailable[3] = channels[3] == 1;  // aVR
    leadAvailable[4] = channels[4] == 1;  // aVL
    leadAvailable[5] = channels[5] == 1;  // aVF
    leadAvailable[6] = channels[6] == 1;  // V1 (C1)
    leadAvailable[7] = channels[8] == 1;  // V2 (C2)
    leadAvailable[8] = channels[9] == 1;  // V3 (C3)
    leadAvailable[9] = channels[10] == 1; // V4 (C4)
    leadAvailable[10] = channels[11] == 1;// V5 (C5)
    leadAvailable[11] = channels[12] == 1;// V6 (C6)

    leadAvailabilityKnown = true;
}

public void setElektrode(int[] electrodes) {
        // Called for electrode status
        // System.out.println("Electrode Status Update");
}

// SerialPortReader inner class removed.
// Serial reading is now handled by the background thread inside SerialCommBiner (jSerialComm).
public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Windows".equals(info.getName())) { 
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainDashboard.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        // MENJALANKAN APLIKASI
        java.awt.EventQueue.invokeLater(() -> {
            MainDashboard frame = new MainDashboard();
            // Memaksa aplikasi langsung full screen 1920x1080 saat di-Run
            frame.setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH); 
            frame.setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAnalyze;
    private javax.swing.JButton btnChooseFile;
    private javax.swing.JButton btnEndSession;
    private javax.swing.JButton btnFind;
    private javax.swing.JButton btnNew;
    private javax.swing.JButton btnPatientInput;
    private javax.swing.JButton btnUploadFile;
    private javax.swing.JButton btnsettings;
    private javax.swing.JDialog dialogEndSession;
    private javax.swing.JDialog dialogPatient;
    private javax.swing.JDialog dialogUpload;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox_cbGain;
    private javax.swing.JComboBox<String> jComboBox_cbSpeed;
    private javax.swing.JComboBox<String> jComboBox_port;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanelButton;
    private javax.swing.JPanel jPanelDropzone;
    private javax.swing.JPanel jPanelHeader;
    private javax.swing.JPanel jPanel_ecgsignal;
    private javax.swing.JPanel jPanel_panelpatient2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JButton jbtnDownload;
    private javax.swing.JButton jbtnfreeze;
    private javax.swing.JTextField jlblHR;
    private javax.swing.JToggleButton jon;
    private javax.swing.JToggleButton jrecord;
    private javax.swing.JTextField jtxtAge2;
    private javax.swing.JTextField jtxtBirthdate2;
    private javax.swing.JTextField jtxtID;
    private javax.swing.JTextArea jtxtNotes2;
    private javax.swing.JLabel lblTitleDashboard;
    private javax.swing.JLabel lblTitlePatient;
    private javax.swing.JTextField txtname;
    private javax.swing.JTextField txtsex;
    // End of variables declaration//GEN-END:variables
}