
package hospitalclient;

public class EmergencyRequest {

    private String requestId;
    private String patientReference;
    private EmergencyType emergencyType;
    private RequestStatus status;

    public EmergencyRequest(
            String requestId,
            String patientReference,
            EmergencyType emergencyType) {

        this.requestId = requestId;
        this.patientReference = patientReference;
        this.emergencyType = emergencyType;
        this.status = RequestStatus.PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public EmergencyType getEmergencyType() {
        return emergencyType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void acceptRequest() {
        if (status != RequestStatus.PENDING) {
            System.out.println("Request already processed.");
            return;
        }

        status = RequestStatus.ACCEPTED;
        System.out.println("Request accepted.");
    }

    public void rejectRequest() {
        if (status != RequestStatus.PENDING) {
            System.out.println("Request already processed.");
            return;
        }

        status = RequestStatus.REJECTED;
        System.out.println("Request rejected.");
    }

    public void displayRequest() {
        System.out.println("\n===== EMERGENCY REQUEST =====");
        System.out.println("Request ID: " + requestId);
        System.out.println("Patient Reference: " + patientReference);
        System.out.println("Emergency Type: " + emergencyType);
        System.out.println("Status: " + status);
    }
}
