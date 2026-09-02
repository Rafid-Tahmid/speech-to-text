# Speech to Text

A Spring Boot web application that records microphone audio in the browser, sends it to a Java backend, transcribes it through OpenAI, and displays the returned text. The same executable application also provides the assignment's uptime, global token statistics, and graceful-shutdown APIs.

## Final verification status

- The packaged application passed all **11/11 TITAN functional checks** on Java 26.0.2.
- TITAN verified both uptime responses, both statistics responses, browser recording state, exact transcription, and clean process exit through the shutdown API.
- Rubric hardening subsequently added a real-server 201-request blocking-concurrency test and standardized backend error handling.
- The complete hardened Maven suite contains **17 passing tests** with zero failures or errors.
- The hardened source has passed locally; its newly packaged JAR has not yet been rechecked through TITAN.

## Technology

- Java and Spring Boot 4.1
- Spring MVC with embedded Tomcat
- Spring `RestClient` for the OpenAI request
- Maven Wrapper for building and testing
- Browser HTML, CSS, JavaScript, `MediaRecorder`, and `fetch`
- JUnit 5, Spring Test, MockMvc, and Mockito
- Java virtual threads for overlapping blocking requests

The Maven build targets Java 17 bytecode. A Java 21 or newer runtime is recommended because virtual threads are used for the concurrency requirement. Local hardening tests passed on Java 25, and TITAN used Java 26.0.2.

## Run locally

The `local` Spring profile uses a deterministic stub and does not require an OpenAI key.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open <http://localhost:8080/> and allow microphone access when the browser asks.

To build and run the same executable-JAR format used by TITAN:

```bash
./mvnw clean package
java -jar target/speech-to-text-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Run the complete regression suite with:

```bash
./mvnw test
```

Run only the more-than-200-request concurrency evidence with:

```bash
./mvnw -Dtest=BlockingRequestConcurrencyTests test
```

The normal profile uses the real OpenAI service and requires `OPENAI_API_KEY` in the process environment. The key must never be placed in source code, browser code, logs, screenshots, or a committed `.env` file. TITAN injects its own key directly into the launched process.

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | Serves the recording interface. |
| `POST` | `/api/v1/transcriptions` | Accepts multipart field `audio` and returns `{"text":"..."}`. |
| `GET` | `/api/v1/admin/uptime` | Returns the server-ready UTC time, current UTC time, and uptime seconds. |
| `GET` | `/api/v1/global/stats` | Returns cumulative OpenAI input and output tokens for this process. |
| `POST` | `/api/v1/admin/shutdown` | Accepts one graceful-shutdown request with HTTP 202; a duplicate receives HTTP 409. |

The administration and statistics responses follow the assignment-provided OpenAPI YAML exactly, including paths, status codes, property names, and five-field JSON errors.

## Architecture

```text
Browser
  |
  |-- GET /api/v1/admin/uptime
  |      `-- AdminController -> ServerLifecycleService
  |
  |-- MediaRecorder -> audio chunks -> audio Blob
  |      `-- POST /api/v1/transcriptions (multipart field "audio")
  |              `-- TranscriptionController
  |                      `-- TranscriptionService
  |                              |-- local profile -> StubTranscriptionService
  |                              `-- normal profile -> OpenAiTranscriptionService
  |                                      |-- OpenAI transcription API
  |                                      `-- GlobalStatsService
  |
  |-- GET /api/v1/global/stats
  |      `-- GlobalStatsController -> GlobalStatsService
  |
  `-- POST /api/v1/admin/shutdown
         `-- AdminController -> ApplicationShutdownService
                                  `-- ApplicationTerminator -> clean JVM exit

