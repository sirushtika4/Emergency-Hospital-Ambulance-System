package server;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Serializes workflow transitions; individual handlers serialize socket output. */
public class EmergencyManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(EmergencyManager.class.getName());
    private final ClientRegistry registry;
    private final HospitalSelector selector;
    private final BedReservationManager reservations;
    private final Map<String, EmergencyRequest> requests = new HashMap<>();
    private final ScheduledExecutorService timer;
    private final long offerTimeoutNanos;
    private final long assignmentTimeoutNanos;
    private long nextId = 1;

    public EmergencyManager(ClientRegistry registry) { this(registry, 30000, 300000); }

    public EmergencyManager(ClientRegistry registry, long offerTimeoutMillis, long assignmentTimeoutMillis) {
        if (offerTimeoutMillis <= 0 || assignmentTimeoutMillis <= 0)
            throw new IllegalArgumentException("Timeouts must be positive");
        this.registry = registry;
        selector = new HospitalSelector(registry);
        reservations = new BedReservationManager(registry);
        offerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(offerTimeoutMillis);
        assignmentTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(assignmentTimeoutMillis);
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "emergency-timeouts");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(() -> {
            try { expireRequests(); }
            catch (RuntimeException e) { LOG.log(Level.SEVERE, "Timeout processing failed", e); }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    public synchronized void submit(ClientHandler sender, String ambulanceId, String type,
                                    String priority, String bedType) {
        if (!isOwner(ambulanceId, sender, ClientRegistry.Role.AMBULANCE)) {
            sender.sendMessage("ERROR|Unauthorized ambulance");
            return;
        }
        String id = String.format(Locale.ROOT, "REQ%06d", nextId++);
        EmergencyRequest request = new EmergencyRequest(id, ambulanceId, type, priority, bedType, sender);
        requests.put(id, request);
        if (!sender.sendMessage("EMERGENCY_REQUEST_RECEIVED|" + id)) {
            finish(request, EmergencyRequest.Status.CANCELLED, "AMBULANCE_DISCONNECTED");
            return;
        }
        offerNext(request, "NO_SUITABLE_HOSPITAL");
    }

    private boolean isOwner(String id, ClientHandler handler, ClientRegistry.Role role) {
        return registry.getClient(id) == handler && registry.getRole(id) == role;
    }

    private void offerNext(EmergencyRequest request, String exhaustedReason) {
        HospitalSelector.Selection selection;
        while ((selection = selector.select(request.requiredBedType, request.attemptedHospitals)) != null) {
            request.selectedHospital = selection.hospitalId;
            request.hospital = selection.handler;
            request.attemptedHospitals.add(selection.hospitalId);
            request.status = EmergencyRequest.Status.OFFERED;
            request.deadlineNanos = System.nanoTime() + offerTimeoutNanos;
            if (selection.handler.sendMessage("EMERGENCY_OFFER|" + request.requestId + "|"
                    + request.ambulanceId + "|" + request.emergencyType + "|" + request.priority
                    + "|" + request.requiredBedType)) {
                LOG.info("Offer " + request.requestId + " sent to " + selection.hospitalId);
                return;
            }
        }
        finish(request, exhaustedReason.equals("OFFER_TIMEOUT")
                ? EmergencyRequest.Status.TIMED_OUT : EmergencyRequest.Status.FAILED, exhaustedReason);
    }

    public synchronized void respond(ClientHandler sender, String requestId, String hospitalId, boolean accept) {
        EmergencyRequest request = requests.get(requestId);
        if (request == null || !isOwner(hospitalId, sender, ClientRegistry.Role.HOSPITAL)
                || request.hospital != sender || !hospitalId.equals(request.selectedHospital)
                || request.status != EmergencyRequest.Status.OFFERED) {
            sender.sendMessage("ERROR|Invalid or stale hospital response");
            return;
        }
        if (System.nanoTime() - request.deadlineNanos >= 0) {
            sender.sendMessage("ERROR|Offer expired");
            offerNext(request, "OFFER_TIMEOUT");
            return;
        }
        if (!accept) {
            sender.sendMessage("HOSPITAL_REJECTED|" + requestId);
            offerNext(request, "ALL_HOSPITALS_REJECTED");
            return;
        }
        if (!reservations.reserve(requestId, hospitalId, sender, request.requiredBedType)) {
            sender.sendMessage("ERROR|Bed no longer available");
            offerNext(request, "NO_BEDS_AVAILABLE");
            return;
        }
        request.status = EmergencyRequest.Status.ASSIGNED;
        request.deadlineNanos = System.nanoTime() + assignmentTimeoutNanos;
        String assigned = "HOSPITAL_ASSIGNED|" + requestId + "|" + hospitalId + "|" + request.requiredBedType;
        // If either delivery fails, undo the hold and notify the remaining peer.
        if (!sender.sendMessage(assigned) || !request.ambulance.sendMessage(assigned)) {
            finish(request, EmergencyRequest.Status.CANCELLED, "ASSIGNMENT_DELIVERY_FAILED");
            return;
        }
        LOG.info("Assigned " + requestId + " to " + hospitalId);
    }

    public synchronized void ambulanceCommand(ClientHandler sender, String command, String requestId, String id) {
        EmergencyRequest request = requests.get(requestId);
        if (request == null || request.ambulance != sender || !request.ambulanceId.equals(id)
                || !isOwner(id, sender, ClientRegistry.Role.AMBULANCE)) {
            sender.sendMessage("ERROR|Unknown request or unauthorized owner");
            return;
        }
        if (command.equals("EMERGENCY_STATUS")) {
            sender.sendMessage("REQUEST_STATUS|" + requestId + "|" + request.status + "|"
                    + (request.selectedHospital == null ? "NONE" : request.selectedHospital));
        } else if (!request.active() || (command.equals("EMERGENCY_COMPLETE")
                && request.status != EmergencyRequest.Status.ASSIGNED)) {
            sender.sendMessage("ERROR|Request is not in the required state");
        } else {
            finish(request, command.equals("EMERGENCY_COMPLETE") ? EmergencyRequest.Status.COMPLETED
                    : EmergencyRequest.Status.CANCELLED, "AMBULANCE_REQUEST");
        }
    }

    public synchronized void disconnected(ClientHandler handler) {
        for (EmergencyRequest request : requests.values()) {
            if (!request.active()) continue;
            if (request.ambulance == handler) {
                finish(request, EmergencyRequest.Status.CANCELLED, "AMBULANCE_DISCONNECTED");
            } else if (request.hospital == handler) {
                if (request.status == EmergencyRequest.Status.OFFERED) offerNext(request, "HOSPITAL_DISCONNECTED");
                else finish(request, EmergencyRequest.Status.FAILED, "HOSPITAL_DISCONNECTED");
            }
        }
    }

    public synchronized void expireRequests() {
        long now = System.nanoTime();
        for (EmergencyRequest request : requests.values()) {
            if (!request.active() || now - request.deadlineNanos < 0) continue;
            if (request.status == EmergencyRequest.Status.OFFERED) {
                request.hospital.sendMessage("OFFER_EXPIRED|" + request.requestId);
                offerNext(request, "OFFER_TIMEOUT");
            } else finish(request, EmergencyRequest.Status.TIMED_OUT, "ASSIGNMENT_TIMEOUT");
        }
    }

    private void finish(EmergencyRequest request, EmergencyRequest.Status status, String reason) {
        reservations.release(request.requestId);
        request.status = status;
        String message = "EMERGENCY_" + status + "|" + request.requestId + "|" + reason;
        request.ambulance.sendMessage(message);
        if (request.hospital != null) request.hospital.sendMessage(message);
        LOG.info(request.requestId + " " + status + " " + reason);
    }

    public synchronized EmergencyRequest.Status getStatus(String requestId) {
        EmergencyRequest request = requests.get(requestId);
        return request == null ? null : request.status;
    }

    @Override
    public synchronized void close() {
        timer.shutdownNow();
        for (EmergencyRequest request : requests.values())
            if (request.active()) finish(request, EmergencyRequest.Status.CANCELLED, "SERVER_SHUTDOWN");
    }
}
