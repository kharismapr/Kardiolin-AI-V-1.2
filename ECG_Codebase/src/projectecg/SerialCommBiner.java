package projectecg;

import com.fazecast.jSerialComm.SerialPort;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a jSerialComm serial port and feeds raw bytes into a BlockingQueue
 * for downstream consumption by {@link ThrdParsingECG}.
 */
public class SerialCommBiner {

    private static final Logger logger = Logger.getLogger(SerialCommBiner.class.getName());

    // How long (ms) the polling thread sleeps when the buffer is empty.
    // Low value = lower latency; zero = busy-wait (wastes CPU).
    private static final int POLL_SLEEP_MS = 5;

    private final String portName;
    private final int baudRate;
    private final BlockingQueue<Byte> queueDataECGByte;

    private SerialPort comPort;

    // Reader thread state — volatile so the reader thread sees updates immediately
    private Thread readerThread;
    private volatile boolean running = false;

    public SerialCommBiner(String portName, int baudRate, BlockingQueue<Byte> queueDataECG) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.queueDataECGByte = queueDataECG;
    }

    public boolean openPort() {

        // Find a verified live handle via system enumeration ---
        comPort = null;
        System.out.println("[SerialCommBiner] Scanning for port: " + portName);
        for (SerialPort p : SerialPort.getCommPorts()) {
            System.out.println("  Found: " + p.getSystemPortName()
                    + " (" + p.getPortDescription() + ")");
            if (p.getSystemPortName().equalsIgnoreCase(portName)) {
                comPort = p;
                System.out.println("[SerialCommBiner] Matched port handle: "
                        + p.getSystemPortName());
                break;
            }
        }

        if (comPort == null) {
            System.err.println("[SerialCommBiner] Port not found in system list: " + portName
                    + "  — Check Device Manager and jComboBox selection.");
            return false;
        }

        // Open FIRST (no params yet)
        boolean opened = comPort.openPort();
        if (!opened) {
            // Print the native Windows error so we know exactly why it failed.
            System.err.println("[SerialCommBiner] openPort() FAILED for " + portName);
            System.err.println("  Native error code    : " + comPort.getLastErrorCode());
            System.err.println("  Native error location: " + comPort.getLastErrorLocation());
            System.err.println("  Hint: Is the port open in Arduino IDE / another app?");
            comPort = null;
            return false;
        }

        // Configure parameters AFTER open
        comPort.setBaudRate(baudRate);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        comPort.setParity(SerialPort.NO_PARITY);

        // TIMEOUT_READ_SEMI_BLOCKING: readBytes() returns when data arrives OR
        // the timeout (100 ms) fires — prevents the polling thread from blocking
        // forever while still giving it a chance to check the running flag.
        comPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                100,  // read timeout ms
                0     // write timeout ms (0 = blocking write)
        );

        System.out.println("[SerialCommBiner] Port opened and configured: "
                + portName + " @ " + baudRate + " baud");

        // --- Step 4: Start polling reader thread ---
        running = true;
        readerThread = new Thread(this::readerLoop, "ECG-SerialReader");
        readerThread.setDaemon(true); // dies automatically with the application
        readerThread.start();

        System.out.println("[SerialCommBiner] Polling reader thread started.");
        return true;
    }

    private void readerLoop() {
        System.out.println("[SerialReader] Thread running — waiting for data from " + portName);
        int totalBytesRead = 0;

        while (running && comPort != null && comPort.isOpen()) {
            try {
                int available = comPort.bytesAvailable();

                if (available > 0) {
                    byte[] buffer = new byte[available];
                    int bytesRead = comPort.readBytes(buffer, buffer.length);

                    if (bytesRead > 0) {
                        totalBytesRead += bytesRead;
                        System.out.println("[SerialReader] Read " + bytesRead
                                + " bytes  (total so far: " + totalBytesRead + ")");

                        for (int i = 0; i < bytesRead; i++) {
                            // BlockingQueue.put() blocks if the queue is full —
                            // this provides back-pressure so we never silently drop data.
                            queueDataECGByte.put(buffer[i]);
                        }
                    }
                } else if (available == 0) {
                    // Buffer is empty — sleep briefly to avoid busy-spinning
                    Thread.sleep(POLL_SLEEP_MS);
                } else {
                    // available < 0 means the port returned an error
                    System.err.println("[SerialReader] bytesAvailable() returned " + available
                            + " — port may be disconnected. Stopping reader.");
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[SerialReader] Thread interrupted — stopping.");
                break;
            } catch (Exception e) {
                if (running) {
                    logger.log(Level.WARNING,
                            "[SerialReader] Unexpected error in read loop: " + e.getMessage(), e);
                }
                break;
            }
        }

        System.out.println("[SerialReader] Thread stopped. Total bytes read: " + totalBytesRead);
    }

    // return true if the serial port is currently open
    public boolean isOpen() {
        return comPort != null && comPort.isOpen();
    }

    public synchronized void closePort() {
        running = false;

        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(2000); // wait up to 2 s for clean exit
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }

        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
            System.out.println("[SerialCommBiner] Port closed: " + portName);
        }
        comPort = null;
    }
}