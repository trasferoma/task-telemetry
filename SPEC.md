# SPEC.md - Async Task Telemetry for Java

## 1. Obiettivo del progetto

Realizzare una libreria Java pura, leggera e framework-agnostic, che permetta a qualunque processo asincrono o di lunga durata di comunicare in tempo reale il proprio stato di avanzamento verso uno o più listener.

La libreria deve funzionare in un progetto Maven Java puro, senza Spring. Spring Boot dovrà essere supportato in futuro tramite un modulo opzionale, ma non deve essere una dipendenza del core.

Il progetto non deve essere uno scheduler, non deve eseguire task, non deve sostituire Spring Batch, Quartz, JobRunr, Temporal o OpenTelemetry. Deve solo fornire un modo semplice, esplicito e disaccoppiato per emettere e ascoltare eventi live prodotti da task asincroni.

Nome provvisorio del progetto: **task-telemetry**.

---

## 2. Problema da risolvere

In Java, e in particolare nei sistemi backend, è comune avere processi asincroni o lunghi:

- job schedulati;
- import/export;
- batch custom;
- elaborazioni lanciate da REST API;
- task eseguiti su thread separati;
- comandi CLI Java;
- migrazioni o attività manutentive.

Spesso questi task non hanno un modo standard, semplice e live per comunicare:

- che sono partiti;
- a che punto sono;
- se sono ancora vivi;
- se hanno generato messaggi informativi o warning;
- se sono terminati correttamente;
- se sono falliti.

La soluzione tipica è scrivere stato su DB, file, cache, log o meccanismi custom. Questo progetto vuole fornire un meccanismo live, leggero e opzionalmente effimero, senza persistenza obbligatoria.

---

## 3. Principio architetturale

Il task non deve conoscere chi lo ascolta.

Il task emette eventi tramite un `TaskReporter`. Uno o più listener ricevono gli eventi tramite un `TaskTelemetry` runtime configurato con un transport.

Schema concettuale:

```text
Async task
  -> TaskReporter
      -> TaskEvent
          -> TaskTransport
              -> TaskListener(s)
```

Il transport deve essere pluggabile. La prima implementazione può essere in-memory o local socket. In futuro potranno essere aggiunti Redis, RabbitMQ, Kafka o altri transport.

---

## 4. Non-obiettivi

La libreria NON deve:

- schedulare task;
- eseguire task;
- fare retry automatici del task;
- gestire code persistenti;
- garantire consegna esattamente una volta;
- salvare obbligatoriamente lo stato su DB;
- fornire una dashboard obbligatoria;
- imporre Spring;
- usare annotazioni come meccanismo principale;
- diventare una libreria reattiva tipo RxJava/Reactor;
- diventare un sistema di workflow orchestration.

Se una feature spinge il progetto verso uno di questi punti, va considerata fuori scope almeno per la prima versione.

---

## 5. Design API: filosofia

L'API deve essere:

- esplicita;
- piccola;
- leggibile;
- testabile;
- senza magia;
- senza classpath scanning;
- senza annotazioni obbligatorie;
- usabile sia in Java puro sia in Spring.

Le annotazioni non fanno parte del design iniziale. La configurazione deve avvenire tramite builder Java.

---

## 6. Concetti principali

### 6.1 Task

Un task è qualunque processo applicativo asincrono o lungo che vuole comunicare telemetria live.

Esempi:

- `IMPORT_CLIENTI`;
- `EXPORT_PRATICHE`;
- `SYNC_DOCUMENTI`;
- `MIGRAZIONE_DATI_2026`.

### 6.2 Execution

Ogni esecuzione concreta di un task deve avere un `executionId`.

Esempio:

```text
Task name: IMPORT_CLIENTI
Execution id: 8f3b7e4d-2c28-4b93-9cc3-0e91f53d33c2
```

Il `taskName` identifica il tipo di task. L'`executionId` identifica una singola esecuzione.

### 6.3 Correlation key

La `correlationKey` è opzionale e serve a collegare una execution a un dominio applicativo.

Esempi:

```text
pratica-556101
tenant-42
user-1827
file-import-clienti-2026.csv
```

