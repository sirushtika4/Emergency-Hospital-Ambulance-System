import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal protocol demo: register, publish ICU beds, and listen for emergencies. */
public class HospitalClient {
    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "HOSP001";
        try (Socket socket = new Socket("localhost", 5000);
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            System.out.println(input.readLine());
            output.println("HOSPITAL_REGISTER|" + id);
            String registration = input.readLine();
            System.out.println(registration);
            if (!("HOSPITAL_REGISTERED|" + id).equals(registration)) return;
            output.println("BED_UPDATE|" + id + "|ICU|3");
            String message;
            while ((message = input.readLine()) != null) System.out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
