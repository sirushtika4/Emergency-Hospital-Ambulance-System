package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class EmergencyServer {

    private static final int PORT = 5000;

    public static void main(String[] args) {

        System.out.println("====================================");
        System.out.println(" Emergency Coordination Server");
        System.out.println("====================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Server started on port " + PORT);
            System.out.println("Waiting for clients...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("------------------------------------");
                System.out.println("New client connected");
                System.out.println(
                        "Client IP: "
                        + clientSocket.getInetAddress().getHostAddress()
                );

                ClientHandler handler =
                        new ClientHandler(clientSocket);

                Thread thread =
                        new Thread(handler);

                thread.start();
            }

        } catch (IOException e) {

            System.out.println(
                    "Server error: "
                    + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}