### 6.4 TaskReporter

Il `TaskReporter` è l'oggetto usato dal task per emettere eventi.

Deve essere associato a una singola execution.

Deve implementare `AutoCloseable`, così da poter essere usato con try-with-resources.

### 6.5 TaskListener

Il `TaskListener` riceve gli eventi emessi dai task.

Il listener può filtrare gli eventi per:

- task name;
- execution id;
- correlation key;
- tipo evento.

### 6.6 TaskTransport

Il `TaskTransport` è l'astrazione che sostituisce il concetto di socket diretto.

Il core non deve sapere se sotto c'è:

- memoria locale;
- socket locale;
- Redis;
- RabbitMQ;
- Kafka;
- altro.

---

## 7. Eventi supportati

La prima versione deve supportare questi tipi di evento:

```text
STARTED
PROGRESS
INFO
WARNING
HEARTBEAT
COMPLETED
FAILED
CANCELLED
CUSTOM
```

### 7.1 STARTED

Emesso quando viene creata una nuova execution.

### 7.2 PROGRESS

Emesso per comunicare avanzamento.

L'avanzamento può essere espresso almeno come percentuale intera `0-100`.

In futuro si può valutare anche un modello più ricco:

```text
current
max
unit
```

Esempio:

```text
current: 450
max: 1000
unit: records
```

### 7.3 INFO

Messaggio informativo.

### 7.4 WARNING

Messaggio non bloccante ma rilevante.

### 7.5 HEARTBEAT

Segnale periodico che indica che la execution è ancora viva.

Non deve richiedere comunicazione bidirezionale. Il task non riceve ping dal listener. Il reporter emette periodicamente heartbeat finché è aperto.

### 7.6 COMPLETED

Evento terminale di successo.

### 7.7 FAILED

Evento terminale di errore.

Deve poter contenere almeno:

- messaggio di errore;
- tipo eccezione;
- stack trace opzionale e configurabile.

### 7.8 CANCELLED

Evento terminale per task interrotto volontariamente.

Nella prima versione può essere presente solo come tipo evento, senza implementare un meccanismo di cancellazione remota.

### 7.9 CUSTOM

Evento custom applicativo. Deve permettere payload opzionale.

Attenzione: il payload custom non deve trasformare la libreria in un message broker generalista.

---

## 8. Modello dati dell'evento

Ogni `TaskEvent` deve contenere almeno:

```text
eventId
taskName
executionId
correlationKey opzionale
eventType
timestamp
sequenceNumber
message opzionale
progress opzionale
payload opzionale
```

Note:

- `eventId` deve identificare il singolo evento.
- `executionId` deve identificare la singola esecuzione del task.
- `sequenceNumber` deve crescere per ogni execution e aiuta i listener a rilevare buchi o riordini.
- `timestamp` deve essere generato dalla libreria.

---

## 9. Heartbeat e gestione alive

Il progetto deve prevedere un heartbeat automatico associato al ciclo di vita del `TaskReporter`.

Regola fondamentale:

```text
TaskReporter aperto  -> execution considerata viva
TaskReporter chiuso  -> heartbeat fermato
JVM morta            -> heartbeat morto
```

Il meccanismo deve funzionare così:

1. viene creata una execution;
2. il reporter emette `STARTED`;
3. la libreria avvia un thread/scheduler interno per heartbeat periodico;
4. il task lavora ed emette progress/info/warning;
5. se il task resta silenzioso, il reporter emette `HEARTBEAT`;
6. quando il task termina, emette `COMPLETED` o `FAILED`;
7. il reporter viene chiuso;
8. il thread heartbeat viene fermato.

Gli eventi normali devono valere come segnali di vita. Quindi un listener può aggiornare `lastSeenAt` sia su `HEARTBEAT`, sia su `PROGRESS`, `INFO`, `WARNING`, ecc.

### 9.1 Stato lato listener

La libreria può fornire utility lato listener per calcolare uno stato derivato:

```text
RUNNING
STALE
LOST
COMPLETED
FAILED
CANCELLED
```

Esempio configurazione:

