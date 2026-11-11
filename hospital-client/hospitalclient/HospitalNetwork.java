
package hospitalclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HospitalNetwork {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private static Socket socket;
    private static BufferedReader input;
    private static PrintWriter output;
    private static Thread listener;

    private static final BlockingQueue<String> messages =
            new LinkedBlockingQueue<>();

    public static synchronized boolean connect(Hospital hospital) {

        if (socket != null && !socket.isClosed()) {
            System.out.println("Already connected.");
            return true;
        }

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 3000);

            input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));

            output = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8),
                    true);

            String greeting = input.readLine();

            if (!"CONNECTED|Emergency Server".equals(greeting)) {
                System.out.println("Unexpected greeting: " + greeting);
                close();
                return false;
            }

            System.out.println("Connected to Emergency Server.");

            output.println("HOSPITAL_REGISTER|" + hospital.getHospitalId());

            String response = input.readLine();

            String expected =
                    "HOSPITAL_REGISTERED|" + hospital.getHospitalId();

            if (!expected.equals(response)) {
                System.out.println("Registration failed: " + response);
                close();
                return false;
            }

            System.out.println("Hospital registered successfully.");

            messages.clear();

            listener = new Thread(() -> {
                try {
                    String message;

                    while ((message = input.readLine()) != null) {
                        messages.offer(message);

                        if (message.startsWith("EMERGENCY_OFFER|")) {
                            System.out.println(
                                    "\nNEW EMERGENCY OFFER: " + message);
                        } else {
                            System.out.println(
                                    "\nServer message: " + message);
                        }
                    }
                } catch (IOException e) {
                    if (socket != null && !socket.isClosed()) {
                        System.out.println(
                                "\nConnection lost: " + e.getMessage());
                    }
                }
            });

            listener.setDaemon(true);
            listener.start();

            return true;

        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
            close();
            return false;
        }
    }

    public static synchronized void sendMessage(String message) {

        if (output == null || socket == null || socket.isClosed()) {
            System.out.println("Not connected to the real server.");
            return;
        }

        output.println(message);

        if (output.checkError()) {
            System.out.println("Failed to send message.");
        }
    }

    public static void sendAvailability(Hospital hospital) {

        sendMessage("BED_UPDATE|"
                + hospital.getHospitalId()
                + "|GENERAL|"
                + hospital.getGeneralBeds());

        sendMessage("BED_UPDATE|"
                + hospital.getHospitalId()
                + "|ICU|"
                + hospital.getIcuBeds());
    }

    public static void sendRequestDecision(
            String hospitalId,
            String requestId,
            RequestStatus decision) {

        if (decision == RequestStatus.ACCEPTED) {
            sendMessage("HOSPITAL_ACCEPT|"
                    + requestId + "|" + hospitalId);

        } else if (decision == RequestStatus.REJECTED) {
            sendMessage("HOSPITAL_REJECT|"
                    + requestId + "|" + hospitalId);
        }
    }

    public static String getNextMessage() {
        return messages.poll();
    }

    public static synchronized void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection.");
        }

        socket = null;
        input = null;
        output = null;
    }
}
