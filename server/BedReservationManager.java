package server;

import java.util.HashMap;
import java.util.Map;

/** All reservation and inventory changes share the registry monitor. */
public class BedReservationManager {
    private static class Reservation {
        final String hospitalId;
        final ClientHandler hospital;
        final String bedType;

        Reservation(String hospitalId, ClientHandler hospital, String bedType) {
            this.hospitalId = hospitalId;
            this.hospital = hospital;
            this.bedType = bedType;
        }
    }

    private final ClientRegistry registry;
    private final Map<String, Reservation> reservations = new HashMap<>();

    public BedReservationManager(ClientRegistry registry) { this.registry = registry; }

    public boolean reserve(String requestId, String hospitalId, ClientHandler hospital, String bedType) {
        synchronized (registry) {
            if (reservations.containsKey(requestId)) return false;
            if (!registry.reserveBed(hospitalId, hospital, bedType)) return false;
            reservations.put(requestId, new Reservation(hospitalId, hospital, bedType));
            return true;
        }
    }

    /** Idempotent; cannot restore a bed twice or into a replacement registration. */
    public boolean release(String requestId) {
        synchronized (registry) {
            Reservation r = reservations.remove(requestId);
            if (r == null) return false;
            registry.releaseBed(r.hospitalId, r.hospital, r.bedType);
            return true;
        }
    }

    public int count() {
        synchronized (registry) { return reservations.size(); }
    }
}
