package server;

import java.io.File;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Dependency-free socket workflow and concurrent reservation regression tests. */
public class Phase2Test {
    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        check(condition.getAsBoolean(), label);
    }

    private static String request(Phase1Test.Peer ambulance, String id, String bed) throws Exception {
        ambulance.send("EMERGENCY_REQUEST|" + id + "|CARDIAC|CRITICAL|" + bed);
        String response = ambulance.in.readLine();
        check(response != null && response.matches("EMERGENCY_REQUEST_RECEIVED\\|REQ[0-9]{6,}"),
                "Request receives a generated ID");
        return response.split("\\|")[1];
    }

    private static void offer(Phase1Test.Peer hospital, String request, String ambulance) throws Exception {
        hospital.expect("EMERGENCY_OFFER|" + request + "|" + ambulance + "|CARDIAC|CRITICAL|ICU");
    }

    private static void accept(Phase1Test.Peer hospital, Phase1Test.Peer ambulance,
                               String request, String hospitalId) throws Exception {
        hospital.send("HOSPITAL_ACCEPT|" + request + "|" + hospitalId);
        String assigned = "HOSPITAL_ASSIGNED|" + request + "|" + hospitalId + "|ICU";
        hospital.expect(assigned);
        ambulance.expect(assigned);
    }

    public static void main(String[] args) throws Exception {
        int previousChecks = Phase1Test.checks;
        socketWorkflow();
        concurrentReservations();
        managerRacesAndTimeouts();
        checks += Phase1Test.checks - previousChecks;
        System.out.println("PASS Phase 2: " + checks + " checks");
    }

    private static void socketWorkflow() throws Exception {
        try (ServerSocket probe = new ServerSocket(5000)) { }
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process server = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                "server.EmergencyServer").redirectErrorStream(true)
                .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "phase2-server-test.log")).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try (Socket ready = new Socket("127.0.0.1", 5000)) { break; }
                catch (ConnectException e) {
                    if (!server.isAlive() || System.nanoTime() > deadline) throw e;
                    Thread.sleep(20);
                }
            }
            try (Phase1Test.Peer a = new Phase1Test.Peer(5000);
                 Phase1Test.Peer b = new Phase1Test.Peer(5000);
                 Phase1Test.Peer h1 = new Phase1Test.Peer(5000);
                 Phase1Test.Peer h2 = new Phase1Test.Peer(5000)) {
                a.error("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL|ICU");
                a.send("AMBULANCE_REGISTER|AMB001"); a.expect("AMBULANCE_REGISTERED|AMB001");
                b.send("AMBULANCE_REGISTER|AMB002"); b.expect("AMBULANCE_REGISTERED|AMB002");
                h1.send("HOSPITAL_REGISTER|HOSP001"); h1.expect("HOSPITAL_REGISTERED|HOSP001");
                h2.send("HOSPITAL_REGISTER|HOSP002"); h2.expect("HOSPITAL_REGISTERED|HOSP002");
                h1.send("BED_UPDATE|HOSP001|ICU|2"); h1.expect("BED_UPDATE_RECEIVED");
                h2.send("BED_UPDATE|HOSP002|ICU|1"); h2.expect("BED_UPDATE_RECEIVED");
                a.error("EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL|");
                a.error("EMERGENCY_REQUEST|AMB002|CARDIAC|CRITICAL|ICU");
                h1.error("HOSPITAL_ACCEPT|missing");
                String first = request(a, "AMB001", "ICU"); offer(h1, first, "AMB001");
                h2.error("HOSPITAL_ACCEPT|" + first + "|HOSP002");
                h1.error("HOSPITAL_ACCEPT|" + first + "|HOSP002");
                a.error("HOSPITAL_ACCEPT|" + first + "|AMB001");
                b.error("EMERGENCY_CANCEL|" + first + "|AMB002");
                a.error("EMERGENCY_COMPLETE|" + first + "|AMB001");
                h1.send("HOSPITAL_REJECT|" + first + "|HOSP001");
                h1.expect("HOSPITAL_REJECTED|" + first); offer(h2, first, "AMB001");
                h1.error("HOSPITAL_ACCEPT|" + first + "|HOSP001");
                accept(h2, a, first, "HOSP002");
                h2.error("HOSPITAL_ACCEPT|" + first + "|HOSP002");
                h2.error("HOSPITAL_REJECT|" + first + "|HOSP002");
                a.send("EMERGENCY_STATUS|" + first + "|AMB001");
                a.expect("REQUEST_STATUS|" + first + "|ASSIGNED|HOSP002");
                h2.error("BED_UPDATE|HOSP002|ICU|0");
                h2.send("BED_UPDATE|HOSP002|ICU|1"); h2.expect("BED_UPDATE_RECEIVED");
                String second = request(b, "AMB002", "ICU"); offer(h1, second, "AMB002");
                check(!first.equals(second), "Unique request IDs");
                accept(h1, b, second, "HOSP001");
                a.send("EMERGENCY_CANCEL|" + first + "|AMB001");
                a.expect("EMERGENCY_CANCELLED|" + first + "|AMBULANCE_REQUEST");
                h2.expect("EMERGENCY_CANCELLED|" + first + "|AMBULANCE_REQUEST");
                a.error("EMERGENCY_CANCEL|" + first + "|AMB001");
                b.send("EMERGENCY_COMPLETE|" + second + "|AMB002");
                b.expect("EMERGENCY_COMPLETED|" + second + "|AMBULANCE_REQUEST");
                h1.expect("EMERGENCY_COMPLETED|" + second + "|AMBULANCE_REQUEST");
                b.error("EMERGENCY_COMPLETE|" + second + "|AMB002");
                String unsupported = request(a, "AMB001", "NEONATAL");
                a.expect("EMERGENCY_FAILED|" + unsupported + "|NO_SUITABLE_HOSPITAL");
                String rejected = request(a, "AMB001", "ICU"); offer(h1, rejected, "AMB001");
                h1.send("HOSPITAL_REJECT|" + rejected + "|HOSP001");
                h1.expect("HOSPITAL_REJECTED|" + rejected); offer(h2, rejected, "AMB001");
                h2.send("HOSPITAL_REJECT|" + rejected + "|HOSP002");
                h2.expect("HOSPITAL_REJECTED|" + rejected);
                a.expect("EMERGENCY_FAILED|" + rejected + "|ALL_HOSPITALS_REJECTED");
                h2.expect("EMERGENCY_FAILED|" + rejected + "|ALL_HOSPITALS_REJECTED");
                String lostBed = request(a, "AMB001", "ICU"); offer(h1, lostBed, "AMB001");
                h1.send("BED_UPDATE|HOSP001|ICU|0"); h1.expect("BED_UPDATE_RECEIVED");
                h1.error("HOSPITAL_ACCEPT|" + lostBed + "|HOSP001");
                offer(h2, lostBed, "AMB001"); accept(h2, a, lostBed, "HOSP002");
                a.send("EMERGENCY_CANCEL|" + lostBed + "|AMB001");
                a.expect("EMERGENCY_CANCELLED|" + lostBed + "|AMBULANCE_REQUEST");
                h2.expect("EMERGENCY_CANCELLED|" + lostBed + "|AMBULANCE_REQUEST");
                h1.send("BED_UPDATE|HOSP001|ICU|2"); h1.expect("BED_UPDATE_RECEIVED");
                String pending = request(a, "AMB001", "ICU"); offer(h1, pending, "AMB001");
                a.send("EMERGENCY_CANCEL|" + pending + "|AMB001");
                a.expect("EMERGENCY_CANCELLED|" + pending + "|AMBULANCE_REQUEST");
                h1.expect("EMERGENCY_CANCELLED|" + pending + "|AMBULANCE_REQUEST");
                h1.error("HOSPITAL_ACCEPT|" + pending + "|HOSP001");
                String rerouted = request(a, "AMB001", "ICU"); offer(h1, rerouted, "AMB001");
                h1.close(); offer(h2, rerouted, "AMB001");
                accept(h2, a, rerouted, "HOSP002");
                a.close();
                h2.expect("EMERGENCY_CANCELLED|" + rerouted + "|AMBULANCE_DISCONNECTED");
                String last = request(b, "AMB002", "ICU"); offer(h2, last, "AMB002");
                accept(h2, b, last, "HOSP002");
                h2.close();
                b.expect("EMERGENCY_FAILED|" + last + "|HOSPITAL_DISCONNECTED");
                String noHospitals = request(b, "AMB002", "ICU");
                b.expect("EMERGENCY_FAILED|" + noHospitals + "|NO_SUITABLE_HOSPITAL");
                check(server.isAlive(), "Server remains alive after workflow disconnects");
            }
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly().waitFor();
        }
    }

    /** An output recorder lets concurrency tests exercise the real managers without socket timing. */
    private static class RecordingHandler extends ClientHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        boolean writable = true;
        RecordingHandler(ClientRegistry registry) {
            super(new Socket(), registry, new MessageRouter(registry));
        }
        @Override public boolean sendMessage(String message) {
            messages.add(message);
            return writable;
        }
    }

    private static RecordingHandler register(ClientRegistry registry, String id, ClientRegistry.Role role) {
        RecordingHandler handler = new RecordingHandler(registry);
        check(registry.register(id, role, handler), "Register test " + id);
        return handler;
    }

    private static void concurrentReservations() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        RecordingHandler hospital = register(registry, "H", ClientRegistry.Role.HOSPITAL);
        registry.updateBeds("H", hospital, "ICU", 1);
        BedReservationManager beds = new BedReservationManager(registry);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                final String request = "R" + i;
                attempts.add(pool.submit(() -> { start.await(); return beds.reserve(request, "H", hospital, "ICU"); }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> attempt : attempts) if (attempt.get(3, TimeUnit.SECONDS)) successes++;
            check(successes == 1 && beds.count() == 1, "One of 32 concurrent reservations wins");
            check(registry.getBedAvailability("H").get("ICU") == 0, "No negative beds");
            check(registry.updateBeds("H", hospital, "ICU", 1), "Capacity refresh allowed");
            check(!beds.reserve("EXTRA", "H", hospital, "ICU"), "Refresh cannot erase reservation");
            check(!registry.updateBeds("H", hospital, "ICU", 0), "Cannot lower capacity below holds");
            for (int i = 0; i < 32; i++) beds.release("R" + i);
            check(beds.count() == 0 && registry.getBedAvailability("H").get("ICU") == 1, "Release restores one bed");
            check(!beds.release("R0"), "Repeated release is harmless");
            check(beds.reserve("SAME", "H", hospital, "ICU"), "Reserve request once");
            check(!beds.reserve("SAME", "H", hospital, "ICU"), "Duplicate request reservation rejected");
            registry.remove("H", hospital);
            RecordingHandler replacement = register(registry, "H", ClientRegistry.Role.HOSPITAL);
            registry.updateBeds("H", replacement, "ICU", 4);
            beds.release("SAME");
            check(registry.getBedAvailability("H").get("ICU") == 4, "Stale release preserves replacement inventory");
            check(!beds.reserve("OLD", "H", hospital, "ICU"), "Disconnected handler cannot reserve");
        } finally { pool.shutdownNow(); }
    }

    private static void managerRacesAndTimeouts() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        RecordingHandler a = register(registry, "A", ClientRegistry.Role.AMBULANCE);
        RecordingHandler b = register(registry, "B", ClientRegistry.Role.AMBULANCE);
        RecordingHandler h = register(registry, "H", ClientRegistry.Role.HOSPITAL);
        registry.updateBeds("H", h, "ICU", 1);
        try (EmergencyManager manager = new EmergencyManager(registry)) {
            manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
            manager.submit(b, "B", "CARDIAC", "CRITICAL", "ICU");
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<?> one = pool.submit(() -> { awaitLatch(start); manager.respond(h, "REQ000001", "H", true); });
                Future<?> two = pool.submit(() -> { awaitLatch(start); manager.respond(h, "REQ000002", "H", true); });
                start.countDown(); one.get(3, TimeUnit.SECONDS); two.get(3, TimeUnit.SECONDS);
                boolean firstWon = manager.getStatus("REQ000001") == EmergencyRequest.Status.ASSIGNED;
                check(firstWon ^ (manager.getStatus("REQ000002") == EmergencyRequest.Status.ASSIGNED),
                        "Concurrent accepts assign only one request");
                check(h.messages.stream().filter(m -> m.startsWith("HOSPITAL_ASSIGNED|")).count() == 1,
                        "Exactly one assignment notification");
                String winner = firstWon ? "REQ000001" : "REQ000002";
                manager.respond(h, winner, "H", true);
                check(h.messages.stream().filter(m -> m.startsWith("HOSPITAL_ASSIGNED|")).count() == 1,
                        "Duplicate acceptance does not repeat assignment");
                manager.ambulanceCommand(firstWon ? a : b, "EMERGENCY_CANCEL", winner, firstWon ? "A" : "B");
                check(registry.getBedAvailability("H").get("ICU") == 1, "Cancellation releases winning bed");
                manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
                Future<?> accept = pool.submit(() -> manager.respond(h, "REQ000003", "H", true));
                Future<?> cancel = pool.submit(() -> manager.ambulanceCommand(a, "EMERGENCY_CANCEL", "REQ000003", "A"));
                accept.get(3, TimeUnit.SECONDS); cancel.get(3, TimeUnit.SECONDS);
                check(manager.getStatus("REQ000003") == EmergencyRequest.Status.CANCELLED
                        && registry.getBedAvailability("H").get("ICU") == 1, "Accept/cancel race leaves no hold");
            } finally { pool.shutdownNow(); }
        }
        try (EmergencyManager manager = new EmergencyManager(registry, 300, 300)) {
            manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
            await(() -> manager.getStatus("REQ000001") == EmergencyRequest.Status.TIMED_OUT, "Offer timeout");
            manager.respond(h, "REQ000001", "H", true);
            check(registry.getBedAvailability("H").get("ICU") == 1, "Late acceptance cannot reserve");
            manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
            manager.respond(h, "REQ000002", "H", true);
            check(manager.getStatus("REQ000002") == EmergencyRequest.Status.ASSIGNED, "Assigned before timeout");
            await(() -> manager.getStatus("REQ000002") == EmergencyRequest.Status.TIMED_OUT, "Assignment timeout");
            check(registry.getBedAvailability("H").get("ICU") == 1, "Expired assignment releases bed");
            RecordingHandler h2 = register(registry, "H2", ClientRegistry.Role.HOSPITAL);
            registry.updateBeds("H2", h2, "ICU", 1);
            manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
            await(() -> h2.messages.stream().anyMatch(m -> m.startsWith("EMERGENCY_OFFER|REQ000003|")),
                    "Offer timeout retries next hospital");
            manager.respond(h2, "REQ000003", "H2", true);
            check(manager.getStatus("REQ000003") == EmergencyRequest.Status.ASSIGNED, "Retried offer accepted");
            manager.ambulanceCommand(a, "EMERGENCY_CANCEL", "REQ000003", "A");
            // Simulate a failed write after the reservation has been acquired.
            h.writable = false;
            manager.submit(a, "A", "CARDIAC", "CRITICAL", "ICU");
            h2.writable = false;
            manager.respond(h2, "REQ000004", "H2", true);
            check(manager.getStatus("REQ000004") == EmergencyRequest.Status.CANCELLED,
                    "Assignment delivery failure cancels request");
            check(registry.getBedAvailability("H2").get("ICU") == 1, "Delivery failure releases bed");
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
