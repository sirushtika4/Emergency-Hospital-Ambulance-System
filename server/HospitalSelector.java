package server;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Choose the hospital with most matching free beds; break ties by hospital ID. */
public class HospitalSelector {
    public static class Selection {
        public final String hospitalId;
        public final ClientHandler handler;

        Selection(String hospitalId, ClientHandler handler) {
            this.hospitalId = hospitalId;
            this.handler = handler;
        }
    }

    private final ClientRegistry registry;
    public HospitalSelector(ClientRegistry registry) { this.registry = registry; }

    public Selection select(String bedType, Set<String> excluded) {
        synchronized (registry) {
            Selection best = null;
            int mostBeds = 0;
            for (Map.Entry<String, ClientHandler> entry : new TreeMap<>(registry.getHospitals()).entrySet()) {
                if (excluded.contains(entry.getKey())) continue;
                int free = registry.getBedAvailability(entry.getKey()).getOrDefault(bedType, 0);
                if (free > mostBeds) {
                    best = new Selection(entry.getKey(), entry.getValue());
                    mostBeds = free;
                }
            }
            return best;
        }
    }
}
