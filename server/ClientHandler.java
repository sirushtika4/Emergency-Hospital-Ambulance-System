package server;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class ClientHandler implements Runnable {
    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final ClientRegistry registry;
    private final MessageRouter router;
    private final Object sendLock = new Object();
    private PrintWriter output;
    private String clientId;
    private ClientRegistry.Role role;

    public ClientHandler(Socket socket, ClientRegistry registry, MessageRouter router) {
        this.socket = socket; this.registry = registry; this.router = router;
    }
    @Override
    public void run() {
        try (Socket connection = socket;
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     connection.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                     connection.getOutputStream(), StandardCharsets.UTF_8), true)) {
            synchronized (sendLock) { output = writer; }
            sendMessage("CONNECTED|Emergency Server");
            String message;
            while ((message = input.readLine()) != null) handleMessage(message);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Socket error for " + socket.getRemoteSocketAddress(), e);
        } finally {
            if (clientId != null) registry.remove(clientId, this);
            synchronized (sendLock) { output = null; }
            LOG.info("Client disconnected: " + socket.getRemoteSocketAddress());
        }
    }
    /** Serializes complete lines from handler and routing threads. */
    public boolean sendMessage(String message) {
        synchronized (sendLock) {
            if (output == null || socket.isClosed()) return false;
            output.println(message);
            if (!output.checkError()) return true;
            LOG.warning("Socket write failed for " + socket.getRemoteSocketAddress());
            try { socket.close(); }
            catch (IOException e) { LOG.log(Level.WARNING, "Socket close failed", e); }
            return false;
        }
    }
    private void handleMessage(String message) {
        String[] parts = message.split("\\|", -1);
        String command = parts[0];
        int expected;
        switch (command) {
            case "AMBULANCE_REGISTER":
            case "HOSPITAL_REGISTER": expected = 2; break;
            case "BED_UPDATE":
            case "EMERGENCY_REQUEST": expected = 4; break;
            default: sendMessage("ERROR|Unknown command"); return;
        }
        if (parts.length != expected) { sendMessage("ERROR|Invalid message format"); return; }
        for (String field : parts) {
            if (!field.matches("[A-Za-z0-9_-]+")) {
                sendMessage("ERROR|Invalid or missing field"); return;
            }
        }
        if (command.endsWith("_REGISTER")) {
            register(parts[1], command.equals("AMBULANCE_REGISTER")
                    ? ClientRegistry.Role.AMBULANCE : ClientRegistry.Role.HOSPITAL);
            return;
        }
        ClientRegistry.Role required = command.equals("BED_UPDATE")
                ? ClientRegistry.Role.HOSPITAL : ClientRegistry.Role.AMBULANCE;
        if (role != required || !parts[1].equals(clientId)) {
            sendMessage("ERROR|Unauthorized role or sender ID"); return;
        }
        if (command.equals("BED_UPDATE")) {
            try {
                int count = Integer.parseInt(parts[3]);
                if (!parts[3].matches("[0-9]+") || count < 0) throw new NumberFormatException();
                if (!registry.updateBeds(clientId, this, parts[2], count)) {
                    sendMessage("ERROR|Hospital registration unavailable"); return;
                }
                LOG.info("Bed availability updated for " + clientId + ": " + parts[2] + "=" + count);
                sendMessage("BED_UPDATE_RECEIVED");
            } catch (NumberFormatException e) {
                sendMessage("ERROR|Bed count must be a non-negative integer");
            }
        } else {
            LOG.info("Emergency request received from " + clientId);
            router.routeEmergency(this, clientId, parts[2], parts[3]);
        }
    }
    private void register(String id, ClientRegistry.Role requestedRole) {
        // Publish the registration and its acknowledgement under the output lock:
        // a concurrent broadcast must not arrive before the registration response.
        synchronized (sendLock) {
        if (clientId != null || !registry.register(id, requestedRole, this)) {
            LOG.info("Duplicate or repeated registration rejected");
            sendMessage("ERROR|Duplicate ID or repeated registration"); return;
        }
        clientId = id; role = requestedRole;
        LOG.info(role + " registered: " + clientId);
        sendMessage(role + "_REGISTERED|" + clientId);
        }
    }
}
