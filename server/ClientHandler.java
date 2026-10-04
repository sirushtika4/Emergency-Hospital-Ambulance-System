package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try {

            input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()
                    )
            );

            output = new PrintWriter(
                    socket.getOutputStream(),
                    true
            );

            output.println(
                    "CONNECTED|Emergency Server"
            );

            String message;

            while ((message = input.readLine()) != null) {

                System.out.println(
                        "Received: "
                        + message
                );

                handleMessage(message);
            }

        } catch (IOException e) {

            System.out.println(
                    "Client disconnected."
            );

        } finally {

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void handleMessage(String message) {

        String[] parts =
                message.split("\\|");

        String command =
                parts[0];

        switch (command) {

            case "AMBULANCE_REGISTER":
                handleAmbulanceRegistration(parts);
                break;

            case "HOSPITAL_REGISTER":
                handleHospitalRegistration(parts);
                break;

            case "BED_UPDATE":
                handleBedUpdate(parts);
                break;

            case "EMERGENCY_REQUEST":
                handleEmergencyRequest(parts);
                break;

            default:
                output.println(
                        "ERROR|Unknown command"
                );
        }
    }

    private void handleAmbulanceRegistration(
            String[] parts) {

        if (parts.length < 2) {
            output.println(
                    "ERROR|Invalid ambulance registration"
            );
            return;
        }

        String ambulanceId =
                parts[1];

        System.out.println(
                "Ambulance registered: "
                + ambulanceId
        );

        output.println(
                "AMBULANCE_REGISTERED|"
                + ambulanceId
        );
    }

    private void handleHospitalRegistration(
            String[] parts) {

        if (parts.length < 2) {
            output.println(
                    "ERROR|Invalid hospital registration"
            );
            return;
        }

        String hospitalName =
                parts[1];

        System.out.println(
                "Hospital registered: "
                + hospitalName
        );

        output.println(
                "HOSPITAL_REGISTERED|"
                + hospitalName
        );
    }

    private void handleBedUpdate(
            String[] parts) {

        if (parts.length < 4) {
            output.println(
                    "ERROR|Invalid bed update"
            );
            return;
        }

        String hospitalName =
                parts[1];

        String bedType =
                parts[2];

        String availableBeds =
                parts[3];

        System.out.println(
                "Bed update -> "
                + hospitalName
                + " | "
                + bedType
                + " | "
                + availableBeds
        );

        output.println(
                "BED_UPDATE_RECEIVED"
        );
    }

    private void handleEmergencyRequest(
            String[] parts) {

        if (parts.length < 4) {
            output.println(
                    "ERROR|Invalid emergency request"
            );
            return;
        }

        String ambulanceId =
                parts[1];

        String emergencyType =
                parts[2];

        String priority =
                parts[3];

        System.out.println(
                "Emergency request -> "
                + ambulanceId
                + " | "
                + emergencyType
                + " | "
                + priority
        );

        output.println(
                "EMERGENCY_REQUEST_RECEIVED"
        );
    }
}