```text
heartbeatInterval = 5 secondi
staleAfter = 15 secondi
lostAfter = 60 secondi
```

Interpretazione:

```text
ultimo evento ricevuto da 8s   -> RUNNING
ultimo evento ricevuto da 20s  -> STALE
ultimo evento ricevuto da 70s  -> LOST
```

`STALE` non significa necessariamente morto. Significa che non arrivano segnali da più tempo del previsto.

`LOST` significa che la execution è probabilmente morta, disconnessa o non più osservabile.

### 9.2 No ping bidirezionale nella prima versione

La prima versione non deve implementare:

- ping dal listener al task;
- comandi remoti;
- pause/resume;
- cancel remoto;
- ACK obbligatori;
- retry di consegna.

Queste feature possono essere valutate in futuro, ma sono fuori scope per la versione iniziale.

---

## 10. Lifecycle e AutoCloseable

Il `TaskReporter` deve implementare `AutoCloseable`.

Comportamento atteso:

- se il task chiama `completed()`, viene emesso `COMPLETED` e poi il reporter può essere chiuso;
- se il task chiama `failed(Throwable)`, viene emesso `FAILED` e poi il reporter può essere chiuso;
- se il reporter viene chiuso senza evento terminale, la libreria deve decidere una strategia esplicita.

Strategia consigliata per la prima versione:

```text
close() senza evento terminale -> emette CANCELLED oppure FAILED configurabile
```

Default consigliato:

```text
close() senza completed/failed/cancelled -> CANCELLED
```

Motivo: non sempre una chiusura senza terminal event è un errore applicativo. Potrebbe essere un'interruzione volontaria o un early return.

Per evitare ambiguità, valutare un metodo:

```text
closeAsFailedIfNotCompleted()
```

oppure una policy nel builder:

```text
onCloseWithoutTerminalEvent = CANCELLED | FAILED | IGNORE
```

Default consigliato: `CANCELLED`.

---

## 11. Configurazione tramite builder

Non devono essere necessari file di configurazione.

L'utente deve poter configurare la libreria tramite builder Java.

Esempio concettuale:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
    .transport(new InMemoryTaskTransport())
    .heartbeatInterval(Duration.ofSeconds(5))
    .staleAfter(Duration.ofSeconds(15))
    .lostAfter(Duration.ofSeconds(60))
    .build();
```

Uso minimale con default:

```java
TaskTelemetry telemetry = TaskTelemetry.defaults();
```

Avvio execution:

```java
try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI")) {
    reporter.progress(10, "Avvio import");
    reporter.progress(50, "Elaborazione in corso");
    reporter.completed("Import completato");
}
```

Ascolto eventi:

```java
telemetry.listen()
    .taskName("IMPORT_CLIENTI")
    .onEvent(event -> {
        // gestione evento
    })
    .start();
```

Nota: i frammenti sopra sono esempi di API desiderata, non implementazione definitiva.

---

## 12. Transport iniziali

### 12.1 InMemoryTaskTransport

Prima implementazione consigliata.

Caratteristiche:

- Java puro;
- stessa JVM;
- utile per test;
- utile per applicazioni monolitiche;
- nessuna serializzazione obbligatoria;
- nessuna persistenza.

Limiti:

- non comunica tra processi diversi;
- se la JVM muore, tutto viene perso.

### 12.2 LocalSocketTaskTransport

Seconda implementazione consigliata.

Caratteristiche:

- Java puro;
- comunicazione tra processi sulla stessa macchina;
- può usare TCP localhost;
- nessun DB;
- nessun file;
- nessun broker esterno.

Limiti:

- messaggi effimeri;
- gestione connessioni da progettare con attenzione;
- delivery non garantita;
- se un listener non è connesso, perde gli eventi.

### 12.3 Transport futuri

Possibili adapter futuri:

- Redis Pub/Sub;
- RabbitMQ topic exchange;
- Kafka;
- WebSocket/SSE bridge;
- HTTP callback.

Questi non devono influenzare il design del core.

---

## 13. Delivery semantics

La prima versione deve dichiarare esplicitamente una semantica semplice:

```text
best effort, live, non persistente
```

Conseguenze:

- se nessuno ascolta, l'evento può essere perso;
- se il listener parte dopo, non recupera eventi passati;
- se il transport cade, gli eventi possono essere persi;
- la libreria non garantisce exactly-once;
- la libreria non deve implementare retry persistente.

Questo comportamento è accettabile perché il progetto nasce come telemetria live, non come event sourcing o coda affidabile.

---

## 14. Moduli Maven proposti

Struttura iniziale:

```text
task-telemetry-parent
  task-telemetry-core
  task-telemetry-transport-inmemory
  task-telemetry-transport-local-socket
  task-telemetry-spring-boot-starter   opzionale/futuro
  task-telemetry-examples
