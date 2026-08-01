package projectecg.services;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.BlockingQueue;
import projectecg.MainDashboard;

// NEED IMPROVEMENT

public class MqttService implements MqttCallback {

    private MqttClient client;
    private String brokerUrl = "tcp://broker.emqx.io:1883";
    private String clientId = "Dashboard_ECG_Java_" + System.currentTimeMillis();
    private String topic = "ecg/data/raw"; 
    private int idPasienAktif = -1;

    private BlockingQueue<String> queueString;
    private MainDashboard dashboard;

    public MqttService(BlockingQueue<String> queueString, Object dashboardObj) {
        this.queueString = queueString;
        
        if (dashboardObj instanceof projectecg.MainDashboard) {
            this.dashboard = (projectecg.MainDashboard) dashboardObj; 
        }
    }

    public void connect() {
        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(this);
            client.connect(options);
            System.out.println("Terhubung ke MQTT Broker EMQX");

            subscribe(topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            client.subscribe(topic);
            System.out.println("Berhasil Subscribe ke topik: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Koneksi ke MQTT Broker terputus!");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());

        try {
            JSONObject jsonObject = new JSONObject(payload);
            
            // 1. Cek ID Pasien
            int idPasienMasuk = jsonObject.getInt("id");
            if (idPasienMasuk != idPasienAktif) {
                idPasienAktif = idPasienMasuk;
                System.out.println("\n[SISTEM] Menerima Data Pasien ID: " + idPasienAktif);
                
                // autofill patient informatinon di dashboard
                if (dashboard != null) {
                    dashboard.autoFillPatientData(idPasienAktif);
                }
            }

            // 2. Deteksi Alat (Jika tidak ada tulisan tipe_alat, anggap 1-lead)
            String tipeAlat = jsonObject.optString("tipe_alat", "1-lead");

            if (tipeAlat.equals("1-lead")) {
                // --- PARSING UNTUK ECG 3 CLICK ---
                JSONArray signalArray = jsonObject.getJSONArray("signals");
                for (int i = 0; i < signalArray.length(); i++) {
                    double val = signalArray.getDouble(i);
                    // Format output: "1-LEAD;voltase"
                    queueString.put("1-LEAD;" + val); 
                }

            } else if (tipeAlat.equals("12-lead")) {
                // --- PARSING UNTUK MEDLAB EG12000 ---
                JSONObject signalsObj = jsonObject.getJSONObject("signals");
                
                JSONArray leadI = signalsObj.getJSONArray("I");
                JSONArray leadII = signalsObj.getJSONArray("II");
                JSONArray leadIII = signalsObj.getJSONArray("III");
                JSONArray aVR = signalsObj.getJSONArray("aVR");
                JSONArray aVL = signalsObj.getJSONArray("aVL");
                JSONArray aVF = signalsObj.getJSONArray("aVF");
                JSONArray V1 = signalsObj.getJSONArray("V1");
                JSONArray V2 = signalsObj.getJSONArray("V2");
                JSONArray V3 = signalsObj.getJSONArray("V3");
                JSONArray V4 = signalsObj.getJSONArray("V4");
                JSONArray V5 = signalsObj.getJSONArray("V5");
                JSONArray V6 = signalsObj.getJSONArray("V6");

                int batchSize = leadI.length();
                for (int i = 0; i < batchSize; i++) {
                    // Format output: "12-LEAD;I;II;III;aVR;aVL;aVF;V1;V2;V3;V4;V5;V6"
                    String combined = "12-LEAD;" + 
                        leadI.getDouble(i) + ";" + leadII.getDouble(i) + ";" + leadIII.getDouble(i) + ";" +
                        aVR.getDouble(i) + ";" + aVL.getDouble(i) + ";" + aVF.getDouble(i) + ";" +
                        V1.getDouble(i) + ";" + V2.getDouble(i) + ";" + V3.getDouble(i) + ";" +
                        V4.getDouble(i) + ";" + V5.getDouble(i) + ";" + V6.getDouble(i);
                        
                    queueString.put(combined);
                }
            }
        } catch (Exception e) {
            System.err.println("Gagal parsing JSON! " + e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { }
}