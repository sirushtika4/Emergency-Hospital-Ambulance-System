# Emergency Coordination Server: Phases 1 and 2

Uses standard Java TCP sockets on port 5000 with one `ClientHandler` thread per
connection. Compile and run from the project root (JDK 8 or newer):

```powershell
javac server/*.java ambulance-client/AmbulanceClient.java hospital-client/HospitalClient.java
java server.EmergencyServer
```

In separate terminals, start hospitals first, then the ambulance demo:

```powershell
java -cp hospital-client HospitalClient HOSP001
java -cp hospital-client HospitalClient HOSP002
java -cp ambulance-client AmbulanceClient AMB001
```

Each hospital publishes three ICU beds and waits for broadcasts. The ambulance
registers, sends one cardiac emergency, reads the acknowledgement, and disconnects.
Stop each hospital and the server with Ctrl+C. The ambulance demo previously sent
an unsupported legacy message; it now uses the existing protocol. The hospital
source was empty and now provides a minimal compatible demo.

## Classes and concurrency

- `EmergencyServer`: owns the shared registry and router, accepts connections,
  and starts a named thread for each handler. Standard Java logging supplies
  timestamps for connection, registration, routing, disconnect, and error events.
- `ClientHandler`: reads newline-delimited UTF-8 messages, validates exact field
  counts and nonempty tokens, checks socket identity and role, and cleans up on
  disconnect. A per-connection lock prevents concurrent writes from interleaving
  and ensures registration acknowledgement precedes broadcasts.
- `ClientRegistry`: stores globally unique IDs, roles, and handlers in a
  `ConcurrentHashMap`. Synchronized compound operations prevent duplicate
  registration and stale cleanup from removing a replacement connection.
  Hospital bed maps belong to their registration; removing it discards its beds.
  Queries return snapshots. No registry lock is held during network writes.
- `MessageRouter`: snapshots hospitals and broadcasts once per handler using
  the handler's output lock for legacy requests. It also exposes the shared
  Phase 2 manager to handlers. Each legacy routing call has its own recipient set.
- `HospitalSelector`: selects a connected hospital with the requested bed type
  and the most free beds; hospital ID breaks ties. Previously attempted hospitals
  are excluded for that request.
- `EmergencyRequest`: stores the generated request ID, ambulance identity,
  emergency type, priority, required bed type, selected hospital, and workflow
  status. Mutable state belongs to the manager's monitor.
- `EmergencyManager`: manages offers, responses, assignments, cancellations,
  completion, disconnects, and scheduled expiration. One shared manager serializes
  state transitions, so duplicate responses cannot create duplicate assignments.
- `BedReservationManager`: holds beds by request ID. Reservations, releases, and
  bed updates share the registry lock; a stale connection cannot change a new
  registration's inventory.
- `Phase1Test`: dependency-free integration and registry tests.
- `Phase2Test`: socket workflow, concurrency, reservation, and timeout tests.
- `AmbulanceClient` / `HospitalClient`: minimal console protocol demonstrations.

## Protocol

Each connection receives `CONNECTED|Emergency Server`. Tokens contain ASCII
letters, digits, underscores, or hyphens; IDs are case-sensitive. Emergency types,
priorities, and bed types are free-form tokens, not predefined enums.

| Request | Successful response |
| --- | --- |
| `AMBULANCE_REGISTER|AMB001` | `AMBULANCE_REGISTERED|AMB001` |
| `HOSPITAL_REGISTER|HOSP001` | `HOSPITAL_REGISTERED|HOSP001` |
| `BED_UPDATE|HOSP001|ICU|3` | `BED_UPDATE_RECEIVED` |
| `EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL` | `EMERGENCY_REQUEST_RECEIVED` |

Hospitals receive `NEW_EMERGENCY|AMB001|CARDIAC|CRITICAL`. The sender must have the
correct registered role and matching ID. Bed counts must be decimal integers
from 0 through 2147483647. Invalid input, duplicate IDs, and repeated registrations
receive `ERROR|<reason>`. With no successful hospital writes, the ambulance receives
`ERROR|No hospitals available`. An acknowledgement indicates a successful socket
write to at least one hospital, not hospital acceptance or treatment assignment.
Logs omit raw requests and patient information.

## Phase 2 assignment protocol

The four-field `EMERGENCY_REQUEST` above still broadcasts `NEW_EMERGENCY` and does
not reserve beds. A fifth field explicitly opts into automatic assignment:

```text
Ambulance -> server: EMERGENCY_REQUEST|AMB001|CARDIAC|CRITICAL|ICU
Server -> ambulance: EMERGENCY_REQUEST_RECEIVED|REQ000001
Server -> selected hospital: EMERGENCY_OFFER|REQ000001|AMB001|CARDIAC|CRITICAL|ICU
Hospital -> server: HOSPITAL_ACCEPT|REQ000001|HOSP001
Server -> both peers: HOSPITAL_ASSIGNED|REQ000001|HOSP001|ICU
```

IDs are unique within a running server process and start at `REQ000001`. Priority
is preserved in offers; selection uses the explicit bed type and free count, not
a clinical priority ranking. Only the selected hospital's original socket may
accept or reject an offer. An offer is not a reservation: the server atomically
checks and holds one bed on acceptance. If that bed has become unavailable, it
rejects the acceptance and tries the next suitable hospital. Each hospital is
attempted at most once per request.