```

### 14.1 task-telemetry-core

Contiene:

- `TaskTelemetry`;
- `TaskTelemetryBuilder`;
- `TaskReporter`;
- `TaskEvent`;
- `TaskEventType`;
- `TaskListener`;
- `TaskTransport`;
- `TaskExecutionDescriptor`;
- heartbeat scheduler abstraction;
- filtro listener;
- policy di chiusura.

Non deve dipendere da Spring.

### 14.2 task-telemetry-transport-inmemory

Implementazione in-memory.

Può essere usata nei test e negli esempi.

### 14.3 task-telemetry-transport-local-socket

Implementazione local socket/TCP localhost.

Deve essere progettata dopo aver stabilizzato il core.

### 14.4 task-telemetry-spring-boot-starter

Modulo futuro.

Deve fornire:

- autoconfigurazione;
- bean `TaskTelemetry`;
- properties opzionali;
- integrazione con `@Scheduled` solo tramite uso del reporter, non tramite annotazioni custom obbligatorie;
- eventuale endpoint actuator futuro.

---

## 15. Dipendenze

Il core deve avere dipendenze minime.

Preferenza:

```text
Java standard library
SLF4J opzionale
JUnit 5 per test
AssertJ per test
```

Serializzazione:

- il core non deve essere legato obbligatoriamente a Jackson;
- introdurre una SPI `TaskEventSerializer` solo quando servirà il transport socket;
- il transport in-memory non deve serializzare.

Per il transport socket si può valutare:

- Jackson come dipendenza del modulo socket;
- oppure serializzazione interna minimale.

Non introdurre dipendenze pesanti nella prima versione.

---

## 16. Compatibilità Java

Baseline scelta e implementata:

```text
Java 17
```

Motivo:

- Java 17 è baseline moderna comune nei backend;
- permette API più pulite (es. `record` per `TaskEvent`, `TaskExecutionDescriptor`);
- evita di trascinarsi compatibilità troppo vecchia.

Il `pom.xml` usa `maven.compiler.release=17`. Java 11 resta una possibilità solo per massimizzare adozione aziendale, ma non è l'obiettivo.

---

## 17. Threading model

Il reporter deve poter avviare un heartbeat periodico.

Requisiti:

- ogni execution può avere un heartbeat associato;
- il thread heartbeat deve fermarsi quando il reporter viene chiuso;
- non creare un thread per ogni task se si può evitare;
- preferire un `ScheduledExecutorService` condiviso per telemetry instance;
- il nome dei thread deve essere riconoscibile;
- i thread devono essere daemon o configurabili.

Esempio nome thread:

```text
task-telemetry-heartbeat-1
```

Il listener dispatch può essere:

- sincrono nella prima versione in-memory;
- opzionalmente asincrono tramite executor configurabile.

Evitare complessità prematura.

---

## 18. Error handling

La libreria non deve rompere il task principale se fallisce l'invio di un evento, salvo configurazione esplicita.

Default consigliato:

```text
se fallisce publish(event): log/warning interno e il task continua
```

Policy configurabile:

```text
PublishFailurePolicy.IGNORE
PublishFailurePolicy.LOG
PublishFailurePolicy.THROW
```

Default: `LOG` oppure `IGNORE` se non si vuole dipendere da logging.

Per un core minimale senza SLF4J, si può usare un `TaskTelemetryErrorHandler` configurabile.

---

## 19. Listener filtering

Il listener deve poter filtrare gli eventi.

Filtri minimi:

```text
taskName
executionId
correlationKey
eventType
```

Il filtro deve essere applicabile lato runtime prima di invocare il listener.

---

## 20. Testabilità

La libreria deve essere facile da testare.

Requisiti:

- clock iniettabile o astratto;
- scheduler heartbeat testabile;
- transport in-memory usabile nei test;
- possibilità di verificare eventi emessi;
- niente sleep reali nei test dove possibile;
- preferire test deterministici.

Test minimi:

1. start emette `STARTED`;
2. progress emette `PROGRESS`;
3. completed emette `COMPLETED`;
4. failed emette `FAILED`;
5. close ferma heartbeat;
6. heartbeat viene emesso mentre il reporter è aperto;
7. nessun heartbeat dopo close;
8. eventi normali aggiornano lastSeen;
9. listener filtrato riceve solo eventi coerenti;
10. due execution dello stesso task restano distinguibili tramite `executionId`.

---

## 21. Esempi d'uso desiderati

### 21.1 Java puro

Scenario: import clienti lanciato manualmente.

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
    .transport(new InMemoryTaskTransport())
    .heartbeatInterval(Duration.ofSeconds(5))
    .build();

try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI")) {
    reporter.progress(0, "Import avviato");
    reporter.progress(50, "Metà file elaborato");
    reporter.completed("Import completato");
}
```