ApiExceptionHandler surrounds the controller boundary and converts failures into
consistent, safe JSON. Tomcat uses virtual threads so blocking requests can overlap.
```

The packages separate responsibilities:

- `controller`: HTTP paths, inputs, status codes, and response bodies.
- `service`: transcription, lifecycle, statistics, and shutdown behaviour.
- `model`: JSON request/response shapes represented by Java records.
- `config`: shared outbound HTTP client configuration.
- `exception`: named application failures handled at the HTTP boundary.
- `static`: the browser interface, styling, and recording behaviour.

## Architecture decisions and justification

These are the significant places where several reasonable designs were available. Each choice was made for the assignment's functional, concurrency, security, deployment, or testability requirements.

### 1. Spring MVC with virtual threads

**Chosen:** Spring MVC and embedded Tomcat process each request using virtual threads.

**Alternatives considered:** a larger pool of platform threads, `@Async`/`CompletableFuture`, or a reactive WebFlux application.

**Why:** the OpenAI call is naturally blocking. Virtual threads allow many requests to wait on network I/O without requiring a large number of expensive operating-system threads, while keeping the controller and service code readable and synchronous. WebFlux could also scale but would introduce a different programming model throughout a small MVC application. Manually adding `@Async` would add futures and another executor without removing the blocking boundary.

`spring.threads.virtual.enabled=true` enables the model. `spring.main.keep-alive=true` ensures that virtual threads alone do not allow the JVM to finish while the server should remain alive.

**Evidence:** `BlockingRequestConcurrencyTests` starts the real server, sends 201 multipart HTTP requests through Tomcat and `TranscriptionController`, and holds every request inside a blocking service until all 201 have entered. It then releases them and verifies all 201 HTTP 200 responses within the timeout.

### 2. Controller/service separation with constructor injection

**Chosen:** controllers depend on service interfaces or focused service classes supplied through constructor injection.

**Alternative considered:** place OpenAI request construction, counters, and lifecycle logic directly in controllers.

**Why:** controllers remain responsible only for HTTP behaviour. External calls and shared state can be tested independently, while MockMvc controller tests replace dependencies with controlled mocks. Constructor injection also makes required dependencies explicit and prevents partially initialized objects.

**Evidence:** `TranscriptionControllerTests` substitutes `TranscriptionService`; `AdminControllerTests` substitutes lifecycle and shutdown services; `OpenAiTranscriptionServiceTests` checks the external boundary separately.

### 3. One transcription interface with profile-selected implementations

**Chosen:** `TranscriptionService` has a `local` stub implementation and a non-local OpenAI implementation selected by Spring profiles.

**Alternatives considered:** comment code in and out, require a real key for every local run, or make the controller know which implementation to call.

**Why:** the browser and controller use the same contract in every environment. Local development is deterministic, fast, and credential-free, while TITAN's normal profile exercises the real service. Environment differences remain configuration rather than conditional controller logic.

**Evidence:** the local application and controller tests use the stub or a mock; `DefaultProfileApplicationTests` confirms that the deployment-style context selects and constructs the real boundary when a safe test-only value is supplied.

### 4. Runtime-only API credentials

**Chosen:** the OpenAI bearer token is read from the operating-system environment as `OPENAI_API_KEY` and retained only in process memory.

**Alternatives rejected:** hard-coding it, placing it in frontend JavaScript, committing it in properties, or copying TITAN's key into a local `.env` file.

**Why:** the browser and repository are untrusted places for a commercial API credential. Runtime injection follows the assignment's cloud-security requirement and lets TITAN provide its token without revealing it.

**Evidence:** the key is validated during construction but its value is never returned or logged. `OpenAiTranscriptionServiceTests` verifies that missing configuration is rejected. Local work uses the stub and therefore needs no credential.

### 5. Spring `RestClient` with proxy-aware bounded requests

**Chosen:** Spring's synchronous `RestClient` uses `SimpleClientHttpRequestFactory`, a three-second connection timeout, and an eight-second read timeout.

**Alternatives considered:** a separate HTTP library, an unbounded default request, or a reactive client.

**Why:** `RestClient` fits the MVC programming model and supports typed JSON responses and multipart requests. The selected JDK-backed request factory honours the JVM proxy properties supplied in TITAN. Timeouts prevent a failed external dependency from occupying a request forever.

The browser-facing multipart field is named `audio`, while OpenAI's multipart file field is named `file`; the service deliberately translates between these two external contracts.

**Evidence:** `OpenAiTranscriptionServiceTests` uses a mock HTTP server to verify the path, bearer authentication, multipart audio/model fields, response parsing, and token recording without contacting OpenAI. TITAN verified the actual network, proxy, credential, latency, and transcription path.

### 6. Native browser recording followed by one multipart upload

**Chosen:** browser `MediaRecorder` collects non-empty chunks, creates one `Blob` when recording stops, and uploads it with `FormData` and `fetch`.

**Alternatives considered:** continuously stream audio chunks, add a frontend framework, or record on the Java server.

**Why:** the assignment limits a recording to five minutes and evaluates short recordings. One upload keeps the protocol and failure recovery understandable, requires no custom streaming server, and keeps microphone access in the browser where permission can be requested properly. The browser creates the multipart boundary automatically, so JavaScript deliberately does not set the `Content-Type` header itself.

The interface disables unsafe duplicate actions, releases every microphone track, resets state after success or failure, supports another recording without refresh, exposes live status text, and changes the accessible button label between “Start recording” and “Stop recording.”

**Evidence:** microphone and repeated-recording flows were manually verified in Chrome. TITAN found both controls, recognized the visible start/stop states, and verified the exact displayed transcription.

### 7. Thread-safe token statistics as one synchronized pair

**Chosen:** one shared `GlobalStatsService` synchronizes both updates and snapshots of `inputTokens` and `outputTokens`.

**Alternatives considered:** unprotected `long` fields or two independent atomic counters.

**Why:** ordinary increments can lose updates when requests overlap. Two independent atomics prevent lost individual increments but a reader could observe one new value and one old value. One lock treats the counters as a consistent pair and is small enough that lock contention is negligible compared with a network transcription.

Usage is recorded only after OpenAI has returned both valid text and usage data, so failed transcriptions do not change successful-use statistics.

**Evidence:** `GlobalStatsControllerTests` verifies the initial zero snapshot and accumulated values. TITAN verified statistics before and after a real transcription.

### 8. Atomic, single-winner graceful shutdown

**Chosen:** `AtomicBoolean.compareAndSet(false, true)` allows exactly one shutdown request to win. A separate non-daemon worker waits briefly for the HTTP 202 response handoff and then calls the isolated `ApplicationTerminator`.

**Alternatives considered:** a plain boolean, a synchronized controller, or closing Spring directly on the HTTP request thread.

**Why:** a plain check followed by assignment is a race: two callers could both observe `false`. The atomic operation combines the decision and state change. Closing the server inside the request thread could interrupt delivery of the acknowledgement. Isolating `System.exit` behind `ApplicationTerminator` makes the dangerous boundary replaceable in tests.

Spring's graceful-shutdown setting allows in-flight requests to finish, with a ten-second lifecycle phase limit so shutdown cannot wait indefinitely.

**Evidence:** `ApplicationShutdownServiceTests` releases 16 callers together, proves exactly one is accepted, and verifies one termination request. Controller tests verify HTTP 202 and 409 bodies. Local JAR and TITAN checks both observed a clean process exit.

### 9. Uptime begins when the web server is usable

**Chosen:** `ServerLifecycleService` records the first `WebServerInitializedEvent` timestamp in an `AtomicReference`.

**Alternative considered:** capture the time when the Spring bean or Java process is constructed.

**Why:** bean construction happens before Tomcat is ready to receive requests, so it overstates user-observable uptime. The web-server event represents the useful start boundary. Atomic first-write behaviour keeps the timestamp stable if event or early-request paths overlap; the first request also provides a safe fallback for an extremely early call.

**Evidence:** `ServerLifecycleServiceTests` verifies event time and duration calculation. Two local calls retained one start time while both current time and numeric uptime advanced equally. The corrected packaged JAR passed TITAN's initial and final uptime checks.

### 10. Centralized and safe API errors

**Chosen:** `ApiExceptionHandler` applies one five-field `ErrorResponse` shape across controllers. A custom `InvalidAudioUploadException` classifies empty audio as HTTP 400, and the handler also converts Spring's missing-multipart failure. Unexpected exceptions become the YAML-compatible generic HTTP 500 response.

**Alternatives considered:** let Spring return its default error format, build responses separately in every controller, or expose exception messages to clients.

**Why:** one boundary prevents endpoints from drifting into inconsistent schemas. A named exception distinguishes a known client error from unrelated programming errors. Generic public messages avoid leaking upstream details, credentials, or internal implementation information.

Logs contain only the request path, status where applicable, and exception class—not exception messages, audio, transcript text, or the API key.

**Evidence:** controller tests cover valid, missing, empty, duplicate, and unexpected paths. Captured-output assertions prove a simulated sensitive upstream message appears in neither the HTTP response nor logs while the safe exception classification is retained.

### 11. Java records for JSON response models

**Chosen:** immutable Java records represent uptime, statistics, shutdown, transcription, OpenAI, and error responses.

**Alternative considered:** mutable JavaBean classes with getters, setters, and no-argument constructors.

**Why:** each response is a fixed data value. Records express the exact fields compactly, are immutable after construction, and are serialized by Spring without repetitive boilerplate. Jackson annotations map OpenAI's snake-case token names and ignore unrelated future response fields.

**Evidence:** MockMvc tests assert the exact JSON property names and values required at every controller boundary.

### 12. Layered regression evidence instead of one end-to-end test

**Chosen:** small unit/slice tests, mocked external-HTTP tests, real-server concurrency tests, manual browser checks, and TITAN end-to-end verification each cover a different boundary.

**Alternative considered:** depend only on manual testing or one large end-to-end test.

**Why:** a single test cannot clearly identify whether a failure came from JSON mapping, a controller, concurrency, a browser, network proxying, credentials, or packaging. Fast focused tests provide precise regression feedback; real-server and TITAN checks then prove that the layers work together.

## Test evidence and assurance

| Test class | Why it exists and expected result | Assurance provided |
|---|---|---|
| `SpeechToTextApplicationTests` | The local-profile Spring context must start. | Core beans and local configuration are compatible. |
| `DefaultProfileApplicationTests` | The deployment-style context must start with a safe injected test value. | The non-local OpenAI implementation and its dependencies can be constructed. |
| `StaticPageTests` | `GET /` must return the web page. | Spring packages and serves the static interface. |
| `TranscriptionControllerTests` (4 tests) | Valid audio returns text; empty/missing audio returns standard 400; an unexpected service failure returns a safe 500. | Multipart mapping, delegation, JSON, centralized errors, no unnecessary service calls, and no sensitive response/log leakage. |
| `OpenAiTranscriptionServiceTests` (2 tests) | A mocked OpenAI request must have the right authentication, multipart data, mapping, and usage; a missing key must fail safely. | The external HTTP boundary is correct without spending money or depending on the internet. |
| `GlobalStatsControllerTests` | Initial and accumulated token totals must match. | The endpoint and shared counter behaviour are correct. |
| `AdminControllerTests` (4 tests) | Uptime, accepted shutdown, duplicate shutdown, and YAML-compatible unexpected error responses must match exactly. | Administrative paths, status codes, and JSON contracts are protected. |
| `ServerLifecycleServiceTests` | Uptime must begin at the server-ready event. | Start time is stable and duration calculation is accurate. |
| `ApplicationShutdownServiceTests` | Of 16 simultaneous callers, exactly one must win and termination must be requested once. | The required race condition is controlled by atomic state. |
| `BlockingRequestConcurrencyTests` | All 201 real blocking HTTP requests must enter together and then return HTTP 200 within the timeout. | Tomcat, Spring MVC, multipart binding, the controller, and virtual-thread configuration handle more than 200 overlapping blocking requests. |

The suite has 17 tests in total. Mocks are used only where an external side effect would make a focused test unsafe or unreliable. The real-server concurrency test does not mock the web layer, and TITAN supplies the final browser, network, OpenAI, executable-JAR, and process-lifecycle evidence.

## Security and operational boundaries

- `OPENAI_API_KEY` is runtime-only and never sent to the browser or written to logs.
- Audio contents and returned transcripts are not written to backend logs.
- Outbound connection and read timeouts bound external waiting.
- Shared mutable lifecycle and statistics state is protected against races.
- Error responses hide internal exception details.
- The local profile prevents accidental paid API calls during ordinary development.
- `/api/v1/admin/shutdown` is intentionally unauthenticated because the supplied assignment API specifies it that way. A real public deployment should authenticate or remove this operation.
- Statistics are process-local and reset when the application restarts, as required by the assignment.

## Generative-AI assistance and provenance

Generative AI—primarily OpenAI Codex/ChatGPT—assisted with design discussions, Java and Spring explanations, implementation suggestions, some code and test drafting, debugging, and documentation structure. The project was developed incrementally rather than generated as one submission. Suggestions were reviewed against the assignment brief and OpenAPI contract, then verified through focused tests, the complete Maven suite, manual browser and `curl` checks, Git review, and TITAN.

The developer remained responsible for applying and reviewing the work, running the verification steps, understanding the design decisions documented above, and protecting the university-provided credential. No TITAN API key was requested, copied, logged, committed, or exposed to the browser or generative-AI conversation.

## Incremental development evidence

The Git history records separate milestones for the uptime API, frontend foundation, microphone capture and cleanup, transcription abstraction, stub upload, OpenAI integration, statistics, graceful shutdown, uptime correction, concurrency hardening, and standardized errors. This makes each design change reviewable and preserves the reasoning path that led to the final application.
