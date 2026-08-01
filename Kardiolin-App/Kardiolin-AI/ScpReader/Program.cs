using System;
using System.IO;
using System.Text;
using ECGConversion;

namespace ScpReader
{
    class Program
    {
        static void Main(string[] args)
        {
            // Syarat: ScpReader.exe <input_scp_path> <output_csv_path>
            if (args.Length < 2)
            {
                Console.WriteLine("ERROR: Parameter kurang! Format: ScpReader.exe <input.scp> <output.csv>");
                return;
            }

            string inputScp = args[0];
            string outputCsv = args[1];

            try
            {
                // BUKA FILE SCP
                IECGFormat format = ECGConverter.Instance.getFormat("SCP-ECG");
                int err = format.Read(inputScp);
                if (err != 0)
                {
                    Console.WriteLine("ERROR: Gagal membaca format file SCP.");
                    return;
                }

                // EKSTRAK DATA SINYAL 12 LEAD
                ECGConversion.ECGSignals.Signals signals;
                if (format.Signals.getSignals(out signals) != 0)
                {
                    Console.WriteLine("ERROR: Gagal mengekstrak sinyal dari file SCP.");
                    return;
                }

                int numLeads = signals.NrLeads;
                
                // Cari panjang sampel data (jumlah baris ke bawah)
                int maxSamples = 0;
                for (int i = 0; i < numLeads; i++)
                {
                    if (signals[i] != null && signals[i].Rhythm != null)
                    {
                        if (signals[i].Rhythm.Length > maxSamples)
                            maxSamples = signals[i].Rhythm.Length;
                    }
                }

                // CETAK ULANG MENJADI FILE CSV
                using (StreamWriter writer = new StreamWriter(outputCsv))
                {
                    // Tulis Header (Lead I, Lead II, ... V6)
                    StringBuilder header = new StringBuilder();
                    for (int i = 0; i < numLeads; i++)
                    {
                        header.Append(signals[i].Type.ToString());
                        if (i < numLeads - 1) header.Append(",");
                    }
                    writer.WriteLine(header.ToString());

                    // Tulis Angka-angkanya Baris demi Baris
                    double scaleFactor = 1000.0; // Harus sama dengan yang di ScpMaker

                    for (int s = 0; s < maxSamples; s++)
                    {
                        StringBuilder row = new StringBuilder();
                        for (int l = 0; l < numLeads; l++)
                        {
                            if (signals[l] != null && signals[l].Rhythm != null && s < signals[l].Rhythm.Length)
                            {
                                // Ambil angka Integer-nya, lalu BAGI dengan 1000.0 agar desimalnya kembali
                                double originalValue = signals[l].Rhythm[s] / scaleFactor;
                                
                                row.Append(originalValue.ToString(System.Globalization.CultureInfo.InvariantCulture));
                            }
                            else
                            {
                                row.Append("0.0"); 
                            }

                            if (l < numLeads - 1) row.Append(",");
                        }
                        writer.WriteLine(row.ToString());
                    }
                }

                // LAPORKAN KE JAVA BAHWA TUGAS SELESAI
                Console.WriteLine("SUCCESS");
            }
            catch (Exception ex)
            {
                Console.WriteLine("ERROR: " + ex.Message);
            }
        }
    }
}