### 21.2 Listener Java puro

```java
telemetry.listen()
    .taskName("IMPORT_CLIENTI")
    .onEvent(System.out::println)
    .start();
```

### 21.3 Uso futuro in Spring

```java
@Component
class ImportClientiJob {

    private final TaskTelemetry telemetry;

    ImportClientiJob(TaskTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Scheduled(cron = "0 0 2 * * *")
    void run() {
        try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI")) {
            reporter.progress(0, "Import avviato");
            // lavoro reale
            reporter.completed("Import completato");
        }
    }
}
```

Questa integrazione Spring non richiede annotazioni custom della libreria.

---

## 22. Convenzioni naming

Nomi consigliati:

```text
TaskTelemetry
TaskTelemetryBuilder
TaskReporter
TaskEvent
TaskEventType
TaskListener
TaskTransport
TaskExecution
TaskExecutionStatus
TaskHeartbeatMonitor
```

Evitare nomi troppo legati a:

- scheduler;
- reactive;
- stream;
- broker;
- batch.

Il progetto deve comunicare che si occupa di telemetria live per task asincroni.

---

## 23. Versione 0.1 - scope consigliato

La versione 0.1 deve essere piccola.

Includere:

- core API;
- builder;
- `TaskReporter` AutoCloseable;
- eventi base;
- heartbeat automatico;
- in-memory transport;
- listener filtering;
- test unitari;
- esempio Java puro.

Escludere:

- Spring Boot starter;
- socket transport;
- Redis;
- dashboard;
- persistenza;
- comandi remoti;
- cancellazione remota;
- retry avanzati;
- OpenTelemetry integration.

---

## 24. Versione 0.2 - scope possibile

Possibili evoluzioni:

- local socket/TCP localhost transport;
- serializer SPI;
- esempi con due processi Java sulla stessa macchina;
- utility per stato `RUNNING/STALE/LOST` lato listener;
- migliore gestione failure di transport.

---

## 25. Versione 0.3+ - idee future

Possibili evoluzioni, da non implementare subito:

- Spring Boot starter;
- Redis Pub/Sub transport;
- RabbitMQ transport;
- Kafka transport;
- bridge SSE/WebSocket per UI;
- endpoint REST opzionale;
- integrazione OpenTelemetry opzionale;
- dashboard separata;
- storage opzionale per ultimo stato noto.

Tutte queste feature devono rimanere opzionali.

---

## 26. Rischi architetturali

### 26.1 Diventare un job runner

Rischio: aggiungere esecuzione task, scheduling, retry, worker pool.

Contromisura: la libreria non esegue mai il lavoro. Osserva soltanto tramite eventi emessi dal codice applicativo.

### 26.2 Diventare un message broker

