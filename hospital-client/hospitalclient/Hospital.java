
package hospitalclient;

public class Hospital {

    private String hospitalId;
    private String hospitalName;
    private String location;
    private String hospitalType;

    private boolean cardiacCare;
    private boolean traumaCare;
    private boolean strokeCare;

    private int generalBeds;
    private int icuBeds;
    private HospitalStatus status = HospitalStatus.AVAILABLE;

    public Hospital(String hospitalId,
                    String hospitalName,
                    String location,
                    String hospitalType,
                    boolean cardiacCare,
                    boolean traumaCare,
                    boolean strokeCare,
                    int generalBeds,
                    int icuBeds) {

        if (generalBeds < 0 || icuBeds < 0) {
            throw new IllegalArgumentException(
                "Bed counts cannot be negative."
            );
        }

        this.hospitalId = hospitalId;
        this.hospitalName = hospitalName;
        this.location = location;
        this.hospitalType = hospitalType;

        this.cardiacCare = cardiacCare;
        this.traumaCare = traumaCare;
        this.strokeCare = strokeCare;

        this.generalBeds = generalBeds;
        this.icuBeds = icuBeds;
    }

    public void updateGeneralBeds(int beds) {
        if (beds < 0) {
            System.out.println("Invalid GENERAL bed count.");
            return;
        }

        generalBeds = beds;
        System.out.println("GENERAL beds updated successfully.");
    }

    public void updateIcuBeds(int beds) {
        if (beds < 0) {
            System.out.println("Invalid ICU bed count.");
            return;
        }

        icuBeds = beds;
        System.out.println("ICU beds updated successfully.");
    }

    public void displayHospitalInfo() {

        System.out.println("\n===== HOSPITAL INFORMATION =====");

        System.out.println("Hospital ID: " + hospitalId);
        System.out.println("Name: " + hospitalName);
        System.out.println("Location: " + location);
        System.out.println("Type: " + hospitalType);
        System.out.println("Operational Status: " + status);

        System.out.println("\n--- Medical Facilities ---");
        System.out.println("Cardiac Care: " + cardiacCare);
        System.out.println("Trauma Care: " + traumaCare);
        System.out.println("Stroke Care: " + strokeCare);

        System.out.println("\n--- Available Beds ---");
        System.out.println("GENERAL: " + generalBeds);
        System.out.println("ICU: " + icuBeds);
    }

public boolean supportsEmergency(EmergencyType type) {

    switch (type) {

        case CARDIAC:
            return cardiacCare;

        case TRAUMA:
            return traumaCare;

        case STROKE:
            return strokeCare;

        default:
            return false;
    }
}

public HospitalStatus getStatus() {
    return status;
}

public void updateStatus(HospitalStatus newStatus) {
    if (newStatus == null) {
        System.out.println("Invalid hospital status.");
        return;
    }

    status = newStatus;
    System.out.println("Hospital status updated to: " + status);
}

public boolean hasAvailableBeds() {
    return generalBeds > 0 || icuBeds > 0;
}

public String getHospitalId() {
    return hospitalId;
}

public String getHospitalName() {
    return hospitalName;
}

public String getLocation() {
    return location;
}

public int getGeneralBeds() {
    return generalBeds;
}

public int getIcuBeds() {
    return icuBeds;
}

public boolean hasCardiacCare() {
    return cardiacCare;
}

public boolean hasTraumaCare() {
    return traumaCare;
}

public boolean hasStrokeCare() {
    return strokeCare;
}



}