```text
Hospital -> server: HOSPITAL_REJECT|REQ000001|HOSP001
Server -> hospital: HOSPITAL_REJECTED|REQ000001
```

Rejection tries another suitable hospital. If none can be selected, the ambulance
receives `EMERGENCY_FAILED|<requestId>|<reason>`. Reasons include
`NO_SUITABLE_HOSPITAL`, `ALL_HOSPITALS_REJECTED`, and `NO_BEDS_AVAILABLE`.
Unknown, stale, duplicate, malformed, or unauthorized responses receive
`ERROR|<reason>` without creating another assignment.

Additional commands must come from the ambulance socket that created the request:

| Command | Result |
| --- | --- |
| `EMERGENCY_STATUS|REQ000001|AMB001` | `REQUEST_STATUS|REQ000001|<status>|<lastSelectedHospitalOrNONE>` |
| `EMERGENCY_CANCEL|REQ000001|AMB001` | Ends an offered or assigned request and releases any reservation |
| `EMERGENCY_COMPLETE|REQ000001|AMB001` | Ends an assigned request and releases its reservation |

Statuses are `OFFERED`, `ASSIGNED`, `CANCELLED`, `COMPLETED`, `FAILED`, and
`TIMED_OUT`. Terminal events are sent to the ambulance and last selected hospital
as `EMERGENCY_<status>|<requestId>|<reason>`, for example
`EMERGENCY_CANCELLED|REQ000001|AMBULANCE_REQUEST`. Repeated terminal commands
return an error and never release a bed twice. Completion in this console demo
ends the reservation; patient admission/occupancy is outside the protocol.

### Inventory and connection lifecycle

`BED_UPDATE|HOSP001|ICU|3` reports allocatable capacity **including outstanding
server reservations**. With one reservation, it exposes two free beds. Repeating
that update still exposes two, preventing a refresh from erasing a hold. Updates
below the outstanding reserved count are rejected. With no reservations, Phase 1
bed-update behavior is unchanged. Hospitals must follow this accounting convention
when implementing Phase 2 clients.

- Ambulance disconnect: cancel its active requests and release reservations.
- Hospital disconnect during an offer: try the next suitable hospital.
- Hospital disconnect after assignment: fail the request with
  `HOSPITAL_DISCONNECTED` and release the reservation; do not silently reassign it.
- Failed assignment delivery: cancel with `ASSIGNMENT_DELIVERY_FAILED` and release
  the reservation. A peer may receive assignment followed by cancellation.
- Hospital reconnection starts with no inventory; an old socket's cleanup cannot
  remove new registrations or change their bed counts.

An offer expires after 30 seconds. The hospital receives
`OFFER_EXPIRED|<requestId>` and the server tries the next suitable hospital.
Exhaustion after an offer timeout produces
`EMERGENCY_TIMED_OUT|<requestId>|OFFER_TIMEOUT`. An assigned reservation expires
after five minutes unless completed or cancelled, producing
`EMERGENCY_TIMED_OUT|<requestId>|ASSIGNMENT_TIMEOUT` and releasing the hold.
The background timer checks every 50 ms; acceptance also checks the offer deadline.
Timeouts can be configured using positive millisecond JVM properties:

```powershell
java -Demergency.offerTimeoutMillis=30000 -Demergency.assignmentTimeoutMillis=300000 server.EmergencyServer
```

The console clients remain unchanged and demonstrate Phase 1. `Phase2Test` supplies
protocol-capable socket peers for the Phase 2 workflow.

## Automated verification

Stop the manually started server first; tests require port 5000 to be free:

```powershell
javac server/*.java
java server.Phase1Test
java server.Phase2Test
```

The suite starts the actual server in a child process and stops it in a finally
block. Its logs are written to `phase1-server-test.log` in Java's temporary
directory. It checks registration, bed storage and isolation, single and multiple
hospital delivery, concurrent broadcasts, duplicate and concurrent registration,
malformed input, role and ID checks, no-hospital errors, ID reuse, stale cleanup,
and continued acceptance after disconnects and a reset connection. Phase 2 tests
also launch the actual server on port 5000, with logs in `phase2-server-test.log`
in Java's temporary directory. They cover selection, rejection/fallback, acceptance,
capacity changes, duplicate/stale responses, authorization, cancellation, completion,
disconnects, concurrent reservations and acceptances, expiration, and delivery failure.
Run the suites sequentially because each requires exclusive use of port 5000.

## Remaining limitations

Data is in memory and disappears on disconnect or restart. Registration establishes
a socket role, not authenticated identity. Authentication, persistence, bounded
connections/input, and slow-client handling are future hardening work. Terminal
request records are retained until server restart. Phase 2 serializes transitions
and notifications under the manager lock, so a slow socket can delay other workflow
operations and timeout processing. Production use would need bounded asynchronous
outbound queues and delivery acknowledgements. No admission tracking, priority
queue, or durable delivery guarantee is implemented.

Compiled `.class` files are ignored. No database or external framework is used.
