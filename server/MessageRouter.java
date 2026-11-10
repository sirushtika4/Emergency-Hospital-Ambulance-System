package server;
import java.util.*;
import java.util.logging.Logger;

public class MessageRouter {
    private static final Logger LOG = Logger.getLogger(MessageRouter.class.getName());
    private final ClientRegistry registry;
    public MessageRouter(ClientRegistry registry) { this.registry = registry; }
    public void routeEmergency(ClientHandler sender, String id, String type, String priority) {
        Set<ClientHandler> recipients = Collections.newSetFromMap(new IdentityHashMap<>());
        recipients.addAll(registry.getHospitals().values());
        int delivered = 0;
        for (ClientHandler hospital : recipients)
            if (hospital.sendMessage("NEW_EMERGENCY|" + id + "|" + type + "|" + priority)) delivered++;
        LOG.info("Emergency forwarded to " + delivered + " hospital(s)");
        sender.sendMessage(delivered == 0 ? "ERROR|No hospitals available" : "EMERGENCY_REQUEST_RECEIVED");
    }
}
