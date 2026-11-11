
package hospitalclient;

import java.util.ArrayList;
import java.util.Scanner;

public class HospitalClient {

    private static final Scanner scanner = new Scanner(System.in);
    private static final ArrayList<Hospital> hospitals =
            HospitalRegistry.createHospitals();
    private static final ArrayList<EmergencyRequest> requests =
            new ArrayList<>();

    private static Hospital hospital = null;
    private static boolean connected = false;

    public static void main(String[] args) {

        int choice;

        do {
            receiveServerMessages();
            showMenu();
            choice = readNumber();

            if (choice == 1) {
                selectHospital();
            } else if (choice == 2) {
                if (checkHospital()) hospital.displayHospitalInfo();
            } else if (choice == 3) {
                updateBeds(false);
            } else if (choice == 4) {
                updateBeds(true);
            } else if (choice == 5) {
                checkEmergencyFacility();
            } else if (choice == 6) {
                showRequests();
            } else if (choice == 7) {
                decideRequest(true);
            } else if (choice == 8) {
                decideRequest(false);
            } else if (choice == 9) {
                updateHospitalStatus();
            } else if (choice == 10) {
                System.out.println("Exiting Hospital Client.");
            } else {
                System.out.println("Invalid choice.");
            }

        } while (choice != 10);

        HospitalNetwork.close();
        scanner.close();
    }

    private static void showMenu() {
        System.out.println("\n===== HOSPITAL CLIENT =====");
        System.out.println("Selected Hospital: " +
                (hospital == null ? "None" : hospital.getHospitalName()));
        System.out.println("1. Select and Register Hospital");
        System.out.println("2. View Hospital Information");
        System.out.println("3. Update GENERAL Beds");
        System.out.println("4. Update ICU Beds");
        System.out.println("5. Check Emergency Facility");
        System.out.println("6. View Emergency Requests");
        System.out.println("7. Accept Emergency Request");
        System.out.println("8. Reject Emergency Request");
        System.out.println("9. Update Hospital Status");
        System.out.println("10. Exit");
        System.out.print("Enter your choice: ");
    }

