/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package projectecg;

/**
 *
 * @author fizel
 */
public class Settings extends javax.swing.JDialog {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Settings.class.getName());

    /**
     * Creates new form Settings
     */
    public Settings(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "Bandpass", "Low-pass", "High-pass", "Low-pass Butterworth", "High-pass Butterworth", "Bandpass Butterworth" }));
        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "50 Hz", "60 Hz" }));
        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "Bazett formula", "Fridericia formula", "Framingham Formula", "Hodges Formula" }));
        
        // Menambahkan listener secara manual
        jComboBox4.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            updateModeVisibility(); // Panggil fungsi cek tadi setiap kali user ganti pilihan
        }
    }); 
        updateModeVisibility();
       
    }
        
   // Method baru untuk menerima status terakhir dari Dashboard
        // GANTI METHOD setCurrentSelection DENGAN INI:

// GANTI ISI METHOD setCurrentSelection DENGAN INI:

public void setCurrentSelection(String mode, String currentFilter, boolean isNotch, int currentHz, boolean isEmg, String currentQtc, int currentRhythm, boolean isP, boolean isT, boolean isR, boolean isPanTompkins) {     
        
        // Logika Tampilan Debug yang JUJUR
        String statusHz = isNotch ? (currentHz + " Hz") : "OFF";
        System.out.println("Settings LOAD -> Mode: " + mode + " | Filter: " + currentFilter + " | Notch: " + statusHz + " | QTc: " + currentQtc + " | PanTompkins: " + (isPanTompkins ? "ON" : "OFF"));

        // 1. Set Mode
        jComboBox4.setSelectedItem(mode);

        // 2. Set Filter & QTc (Visual Logic)
        if ("Monitoring".equals(mode)) {
            // Jika Monitoring, paksa tampilan GUI jadi "-" biar user tau ini mati
            jComboBox1.setSelectedItem("-");
            jComboBox2.setSelectedItem("-"); // QTc juga strip
        } else {
            jComboBox1.setSelectedItem(currentFilter);
            jComboBox2.setSelectedItem(currentQtc); // Load QTc yang tersimpan
        }

        // 3. Set Status Toggle Button
        jToggleButton5.setSelected(isNotch); 
        jToggleButton4.setSelected(isEmg);   
        tglbtnPanTompkins.setSelected(isPanTompkins);
        jToggleButton5.setText(isNotch ? "ON" : "OFF");
        jToggleButton4.setText(isEmg ? "ON" : "OFF");
        tglbtnPanTompkins.setText(isPanTompkins ? "ON" : "OFF");

        // 4. Logika ComboBox Hz
        // Hanya set ke 50/60 jika Notch ON. Jika OFF, set ke strip.
        if (isNotch) {
            if (currentHz == 50) jComboBox3.setSelectedItem("50 Hz");
            else if (currentHz == 60) jComboBox3.setSelectedItem("60 Hz");
            else jComboBox3.setSelectedItem("-");
        } else {
            jComboBox3.setSelectedItem("-");
        }
        jTextField1.setText(String.valueOf(currentRhythm)); 

        jToggleButton1.setSelected(isP); // P Wave
        jToggleButton1.setText(isP ? "ON" : "OFF");
        jToggleButton2.setSelected(isT); // T Wave
        jToggleButton2.setText(isT ? "ON" : "OFF");
        jToggleButton3.setSelected(isR); // R Wave
        jToggleButton3.setText(isR ? "ON" : "OFF");
        
        updateModeVisibility();
    }


