package server;

import java.util.HashSet;
import java.util.Set;

/** Mutable workflow state is accessed only under its EmergencyManager's monitor. */
public class EmergencyRequest {
    public enum Status { OFFERED, ASSIGNED, CANCELLED, COMPLETED, FAILED, TIMED_OUT }

    public final String requestId;
    public final String ambulanceId;
    public final String emergencyType;
    public final String priority;
    public final String requiredBedType;
    final ClientHandler ambulance;
    final Set<String> attemptedHospitals = new HashSet<>();
    String selectedHospital;
    ClientHandler hospital;
    Status status = Status.OFFERED;
    long deadlineNanos;

    EmergencyRequest(String requestId, String ambulanceId, String emergencyType,
                     String priority, String requiredBedType, ClientHandler ambulance) {
        this.requestId = requestId;
        this.ambulanceId = ambulanceId;
        this.emergencyType = emergencyType;
        this.priority = priority;
        this.requiredBedType = requiredBedType;
        this.ambulance = ambulance;
    }

    boolean active() { return status == Status.OFFERED || status == Status.ASSIGNED; }
}
