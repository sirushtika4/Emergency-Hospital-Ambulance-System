import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


public class HospitalClient {
	public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = 5000;

        try (Scanner keyboard = new Scanner(System.in);
             Socket socket = new Socket()) {

            System.out.print("Hospital name: ");
            String hospital = keyboard.nextLine().trim();
            if (hospital.isEmpty() || hospital.contains("|")) {
                System.out.println("Enter a hospital name without the | character.");
                return;
            }
            
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(10000);

            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter output = new PrintWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                // Read the server greeting before sending any command.
                System.out.println("Server: " + readResponse(input));
                String registration = exchange(input, output,
                        "HOSPITAL_REGISTER|" + hospital);
                System.out.println("Server: " + registration);
                if (!registration.equals("HOSPITAL_REGISTERED|" + hospital)) {
                    return;
                }
                
                
                while (true) {
                    System.out.println("\n1. Send bed update\n2. Exit");
                    System.out.print("Choose: ");
                    String choice = keyboard.nextLine().trim();

                    if (choice.equals("2")) {
                        break;
                    }
                    if (!choice.equals("1")) {
                        System.out.println("Enter 1 or 2.");
                        continue;
                    }

                    System.out.print("Bed type (GENERAL or ICU): ");
                    String type = keyboard.nextLine().trim()
                            .toUpperCase(java.util.Locale.ROOT);
                    if (!type.equals("GENERAL") && !type.equals("ICU")) {
                        System.out.println("Choose GENERAL or ICU.");
                        continue;
                    }
                    
                    System.out.print("Available beds: ");
                    int beds;
                    try {
                        beds = Integer.parseInt(keyboard.nextLine().trim());
                        if (beds < 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Enter a whole number of zero or more.");
                        continue;
                    }

                    String reply = exchange(input, output,
                            "BED_UPDATE|" + hospital + "|" + type + "|" + beds);
                    System.out.println("Server: " + reply);
                    if (reply.equals("BED_UPDATE_RECEIVED")) {
                        System.out.println("The server acknowledged your update.");
                    }
                }
            }
            
        } catch (IOException e) {
        	
        	System.out.println("Connection ended: " + e.getMessage());
            System.out.println("Check that the server is running at " + host
                    + ":" + port + ".");
        	
        }
	}
	
	private static String exchange(BufferedReader input, PrintWriter output,
            String command) throws IOException {
          output.println(command);
          if (output.checkError()) {
                 throw new IOException("Could not send the command");
           }
           return readResponse(input);
       }

     private static String readResponse(BufferedReader input) throws IOException {
             String response = input.readLine();
             if (response == null) {
               throw new EOFException("The server disconnected");
              }
              return response;
         }
}

	
                
            
            
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	