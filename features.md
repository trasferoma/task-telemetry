# features.md — Funzionalità future (backlog)

Questo file raccoglie le funzionalità non ancora implementate e non pianificate
per l'immediato. `SPEC.md` resta il documento di riferimento per ciò che è
implementato e per i requisiti.

---

## Dispatch asincrono dei listener (SPEC §17)

**Stato: non pianificato per ora.** Il dispatch resta **sincrono** di default.

### Scopo
Disaccoppiare l'esecuzione dei listener dal thread del task. Oggi la consegna è
sincrona sul thread del chiamante (`InMemoryTaskTransport.publish` +
`TaskReporter.emit` `synchronized`): un listener **lento** (I/O, DB, UI remota)
rallenta il task, perché il task aspetta che tutti i `onEvent` finiscano prima di
proseguire. Per una telemetria "live" l'osservatore non dovrebbe penalizzare
l'osservato.

### Cosa cambierebbe
Il task accoda l'evento e prosegue subito; un thread separato consegna ai
listener. Il task non aspetta più gli osservatori e il monitor del reporter si
libera immediatamente.

### Avvertenza semantica (importante)
In modalità sincrona, un'eccezione del listener risale fino a `publish` e il
reporter applica la publish-failure policy (`ignore/log/rethrow`). In async la
`publish` ritorna prima che il listener giri, quindi:
- `rethrow` **non** può più rilanciare il fallimento nel task;
- il dispatcher deve **isolare e loggare** le eccezioni dei listener sul proprio
  thread (un listener rotto non deve fermare il loop né bloccare gli altri).

### Decisioni aperte (da affrontare se/quando si farà)
- **Dove**: decoratore `AsyncTaskTransport` (avvolge qualsiasi `TaskTransport`,
  riusabile e componibile) vs flag su `InMemoryTaskTransport`.
- **Backpressure**: coda *bounded con drop* + conteggio dei droppati (onesto con
  la semantica best-effort) vs coda illimitata (semplice ma rischio memory leak
  con listener lenti).
- **Ordinamento**: un singolo thread dispatcher preserva l'ordine FIFO
  (sequenceNumber in ordine).
- **Lifecycle**: l'esecutore va chiuso (thread daemon nominati, `AutoCloseable`,
  shutdown via `TaskTelemetry.close()`).

### Quando ha senso
Solo con listener lenti / I/O / non affidabili. Con listener veloci in-process il
sync va benissimo e l'async sarebbe complessità inutile.

---

## Modello progress ricco: current/max/unit (SPEC §7.2)

**Stato: non pianificato per ora.** Il progress resta una **singola percentuale
intera** (`Integer`, 0–100) abbinata agli eventi `PROGRESS`.

### Scopo
Esprimere l'avanzamento in unità concrete oltre alla sola percentuale:
```
current: 450
max:     1000
unit:    records
```
da cui la percentuale è derivabile (45%). Servono due modalità, perché non sempre
si conosce current/max: percentuale secca (esistente) e ricca (current/max/unit).

### Bozza di design (da rivedere se/quando si farà)
- record `TaskProgress(current, max, unit, percentage)` con factory che calcola la
  percentuale; `unit` etichetta libera e opzionale (es. "records", "MB", "file");
- nuovo overload `reporter.progress(long current, long max, String unit, String message)`
  che valorizza sia la `%` (in `event.progress()`) sia il dettaglio;
- l'overload esistente `progress(int, message)` resta invariato.

### Decisioni aperte
- Dove mettere current/max/unit: nel `payload` (come `TaskFailure` per `FAILED`,
  niente modifiche alla forma di `TaskEvent`, `TaskWatcher.onProgress` invariato)
  vs un **campo dedicato** tipizzato su `TaskEvent` (più pulito ma fa crescere il
  record e tocca builder/costruzioni/test).
- Se mantenere sempre disponibile la percentuale o solo in modalità ricca.

---

## Altre funzionalità future
Già descritte in `SPEC.md`, non duplicate qui:
- Transport remoti via broker: Redis/RabbitMQ/Kafka (§25). Il transport
  cross-process **localhost** è già implementato (§24, package
  `org.tasktelemetry.transport.crossprocess`).
- Auto-reconnect / gestione robusta della connessione per il transport socket (§24).
- Spring Boot starter (§25).
- Bridge SSE/WebSocket, endpoint REST, integrazione OpenTelemetry (§25).
