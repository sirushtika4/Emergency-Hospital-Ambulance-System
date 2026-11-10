import java.io.*;
import java.net.*;

public class AmbulanceClient {

    public static void main(String[] args) {

        String serverAddress = "localhost";
        int port = 5000;

        try {
            Socket socket = new Socket(serverAddress, port);

            System.out.println("Connected to Emergency Server.");

            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            PrintWriter output = new PrintWriter(
                    socket.getOutputStream(),
                    true
            );

            System.out.println("Server response: " + input.readLine());
            String id = args.length > 0 ? args[0] : "AMB001";
            output.println("AMBULANCE_REGISTER|" + id);
            String registration = input.readLine();
            System.out.println("Server response: " + registration);
            if (("AMBULANCE_REGISTERED|" + id).equals(registration)) {
                output.println("EMERGENCY_REQUEST|" + id + "|CARDIAC|CRITICAL");
                System.out.println("Server response: " + input.readLine());
            }

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
