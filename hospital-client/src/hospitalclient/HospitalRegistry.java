
package hospitalclient;

import java.util.ArrayList;

public class HospitalRegistry {

    public static ArrayList<Hospital> createHospitals() {

        ArrayList<Hospital> hospitals = new ArrayList<>();

        // Government hospitals
        hospitals.add(new Hospital(
            "H001", "National Hospital Galle (Karapitiya)",
            "Galle", "Government",
            true, true, true, 15, 5
        ));

        hospitals.add(new Hospital(
            "H002", "National Hospital of Sri Lanka",
            "Colombo", "Government",
            true, true, true, 20, 8
        ));

        hospitals.add(new Hospital(
            "H003", "National Hospital Kandy",
            "Kandy", "Government",
            true, true, true, 18, 6
        ));

        hospitals.add(new Hospital(
            "H004", "Teaching Hospital Jaffna",
            "Jaffna", "Government",
            true, true, true, 12, 4
        ));

        hospitals.add(new Hospital(
            "H005", "Teaching Hospital Peradeniya",
            "Kandy", "Government",
            true, true, false, 14, 5
        ));

        // Private hospitals
        hospitals.add(new Hospital(
            "H006", "Galle Co-operative Hospital",
            "Galle", "Private",
            true, false, false, 10, 3
        ));

        hospitals.add(new Hospital(
            "H007", "Ruhunu Hospital",
            "Galle", "Private",
            false, true, false, 8, 2
        ));

        hospitals.add(new Hospital(
            "H008", "Nawaloka Hospital",
            "Colombo", "Private",
            true, true, false, 16, 5
        ));

        hospitals.add(new Hospital(
            "H009", "Lanka Hospitals",
            "Colombo", "Private",
            true, true, true, 15, 6
        ));

        hospitals.add(new Hospital(
            "H010", "Asiri Hospital Kandy",
            "Kandy", "Private",
            true, false, true, 11, 4
        ));

        return hospitals;
    }

    public static void displayHospitals(
            ArrayList<Hospital> hospitals) {

        System.out.println("\n===== SELECT HOSPITAL =====");

        for (int i = 0; i < hospitals.size(); i++) {
            Hospital hospital = hospitals.get(i);

            System.out.println(
                (i + 1) + ". "
                + hospital.getHospitalName()
                + " (" + hospital.getLocation() + ")"
            );
        }
    }
}
