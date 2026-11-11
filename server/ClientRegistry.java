package server;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    public enum Role { AMBULANCE, HOSPITAL }
    private static class Registration {
        final ClientHandler handler;
        final Role role;
        final Map<String, Integer> beds = new ConcurrentHashMap<>();
        final Map<String, Integer> reserved = new HashMap<>();
        Registration(ClientHandler handler, Role role) { this.handler = handler; this.role = role; }
    }
    private final ConcurrentHashMap<String, Registration> clients = new ConcurrentHashMap<>();
    public synchronized boolean register(String id, Role role, ClientHandler handler) {
        if (id == null || id.trim().isEmpty() || role == null || handler == null)
            throw new IllegalArgumentException("Missing registration fields");
        for (Registration r : clients.values()) if (r.handler == handler) return false;
        return clients.putIfAbsent(id, new Registration(handler, role)) == null;
    }
    public ClientHandler getClient(String id) {
        Registration r = clients.get(id); return r == null ? null : r.handler;
    }
    public Role getRole(String id) {
        Registration r = clients.get(id); return r == null ? null : r.role;
    }
    public int count() { return clients.size(); }
    public synchronized Map<String, ClientHandler> getHospitals() {
        Map<String, ClientHandler> result = new HashMap<>();
        clients.forEach((id, r) -> { if (r.role == Role.HOSPITAL) result.put(id, r.handler); });
        return result;
    }
    public synchronized void remove(String id, ClientHandler handler) {
        Registration r = clients.get(id);
        if (r != null && r.handler == handler) clients.remove(id, r);
    }
    public synchronized boolean updateBeds(String id, ClientHandler handler, String type, int count) {
        if (count < 0 || type == null || type.trim().isEmpty())
            throw new IllegalArgumentException("Invalid bed availability");
        Registration r = clients.get(id);
        if (r == null || r.handler != handler || r.role != Role.HOSPITAL) return false;
        // BED_UPDATE reports capacity including this server's outstanding holds.
        if (count < r.reserved.getOrDefault(type, 0)) return false;
        r.beds.put(type, count);
        return true;
    }
    public synchronized Map<String, Integer> getBedAvailability(String id) {
        Registration r = clients.get(id);
        Map<String, Integer> available = new HashMap<>();
        if (r != null) r.beds.forEach((type, count) ->
                available.put(type, count - r.reserved.getOrDefault(type, 0)));
        return available;
    }

    synchronized boolean reserveBed(String id, ClientHandler owner, String type) {
        Registration r = clients.get(id);
        if (r == null || r.handler != owner || r.role != Role.HOSPITAL) return false;
        int held = r.reserved.getOrDefault(type, 0);
        if (r.beds.getOrDefault(type, 0) <= held) return false;
        r.reserved.put(type, held + 1);
        return true;
    }

    synchronized void releaseBed(String id, ClientHandler owner, String type) {
        Registration r = clients.get(id);
        // A reservation from an old socket must never affect a new registration.
        if (r != null && r.handler == owner) {
            int held = r.reserved.getOrDefault(type, 0);
            if (held > 0) r.reserved.put(type, held - 1);
        }
    }
}