    private static int readNumber() {
        String text = scanner.nextLine().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean checkHospital() {
        if (hospital == null) {
            System.out.println("Select a hospital using Option 1 first.");
            return false;
        }
        return true;
    }

    private static void selectHospital() {
        if (connected) {
            System.out.println(
                    "A hospital is already registered on this connection.");
            System.out.println(
                    "Restart the client to select another hospital.");
            return;
        }

        HospitalRegistry.displayHospitals(hospitals);
        System.out.print("Select hospital number: ");
        int number = readNumber();

        if (number < 1 || number > hospitals.size()) {
            System.out.println("Invalid hospital number.");
            return;
        }

        hospital = hospitals.get(number - 1);
        System.out.println("Selected: " + hospital.getHospitalName());

        connected = HospitalNetwork.connect(hospital);

        if (connected) {
            HospitalNetwork.sendAvailability(hospital);
            System.out.println("Hospital connected. Bed updates sent.");
            System.out.println(
                    "Keep this client running to receive emergencies.");
        } else {
            System.out.println(
                    "Could not connect. Check that the server is running.");
        }
    }

    private static void updateBeds(boolean icu) {
        if (!checkHospital()) return;

        System.out.print(icu
                ? "Enter available ICU beds: "
                : "Enter available GENERAL beds: ");

        int beds = readNumber();

        if (beds < 0) {
            System.out.println("Enter a valid non-negative number.");
            return;
        }

        if (icu) {
            hospital.updateIcuBeds(beds);
        } else {
            hospital.updateGeneralBeds(beds);
        }

        if (connected) {
            String type = icu ? "ICU" : "GENERAL";
            HospitalNetwork.sendMessage(
                    "BED_UPDATE|" + hospital.getHospitalId()
                    + "|" + type + "|" + beds);
            System.out.println("Bed update sent to server.");
        } else {
            System.out.println(
                    "Beds updated locally. Server is not connected.");
        }
    }

    private static void checkEmergencyFacility() {
        if (!checkHospital()) return;

        System.out.println("1. CARDIAC");
        System.out.println("2. TRAUMA");
        System.out.println("3. STROKE");
        System.out.print("Select emergency type: ");

        int type = readNumber();

        if (type < 1 || type > 3) {
            System.out.println("Invalid emergency type.");
            return;
        }

        EmergencyType emergencyType =
                EmergencyType.values()[type - 1];

        System.out.println(
                hospital.supportsEmergency(emergencyType)
                ? "Hospital supports this emergency."
                : "Hospital does not support this emergency.");
    }

    private static void receiveServerMessages() {
        String message;

        while ((message = HospitalNetwork.getNextMessage()) != null) {

            if (message.startsWith("EMERGENCY_OFFER|")) {
                String[] parts = message.split("\\|");

                if (parts.length != 6) {
                    System.out.println("Invalid emergency offer: " + message);
                    continue;
                }

                try {
                    EmergencyType type =
                            EmergencyType.valueOf(parts[3]);

                    if (findRequest(parts[1]) == null) {
                        requests.add(new EmergencyRequest(
                                parts[1], parts[2], type));
                    }

                    System.out.println(
                            "\nEmergency offer received: " + parts[1]);
                    System.out.println("Ambulance: " + parts[2]);
                    System.out.println("Type: " + parts[3]);
                    System.out.println("Priority: " + parts[4]);
                    System.out.println("Required bed: " + parts[5]);
                    System.out.println(
                            "Use Option 7 to accept or 8 to reject.");

                } catch (IllegalArgumentException e) {
                    System.out.println(
                            "Unknown emergency type in offer.");
                }

            } else if (message.startsWith("OFFER_EXPIRED|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 2) {
                    EmergencyRequest request = findRequest(parts[1]);
                    if (request != null &&
                            request.getStatus() == RequestStatus.PENDING) {
                        request.rejectRequest();
                    }
                }
                System.out.println("Server: " + message);

            } else {
                System.out.println("Server: " + message);
            }
        }
    }

    private static void showRequests() {
        receiveServerMessages();

        if (requests.isEmpty()) {
            System.out.println("No emergency offers received yet.");
            System.out.println(
                    "Ask the ambulance client to send an emergency.");
            return;
        }

        for (EmergencyRequest request : requests) {
            request.displayRequest();
        }
    }

    private static EmergencyRequest findRequest(String id) {
        for (EmergencyRequest request : requests) {
            if (request.getRequestId().equalsIgnoreCase(id)) {
                return request;
            }
        }
        return null;
    }

    private static void decideRequest(boolean accept) {
        if (!checkHospital()) return;

        if (!connected) {
            System.out.println("Not connected to server.");
            return;
        }

        receiveServerMessages();

        System.out.print("Enter request ID: ");
        String requestId = scanner.nextLine().trim();

        EmergencyRequest request = findRequest(requestId);

        if (request == null) {
            System.out.println("Request not found.");
            return;
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            System.out.println("Request is no longer pending.");
            return;
        }

        if (accept) {
            if (!hospital.supportsEmergency(
                    request.getEmergencyType())) {
                System.out.println(
                        "Hospital does not support this emergency.");
                return;
            }

            if (hospital.getStatus() == HospitalStatus.UNAVAILABLE
                    || !hospital.hasAvailableBeds()) {
                System.out.println(
                        "Hospital is unavailable or has no beds.");
                return;
            }

            System.out.print("Confirm acceptance (yes/no): ");
            String confirmation = scanner.nextLine().trim();

            if (!confirmation.equalsIgnoreCase("yes")) {
                System.out.println("Acceptance cancelled.");
                return;
            }
        }

        HospitalNetwork.sendRequestDecision(
                hospital.getHospitalId(),
                request.getRequestId(),
                accept ? RequestStatus.ACCEPTED
                       : RequestStatus.REJECTED);

        System.out.println("Decision sent. Waiting for server response.");
        System.out.println(
                "Use Option 6 to check for server messages.");

        // The server decides whether an offer is still valid.
        // Do not mark the request accepted before server confirmation.
    }

    private static void updateHospitalStatus() {
        if (!checkHospital()) return;

        System.out.println("1. AVAILABLE");
        System.out.println("2. LIMITED");
        System.out.println("3. UNAVAILABLE");
        System.out.print("Select status: ");

        int status = readNumber();

        if (status < 1 || status > 3) {
            System.out.println("Invalid status.");
            return;
        }

        hospital.updateStatus(HospitalStatus.values()[status - 1]);
        System.out.println("Hospital status updated locally.");
        System.out.println(
                "The current server protocol does not send status updates.");
    }
}
