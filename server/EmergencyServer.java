import java.io.*;
import java.net.*;

public class EmergencyServer {

    public static void main(String[] args) {
        int port = 5000;

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            System.out.println("====================================");
            System.out.println(" Emergency Coordination Server");
            System.out.println("====================================");
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for client connection...");

            Socket clientSocket = serverSocket.accept();

            System.out.println("Client connected!");
            System.out.println("Client IP: "
                    + clientSocket.getInetAddress().getHostAddress());

            BufferedReader input = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );

            PrintWriter output = new PrintWriter(
                    clientSocket.getOutputStream(),
                    true
            );

            String message = input.readLine();

            System.out.println("Message received: " + message);

            output.println("Server received your message successfully.");

            clientSocket.close();
            serverSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
