using System;
using System.IO;
using System.Linq;
using System.Globalization;
using System.Collections.Generic;
using ECGConversion;
using ECGConversion.ECGDemographics;
using ECGConversion.ECGSignals;

namespace ScpMaker
{
    class Program
    {
        static void Main(string[] args)
        {
            // Syarat: ScpMaker.exe <file_csv> <sample_rate> <folder_tujuan> <id_pasien>
            if (args.Length < 4)
            {
                Console.WriteLine("GAGAL: Parameter dari Java kurang!");
                return;
            }

            string inputCsv = args[0];
            int sampleRate = int.Parse(args[1]);
            string outputDir = args[2];
            string patientId = args[3];

            if (!outputDir.EndsWith("\\")) outputDir += "\\";

            try
            {
                // 1. Baca seluruh baris dari file CSV
                var rawLines = File.ReadAllLines(inputCsv);
                var cleanLines = new List<string[]>();

                // 2. Filter Header (Buang baris yang isinya teks)
                foreach (var line in rawLines)
                {
                    var parts = line.Split(',');
                    // Cek elemen terakhir (Lead V6), apakah bisa dijadikan angka?
                    // Kalau gagal (misal isinya teks "V6"), lewati baris ini!
                    if (!double.TryParse(parts[parts.Length - 1], NumberStyles.Any, CultureInfo.InvariantCulture, out _))
                    {
                        continue; 
                    }
                    cleanLines.Add(parts);
                }

                int numSamples = cleanLines.Count;
                int numLeads = 12;

                double[][] leadsData = new double[numLeads][];
                for (int i = 0; i < numLeads; i++)
                {
                    leadsData[i] = new double[numSamples];
                }

                // 3. Ekstrak Angka 
                for (int s = 0; s < numSamples; s++)
                {
                    var parts = cleanLines[s];
                    
                    // Deteksi kalo ada string (header csv) di awal, maka data Lead_I dimulai dari index 1
                    int startIndex = (parts.Length >= 13) ? 1 : 0; 
                    
                    for (int l = 0; l < numLeads && (l + startIndex) < parts.Length; l++)
                    {
                        leadsData[l][s] = double.Parse(parts[l + startIndex], CultureInfo.InvariantCulture);
                    }
                }

                // 4. Kirim tempat 12 Lead ke fungsi perakit SCP
                CreateScpEcgFile(leadsData, sampleRate, outputDir, patientId);
                
                Console.WriteLine("SUCCESS");
            }
            catch (Exception ex)
            {
                Console.WriteLine("ERROR: " + ex.Message);
            }
        }

        // =========================================================================
        // FUNGSI PERAKIT 12-LEAD SCP
        // =========================================================================
        public static void CreateScpEcgFile(double[][] voltages, int sampleRate, string directory, string patientId)
        {
            var filePath = directory + patientId;
            IECGFormat format = ECGConverter.Instance.getFormat("SCP-ECG");
            
            if (format != null)
            {
                // Identitas pasien
                format.Demographics.Init();
                format.Demographics.PatientID = patientId;
                format.Demographics.LastName = "ECG-Patient";
                format.Demographics.TimeAcquisition = DateTime.Now;
                
                AcquiringDeviceID acqID = new AcquiringDeviceID(true);
                Communication.IO.Tools.BytesTool.writeString("FTUI-ESP32", acqID.ModelDescription, 0, acqID.ModelDescription.Length);
                format.Demographics.AcqMachineID = acqID;
                
                // Definisi standar 12 lead ECG
                var leadType = new LeadType[] { 
                    LeadType.I, LeadType.II, LeadType.III, 
                    LeadType.aVR, LeadType.aVL, LeadType.aVF, 
                    LeadType.V1, LeadType.V2, LeadType.V3, 
                    LeadType.V4, LeadType.V5, LeadType.V6 
                }; 
                
                Signals sigs = new Signals((byte)leadType.Length);
                sigs.RhythmAVM = 1; 
                sigs.RhythmSamplesPerSecond = sampleRate;

                // Memasukkan data tegangan ke masing-masing lead
                double scaleFactor = 1000.0; // Faktor pengali untuk menyelamatkan desimal
                
                for (int i = 0; i < sigs.NrLeads; i++)
                {
                    sigs[i] = new Signal();
                    sigs[i].Type = leadType[i];
                    
                    // Kalikan dengan 1000 seb diubah jadi Integer
                    sigs[i].Rhythm = voltages[i].Select(v => Convert.ToInt16(v * scaleFactor)).ToArray();
                    
                    sigs[i].RhythmStart = 0;
                    sigs[i].RhythmEnd = voltages[i].Length - 1;
                }
                
                if (format.Signals.setSignals(sigs) != 0) throw new Exception("Set Signals Failed!");
                
                // Simpan file
                var outputFile = filePath + ".scp";
                ECGWriter.Write(format, outputFile, true);
                
                if (ECGWriter.getLastError() != 0) throw new Exception(ECGWriter.getLastErrorMessage());
            }
        }
    }
}