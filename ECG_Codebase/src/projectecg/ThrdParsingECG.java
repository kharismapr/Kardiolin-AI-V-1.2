/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package projectecg;

import projectecg.MainDashboard;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author deast
 */
public class ThrdParsingECG implements Runnable {

    BlockingQueue<Byte> queueDataECGByte;
    BlockingQueue<String> queueDataECG;
    MainDashboard mainFrame;
    private final java.io.ByteArrayOutputStream asciiBuffer = new java.io.ByteArrayOutputStream();

    boolean EXIT = false;
    //
    byte StatusErrorF8;
    byte StatusErrorF9;
    byte StatusErrorFA;
    byte StatusErrorFC;
    byte StatusErrorFE;
    byte StatusErrorFF;
    //
    String strSendF8 = "";
    String strSendF9 = "";
    String strSendFA = "";
    String strSendFC = "";
    String strSendFE = "";
    String strSendFF = "";
    String strSendDefault = "";
    //

    //Constructor();
    public ThrdParsingECG(MainDashboard mainFrame, BlockingQueue<Byte> queueDataECGByte, BlockingQueue<String> queueDataECG) {
        this.queueDataECGByte = queueDataECGByte;
        this.queueDataECG = queueDataECG;
        this.mainFrame = mainFrame;
        //
    }

    public void setExit() {
        EXIT = true;
    }

