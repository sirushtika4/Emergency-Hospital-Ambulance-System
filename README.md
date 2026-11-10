# Emergency Coordination Server: Phase 1

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
  the handler's output lock. Each routing call has its own recipient set.
- `Phase1Test`: dependency-free integration and registry tests.
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

## Automated verification

Stop the manually started server first; tests require port 5000 to be free:

```powershell
javac server/*.java
java server.Phase1Test
```

The suite starts the actual server in a child process and stops it in a finally
block. Its logs are written to `phase1-server-test.log` in Java's temporary
directory. It checks registration, bed storage and isolation, single and multiple
hospital delivery, concurrent broadcasts, duplicate and concurrent registration,
malformed input, role and ID checks, no-hospital errors, ID reuse, stale cleanup,
and continued acceptance after disconnects and a reset connection.

## Phase 2

Hospital acceptance/rejection, automatic assignment, atomic bed reservations,
request identifiers and delivery acknowledgement/retry rules remain to be designed.
Data is in memory and disappears on disconnect or restart. Registration establishes
a socket role, not authenticated identity. Authentication, persistence, bounded
connections/input, and slow-client handling are future hardening work. Currently a
slow hospital write can delay the ambulance handler routing to it.

Compiled `.class` files are ignored. No database or external framework is used.
