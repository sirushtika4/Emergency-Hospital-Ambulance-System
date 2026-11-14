import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class AmbulanceClient {

    private static final int PORT = 5000;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] TYPES = {
            "CARDIAC", "ACCIDENT", "BURN",
            "STROKE", "MATERNITY", "OTHER"
    };

    private static final String[] PRIORITIES = {
            "CRITICAL", "HIGH", "MEDIUM", "LOW"
    };

    private static final String[] BED_TYPES = {
            "ICU", "GENERAL", "EMERGENCY", "MATERNITY"
    };

    // History: time, type, priority/bed, status
    private static final List<String[]> history =
            new CopyOnWriteArrayList<>();

    private static volatile boolean waiting = false;
    private static volatile boolean connected = false;
    private static volatile boolean registered = false;

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    public static void main(String[] args) {

        // Reset state for a new run.
        waiting = false;
        connected = false;
        registered = false;
        history.clear();

        Scanner sc = new Scanner(System.in);

        System.out.print(
                "Server IP (press Enter for localhost): ");

        String host = sc.nextLine().trim();

        if (host.isEmpty()) {
            host = "localhost";
        }

        Socket socket = new Socket();

        // Connect to server.
        try {
            System.out.println(
                    "Connecting to " + host + ":" + PORT + "...");

            socket.connect(
                    new InetSocketAddress(host, PORT),
                    CONNECT_TIMEOUT_MS
            );

            connected = true;

        } catch (SocketTimeoutException e) {
            System.out.println(
                    "Connection timed out. Check server IP and port.");
            return;

        } catch (IOException e) {
            System.out.println(
                    "Cannot connect. Make sure EmergencyServer is running.");
            return;
        }

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(
                     s.getOutputStream(), true)) {

            // Listen for server messages.
            Thread listener = new Thread(() -> {
                try {
                    String line;

                    while ((line = in.readLine()) != null) {
                        handleServerMessage(line);
                    }

                } catch (IOException e) {
                    // Socket closed or connection lost.
                } finally {
                    connected = false;
                    waiting = false;

                    System.out.println(
                            "\n[" + now()
                            + "] Disconnected from server.");
                }
            });

            listener.setDaemon(true);
            listener.start();

            // Allow welcome message to arrive.
            Thread.sleep(500);

            if (!connected) {
                System.out.println("Server disconnected.");
                return;
            }

            // Register ambulance.
            String id;

            while (true) {
                System.out.print(
                        "Enter Ambulance ID (e.g. AMB001): ");

                id = sc.nextLine().trim().toUpperCase();

                if (!id.isEmpty()
                        && !id.contains("|")
                        && !id.contains(" ")) {
                    break;
                }

                System.out.println(
                        "Invalid ID. Do not use spaces or '|'.");
            }

            out.println("AMBULANCE_REGISTER|" + id);

            if (out.checkError()) {
                System.out.println(
                        "Failed to send registration.");
                return;
            }

            // Wait for registration confirmation.
            for (int i = 0;
                 i < 30 && !registered && connected;
                 i++) {

                Thread.sleep(100);
            }

            if (!registered) {
                System.out.println(
                        "Registration failed or was not confirmed.");
                System.out.println(
                        "Check the Emergency Server console.");
                return;
            }

            // Main menu.
            while (connected) {

                System.out.println();
                System.out.println(
                        "========= AMBULANCE " + id + " =========");

                System.out.println(
                        "Status: " + (waiting
                                ? "WAITING FOR HOSPITAL ASSIGNMENT"
                                : "READY"));

                System.out.println("1. Send Emergency Request");
                System.out.println("2. View Request History");
                System.out.println("3. Exit");
                System.out.print("Choose: ");

                String choice = sc.nextLine().trim();

                if (!connected) {
                    System.out.println(
                            "Connection lost. Cannot continue.");
                    break;
                }

                switch (choice) {

                    case "1":

                        if (waiting) {
                            System.out.println(
                                    "An emergency request is already waiting.");
                            break;
                        }

                        String type = pick(
                                sc, "Emergency type", TYPES);

                        String priority = pick(
                                sc, "Priority", PRIORITIES);

                        String bedType = pick(
                                sc, "Bed type needed", BED_TYPES);

                        if (!connected) {
                            System.out.println(
                                    "Server disconnected.");
                            break;
                        }

                        String request =
                                "EMERGENCY_REQUEST|"
                                + id + "|"
                                + type + "|"
                                + priority + "|"
                                + bedType;

                        history.add(new String[]{
                                now(),
                                type,
                                priority + "/" + bedType,
                                "WAITING"
                        });

                        waiting = true;

                        out.println(request);

                        if (out.checkError()) {
                            updateLastStatus("SEND FAILED");
                            waiting = false;

                            System.out.println(
                                    "Failed to send emergency request.");
                            break;
                        }

                        System.out.println(
                                "\n[" + now()
                                + "] Emergency request sent.");

                        System.out.println(
                                "Type: " + type);

                        System.out.println(
                                "Priority: " + priority);

                        System.out.println(
                                "Bed needed: " + bedType);

                        System.out.println(
                                "Waiting for server response...");
                        break;

                    case "2":

                        if (history.isEmpty()) {
                            System.out.println(
                                    "No emergency requests yet.");
                        } else {

                            System.out.printf(
                                    "%-4s %-9s %-10s %-16s %s%n",
                                    "#", "Time", "Type",
                                    "Priority/Bed", "Status");

                            for (int i = 0;
                                 i < history.size();
                                 i++) {

                                String[] h = history.get(i);

                                System.out.printf(
                                        "%-4d %-9s %-10s %-16s %s%n",
                                        i + 1,
                                        h[0],
                                        h[1],
                                        h[2],
                                        h[3]);
                            }
                        }
                        break;

                    case "3":

                        if (waiting) {
                            System.out.print(  "Emergency still waiting. "  + "Exit anyway? (Y/N): ");

                            String answer = sc.nextLine().trim();

                            if (!answer.equalsIgnoreCase("Y")) {
                                break;
                            }
                        }

                        System.out.println("Disconnecting...");
                        return;

                    default:

                        if (!choice.isEmpty()) {
                            System.out.println( "Invalid menu option.");
                        }
                        break;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Program interrupted.");

        } catch (IOException e) {
            System.out.println(
                    "Network error: " + e.getMessage());

        } catch (Exception e) {
            System.out.println(
                    "Unexpected error: " + e.getMessage());
            e.printStackTrace();

        } finally {
            connected = false;
            waiting = false;

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // Handle messages received from server.
    private static void handleServerMessage(String line) {

        System.out.println(
                "\n[" + now() + "] SERVER MESSAGE: " + line);

        String[] p = line.split("\\|", -1);

        if (p.length == 0) {
            return;
        }

        String cmd = p[0].trim().toUpperCase();

        String rest = line.contains("|")
                ? line.substring(line.indexOf('|') + 1)
                : "";

        switch (cmd) {

            case "CONNECTED":

                System.out.println(
                        "Connected: " + rest);
                break;

            case "AMBULANCE_REGISTERED":

                registered = true;

                System.out.println(
                        "Ambulance registered: " + rest);
                break;

            case "EMERGENCY_REQUEST_RECEIVED":

                System.out.println(
                        "Server received emergency request.");

                if (!rest.isEmpty()) {
                    System.out.println(
                            "Request details: " + rest);
                }

                System.out.println(
                        "Waiting for a suitable hospital...");
                break;

            case "HOSPITAL_ASSIGNED":

                // Expected:
                // HOSPITAL_ASSIGNED|REQ000001|H001|ICU
                if (p.length < 3) {
                    System.out.println(
                            "Invalid assignment message: " + line);
                    break;
                }

                String hospitalId = p[2];
                String assignedRequestId = p.length > 1
                        ? p[1] : "";

                String assignedBed = p.length > 3
                        ? p[3] : "UNKNOWN";

                updateLastStatus(
                        "ASSIGNED -> " + hospitalId);

                waiting = false;

                System.out.println(
                        "\n*** HOSPITAL ASSIGNED ***");

                System.out.println(
                        "Request ID: " + assignedRequestId);

                System.out.println(
                        "Hospital ID: " + hospitalId);

                System.out.println(
                        "Bed Type: " + assignedBed);

                System.out.println(
                        "Emergency request ACCEPTED.");
                break;

            case "ASSIGNED":

                // Compatibility with older server responses.
                String legacyHospital = p.length > 1
                        ? p[p.length - 1] : "UNKNOWN";

                updateLastStatus(
                        "ASSIGNED -> " + legacyHospital);

                waiting = false;

                System.out.println(
                        "Hospital assigned: " + legacyHospital);
                break;

            case "EMERGENCY_CANCELLED":

                updateLastStatus("CANCELLED");
                waiting = false;

                System.out.println(
                        "Emergency cancelled: " + rest);
                break;

            case "EMERGENCY_FAILED":
            case "NO_HOSPITAL_AVAILABLE":

                updateLastStatus("FAILED");
                waiting = false;

                String failedRequestId = p.length > 1
                        ? p[1] : "UNKNOWN";

                String reason = p.length > 2
                        ? p[2].replace("_", " ")
                        : "No suitable hospital";

                System.out.println(
                        "\n*** EMERGENCY REQUEST FAILED ***");

                System.out.println(
                        "Request ID: " + failedRequestId);

                System.out.println(
                        "Reason: " + reason);

                System.out.println(
                        "Check hospital registration, "
                        + "bed availability, and server matching.");
                break;

            case "ERROR":

                if (waiting) {
                    updateLastStatus("ERROR");
                }

                waiting = false;

                System.out.println(
                        "Server error: " + rest);
                break;

            default:

                System.out.println(
                        "Unrecognized server response: " + line);
                break;
        }
    }

    private static void updateLastStatus(String status) {

        if (!history.isEmpty()) {
            history.get(history.size() - 1)[3] = status;
        }
    }

    // Display numbered selection menu.
    private static String pick(
            Scanner sc,
            String label,
            String[] options) {

        while (true) {

            System.out.println("\n" + label + ":");

            for (int i = 0; i < options.length; i++) {
                System.out.println("  " + (i + 1) + ". " + options[i]);
            }

            System.out.print("Select: ");

            try {
                int n = Integer.parseInt(
                        sc.nextLine().trim());

                if (n >= 1 && n <= options.length) {
                    return options[n - 1];
                }

            } catch (NumberFormatException ignored) {
            }

            System.out.println(
                    "Invalid selection. Try again.");
        }
    }
}