Rischio: payload generici, routing complesso, ACK, retry, persistenza.

Contromisura: limitare il modello a eventi di telemetria task.

### 26.3 Diventare una libreria reactive

Rischio: operatori, stream processing, backpressure, pipeline.

Contromisura: listener callback semplice e transport pluggabile.

### 26.4 Troppa magia

Rischio: annotazioni, reflection, classpath scanning, configurazioni implicite.

Contromisura: builder esplicito, API diretta, zero annotazioni obbligatorie.

---

## 27. Decisioni già prese

1. La libreria deve essere Java pura.
2. Spring deve essere opzionale e successivo.
3. Niente annotazioni nella prima versione.
4. Configurazione tramite builder Java.
5. Il task comunica solo in uscita.
6. Niente comunicazione bidirezionale nella prima versione.
7. Heartbeat automatico legato al ciclo di vita del `TaskReporter`.
8. `TaskReporter` implementa `AutoCloseable`.
9. Nessuno storage obbligatorio.
10. Delivery best effort live.
11. Transport pluggabile.
12. Primo transport: in-memory.
13. Secondo transport possibile: local socket/TCP localhost.

---

## 28. Domande aperte

Risposte adottate nell'implementazione corrente:

1. Nome del progetto: `task-telemetry` (confermato come nome di lavoro, non ancora deciso come definitivo).
2. Default policy per `close()` senza evento terminale: **`CANCELLED`** (RISOLTA). Configurabile via `TaskReporter.CloseBehavior` (`CANCELLED`, `FAILED`, `IGNORE`).
3. Java baseline: **17** (RISOLTA).
4. SLF4J nel core oppure error handler interno: parzialmente aperta. `slf4j-api` è dipendenza **optional**; la publish-failure policy / error handler (§18) **non è ancora implementata**: l'`InMemoryTaskTransport` lascia propagare l'eccezione del listener al chiamante di `publish`.
5. Progress: **solo percentuale intera 0-100** in v1 (RISOLTA per ora). Il modello `current/max/unit` resta futuro.
6. `TaskTelemetry`: **istanza esplicita** creata via builder, nessun singleton globale (RISOLTA).
7. Dispatch del listener: **sincrono** in v1 (RISOLTA). L'opzione asincrona resta futura.
8. Transport in-memory: **solo inoltro live**, nessun ultimo stato mantenuto (RISOLTA).

---

## 29. Indicazioni per Claude Code

Implementare in modo incrementale.

Layout Maven previsto (multi-modulo):

```text
task-telemetry-core
task-telemetry-transport-inmemory
task-telemetry-examples
```

> Stato attuale: il progetto è ancora a **modulo singolo**, ma le classi sono
> organizzate per funzionalità in sotto-package:
> `org.tasktelemetry` (runtime: `TaskTelemetry`, `TaskReporter`),
> `org.tasktelemetry.event` (`TaskEvent`, `TaskEventType`, `TaskExecutionDescriptor`),
> `org.tasktelemetry.transport` (`TaskTransport`, `InMemoryTaskTransport`),
> `org.tasktelemetry.heartbeat` (`HeartbeatScheduler`, `HeartbeatHandle`, `ExecutorHeartbeatScheduler`),
> `org.tasktelemetry.listener` (`TaskListener`, `ListenerRegistration`, `ListenerHandle`, `FilteringTaskListener`),
> `org.tasktelemetry.example` (`PureJavaExample`).
> La conversione a multi-modulo resta da fare.

Non implementare subito socket, Redis, Spring o dashboard.

Prima stabilizzare il core e i test.

Ordine suggerito:

1. creare enum `TaskEventType`;
2. creare modello immutabile `TaskEvent`;
3. creare descriptor execution;
4. creare interfaccia `TaskTransport`;
5. creare interfaccia/funzionale `TaskListener`;
6. creare `TaskReporter`;
7. creare `TaskTelemetry` e builder;
8. implementare heartbeat automatico;
9. implementare in-memory transport;
10. aggiungere listener filtering;
11. aggiungere test unitari;
12. aggiungere esempio Java puro.

Favorire codice semplice, leggibile, con early return quando utile.

