# task-telemetry

[English version](README.md)

**task-telemetry** è una libreria Java leggera per emettere e osservare telemetria live da task asincroni o di lunga durata.

Permette a un task di comunicare:

* quando parte;
* a che punto è;
* messaggi informativi e warning;
* se è ancora vivo;
* come è terminato.

Il task non sa chi lo ascolta. Emette eventi e basta. Uno o più listener possono osservare questi eventi tramite un transport pluggabile.

## Perché

Nei sistemi backend esistono spesso operazioni lunghe:

* job schedulati;
* import ed export;
* task in background;
* comandi di manutenzione;
* processi CLI;
* operazioni asincrone lanciate da API REST.

La soluzione tipica è scrivere lo stato su log, file, database, cache o tabelle custom. Funziona, ma spesso accoppia il task a chi lo osserva e introduce persistenza anche quando serve solo visibilità live.

**task-telemetry** fornisce un modo piccolo, esplicito e indipendente dal framework per pubblicare eventi live di un task.

## Cosa non è

Questa libreria, volutamente, non è:

* uno scheduler;
* un job runner;
* un workflow engine;
* un message broker;
* una coda persistente;
* un sostituto di Spring Batch, Quartz, JobRunr, Temporal o OpenTelemetry;
* una libreria reactive;
* una dashboard.

Osserva i task. Non li esegue.

## Funzionalità

* Libreria Java pura.
* Baseline Java 17.
* Progetto Maven.
* Nessuna dipendenza runtime.
* Core indipendente dai framework.
* Spring non è richiesto.
* Configurazione esplicita tramite builder.
* Nessuna annotazione obbligatoria.
* Nessun classpath scanning.
* Consegna live best-effort.
* Heartbeat automatico.
* Filtri sui listener.
* Transport in-memory.
* Transport socket localhost per processi separati.
* Helper `TaskWatcher` per consumatori di alto livello.
* Stato di vita derivato: `RUNNING`, `STALE`, `LOST`, `COMPLETED`, `FAILED`, `CANCELLED`.

## Concetti principali

### Task

Un task è un tipo logico di lavoro.

Esempi:

* `IMPORT_CLIENTI`
* `EXPORT_REPORT`
* `UPLOAD_FILE`
* `MIGRAZIONE_DATI_2026`

### Execution

Una execution è una singola esecuzione concreta di un task.

Ogni execution ha un `executionId`.

### Correlation key

La correlation key è facoltativa. Collega una execution a un valore del dominio applicativo.

Esempi:

* `clienti-2026.csv`
* `tenant-42`
* `user-1827`
* `pratica-556101`

### Reporter

Un `TaskReporter` viene usato dal task per emettere eventi.

È associato a una singola execution e implementa `AutoCloseable`.

### Listener

Un listener riceve gli eventi del task.

I listener possono filtrare per:

* nome del task;
* execution id;
* correlation key;
* tipo evento.

### Transport

Il transport è il canale usato per consegnare gli eventi.

Transport attuali:

* in-memory;
* socket localhost.

Transport futuri potrebbero includere Redis, RabbitMQ, Kafka, bridge WebSocket/SSE o callback HTTP.

## Tipi di evento

| Evento      | Significato                                                |
| ----------- | ---------------------------------------------------------- |
| `STARTED`   | L'esecuzione è iniziata.                                   |
| `PROGRESS`  | Il task comunica un avanzamento da 0 a 100.                |
| `INFO`      | Messaggio informativo.                                     |
| `WARNING`   | Warning non bloccante.                                     |
| `HEARTBEAT` | Il task è ancora vivo.                                     |
| `COMPLETED` | Il task è terminato con successo.                          |
| `FAILED`    | Il task è terminato con errore.                            |
| `CANCELLED` | Il task è stato annullato o chiuso senza evento terminale. |

## Modello evento

Ogni evento contiene:

* `eventId`;
* `taskName`;
* `executionId`;
* `correlationKey` opzionale;
* `eventType`;
* `timestamp`;
* `sequenceNumber`;
* `message` opzionale;
* `progress` opzionale.

Gli eventi trasportano solo dati scalari più `message` e `progress`.

Non esiste un payload arbitrario di tipo `Object`. Questo mantiene il modello coerente sia in memoria sia tra processi diversi.

## Installazione

Il progetto richiede Java 17.

Se l'artefatto non è ancora pubblicato su un repository Maven pubblico, compilalo e installalo localmente:

```bash
mvn clean install
```

Poi usalo da un altro progetto Maven:

```xml
<dependency>
    <groupId>org.tasktelemetry</groupId>
    <artifactId>task-telemetry</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Esempio minimo

### Creare il runtime

```java
try (TaskTelemetry telemetry = TaskTelemetry.defaults()) {
    // usa telemetry qui
}
```

`TaskTelemetry.defaults()` crea un runtime pronto all'uso con transport in-memory e heartbeat automatico.

### Emettere eventi da un task

```java
try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI", "clienti.csv")) {
    reporter.progress(0, "Import avviato");
    reporter.info("Lettura del file");
    reporter.progress(50, "Metà elaborazione");
    reporter.warning("Riga 42 ignorata");
    reporter.progress(100, "Import completato");

    reporter.completed("Import riuscito");
}
```

Quando il reporter viene aperto, viene emesso automaticamente un evento `STARTED`.

Se il reporter viene chiuso senza un evento terminale, il comportamento di default è emettere `CANCELLED`.

### Ascoltare eventi

```java
ListenerHandle handle = telemetry.listen()
        .taskName("IMPORT_CLIENTI")
        .onEvent(event -> {
            System.out.println(event.type() + " " + event.message());

            if (event.progress() != null) {
                updateProgressBar(event.progress());
            }
        })
        .start();

