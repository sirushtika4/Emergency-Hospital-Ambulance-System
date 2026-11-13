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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] TYPES = {"CARDIAC", "ACCIDENT", "BURN", "STROKE", "MATERNITY", "OTHER"};
    private static final String[] PRIORITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
    private static final String[] BED_TYPES = {"ICU", "GENERAL", "EMERGENCY", "MATERNITY"};

    // History rows: {time, type, priority/bed, status}
    private static final List<String[]> history = new CopyOnWriteArrayList<>();
    private static volatile boolean waiting = false;
    private static volatile boolean connected = true;
    private static volatile boolean registered = false;

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Server IP (press Enter for localhost): ");
        String host = sc.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        // ---------- Connect with timeout ----------
        Socket socket = new Socket();
        try {
            System.out.println("Connecting to " + host + ":" + PORT + " ...");
            socket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
        } catch (SocketTimeoutException e) {
            System.out.println("Timed out. Check the IP, firewall (port 5000) and that both PCs are on the same network.");
            return;
        } catch (IOException e) {
            System.out.println("Cannot connect to " + host + ":" + PORT + ". Is EmergencyServer running?");
            return;
        }

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            // ---------- Listener thread: receives ALL server messages ----------
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        handleServerMessage(line);
                    }
                } catch (IOException e) {
                    // socket closed
                }
                connected = false;
                System.out.println("\n[" + now() + "] Disconnected from server.");
            });
            listener.setDaemon(true);
            listener.start();

            // ---------- Register ----------
            Thread.sleep(500);   // let the server's welcome message print first
            String id;
            while (true) {
                System.out.print("Enter Ambulance ID (e.g. AMB001): ");
                id = sc.nextLine().trim().toUpperCase();
                if (!id.isEmpty() && !id.contains("|") && !id.contains(" ")) break;
                System.out.println("ID cannot be empty or contain spaces or '|'.");
            }
            out.println("AMBULANCE_REGISTER|" + id);

            // Wait up to 3 seconds for AMBULANCE_REGISTERED
            for (int i = 0; i < 30 && !registered && connected; i++) Thread.sleep(100);
            if (!registered) {
                System.out.println("Registration not confirmed by server. Check the server console.");
            }

            // ---------- Menu ----------
            while (connected) {
                Thread.sleep(300);
                System.out.println("\n========= AMBULANCE " + id + " =========");
                System.out.println("Status: " + (waiting ? "WAITING FOR HOSPITAL ASSIGNMENT..." : "READY"));
                System.out.println("1. Send Emergency Request");
                System.out.println("2. View Request History");
                System.out.println("3. Exit");
                System.out.print("Choose: ");
                String choice = sc.nextLine().trim();

                switch (choice) {
                    case "1":
                        if (waiting) {
                            System.out.println("You already have an open emergency. Wait for the assignment.");
                            break;
                        }
                        String type = pick(sc, "Emergency type", TYPES);
                        String priority = pick(sc, "Priority", PRIORITIES);
                        String bedType = pick(sc, "Bed type needed", BED_TYPES);

                        history.add(new String[]{now(), type, priority + "/" + bedType, "WAITING"});
                        waiting = true;
                        out.println("EMERGENCY_REQUEST|" + id + "|" + type + "|" + priority + "|" + bedType);
                        System.out.println("[" + now() + "] Emergency request sent: " + type
                                + " (" + priority + ", needs " + bedType + ")");
                        System.out.println("Waiting for hospital assignment... (press Enter to refresh menu)");
                        break;

                    case "2":
                        if (history.isEmpty()) {
                            System.out.println("No requests yet.");
                        } else {
                            System.out.printf("%-4s %-9s %-10s %-16s %s%n", "#", "Time", "Type", "Priority/Bed", "Status");
                            for (int i = 0; i < history.size(); i++) {
                                String[] h = history.get(i);
                                System.out.printf("%-4d %-9s %-10s %-16s %s%n", i + 1, h[0], h[1], h[2], h[3]);
                            }
                        }
                        break;

                    case "3":
                        if (waiting) {
                            System.out.print("An emergency is still waiting. Exit anyway? (Y/N): ");
                            if (!sc.nextLine().trim().equalsIgnoreCase("Y")) break;
                        }
                        System.out.println("Disconnecting...");
                        return;   // try-with-resources closes the socket

                    default:
                        if (!choice.isEmpty()) System.out.println("Invalid choice.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Handles every message from the server (listener thread) ----------
    private static void handleServerMessage(String line) {
        String[] p = line.split("\\|");
        String cmd = p[0].trim().toUpperCase();
        String rest = line.contains("|") ? line.substring(line.indexOf('|') + 1) : "";

        switch (cmd) {
            case "CONNECTED":
                System.out.println("[" + now() + "] Connected: " + rest);
                break;

            case "AMBULANCE_REGISTERED":
                registered = true;
                System.out.println("[" + now() + "] Registered as " + rest);
                break;

            case "EMERGENCY_REQUEST_RECEIVED":
                System.out.println("[" + now() + "] Server received request"
                        + (rest.isEmpty() ? "" : " (" + rest + ")") + ", finding a hospital...");
                break;

            case "HOSPITAL_ASSIGNED":
            case "ASSIGNED":
                String hospital = p[p.length - 1];
                updateLastStatus("ASSIGNED -> " + hospital);
                waiting = false;
                System.out.println("\n[" + now() + "] *** HOSPITAL ASSIGNED: " + hospital + " ***  Proceed there.");
                System.out.println("    Details: " + rest);
                break;

            case "EMERGENCY_CANCELLED":
                updateLastStatus("CANCELLED");
                waiting = false;
                System.out.println("\n[" + now() + "] Emergency cancelled: " + rest);
                break;

            case "EMERGENCY_FAILED":
            case "NO_HOSPITAL_AVAILABLE":
                updateLastStatus("FAILED");
                waiting = false;
                String reqId = p.length > 1 ? p[1] : "";
                String reason = p.length > 2 ? p[2].replace("_", " ") : "no suitable hospital";
                System.out.println("\n[" + now() + "] !!! Request " + reqId + " failed: " + reason + " !!!");
                System.out.println("    Make sure a hospital is connected and has free beds of that type.");
                break;

            case "ERROR":
                if (waiting) updateLastStatus("ERROR");
                waiting = false;
                System.out.println("\n[" + now() + "] Server error: " + rest);
                break;

            default:
                System.out.println("[" + now() + "] Server: " + line);
        }
    }

    private static void updateLastStatus(String status) {
        if (!history.isEmpty()) history.get(history.size() - 1)[3] = status;
    }

    // Numbered menu so the user can't mistype values
    private static String pick(Scanner sc, String label, String[] options) {
        while (true) {
            System.out.println(label + ":");
            for (int i = 0; i < options.length; i++) System.out.println("  " + (i + 1) + ". " + options[i]);
            System.out.print("Select: ");
            try {
                int n = Integer.parseInt(sc.nextLine().trim());
                if (n >= 1 && n <= options.length) return options[n - 1];
            } catch (NumberFormatException ignored) { }
            System.out.println("Invalid selection.");
        }
    }
}

