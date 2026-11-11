import java.io.*;
import java.net.*;
import java.util.Scanner;

public class AmbulanceClient {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Server: " + input.readLine());

            System.out.print("Enter Ambulance ID (e.g. AMB001): ");
            String ambulanceId = scanner.nextLine().trim();
            output.println("AMBULANCE_REGISTER|" + ambulanceId);
            System.out.println("Server: " + input.readLine());

            while (true) {
                System.out.println("\n===== AMBULANCE MENU =====");
                System.out.println("1. Send Emergency Request");
                System.out.println("2. Exit");
                System.out.print("Choose: ");
                String choice = scanner.nextLine().trim();

                if (choice.equals("1")) {
                    System.out.print("Emergency type (CARDIAC, ACCIDENT, BURN): ");
                    String type = scanner.nextLine().trim();
                    System.out.print("Priority (HIGH / MEDIUM / LOW): ");
                    String priority = scanner.nextLine().trim().toUpperCase();

                    output.println("EMERGENCY_REQUEST|" + ambulanceId + "|" + type + "|" + priority);
                    System.out.println("Server: " + input.readLine());
                } else if (choice.equals("2")) {
                    System.out.println("Disconnecting...");
                    break;
                } else {
                    System.out.println("Invalid choice.");
                }
            }
        } catch (ConnectException e) {
            System.out.println("Cannot connect. Is EmergencyServer running on port " + PORT + "?");
        } catch (IOException e) {
            e.printStackTrace();
        }
        scanner.close();
    }
}