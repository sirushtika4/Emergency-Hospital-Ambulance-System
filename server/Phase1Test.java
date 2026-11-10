package server;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Dependency-free socket integration tests; runs the real main server on port 5000. */
public class Phase1Test {
    static int checks;
    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    static class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            expect("CONNECTED|Emergency Server");
        }
        void send(String text) { out.println(text); }
        void expect(String text) throws IOException { check(text.equals(in.readLine()), text); }
        void error(String text) throws IOException {
            send(text); String response = in.readLine();
            check(response != null && response.startsWith("ERROR|"), "Reject " + text);
        }
        public void close() throws IOException { socket.close(); }
    }
    public static void main(String[] args) throws Exception {
        // Fail before launching if another server already owns the required port.
        try (ServerSocket probe = new ServerSocket(5000)) { }
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process server = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                "server.EmergencyServer").redirectErrorStream(true)
                .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "phase1-server-test.log")).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try (Socket ready = new Socket("127.0.0.1", 5000)) { break; }
                catch (ConnectException e) {
                    if (!server.isAlive() || System.nanoTime() > deadline) throw e;
                    Thread.sleep(20);
                }
            }
            try (Peer a = new Peer(5000); Peer h = new Peer(5000); Peer h2 = new Peer(5000);
                 Peer other = new Peer(5000)) {
                other.error("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL");
                a.send("AMBULANCE_REGISTER|AMB001"); a.expect("AMBULANCE_REGISTERED|AMB001");
                a.error("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL");
                h.send("HOSPITAL_REGISTER|HOSP001"); h.expect("HOSPITAL_REGISTERED|HOSP001");
                h.send("BED_UPDATE|HOSP001|ICU|3"); h.expect("BED_UPDATE_RECEIVED");
                a.send("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL");
                h.expect("NEW_EMERGENCY|AMB001|CARDIAC|CRITICAL"); a.expect("EMERGENCY_REQUEST_RECEIVED");
                h2.send("HOSPITAL_REGISTER|HOSP002"); h2.expect("HOSPITAL_REGISTERED|HOSP002");
                other.error("AMBULANCE_REGISTER|HOSP001");
                a.error("HOSPITAL_REGISTER|HOSP003");
                for (String invalid : new String[]{"", "UNKNOWN", "BED_UPDATE", "HOSPITAL_REGISTER|",
                        "HOSPITAL_REGISTER| X", "HOSPITAL_REGISTER|X|extra",
                        "BED_UPDATE|HOSP001|ICU|", "BED_UPDATE|HOSP001|ICU|-1",
                        "BED_UPDATE|HOSP001|ICU|1.5", "BED_UPDATE|HOSP001|ICU|2147483648",
                        "BED_UPDATE|HOSP001|ICU|3|extra"}) h.error(invalid);
                a.error("BED_UPDATE|AMB001|ICU|2");
                h.error("EMERGENCY_REQUEST|HOSP001|CARDIAC|CRITICAL");
                h.error("BED_UPDATE|HOSP002|ICU|2");
                a.error("EMERGENCY_REQUEST|AMB999|CARDIAC|CRITICAL");
                // Concurrent sends exercise both hospital output locks.
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<?> f1 = pool.submit(() -> { for (int i=0;i<10;i++) a.send("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL"); });
                    other.send("AMBULANCE_REGISTER|AMB002"); other.expect("AMBULANCE_REGISTERED|AMB002");
                    Future<?> f2 = pool.submit(() -> { for (int i=0;i<10;i++) other.send("EMERGENCY_REQUEST|AMB002|TRAUMA|HIGH"); });
                    f1.get(); f2.get();
                    for (Peer hospital : Arrays.asList(h, h2)) {
                        Map<String,Integer> counts = new HashMap<>();
                        for (int i=0;i<20;i++) counts.merge(hospital.in.readLine(), 1, Integer::sum);
                        check(counts.getOrDefault("NEW_EMERGENCY|AMB001|CARDIAC|CRITICAL",0)==10
                                && counts.getOrDefault("NEW_EMERGENCY|AMB002|TRAUMA|HIGH",0)==10, "Concurrent broadcast exactly once");
                    }
                    for (int i=0;i<10;i++) { a.expect("EMERGENCY_REQUEST_RECEIVED"); other.expect("EMERGENCY_REQUEST_RECEIVED"); }
                } finally { pool.shutdownNow(); }
                h.close();
                boolean reclaimed = false;
                try (Peer replacement = new Peer(5000)) {
                    for (int i=0;i<100;i++) {
                        replacement.send("HOSPITAL_REGISTER|HOSP001");
                        if ("HOSPITAL_REGISTERED|HOSP001".equals(replacement.in.readLine())) { reclaimed=true; break; }
                        Thread.sleep(10);
                    }
                    check(reclaimed, "Disconnected ID reusable");
                    replacement.send("BED_UPDATE|HOSP001|ICU|0"); replacement.expect("BED_UPDATE_RECEIVED");
                }
                h2.socket.setSoLinger(true, 0); h2.close();
                try (Peer alive = new Peer(5000)) {
                    alive.send("AMBULANCE_REGISTER|ALIVE"); alive.expect("AMBULANCE_REGISTERED|ALIVE");
                }
                check(server.isAlive(), "Server survives client failures");
            }
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly().waitFor();
        }
        registryTests();
        concurrentRegistrationTest();
        System.out.println("PASS: " + checks + " checks");
    }
    static void concurrentRegistrationTest() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        MessageRouter router = new MessageRouter(registry);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try (Socket s1 = new Socket(); Socket s2 = new Socket()) {
            ClientHandler first = new ClientHandler(s1, registry, router);
            ClientHandler second = new ClientHandler(s2, registry, router);
            Future<Boolean> one = pool.submit(() -> {
                start.await();
                return registry.register("RACE", ClientRegistry.Role.HOSPITAL, first);
            });
            Future<Boolean> two = pool.submit(() -> {
                start.await();
                return registry.register("RACE", ClientRegistry.Role.HOSPITAL, second);
            });
            start.countDown();
            check(one.get(3, TimeUnit.SECONDS) ^ two.get(3, TimeUnit.SECONDS),
                    "Exactly one concurrent registration wins");
            check(registry.count() == 1, "Concurrent registration count");
        } finally { pool.shutdownNow(); }
    }
    static void registryTests() throws Exception {
        ClientRegistry r = new ClientRegistry();
        MessageRouter router = new MessageRouter(r);
        try (Socket s1 = new Socket(); Socket s2 = new Socket()) {
            ClientHandler old = new ClientHandler(s1,r,router), fresh = new ClientHandler(s2,r,router);
            check(r.register("H",ClientRegistry.Role.HOSPITAL,old), "Register");
            check(!r.register("H",ClientRegistry.Role.AMBULANCE,fresh), "Global duplicate");
            check(!r.register("OTHER",ClientRegistry.Role.HOSPITAL,old), "One ID per connection");
            check(r.updateBeds("H",old,"ICU",3), "Store beds");
            check(r.getBedAvailability("H").get("ICU")==3, "Read beds");
            r.getBedAvailability("H").put("ICU",99);
            check(r.getBedAvailability("H").get("ICU")==3, "Snapshot isolation");
            check(!r.updateBeds("H",fresh,"ICU",8), "Bed ownership");
            r.remove("H",fresh); check(r.count()==1, "Removal ownership");
            r.remove("H",old); check(r.getBedAvailability("H").isEmpty(), "Beds cleared");
            check(r.register("H",ClientRegistry.Role.HOSPITAL,fresh), "Reuse ID");
            r.remove("H",old); check(r.getClient("H")==fresh, "Stale disconnect safe");
            check(r.getBedAvailability("H").isEmpty(), "No stale availability");
            check(r.register("H2",ClientRegistry.Role.HOSPITAL,old), "Second hospital");
            r.updateBeds("H",fresh,"ICU",2); r.updateBeds("H2",old,"ICU",7);
            check(r.getBedAvailability("H").get("ICU")==2 && r.getBedAvailability("H2").get("ICU")==7,
                    "Hospital isolation");
            check(r.getRole("H")==ClientRegistry.Role.HOSPITAL && r.getHospitals().size()==2, "Role and hospital retrieval");
        }
    }
}