// Method ini bertugas mengecek mode dan mematikan/menyalakan tombol
private void updateModeVisibility() {
    // Ambil nilai yang dipilih di jComboBox4
    String selectedMode = (String) jComboBox4.getSelectedItem();
    
    // Check if mode is "Signal Classification"
    // If Signal Classification = true (enabled). If Monitoring = false (disabled/grayed out).
    boolean isSignalClassification = "Classification".equals(selectedMode);
    
    // Apply enabled/disabled status to all other setting components
    // Filter & Formula
    jComboBox1.setEnabled(isSignalClassification); // Filter Type
    jComboBox2.setEnabled(isSignalClassification); // QTC Formula
    jComboBox3.setEnabled(isSignalClassification); // Hz selection
    
    // Toggle buttons (ON/OFF)
    jToggleButton1.setEnabled(isSignalClassification);
    jToggleButton2.setEnabled(isSignalClassification);
    jToggleButton3.setEnabled(isSignalClassification);
    jToggleButton4.setEnabled(isSignalClassification);
    jToggleButton5.setEnabled(isSignalClassification);
    
    // Text Fields & Other Labels if needed
    jTextField1.setEnabled(isSignalClassification); // Rhythm Time
}

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    /**
     * This method is called from within the constructor to initialize the form.     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jToggleButton1 = new javax.swing.JToggleButton();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();
        jLabel3 = new javax.swing.JLabel();
        jToggleButton4 = new javax.swing.JToggleButton();
        jLabel9 = new javax.swing.JLabel();
        jToggleButton5 = new javax.swing.JToggleButton();
        jComboBox1 = new javax.swing.JComboBox<>();
        jComboBox2 = new javax.swing.JComboBox<>();
        jComboBox3 = new javax.swing.JComboBox<>();
        jComboBox4 = new javax.swing.JComboBox<>();
        jLabel10 = new javax.swing.JLabel();
        btnApply = new javax.swing.JButton();
        jLabel11 = new javax.swing.JLabel();
        tglbtnPanTompkins = new javax.swing.JToggleButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "ECG SETTING", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.ABOVE_TOP));

        jTextField1.addActionListener(this::jTextField1ActionPerformed);

        jLabel1.setText("Rhythm Time   :");

        jLabel2.setText("ECG Mode       : ");

        jLabel4.setText("EMG Filter        : ");

        jLabel5.setText("Notch Filter : ");

        jLabel6.setText("P Wave : ");

        jLabel7.setText("T Wave : ");

        jLabel8.setText("R Wave : ");

        jToggleButton1.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jToggleButton1.setText("ON/OFF");
        jToggleButton1.addActionListener(this::jToggleButton1ActionPerformed);

        jToggleButton2.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jToggleButton2.setText("ON/OFF");
        jToggleButton2.addActionListener(this::jToggleButton2ActionPerformed);

        jToggleButton3.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jToggleButton3.setText("ON/OFF");
        jToggleButton3.addActionListener(this::jToggleButton3ActionPerformed);

        jLabel3.setText("Filter                : ");

        jToggleButton4.setText("ON/OFF");
        jToggleButton4.addActionListener(this::jToggleButton4ActionPerformed);

        jLabel9.setText("QTC Formula   : ");

        jToggleButton5.setText("ON/OFF");
        jToggleButton5.addActionListener(this::jToggleButton5ActionPerformed);

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "Band-pass", "Low-pass", "High-pass", "Low-pass Butterworth", "High-pass Butterworth", "Bandpass Butterworth" }));

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "Bazett formula", "Fridericia formula", "Framingham Formula", "Hodges Formula" }));

        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "-", "50  Hz", "60 Hz" }));
        jComboBox3.addActionListener(this::jComboBox3ActionPerformed);

        jComboBox4.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Monitoring", "Classification" }));
        jComboBox4.addActionListener(this::jComboBox4ActionPerformed);

        jLabel10.setText("Second");

        btnApply.setText("Apply");
        btnApply.addActionListener(this::btnApplyActionPerformed);

        jLabel11.setText("Pan-Tompkins :");

        tglbtnPanTompkins.setText("ON/OFF");
        tglbtnPanTompkins.addActionListener(this::tglbtnPanTompkinsActionPerformed);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel1)
                                    .addComponent(jLabel3)
                                    .addComponent(jLabel2))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel10)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                            .addComponent(jComboBox4, javax.swing.GroupLayout.Alignment.LEADING, 0, 280, Short.MAX_VALUE)
                                            .addComponent(jComboBox1, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                        .addGap(0, 0, Short.MAX_VALUE))))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel11)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(tglbtnPanTompkins, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel4)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jToggleButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGap(12, 12, 12)
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jToggleButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 289, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(9, 9, 9)
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jToggleButton1)
                                .addGap(12, 12, 12)
                                .addComponent(jLabel7)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jToggleButton2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel8)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jToggleButton3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGap(0, 300, Short.MAX_VALUE)
                        .addComponent(btnApply, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jToggleButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(jToggleButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(tglbtnPanTompkins))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 15, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addGap(16, 16, 16)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel6)
                    .addComponent(jToggleButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addGap(2, 2, 2)
                            .addComponent(jToggleButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(jLabel7))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addGap(2, 2, 2)
                            .addComponent(jToggleButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(jLabel8)))
                .addGap(18, 18, 18)
                .addComponent(btnApply)
                .addGap(16, 16, 16))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 429, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addContainerGap(9, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(9, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 347, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField1ActionPerformed

    private void jToggleButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        if(jToggleButton1.isSelected()) jToggleButton1.setText("ON"); else jToggleButton1.setText("OFF");// TODO add your handling code here:
    }//GEN-LAST:event_jToggleButton1ActionPerformed

    private void jToggleButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton2ActionPerformed
        if(jToggleButton2.isSelected()) jToggleButton2.setText("ON"); else jToggleButton2.setText("OFF");
    }//GEN-LAST:event_jToggleButton2ActionPerformed

    private void jToggleButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton3ActionPerformed
        if(jToggleButton3.isSelected()) jToggleButton3.setText("ON"); else jToggleButton3.setText("OFF");// TODO add your handling code here:
    }//GEN-LAST:event_jToggleButton3ActionPerformed

    private void jToggleButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton4ActionPerformed
        if(jToggleButton4.isSelected()) jToggleButton4.setText("ON"); else jToggleButton4.setText("OFF");
    }//GEN-LAST:event_jToggleButton4ActionPerformed

    private void jToggleButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton5ActionPerformed
        if(jToggleButton5.isSelected()) jToggleButton5.setText("ON"); else jToggleButton5.setText("OFF");
    }//GEN-LAST:event_jToggleButton5ActionPerformed

    private void jComboBox3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox3ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jComboBox3ActionPerformed

    private void btnApplyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnApplyActionPerformed
    String selectedMode = (String) jComboBox4.getSelectedItem();
    
        if (this.getOwner() instanceof MainDashboard) {
            MainDashboard dashboard = (MainDashboard) this.getOwner();
            
            dashboard.setAppMode(selectedMode);
            
            if ("Monitoring".equals(selectedMode)) {
                // === IF MONITORING MODE: DISABLE ALL PROCESSING ===
                // We ignore user input in GUI and force reset to safe defaults
                
                dashboard.filterType = "-";
                dashboard.qtcFormula = "-";      // Reset QTc
                dashboard.isNotchOn = false;
                dashboard.isEmgOn = false;
                dashboard.showPWave = false;
                dashboard.showTWave = false;
                dashboard.showRWave = false;
                
                // notchFreq remains 50 as default memory, doesn't matter since isNotchOn = false
                
                System.out.println("APPLY MONITORING -> All Processing Disabled (Raw Signal).");
                
            } else {
                // === IF SIGNAL CLASSIFICATION MODE: READ USER INPUT ===
                
                // 1. Filter Utama
                dashboard.filterType = (String) jComboBox1.getSelectedItem();
                
                // 2. QTc Formula (TAMBAHAN PENTING)
                dashboard.qtcFormula = (String) jComboBox2.getSelectedItem();

                // 3. Notch Filter
                dashboard.isNotchOn = jToggleButton5.isSelected(); 
                
                String hzString = (String) jComboBox3.getSelectedItem();
                // Parsing Hz hanya jika Notch ON dan String Valid
                if (dashboard.isNotchOn && hzString != null && hzString.contains("Hz")) {
                    try {
                        dashboard.notchFreq = Integer.parseInt(hzString.replace("Hz", "").trim());
                    } catch (NumberFormatException e) {
                        dashboard.notchFreq = 50; 
                    }
                } else {
                    dashboard.notchFreq = 50; // Default aman
                }

                // 4. EMG & Pan-Tompkins Filter
                dashboard.isEmgOn = jToggleButton4.isSelected();
                dashboard.isPanTompkinsOn = tglbtnPanTompkins.isSelected();
                
                try {
                    String rhythmText = jTextField1.getText().trim();
                    if (!rhythmText.isEmpty()) {
                        int val = Integer.parseInt(rhythmText);
                        // Validasi: Jangan biarkan nilai negatif atau 0
                        if (val > 0) {
                            dashboard.rhythmTimeSeconds = val;
                        } else {
                            dashboard.rhythmTimeSeconds = 10; // Default jika user iseng isi 0
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid Rhythm Time Input. Using default 10s.");
                    dashboard.rhythmTimeSeconds = 10; // Fallback jika input bukan angka
                }
                
                System.out.println("APPLY SIGNAL CLASSIFICATION -> Filter: " + dashboard.filterType + 
                                   " | QTc: " + dashboard.qtcFormula + 
                                   " | Notch: " + (dashboard.isNotchOn ? dashboard.notchFreq + "Hz" : "OFF") +
                                   " | PanTompkins: " + (dashboard.isPanTompkinsOn ? "ON" : "OFF"));
                dashboard.showPWave = jToggleButton1.isSelected();
                dashboard.showTWave = jToggleButton2.isSelected();
                dashboard.showRWave = jToggleButton3.isSelected();

                System.out.println("APPLY VISUALS -> P:" + dashboard.showPWave + 
                                   ", T:" + dashboard.showTWave + ", R:" + dashboard.showRWave);
            }
            
            // Panggil hitung ulang di dashboard
            dashboard.applySettingsFromDialog();
            dashboard.setAppMode(selectedMode);

        }
        
    this.dispose();
    }//GEN-LAST:event_btnApplyActionPerformed

    private void jComboBox4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox4ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jComboBox4ActionPerformed

    private void tglbtnPanTompkinsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tglbtnPanTompkinsActionPerformed
        if(tglbtnPanTompkins.isSelected()) tglbtnPanTompkins.setText("ON"); else tglbtnPanTompkins.setText("OFF");
    }//GEN-LAST:event_tglbtnPanTompkinsActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                Settings dialog = new Settings(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnApply;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox2;
    private javax.swing.JComboBox<String> jComboBox3;
    private javax.swing.JComboBox<String> jComboBox4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JToggleButton jToggleButton2;
    private javax.swing.JToggleButton jToggleButton3;
    private javax.swing.JToggleButton jToggleButton4;
    private javax.swing.JToggleButton jToggleButton5;
    private javax.swing.JToggleButton tglbtnPanTompkins;
    // End of variables declaration//GEN-END:variables
}