Non introdurre framework non necessari.

Non introdurre Spring nella prima implementazione.

Non creare annotazioni.

Non creare una dashboard.

Non implementare un sistema di scheduling.

---

## 30. Stato di implementazione (giugno 2026)

Baseline: Java 17, Maven, modulo singolo. Dipendenze di test: JUnit 5, AssertJ,
Mockito, Instancio. Build con unit test (Surefire) e integration test `*IT`
(Failsafe).

### 30.1 Implementato

- `TaskEventType`: i 9 tipi previsti, con `isTerminal()` per i terminali
  (`COMPLETED`, `FAILED`, `CANCELLED`).
- `TaskEvent`: record immutabile con builder e validazione (campi richiesti non
  nulli/non vuoti, `sequenceNumber >= 0`, `progress` in 0-100 se presente).
  Campi opzionali (`correlationKey`, `message`, `progress`, `payload`) nullabili.
- `TaskExecutionDescriptor`: record (`taskName`, `executionId`, `correlationKey`)
  con factory `of(taskName, executionId)`.
- `TaskTransport`: SPI con `publish` / `subscribe` / `unsubscribe`.
- `TaskListener`: interfaccia funzionale `onEvent(TaskEvent)`.
- `InMemoryTaskTransport`: dispatch sincrono, thread-safe, nessuna
  serializzazione, nessuna history; l'eccezione di un listener propaga al
  chiamante di `publish`.
- `TaskReporter` (`AutoCloseable`): emette `STARTED` alla creazione; API
  `progress` / `info` / `warning` / `heartbeat` / `custom` e terminali
  `completed` / `failed` / `cancelled`; genera `eventId`
  (`executionId-sequenceNumber`), `timestamp` da `Clock` iniettabile e
  `sequenceNumber` monotono; close policy via `CloseBehavior` (default
  `CANCELLED`); dopo un terminale ogni emissione lancia `IllegalStateException`,
  `close()` diventa idempotente. Thread-safe (emissione serializzata).
- Heartbeat automatico: `HeartbeatScheduler` / `HeartbeatHandle` (astrazione
  testabile) ed `ExecutorHeartbeatScheduler` (thread daemon
  `task-telemetry-heartbeat-N`). Emette `HEARTBEAT` solo in caso di silenzio (gli
  eventi normali sopprimono il battito successivo); si ferma su evento terminale
  e su close.
- `TaskTelemetry` + builder (`AutoCloseable`): `defaults()`, `start(taskName)` /
  `start(taskName, correlationKey)`, `listen()`. Chiude lo scheduler solo se di
  proprietà del runtime. `executionId` generato via `Supplier<String>`
  (default UUID, sovrascrivibile per test deterministici).
- Listener filtering (§19): `FilteringTaskListener` (filtri per `taskName`,
  `executionId`, `correlationKey`, `eventType`, in AND; filtro nullo = match con
  tutto), `ListenerRegistration` fluente, `ListenerHandle` per de-registrare.
- Esempio Java puro: `org.tasktelemetry.example.PureJavaExample`.

### 30.2 Non ancora implementato

- Publish-failure policy / error handler interno (§18): oggi l'eccezione del
  listener propaga, nessuna policy `IGNORE/LOG/THROW`.
- Stato derivato lato listener `RUNNING/STALE/LOST` (§9.1).
- Dispatch asincrono opzionale (§17): solo sincrono.
- Modello progress ricco `current/max/unit` (§7.2): solo percentuale.
- `failed(Throwable)`: il messaggio è `Throwable.toString()` e il throwable
  finisce nel `payload`; nessuna opzione configurabile per lo stack trace (§7.7).
- Layout Maven multi-modulo (§14).
- Transport socket, Spring Boot starter e transport remoti (§24, §25).

### 30.3 Note di design

- `eventId` è deterministico (`executionId-sequenceNumber`), non un UUID a sé:
  univoco dato un `executionId` univoco e comodo per i test. Reso eventualmente
  configurabile in futuro.
- Nessuna SPI di serializzazione: verrà introdotta solo con il transport socket
  (§15).

