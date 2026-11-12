import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class AmbulanceClient {

    private static final int PORT = 5000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] TYPES = {"CARDIAC", "ACCIDENT", "BURN", "STROKE", "MATERNITY", "OTHER"};
    private static final String[] PRIORITIES = {"HIGH", "MEDIUM", "LOW"};

   
    private static final List<String[]> history = new CopyOnWriteArrayList<>();
    private static volatile boolean waiting = false;   
    private static volatile boolean connected = true;

    private static String now() {
        return LocalTime.now().format(TIME);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Server IP (press Enter for localhost): ");
        String host = sc.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        try (Socket socket = new Socket(host, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // ---------- Listener thread ----------
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
            String id;
            while (true) {
                System.out.print("Enter Ambulance ID (e.g. AMB001): ");
                id = sc.nextLine().trim().toUpperCase();
                if (!id.isEmpty() && !id.contains("|") && !id.contains(" ")) break;
                System.out.println("ID cannot be empty or contain spaces or '|'.");
            }
            out.println("AMBULANCE_REGISTER|" + id);

            // ---------- Menu ----------
            while (connected) {
                Thread.sleep(300);
                System.out.println("\n========= AMBULANCE " + id + " =========");
                System.out.println("Status: " + (waiting ? "WAITING FOR HOSPITAL..." : "READY"));
                System.out.println("1. Send Emergency Request");
                System.out.println("2. View Request History");
                System.out.println("3. Exit");
                System.out.print("Choose: ");
                String choice = sc.nextLine().trim();

                switch (choice) {
                    case "1":
                        if (waiting) {
                            System.out.println("You already have an open request. Wait for the reply.");
                            break;
                        }
                        String type = pick(sc, "Emergency type", TYPES);
                        String priority = pick(sc, "Priority", PRIORITIES);
                        history.add(new String[]{now(), type, priority, "WAITING"});
                        waiting = true;
                        out.println("EMERGENCY_REQUEST|" + id + "|" + type + "|" + priority);
                        System.out.println("[" + now() + "] Request sent: " + type + " (" + priority + ")");
                        break;

                    case "2":
                        if (history.isEmpty()) {
                            System.out.println("No requests yet.");
                        } else {
                            System.out.printf("%-4s %-9s %-10s %-8s %s%n", "#", "Time", "Type", "Priority", "Status");
                            for (int i = 0; i < history.size(); i++) {
                                String[] h = history.get(i);
                                System.out.printf("%-4d %-9s %-10s %-8s %s%n", i + 1, h[0], h[1], h[2], h[3]);
                            }
                        }
                        break;

                    case "3":
                        System.out.println("Disconnecting...");
                        return;

                    default:
                        // empty Enter just refreshes the menu
                        if (!choice.isEmpty()) System.out.println("Invalid choice.");
                }
            }

        } catch (ConnectException e) {
            System.out.println("Cannot connect to " + host + ":" + PORT + ". Is EmergencyServer running?");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Runs on the listener thread
    private static void handleServerMessage(String line) {
        String[] p = line.split("\\|");
        switch (p[0]) {
            case "ASSIGNED":
                updateLastStatus("ASSIGNED -> " + p[1]);
                waiting = false;
                System.out.println("\n[" + now() + "] *** HOSPITAL ASSIGNED: " + p[1] + " ***  Proceed there.");
                break;
            case "NO_HOSPITAL_AVAILABLE":
                updateLastStatus("NO HOSPITAL");
                waiting = false;
                System.out.println("\n[" + now() + "] !!! No hospital available. Try again shortly. !!!");
                break;
            case "AMBULANCE_REGISTERED":
                System.out.println("[" + now() + "] Registered as " + p[1]);
                break;
            case "EMERGENCY_REQUEST_RECEIVED":
                System.out.println("[" + now() + "] Server received request, finding a hospital...");
                break;
            case "ERROR":
                waiting = false;
                System.out.println("[" + now() + "] Server error: " + (p.length > 1 ? p[1] : ""));
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