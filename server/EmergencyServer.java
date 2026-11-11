package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmergencyServer {

    private static final int PORT = 5000;
    private static final Logger LOG = Logger.getLogger(EmergencyServer.class.getName());

    public static void main(String[] args) {

        System.out.println("====================================");
        System.out.println(" Emergency Coordination Server");
        System.out.println("====================================");

        ClientRegistry registry = new ClientRegistry();
        try (EmergencyManager emergencies = new EmergencyManager(registry,
                     Long.getLong("emergency.offerTimeoutMillis", 30000L),
                     Long.getLong("emergency.assignmentTimeoutMillis", 300000L));
             ServerSocket serverSocket = new ServerSocket(PORT)) {
            MessageRouter router = new MessageRouter(registry, emergencies);

            LOG.info("Server started on port " + PORT);
            System.out.println("Waiting for clients...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                LOG.info("Client connected: " + clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort());

                ClientHandler handler =
                        new ClientHandler(clientSocket, registry, router);

                Thread thread =
                        new Thread(handler, "client-" + clientSocket.getRemoteSocketAddress());

                thread.start();
            }

        } catch (IOException e) {

            LOG.log(Level.SEVERE, "Server error", e);
        }
    }
}