    private void tunggu(int msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
            Logger.getLogger(ThrdParsingECG.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //
    private Byte getData() {
        Byte by;
        by = queueDataECGByte.poll();
        return by;
    }

    //
    private int getDataLoop() {
        Byte by;
        do {
            by = queueDataECGByte.poll();
        } while (by == null);
        //System.out.printf("%X\n", by & 0xFF);
        return (by & 0xFF);
    }
    //

    @Override
    public void run() {
        int dat;
        int len;
        int checksum;
        int sum;
        int[] buf = new int[20];
        int[] ChannelF8 = new int[8];
        int[] ChannelFE = new int[5];
        int[] Elektrode = new int[10];
        int[] Channels = new int[13];
        String ss;
        int temp;
        int ELL = 0xFF;
        int ERL = 0xFF;
        int ELA = 0xFF;
        int ERA = 0xFF;
        int ECHEST = 0xFF;
        int RASPWAV;
        int SPEED = 0xFF;
        int AMP = 0xFF;
        int EMGFilter = 0xFF;
        int Filter2 = 0xFF;
        int S = 0xFF;
        int K1 = 0xFF;
        int K2 = 0xFF;
        int N = 0xFF;
        int CI = 0xFF, CII = 0xFF, CIII = 0xFF, CaVR = 0xFF, CaVL = 0xFF, CaVF = 0xfF, CC1 = 0xFF;
        int CC2 = 0xFF, CC3 = 0xFF, CC4 = 0xFF, CC5 = 0xFF, CC6 = 0xFF;  //Channel
        int EC2 = 0xFF, EC3 = 0xFF, EC4 = 0xFF, EC5 = 0xFF, EC6 = 0xFF;  //Electrode
        int PulseValue;
        int RespirationValue;
        String IDPasien = "pas-001";
        String SN = "EKG12L.001";
        long NoUrut = 0;
        //inisialisasi
        for (int i = 0; i < 8; i++) {
            ChannelF8[i] = 1; // assume all active; 0xFC packet will correct if needed
        }
        for (int i = 0; i < 5; i++) {
            ChannelFE[i] = 0;
        }
        for (int i = 0; i < 10; i++) {
            Elektrode[i] = 0;
        }
        //
        while (!EXIT) {
            Byte By = getData(); //ambil data dari queue
            if (By == null) {
                continue;
            }
            dat = By & 0xFF;
            //System.out.printf("%d\n", dat);
            switch (dat) {
                case 0xFD:
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    do {
                        dat = getDataLoop();
                        if (dat != 0x00) {
                            buffer.write(dat); // Simpan data
                        }                        //System.out.printf("%c", dat);
                    } while (dat != 0x00);
                    //System.out.println("\n==================");
                    try {
                        String receivedString = buffer.toString("UTF-8"); // atau "UTF-8"
                        if (!receivedString.isEmpty()) {
                            mainFrame.setIndentity(receivedString);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(ThrdParsingECG.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    break;
                //====================================================================
                //limb waveform blocks
                //Waveform blocks are transmitted 50, 100, 150 or 300 times per second
                //====================================================================
                case 0xF8:   //0xF8 secara default bertipe int
                    StatusErrorF8 = 0;
                    strSendF8 = "F8;";
                    dat = getDataLoop();     //byte ke-2 baca panjang data dan checksum
                    len = (dat >> 4) & 0x0F; //hitung panjang data
                    checksum = dat & 0x0F;   //hitung checksum
                    //=====Pengecekan data & checksum====
                    sum = 0xF8;
                    for (int i = 0; i < len; i++) {
                        dat = getDataLoop();
                        buf[i] = dat;
                        if (dat > 0xF7) {  //tidak ada data lebih besar dari F8 kecuali Marker
                            StatusErrorF8 = 1;
                        }
                        sum = sum + dat;
                    }
                    if (checksum != (sum % 16)) {
                        StatusErrorF8 = 1;
                    }
                    //====Data Wave======================================================================
                    //Ada 8 wave yang di kirim dengan urutan : I, II, III, aVR, aVL, aVF, C1, Respiration
                    //data yang dikirim : F8;I;II;III;aVR;aVL;aVF;C1;Resp;StatusError\n
                    //===================================================================================
                    int k = 0;
                    for (int i = 0; i < 8; i++) {
                        if (ChannelF8[i] == 1) {
                            ss = String.format("%d;", buf[k++]);
                            strSendF8 = strSendF8 + ss;
                        } else {
                            strSendF8 = strSendF8 + ";";
                        }
                    }
                    ss = String.format("%d;%d\n", StatusErrorF8, NoUrut++);
                    strSendF8 = strSendF8 + ss;
                    queueDataECG.add(strSendF8);
                    //System.out.print(strSendF8);
                    break;
                //=================================
                //detected respiration (0xF9 marker).
                //hanya 3 byte
                //=============================
                case 0xF9:   // Type == 01 → pulse value
                    StatusErrorF9 = 0;
                    sum = 0xF9;
                    checksum = getDataLoop();       //Byte ke-2 checksum (8bit)
                    PulseValue = getDataLoop();     //Byte ke-3 => value
                    if (PulseValue > 0xF7) {
                        StatusErrorF9 = 1;
                    }
                    sum = sum + PulseValue;
                    if (checksum != (sum % 128)) {
                        StatusErrorF9 = 1;
                    }
                    strSendF9 = String.format("F9;%d;%d\n", PulseValue, StatusErrorF9);
                    queueDataECG.add(strSendF9);
                    //System.out.print(strSendF9);
                    break;
                //=========================================
                //0xFA marker, can be  used for a pulse "beep"
                //hanya 3 byte
                //=====================================
                case 0xFA:      //Type == 10 → respiration value
                    StatusErrorFA = 0;
                    sum = 0xFA;
                    checksum = getDataLoop(); //Byte ke-2 checksum
                    RespirationValue = getDataLoop();  //value

                    if (RespirationValue > 0xF7) {
                        StatusErrorFA = 1;
                    }
                    sum = sum + RespirationValue;
                    if (checksum != (sum % 128)) {
                        StatusErrorFA = 1;
                    }
                    strSendFA = String.format("FA;%d;%d\n", RespirationValue, StatusErrorFA);
                    queueDataECG.add(strSendFA);
                    //System.out.print(strSendFA);
                    break;
                //
                //=============================================
                //Status blocks are transmitted once per second
                //Status blocks contain five bytes (5 Byte)
                //=============================================
                case 0xFC:
                    StatusErrorFC = 0;
                    sum = 0xFC;
                    checksum = getDataLoop(); //Byte ke-2 data checksum
                    dat = getDataLoop() & 0xFF; //byte ke-3 data electroda
                    if (dat > 0xF7) {
                        StatusErrorFC = 1;
                    }
                    sum = sum + dat;
                    if ((dat & 0x01) != 0) {
                        ELL = 1;
                    } else {
                        ELL = 0;
                    }
                    if ((dat & 0x02) != 0) {
                        ERL = 1;
                    } else {
                        ERL = 0;
                    }
                    if ((dat & 0x04) != 0) {
                        ELA = 1;
                    } else {
                        ELA = 0;
                    }
                    if ((dat & 0x08) != 0) {
                        ERA = 1;
                    } else {
                        ERA = 0;
                    }
                    if ((dat & 0x10) != 0) {
                        ECHEST = 1;
                    } else {
                        ECHEST = 0;
                    }
                    if ((dat & 0x40) != 0) {
                        RASPWAV = 1;
                    } else {
                        RASPWAV = 0;
                    }
                    //Electrode
                    strSendFC = String.format("FC;%d;%d;%d;%d;%d;%d;", ELL, ERL, ELA, ERA, ECHEST, RASPWAV);
                    Elektrode[0] = ELL;
                    Elektrode[1] = ERL;
                    Elektrode[2] = ELA;
                    Elektrode[3] = ERA;
                    Elektrode[4] = ECHEST;
                    Channels[7] = RASPWAV;
                    //
                    dat = getDataLoop(); //byte ke-4 channel
                    if (dat > 0xF7) {
                        StatusErrorFC = 1;
                    }
                    sum = sum + dat;
                    if ((dat & 0x01) != 0) {
                        CI = 1;
                    } else {
                        CI = 0;
                    }
                    Channels[0] = CI;
                    if ((dat & 0x02) != 0) {
                        CII = 1;
                    } else {
                        CII = 0;
                    }
                    Channels[1] = CII;
                    if ((dat & 0x04) != 0) {
                        CIII = 1;
                    } else {
                        CIII = 0;
                    }
                    Channels[2] = CIII;
                    if ((dat & 0x08) != 0) {
                        CaVR = 1;
                    } else {
                        CaVR = 0;
                    }
                    Channels[3] = CaVR;
                    if ((dat & 0x10) != 0) {
                        CaVL = 1;
                    } else {
                        CaVL = 0;
                    }
                    Channels[4] = CaVL;
                    if ((dat & 0x20) != 0) {
                        CaVF = 1;
                    } else {
                        CaVF = 0;
                    }
                    Channels[5] = CaVF;
                    if ((dat & 0x40) != 0) {
                        CC1 = 1;
                    } else {
                        CC1 = 0;
                    }
                    Channels[6] = CC1;
                    ss = String.format("%d;%d;%d;%d;%d;%d;%d;", CI, CII, CIII, CaVR, CaVL, CaVF, CC1);
                    strSendFC = strSendFC + ss;
                    ChannelF8[0] = CI;
                    ChannelF8[1] = CII;
                    ChannelF8[2] = CIII;
                    ChannelF8[3] = CaVR;
                    ChannelF8[4] = CaVL;
                    ChannelF8[5] = CaVF;
                    ChannelF8[6] = CC1;
                    ChannelF8[7] = RASPWAV;

                    dat = getDataLoop(); //byte ke-5 EKG status
                    if (dat > 0xF7) {
                        StatusErrorFC = 1;
                    }
                    sum = sum + dat;
                    temp = dat & 0x03;
                    switch (temp) {
                        case 0:
                            SPEED = 50;
                            break;
                        case 1:
                            SPEED = 100;
                            break;
                        case 2:
                            SPEED = 150;
                            break;
                        case 3:
                            SPEED = 300;
                            break;
                        default:
                            break;
                    }
                    AMP = (dat >> 2) & 0x03;
                    if ((dat & 0x10) != 0) {
                        EMGFilter = 1;
                    } else {
                        EMGFilter = 0;
                    }
                    temp = dat >> 5;
                    Filter2 = temp & 0x03;
                    ss = String.format("%d;%d;%d;%d;", SPEED, AMP, EMGFilter, Filter2);
                    strSendFC = strSendFC + ss;
                    //
                    dat = getDataLoop() & 0xFF; //byte ke-6 status
                    if (dat > 0xF7) {
                        StatusErrorFC = 1;
                    }
                    sum = sum + dat;
                    S = dat & 0x0F;
                    if ((dat & 0x10) != 0) {
                        K1 = 1;
                    } else {
                        K1 = 0;
                    }
                    if ((dat & 0x20) != 0) {
                        K2 = 1;
                    } else {
                        K2 = 0;
                    }
                    if ((dat & 0x40) != 0) {
                        N = 1;
                    } else {
                        N = 0;
                    }
                    //
                    if (checksum != (sum % 128)) {
                        StatusErrorFC = 1;
                    }
                    ss = String.format("%d;%d;%d;%d;%d\n", S, K1, K2, N, StatusErrorFC);
                    strSendFC = strSendFC + ss;
                    queueDataECG.add(strSendFC);
                    //System.out.print(strSendFC);
                    break;

                //============================================================================
                //=== 0xFE (chest waves) ===
                //=== Waveform blocks are transmitted 50, 100, 150 or 300 times per second ===
                //=== Ada 3 sampai 7 byte
                //============================================================================
                case 0xFE:
                    StatusErrorFE = 0;
                    strSendFE = "FE;";
                    dat = getDataLoop();     //byte ke-2 baca data length & checksum
                    len = (dat >> 4) & 0x0F; //hitung panjang data
                    checksum = dat & 0x0F;   //hitung checksum
                    //
                    //Check Panjang data dan Check Sum
                    sum = 0xFE;
                    for (int i = 0; i < len; i++) {
                        dat = getDataLoop();
                        buf[i] = dat;
                        if (dat > 0xF7) {
                            StatusErrorFE = 1;
                        }
                        sum = sum + dat;
                    }
                    if (checksum != (sum % 16)) {
                        StatusErrorFE = 1;
                    }
                    //Wave berurutan ada 5 yaitu : C2, C3, C4, C5, C6
                    k = 0;
                    for (int i = 0; i < 5; i++) {  //5 channls
                        //if (ChannelFE[i] == 1) {
                        ss = String.format("%d;", buf[k++]);
                        strSendFE = strSendFE + ss;
                        //} else {
                        //strSendFE = strSendFE + ";";
                        // }
                    }
                    ss = String.format("%d\n", StatusErrorFE);
                    strSendFE = strSendFE + ss;
                    queueDataECG.add(strSendFE);
                    //System.out.print(strSendFE);
                    break;
                //==============================================
                //Status blocks are transmitted once per second
                //Chest status blocks contain four bytes (4 Byte)
                //==========================================
                case 0xFF:
                    StatusErrorFF = 0;
                    sum = 0xFF;
                    //======================
                    //byte ke-2 checksum 
                    dat = getDataLoop();
                    checksum = dat;
                    sum = sum + dat;
                    //======================
                    //byte ke-3 electrode
                    dat = getDataLoop();
                    //System.out.printf("Elektrode = %X\n", dat);
                    if (dat > 0xF7) {
                        StatusErrorFF = 1;
                    }
                    sum = sum + dat;
                    if ((dat & 0x01) != 0) {
                        EC2 = 1;
                    } else {
                        EC2 = 0;
                    }
                    Elektrode[5] = EC2;
                    if ((dat & 0x02) != 0) {
                        EC3 = 1;
                    } else {
                        EC3 = 0;
                    }
                    Elektrode[6] = EC3;
                    if ((dat & 0x04) != 0) {
                        EC4 = 1;
                    } else {
                        EC4 = 0;
                    }
                    Elektrode[7] = EC4;
                    if ((dat & 0x08) != 0) {
                        EC5 = 1;
                    } else {
                        EC5 = 0;
                    }
                    Elektrode[8] = EC5;
                    if ((dat & 0x10) != 0) {
                        EC6 = 1;
                    } else {
                        EC6 = 0;
                    }
                    Elektrode[9] = EC6;
                    //=======================
                    //byte ke-4 channel
                    dat = getDataLoop();
                    //System.out.printf("channel = %X\n", dat);
                    if (dat > 0xF7) {
                        StatusErrorFF = 1;
                    }
                    sum = sum + dat;
                    if ((dat & 0x01) != 0) {
                        CC2 = 1;
                    } else {
                        CC2 = 0;
                    }
                    Channels[8] = CC2;
                    //
                    if ((dat & 0x02) != 0) {
                        CC3 = 1;
                    } else {
                        CC3 = 0;
                    }
                    Channels[9] = CC3;
                    //
                    if ((dat & 0x04) != 0) {
                        CC4 = 1;
                    } else {
                        CC4 = 0;
                    }
                    Channels[10] = CC4;
                    if ((dat & 0x08) != 0) {
                        CC5 = 1;
                    } else {
                        CC5 = 0;
                    }
                    Channels[11] = CC5;
                    //
                    if ((dat & 0x10) != 0) {
                        CC6 = 1;
                    } else {
                        CC6 = 0;
                    }
                    Channels[12] = CC6;
                    //
                    if (checksum != (sum % 128)) {
                        StatusErrorFF = 1;
                    }
                    ss = String.format("FF;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d\n", EC2, EC3, EC4, EC5, EC6, CC2, CC3, CC4, CC5, CC6, StatusErrorFF);
                    strSendFF = ss;
                    queueDataECG.add(strSendFF);
                    mainFrame.setStatusChannels(Channels);
                    mainFrame.setElektrode(Elektrode);
                    //System.out.print(strSendFF);
                    //
                    ChannelFE[0] = CC2;
                    ChannelFE[1] = CC3;
                    ChannelFE[2] = CC4;
                    ChannelFE[3] = CC5;
                    ChannelFE[4] = CC6;
                    //================================================
                    //Header dimasukkan di FF agar dikirim tiap second
                    //================================================
                    ss = String.format("HD;%s;%s\n", IDPasien, SN);
                    queueDataECG.add(ss);
                    break;
                default:
                    if (dat == '\n' || dat == '\r') {
                        if (asciiBuffer.size() > 0) {
                            try {
                                String line = asciiBuffer.toString("UTF-8").trim();
                                if (line.startsWith("1-LEAD;")) {
                                    try {
                                        String rawValueStr = line.substring("1-LEAD;".length());
                                        int rawCount = Integer.parseInt(rawValueStr.trim());
                                        double mv = rawCount * 0.00038147; // konversi raw ADC count -> mV (gain 20V/V)
                                        queueDataECG.add(String.format(java.util.Locale.US, "1-LEAD;%.5f\n", mv));
                                    } catch (NumberFormatException e) {
                                        // baris tidak valid, abaikan
                                    }
                                } else if (line.startsWith("12-LEAD;")) {
                                    queueDataECG.add(line + "\n");
                                }
                            } catch (Exception e) {
                                // ignore encoding issues
                            }
                            asciiBuffer.reset();
                        }
                    } else if (dat >= 32 && dat <= 126) { // printable ASCII characters
                        asciiBuffer.write(dat);
                    } else {
                        // Jika menerima binary garbage, reset buffer
                        asciiBuffer.reset();
                    }
                    strSendDefault = "DF;1\n";
                    break;
            }
        }
    }
}