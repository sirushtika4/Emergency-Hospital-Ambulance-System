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

            output.println("AMBULANCE: Emergency patient incoming.");

            String response = input.readLine();

            System.out.println("Server response: " + response);

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}