// più tardi
handle.stop();
```

## Heartbeat automatico

Un task può restare silenzioso per un po' anche se è ancora in esecuzione.

Per questo `task-telemetry` può emettere automaticamente eventi `HEARTBEAT` finché il reporter è aperto.

Anche gli eventi normali, come `PROGRESS`, `INFO` o `WARNING`, valgono come segnali di vita.

Regola base:

```text
TaskReporter aperto  -> execution considerata viva
TaskReporter chiuso  -> heartbeat fermato
JVM morta            -> heartbeat fermato
```

L'intervallo dell'heartbeat può essere configurato:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .heartbeatInterval(Duration.ofSeconds(2))
        .build();
```

## TaskWatcher

`TaskWatcher` è un helper di livello più alto per chi consuma eventi.

È utile quando il consumatore vuole semplicemente:

* aspettare che il task compaia;
* ricevere aggiornamenti di avanzamento;
* reagire agli heartbeat;
* aspettare la conclusione del task o capire che è stato perso.

```java
try (TaskWatcher watcher = new TaskWatcher(transport, "UPLOAD_FILE")) {
    watcher.onProgress(percent -> updateProgressBar(percent));
    watcher.onHeartbeat(() -> log("il task è ancora vivo"));

    if (!watcher.awaitStart(Duration.ofSeconds(5))) {
        return;
    }

    TaskExecutionStatus status = watcher.awaitCompletion();

    System.out.println("Stato finale: " + status);
}
```

Stati finali possibili:

* `COMPLETED`
* `FAILED`
* `CANCELLED`
* `LOST`

## Stato di vita

Un consumatore può derivare lo stato corrente di una execution dall'ultimo evento ricevuto.

Stati supportati:

| Stato       | Significato                                               |
| ----------- | --------------------------------------------------------- |
| `RUNNING`   | Gli eventi arrivano, il task è vivo.                      |
| `STALE`     | Non arrivano eventi da più tempo del previsto.            |
| `LOST`      | La execution è probabilmente morta o non più osservabile. |
| `COMPLETED` | Il task è terminato con successo.                         |
| `FAILED`    | Il task è fallito.                                        |
| `CANCELLED` | Il task è stato annullato.                                |

Esempio di soglie:

```text
heartbeatInterval = 5 secondi
staleAfter        = 15 secondi
lostAfter         = 60 secondi
```

Interpretazione:

```text
ultimo evento da 8s  -> RUNNING
ultimo evento da 20s -> STALE
ultimo evento da 70s -> LOST
```

## Stessa JVM o processi separati

Produttore e consumatore comunicano solo se condividono lo stesso transport.

### Stessa JVM

Usa la stessa istanza di `TaskTelemetry` oppure lo stesso transport in-memory.

### Processi separati sulla stessa macchina

Usa il transport socket localhost.

Il modello ha due attori:

* il processo task fa da server;
* il processo client si connette e riceve gli eventi da quel momento in poi.

Non c'è un broker e non c'è replay degli eventi.

Gli eventi emessi prima della connessione del client non vengono recuperati.

## Semantica di consegna

L'obiettivo iniziale della libreria è osservabilità live, non consegna garantita.

Semantica attuale:

```text
best-effort, live, non persistente
```

Conseguenze:

* se nessuno ascolta, l'evento può essere perso;
* se un listener parte dopo, non riceve eventi passati;
* se il transport cade, gli eventi possono essere persi;
* non c'è garanzia exactly-once;
* non c'è retry persistente.

È una scelta esplicita.

## Gestione errori

Per default, se la pubblicazione di un evento fallisce, il task principale non deve fallire.

Politiche disponibili:

* ignorare l'errore di pubblicazione;
* loggare l'errore;
* rilanciare l'errore.

Esempio:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .errorHandler(TaskTelemetryErrorHandler.logging())
        .build();
```

## Configurazione

La configurazione è esplicita e basata su builder.

Esempio:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .transport(new InMemoryTaskTransport())
        .heartbeatInterval(Duration.ofSeconds(5))
        .closeBehavior(TaskReporter.CloseBehavior.CANCELLED)
        .logPrefix("task-telemetry -")
        .errorHandler(TaskTelemetryErrorHandler.logging())
        .build();
```

Non ci sono annotazioni obbligatorie e non ci sono file di configurazione esterni.

## Principi di design

Il progetto segue alcune regole precise:

* mantenere piccolo il core;
* mantenere esplicita l'API;
* evitare magia;
* evitare accoppiamento con framework;
* evitare persistenza nel core;
* non eseguire task;
* non schedulare task;
* non introdurre comandi bidirezionali nella prima versione;
* mantenere i transport pluggabili.

## Roadmap

Possibili evoluzioni future:

* Spring Boot starter;
* transport Redis Pub/Sub;
* transport RabbitMQ;
* transport Kafka;
* bridge WebSocket/SSE per UI;
* bridge REST opzionale;
* dashboard opzionale;
* storage opzionale per ultimo stato noto;
* integrazione OpenTelemetry opzionale.

Tutto questo deve rimanere opzionale.

## Stato attuale

Il progetto è in sviluppo attivo.

Attualmente sono implementati:

* core API;
* `TaskReporter`;
* `TaskTelemetry`;
* `TaskEvent` immutabile;
* tipi evento;
* heartbeat automatico;
* listener filtering;
* transport in-memory;
* transport socket localhost;
* `TaskWatcher`;
* monitoraggio liveness;
* esempi Java;
* test unitari e integration test.

La stabilità dell'API non è ancora garantita.

## Licenza

Aggiungere un file di licenza prima di pubblicare release stabili.

Finché una licenza non viene dichiarata esplicitamente, il fatto che il repository sia pubblico non significa automaticamente che il codice possa essere riutilizzato liberamente.
