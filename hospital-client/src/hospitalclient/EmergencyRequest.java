package hospitalclient;

public class EmergencyRequest {

    private final String requestId;
    private final String patientReference;
    private final EmergencyType emergencyType;
    private final String priority;
    private final String requiredBedType;
    private RequestStatus status;

    // Kept for compatibility with any existing code using the original constructor.
    public EmergencyRequest(String requestId, String patientReference,
                            EmergencyType emergencyType) {
        this(requestId, patientReference, emergencyType, "UNKNOWN", "UNKNOWN");
    }

    public EmergencyRequest(String requestId, String patientReference,
                            EmergencyType emergencyType, String priority,
                            String requiredBedType) {
        this.requestId = requestId;
        this.patientReference = patientReference;
        this.emergencyType = emergencyType;
        this.priority = priority;
        this.requiredBedType = requiredBedType;
        this.status = RequestStatus.PENDING;
    }

    public String getRequestId() { return requestId; }
    public EmergencyType getEmergencyType() { return emergencyType; }
    public String getRequiredBedType() { return requiredBedType; }
    public RequestStatus getStatus() { return status; }

    public void acceptRequest() {
        if (status == RequestStatus.PENDING) {
            status = RequestStatus.ACCEPTED;
            System.out.println("Request accepted by server.");
        }
    }

    public void rejectRequest() {
        if (status == RequestStatus.PENDING) {
            status = RequestStatus.REJECTED;
            System.out.println("Request rejected by hospital.");
        }
    }

    public void expireRequest() {
        if (status == RequestStatus.PENDING) status = RequestStatus.EXPIRED;
    }

    public void failRequest() {
        if (status == RequestStatus.PENDING) status = RequestStatus.FAILED;
    }

    public void cancelRequest() {
        if (status == RequestStatus.PENDING || status == RequestStatus.ACCEPTED)
            status = RequestStatus.CANCELLED;
    }

    public void displayRequest() {
        System.out.println("\n===== EMERGENCY REQUEST =====");
        System.out.println("Request ID: " + requestId);
        System.out.println("Ambulance ID: " + patientReference);
        System.out.println("Emergency Type: " + emergencyType);
        System.out.println("Priority: " + priority);
        System.out.println("Required Bed: " + requiredBedType);
        System.out.println("Status: " + status);
    }
}
