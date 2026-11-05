#!/usr/bin/env bash
# The persons service's repository: the server of jeffrey-testapp the two real runs ("persons direct",
# "persons dom") were built from, committed, so the working directory holds the code a finding names. The files
# between the markers are fixtures/testapp/ verbatim, written by build/scripts/write-persons-scaffold.py
# (test-plugin-eval-mocks.py checks them).
set -euo pipefail
mkdir -p server/src/main/java/jeffrey/testapp/server
mkdir -p server/src/main/java/jeffrey/testapp/server/leak
mkdir -p server/src/main/java/jeffrey/testapp/server/metrics
mkdir -p server/src/main/java/jeffrey/testapp/server/service
mkdir -p server/src/main/resources
cat > server/src/main/java/jeffrey/testapp/server/ApplicationConfiguration.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_APPLICATIONCONFIGURATION_JAVA'
package jeffrey.testapp.server;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeAttributesCustomizer;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;
import jeffrey.testapp.server.leak.NativeMemoryAllocatorImpl;
import jeffrey.testapp.server.leak.NoopNativeMemoryAllocator;
import jeffrey.testapp.server.service.EfficientPersonService;
import jeffrey.testapp.server.service.InefficientPersonService;
import jeffrey.testapp.server.service.PersonService;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.Queue;

/**
 * Inbound HTTP needs no code here: jeffrey-tracing-spring-boot-starter registers a servlet filter
 * first in the chain that records every request as a {@code jeffrey.HttpServerExchange} event and
 * opens it as the root span of the request's trace, named by the matched handler pattern.
 */
@Configuration
public class ApplicationConfiguration {

    private static final String ATTRIBUTE_SERVICE_MODE = "testapp.mode";
    private static final String MODE_DIRECT = "direct";
    private static final String MODE_DOM = "dom";

    private static final String NOTIFICATION_NATIVE_MEMORY_LEAK = "NATIVE_MEMORY_LEAK_MODE_ENABLED";
    private static final String NOTIFICATION_CATEGORY = "RESOURCE";
    private static final String NOTIFICATION_SOURCE = "testapp-server";

    /**
     * Adds the service mode to every request span's attributes. Jeffrey indexes attributes one key
     * at a time, so {@code testapp.mode} becomes a facet in Traces → Attributes. Low-cardinality by
     * construction: it is one of two values.
     */
    @Bean
    public HttpExchangeAttributesCustomizer serviceModeAttributesCustomizer(
            @Value("${efficient.mode:true}") boolean efficientMode) {

        String mode = efficientMode ? MODE_DIRECT : MODE_DOM;
        return (attributes, request, response) -> attributes.put(ATTRIBUTE_SERVICE_MODE, mode);
    }

    @Bean
    public NamedParameterJdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public NativeMemoryAllocator nativeMemoryLeakingService(
            @Value("${native-memory-leak.mode:false}") boolean nativeMemoryLeakMode) {

        if (nativeMemoryLeakMode) {
            System.out.println("Native memory leak mode: Enabled.");
            notifyNativeMemoryLeakMode();
            return new NativeMemoryAllocatorImpl();
        } else {
            System.out.println("Native memory leak mode: Disabled.");
            return new NoopNativeMemoryAllocator();
        }
    }

    @Bean
    public PersonService personService(
            @Value("${efficient.mode:true}") boolean efficientMode,
            PersonRepository personRepository,
            NativeMemoryAllocator nativeMemoryAllocator) {

        System.out.println("EFFICIENT_MODE=" + efficientMode);
        return efficientMode
                ? new EfficientPersonService(personRepository, nativeMemoryAllocator)
                : new InefficientPersonService(personRepository, nativeMemoryAllocator);
    }

    @Bean
    public PersonRepository personRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PersonRepository(jdbcTemplate);
    }

    @Bean
    public Queue<Person> personQueue() {
        Integer cacheSize = Integer.getInteger("cacheSize");
        // It's not properly synchronized, but I don't care :)
        // I just need to keep some data in Old Generation
        return cacheSize == null
                ? new NoOpQueue<>()
                : new CircularFifoQueue<>(cacheSize);
    }

    private static void notifyNativeMemoryLeakMode() {
        NotificationEvent notification = new NotificationEvent();
        notification.type = NOTIFICATION_NATIVE_MEMORY_LEAK;
        notification.message = "Every other native allocation made while serving a person is never freed";
        notification.severity = Severity.HIGH.name();
        notification.category = NOTIFICATION_CATEGORY;
        notification.source = NOTIFICATION_SOURCE;
        notification.emit();
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_APPLICATIONCONFIGURATION_JAVA
cat > server/src/main/java/jeffrey/testapp/server/DatabaseConfiguration.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_DATABASECONFIGURATION_JAVA'
package jeffrey.testapp.server;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfiguration.class);

    private static final String POOL_NAME = "testapp-sqlite";

    private static final SQLiteConfig.JournalMode JOURNAL_MODE = SQLiteConfig.JournalMode.WAL;
    private static final SQLiteConfig.SynchronousMode SYNCHRONOUS = SQLiteConfig.SynchronousMode.NORMAL;
    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final SQLiteConfig.TempStore TEMP_STORE = SQLiteConfig.TempStore.MEMORY;
    private static final boolean FOREIGN_KEYS = true;

    /**
     * The pool is created with the no-arg constructor and configured through setters, so it does
     * not start until the first connection is borrowed. That leaves it un-started when
     * jeffrey-tracing-spring-boot-starter's bean post-processor sees it, which is when it attaches
     * its JFR metrics tracker: connection created/acquired/borrowed/timeout events plus the periodic
     * {@code jeffrey.JdbcPoolStatistics} event, all of which feed Jeffrey's connection-pool dashboard.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(
            @Value("${database.file}") String file,
            @Value("${database.cleanup-on-shutdown:true}") boolean cleanupOnShutdown,
            @Value("${database.pool.max-size:10}") int maxPoolSize,
            @Value("${database.pool.min-idle:1}") int minIdle) {

        Path dbPath = Path.of(file);
        ensureParentDirectory(dbPath);

        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(JOURNAL_MODE);
        sqliteConfig.setSynchronous(SYNCHRONOUS);
        sqliteConfig.setTempStore(TEMP_STORE);
        sqliteConfig.enforceForeignKeys(FOREIGN_KEYS);
        sqliteConfig.setBusyTimeout(BUSY_TIMEOUT_MS);

        SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
        sqliteDataSource.setUrl("jdbc:sqlite:" + dbPath);

        CleanupAwareDataSource dataSource = new CleanupAwareDataSource(dbPath, cleanupOnShutdown);
        dataSource.setPoolName(POOL_NAME);
        dataSource.setDataSource(sqliteDataSource);
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setMinimumIdle(minIdle);
        return dataSource;
    }

    private static void ensureParentDirectory(Path dbPath) {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create parent directory for database file: " + parent, ex);
        }
    }

    static final class CleanupAwareDataSource extends HikariDataSource {

        private static final String[] SQLITE_FILE_SUFFIXES = {"", "-wal", "-shm"};

        private final Path file;
        private final boolean cleanupOnShutdown;

        CleanupAwareDataSource(Path file, boolean cleanupOnShutdown) {
            this.file = file;
            this.cleanupOnShutdown = cleanupOnShutdown;
        }

        @Override
        public void close() {
            super.close();
            if (cleanupOnShutdown) {
                deleteSqliteFiles();
            }
        }

        private void deleteSqliteFiles() {
            String base = file.toString();
            for (String suffix : SQLITE_FILE_SUFFIXES) {
                Path path = Path.of(base + suffix);
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    LOG.warn("Failed to delete SQLite file: path={} error={}", path, ex.toString());
                }
            }
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_DATABASECONFIGURATION_JAVA
cat > server/src/main/java/jeffrey/testapp/server/Helpers.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_HELPERS_JAVA'
package jeffrey.testapp.server;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public abstract class Helpers {

    public static Collection<Integer> generateIds(int max, int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return Stream.generate(() -> random.nextInt(max))
                .limit(count)
                .toList();
    }

    public static int generateId(int max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return random.nextInt(max);
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_HELPERS_JAVA
cat > server/src/main/java/jeffrey/testapp/server/IDHolder.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_IDHOLDER_JAVA'
package jeffrey.testapp.server;

import java.util.ArrayList;
import java.util.List;

public abstract class IDHolder {

    public static final List<Long> IDS = new ArrayList<>();

}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_IDHOLDER_JAVA
cat > server/src/main/java/jeffrey/testapp/server/NamedThreadFactory.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_NAMEDTHREADFACTORY_JAVA'
package jeffrey.testapp.server;

import org.springframework.util.CustomizableThreadCreator;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory extends CustomizableThreadCreator implements ThreadFactory {

    public NamedThreadFactory(String prefix) {
        super(prefix);
        setDaemon(true);
    }

    public Thread newThread(Runnable runnable) {
        return this.createThread(runnable);
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_NAMEDTHREADFACTORY_JAVA
cat > server/src/main/java/jeffrey/testapp/server/NoOpQueue.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_NOOPQUEUE_JAVA'
package jeffrey.testapp.server;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class NoOpQueue<T> implements Queue<T> {

    @Override
    public boolean add(T s) {
        return false;
    }

    @Override
    public boolean offer(T s) {
        return false;
    }

    @Override
    public T remove() {
        return null;
    }

    @Override
    public T poll() {
        return null;
    }

    @Override
    public T element() {
        return null;
    }

    @Override
    public T peek() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_NOOPQUEUE_JAVA
cat > server/src/main/java/jeffrey/testapp/server/Person.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSON_JAVA'
package jeffrey.testapp.server;

public record Person(
        Long id,
        String firstname,
        String lastname,
        String city,
        String country,
        String phone,
        String politicalOpinion) {

    public Person copyWithId(Long id) {
        return new Person(id, firstname, lastname, city, country, phone, politicalOpinion);
    }

    @Override
    public int hashCode() {
        return (int) (id / 10);
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSON_JAVA
cat > server/src/main/java/jeffrey/testapp/server/PersonController.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONCONTROLLER_JAVA'
package jeffrey.testapp.server;

import cafe.jeffrey.jfr.events.trace.Tracer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jeffrey.testapp.server.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/persons", produces = "application/json")
public class PersonController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Logger LOG = LoggerFactory.getLogger(PersonController.class);

    /**
     * The "dom" mode's detour through a JSON tree, as its own span: the garbage it creates is
     * attributed to this span's flamegraph and allocations in Jeffrey.
     */
    private static final String SPAN_TO_JSON_TREE = "person.to-json-tree";

    private final boolean efficientMode;
    private final PersonService service;
    private final PersonRepository repository;

    public PersonController(
            @Value("${efficient.mode:true}") boolean efficientMode,
            PersonService service,
            PersonRepository repository) {

        this.efficientMode = efficientMode;
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity getRandomPerson() {
        Optional<Person> personOpt = service.getRandomPerson();
        if (personOpt.isPresent()) {
            Person person = personOpt.get();
            LOG.info("Get Person: id={} firstname={} lastname={}",
                    person.id(), person.firstname(), person.lastname());
        }

        if (efficientMode) {
            return ResponseEntity.of(personOpt);
        } else {
            return ResponseEntity.of(personOpt.map(PersonController::toJsonTree));
        }
    }

    @GetMapping("/{count}")
    public ResponseEntity getNPersons(@PathVariable("count") int count) {
        List<Person> persons = service.getNPersons(count);
        List<Long> ids = persons.stream()
                .map(Person::id)
                .toList();
        LOG.info("Get NPersons: IDs={} count={}", ids, count);

        if (efficientMode) {
            return ResponseEntity.of(Optional.of(persons));
        } else {
            return ResponseEntity.of(Optional.of(toJsonTree(persons)));
        }
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity addNewPerson(@RequestBody Person person) {
        Person addedPerson = repository.insert(person);
        LOG.info("Added Person: {}", addedPerson);

        if (efficientMode) {
            return ResponseEntity.of(Optional.of(addedPerson));
        } else {
            return ResponseEntity.of(Optional.of(toJsonTree(addedPerson)));
        }
    }

    @GetMapping("/count")
    public ResponseEntity personCount() {
        int count = repository.count().intValue();
        LOG.info("Person Count: {}", count);

        if (efficientMode) {
            return ResponseEntity.of(Optional.of(count));
        } else {
            return ResponseEntity.of(Optional.of(toJsonTree(count)));
        }
    }

    @DeleteMapping("/{id}")
    public void removePerson(@PathVariable("id") Long id) {
        repository.remove(id);
        LOG.info("Removed Person: {}", id);
    }

    @PostMapping("/reload")
    public void resetAndLoadSnapshot() {
        throw new UnsupportedOperationException();
    }

    private static JsonNode toJsonTree(Object value) {
        return Tracer.call(SPAN_TO_JSON_TREE, () -> MAPPER.valueToTree(value));
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONCONTROLLER_JAVA
cat > server/src/main/java/jeffrey/testapp/server/PersonRepository.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONREPOSITORY_JAVA'
package jeffrey.testapp.server;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import cafe.jeffrey.jfr.events.trace.Tracer;
import tools.jackson.databind.json.JsonMapper;
import jeffrey.testapp.server.metrics.DatabaseClient;
import jeffrey.testapp.server.metrics.DatabaseClient.StatementLabel;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PersonRepository {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String GROUP_LABEL = "person-repository";

    // Span names: stable and low-cardinality, they name the operation, never the entity
    private static final String SPAN_REDACT_OPINIONS = "person.redact-opinions";
    private static final String SPAN_BACKUP_AND_RELOAD = "person.backup-and-reload";
    private static final String SPAN_BACKUP_WRITE_LOCK = "person.backup.acquire-write-lock";
    private static final String SPAN_BACKUP_DUMP = "person.backup.dump";
    private static final String SPAN_BACKUP_LOAD = "person.backup.load";

    private static final String NOTIFICATION_BACKUP_RELOADED = "PERSON_BACKUP_RELOADED";
    private static final String NOTIFICATION_CATEGORY = "MAINTENANCE";

    private static final String FIND_BY_ID = """
            SELECT * FROM person WHERE id = :id
            """;

    private static final String FIND_ALL_BY_IDS = """
            SELECT * FROM person WHERE id IN (:ids)
            """;

    private static final String ALL_QUERY = """
            SELECT * FROM person
            """;

    private static final String ALL_IDS_QUERY = """
            SELECT id FROM person
            """;

    private static final String COUNT_QUERY = """
            SELECT COUNT(*) FROM person
            """;

    private static final String REMOVE_QUERY_BY_ID = """
            DELETE FROM person WHERE id = :id
            """;

    private static final String REMOVE_ALL = """
            DELETE FROM person
            """;

    private static final String INSERT_PERSON = """
            INSERT INTO person (firstname, lastname, city, country, phone, political_opinion)
            VALUES (:firstname, :lastname, :city, :country, :phone, :political_opinion)
            """;

    private final Path backupFile;

    private final DatabaseClient databaseClient;

    // It's FAIR scheduling to not starve the writer thread
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private final Map<Person, Boolean> cache = new ConcurrentHashMap<>();

    public PersonRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.databaseClient = new DatabaseClient(jdbcTemplate, GROUP_LABEL);
        try {
            this.backupFile = Files.createTempFile("jeffrey-testapp", null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return a single Person according to provided {@code id} parameter.
     *
     * @param id id of the expected Person entity
     * @return Person entity, or {@code null} if the entity does not exist
     */
    public Optional<Person> findById(long id) {
        readLock.lock();
        try {
            SqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
            Optional<Person> personOpt = databaseClient.querySingle(
                    StatementLabel.FIND_BY_ID, FIND_BY_ID, params, personMapper());

            return Tracer.call(SPAN_REDACT_OPINIONS, () -> personOpt.map(PersonRepository::modifyOpinion));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Tries to find the Person entities according to a provided list of {@code ids}.
     *
     * @param ids IDs of the expected Person entities
     * @return a list of the resolved entities
     */
    public List<Person> findByIds(Collection<Long> ids) {
        readLock.lock();
        try {
            SqlParameterSource params = new MapSqlParameterSource("ids", ids);
            List<Person> persons = databaseClient.query(
                    StatementLabel.FIND_ALL_BY_IDS, FIND_ALL_BY_IDS, params, personMapper());

            return Tracer.call(SPAN_REDACT_OPINIONS, () -> persons.stream()
                    .map(PersonRepository::modifyOpinion)
                    .toList());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Current number of persons in the table.
     *
     * @return count of all persons.
     */
    public Long count() {
        readLock.lock();
        try {
            return databaseClient.queryLong(StatementLabel.COUNT_QUERY, COUNT_QUERY, new MapSqlParameterSource());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Remove a person with a provided {@code id}
     *
     * @param id person's ID to delete
     */
    public void remove(long id) {
        readLock.lock();
        try {
            IDHolder.IDS.remove(id);
            databaseClient.delete(StatementLabel.REMOVE_QUERY_BY_ID, REMOVE_QUERY_BY_ID, new MapSqlParameterSource("id", id));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Add a new person represented by {@code person} parameter
     *
     * @param person person entity to be inserted
     * @return full populated person with a new ID
     */
    public Person insert(Person person) {
        return insert(StatementLabel.INSERT_PERSON, person);
    }

    private Person insert(StatementLabel statement, Person person) {
        readLock.lock();
        try {
            Map<String, Object> parameters = Map.of(
                    "firstname", person.firstname(),
                    "lastname", person.lastname(),
                    "city", person.city(),
                    "country", person.country(),
                    "phone", person.phone(),
                    "political_opinion", person.politicalOpinion());

            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            databaseClient.update(statement, INSERT_PERSON, new MapSqlParameterSource(parameters), keyHolder);
            Long id = extractId(keyHolder);
            IDHolder.IDS.add(id);
            return person.copyWithId(id);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Streams all persons from the table.
     */
    private void streamAll(Consumer<Person> consumer) {
        databaseClient.queryStream(StatementLabel.BACKUP_DUMP, ALL_QUERY, personMapper(), consumer);
    }

    public List<Long> findAllIds() {
        readLock.lock();
        try {
            return databaseClient.query(
                    StatementLabel.ALL_IDS_QUERY,
                    ALL_IDS_QUERY,
                    (rs, __) -> rs.getLong("id"));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Execute a single raw statement. Dangerous, use it sparingly and with cautions.
     *
     * @param statement statement to be executed.
     */
    public void insertRaw(String statement) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        databaseClient.update(StatementLabel.RAW_INSERT, statement, new EmptySqlParameterSource(), keyHolder);
        Long id = extractId(keyHolder);
        IDHolder.IDS.add(id);
    }

    /**
     * Dumps the whole table into a file, deletes it and inserts it back row by row — under the
     * write lock, so every request arriving meanwhile waits for it.
     * <p>
     * Runs on the "backup" scheduler, outside any request, so it is the root of its own trace: the
     * {@code person.backup-and-reload} span with the dump, the delete and one insert per person
     * nested underneath. Deliberately inefficient (no batching) to give the Traces view an N+1
     * shape to find.
     */
    public void backupAndReload() {
        Tracer.run(SPAN_BACKUP_AND_RELOAD, () -> {
            Tracer.run(SPAN_BACKUP_WRITE_LOCK, writeLock::lock);
            try {
                long dumped = Tracer.call(SPAN_BACKUP_DUMP, this::dumpToFile);
                deleteAll();
                long loaded = Tracer.call(SPAN_BACKUP_LOAD, this::loadFromFile);
                notifyBackupReloaded(dumped, loaded);
            } finally {
                writeLock.unlock();
            }
        });
    }

    /**
     * Emitted inside the backup span, so {@code emit()} records the trace and span it belongs to.
     */
    private static void notifyBackupReloaded(long dumped, long loaded) {
        NotificationEvent notification = new NotificationEvent();
        notification.type = NOTIFICATION_BACKUP_RELOADED;
        notification.message = "Person table backed up and reloaded: " + loaded + " of " + dumped + " rows inserted back";
        notification.severity = (dumped == loaded ? Severity.LOW : Severity.HIGH).name();
        notification.category = NOTIFICATION_CATEGORY;
        notification.source = GROUP_LABEL;
        notification.attributes = EventAttributes.create()
                .put("dumped", dumped)
                .put("loaded", loaded)
                .json();
        notification.emit();
    }

    private void deleteAll() {
        databaseClient.delete(StatementLabel.REMOVE_ALL, REMOVE_ALL, new MapSqlParameterSource());
    }

    private long dumpToFile() {
        LongAdder rows = new LongAdder();
        try (BufferedWriter writer = Files.newBufferedWriter(backupFile)) {
            streamAll(person -> {
                write(writer, convertToString(person));
                rows.increment();
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return rows.sum();
    }

    private long loadFromFile() {
        LongAdder rows = new LongAdder();
        try (Stream<String> stream = Files.lines(backupFile)) {
            stream.map(PersonRepository::parseJsonValue)
                    .forEach(person -> {
                        insert(StatementLabel.BACKUP_LOAD, person);
                        rows.increment();
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return rows.sum();
    }

    private static Person parseJsonValue(String json) {
        try {
            return MAPPER.readValue(json, Person.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String convertToString(Person person) {
        try {
            return MAPPER.writeValueAsString(person);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void write(BufferedWriter writer, String json) {
        try {
            writer.write(json);
            writer.newLine();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static RowMapper<Person> personMapper() {
        return (rs, __) -> new Person(
                rs.getLong("id"),
                rs.getString("firstname"),
                rs.getString("lastname"),
                rs.getString("city"),
                rs.getString("country"),
                rs.getString("phone"),
                rs.getString("political_opinion")
        );
    }

    private static Person modifyOpinion(Person person) {
        return new Person(
                person.id(),
                person.firstname(),
                person.lastname(),
                person.city(),
                person.country(),
                person.phone(),
                person.politicalOpinion().replaceAll("\\sm.+?\\s", " - "));
    }

    private static Long extractId(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONREPOSITORY_JAVA
cat > server/src/main/java/jeffrey/testapp/server/PersonWithTimestamp.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONWITHTIMESTAMP_JAVA'
package jeffrey.testapp.server;

import java.time.Instant;

public record PersonWithTimestamp(Person person, Instant timestamp) {
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_PERSONWITHTIMESTAMP_JAVA
cat > server/src/main/java/jeffrey/testapp/server/ServerApplication.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVERAPPLICATION_JAVA'
package jeffrey.testapp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication(exclude = JdbcTemplateAutoConfiguration.class)
public class ServerApplication implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerApplication.class);

    private static final String BACKUP_INTERVAL_PROPERTY = "backup.interval";
    private static final Duration DEFAULT_BACKUP_INTERVAL = Duration.ofMinutes(5);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ServerApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        PersonRepository repository = event.getApplicationContext()
                .getBeanFactory()
                .getBean(PersonRepository.class);

        IDHolder.IDS.addAll(repository.findAllIds());
        LOG.info("Loaded {} person IDs from database", IDHolder.IDS.size());

        Duration interval = event.getApplicationContext().getEnvironment()
                .getProperty(BACKUP_INTERVAL_PROPERTY, Duration.class, DEFAULT_BACKUP_INTERVAL);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("backup"));
        executor.scheduleAtFixedRate(() -> {
            LOG.info("Backup and Reloading Initiated");
            repository.backupAndReload();
            LOG.info("Backup and Reloading Finished");
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVERAPPLICATION_JAVA
cat > server/src/main/java/jeffrey/testapp/server/leak/NativeMemoryAllocator.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NATIVEMEMORYALLOCATOR_JAVA'
package jeffrey.testapp.server.leak;

import java.io.Closeable;

public interface NativeMemoryAllocator {

    Allocation allocate();

    interface Allocation extends Closeable {
    }

    class HeapAllocation implements Allocation {
        @Override
        public void close() {
        }
    }

    class NativeAllocation implements Allocation {

        private final long address;
        private final boolean leaked;

        public NativeAllocation() {
            this.leaked = true;
            this.address = -1;
        }

        public NativeAllocation(long bytes, boolean leaked) {
            this.address = UnsafeHolder.INSTANCE.allocateMemory(bytes);
            this.leaked = leaked;
        }

        @Override
        public void close() {
            if (leaked) {
                UnsafeHolder.INSTANCE.freeMemory(address);
            }
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NATIVEMEMORYALLOCATOR_JAVA
cat > server/src/main/java/jeffrey/testapp/server/leak/NativeMemoryAllocatorImpl.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NATIVEMEMORYALLOCATORIMPL_JAVA'
package jeffrey.testapp.server.leak;

public class NativeMemoryAllocatorImpl implements NativeMemoryAllocator {

    private static final int ALLOCATION_SIZE = 128;

    private boolean leakNext = false;

    @Override
    public Allocation allocate() {
        leakNext ^= true;
        return new NativeAllocation(ALLOCATION_SIZE, leakNext);
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NATIVEMEMORYALLOCATORIMPL_JAVA
cat > server/src/main/java/jeffrey/testapp/server/leak/NoopNativeMemoryAllocator.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NOOPNATIVEMEMORYALLOCATOR_JAVA'
package jeffrey.testapp.server.leak;

public class NoopNativeMemoryAllocator implements NativeMemoryAllocator {

    @Override
    public Allocation allocate() {
        return new HeapAllocation();
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_NOOPNATIVEMEMORYALLOCATOR_JAVA
cat > server/src/main/java/jeffrey/testapp/server/leak/UnsafeHolder.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_UNSAFEHOLDER_JAVA'
package jeffrey.testapp.server.leak;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeHolder {

    public static final Unsafe INSTANCE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            INSTANCE = (Unsafe) f.get(null);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_LEAK_UNSAFEHOLDER_JAVA
cat > server/src/main/java/jeffrey/testapp/server/metrics/DatabaseClient.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_METRICS_DATABASECLIENT_JAVA'
/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jeffrey.testapp.server.metrics;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcExecuteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.SqlBinaryValue;
import org.springframework.jdbc.support.KeyHolder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Records every statement as a Jeffrey JDBC event, named by its {@link StatementLabel} and grouped
 * under the repository that issued it — the same shape as Jeffrey's own {@code DatabaseClient}.
 * <p>
 * Every statement goes through {@link TracedEvents#emit}, which is the whole leaf lifecycle: the
 * zero-cost passthrough when nothing is recording, the interval, {@code failed(e)} on the exception
 * path, and {@code commitSpan()} — which nests the statement under the span in progress (the HTTP
 * request's root span, or a {@code Tracer} span in between). The fillers run only when the event is
 * actually committed.
 * <p>
 * This replaces the starter's {@code DataSource} wrapper ({@code jeffrey.tracing.jdbc-enabled=false}
 * in application.properties): the wrapper names statements by verb and table and does not count
 * returned rows, while this client records the label, the bound parameters and the row counts.
 */
public class DatabaseClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String LOB_VALUE = "<lob-value>";

    private final NamedParameterJdbcTemplate delegate;
    private final String groupLabel;

    public DatabaseClient(NamedParameterJdbcTemplate delegate, String groupLabel) {
        this.delegate = delegate;
        this.groupLabel = groupLabel;
    }

    public int insert(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int insertWithLob(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.isLob = true;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public long batchInsert(StatementLabel statement, String sql, SqlParameterSource[] paramSources) {
        JdbcInsertEvent event = new JdbcInsertEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> sumRows(delegate.batchUpdate(sql, paramSources)),
                (e, rows) -> {
                    e.rows = rows != null ? rows : 0;
                    e.isBatch = true;
                    // Don't populate `params` and `sql` in batch processing
                });
    }

    public int update(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcUpdateEvent event = new JdbcUpdateEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int update(
            StatementLabel statement, String sql, SqlParameterSource paramSource, KeyHolder generatedKeyHolder) {

        JdbcUpdateEvent event = new JdbcUpdateEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource, generatedKeyHolder),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int delete(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcDeleteEvent event = new JdbcDeleteEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.update(sql, paramSource),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public int delete(StatementLabel statement, String sql) {
        JdbcDeleteEvent event = new JdbcDeleteEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.getJdbcOperations().update(sql),
                (e, rows) -> {
                    e.sql = sql;
                    e.rows = rows != null ? rows : 0;
                });
    }

    public void execute(StatementLabel statement, String sql) {
        JdbcExecuteEvent event = new JdbcExecuteEvent(statement.label(), groupLabel);
        TracedEvents.emit(event,
                () -> delegate.getJdbcOperations().execute(sql),
                e -> e.sql = sql);
    }

    public <T> List<T> query(StatementLabel statement, String sql, RowMapper<T> rowMapper) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.query(sql, rowMapper),
                (e, list) -> {
                    e.sql = sql;
                    e.rows = list != null ? list.size() : 0;
                });
    }

    public <T> List<T> query(
            StatementLabel statement, String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {

        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> delegate.query(sql, paramSource, rowMapper),
                (e, list) -> {
                    e.sql = sql;
                    e.rows = list != null ? list.size() : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    public long queryLong(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        Long value = TracedEvents.emit(event,
                () -> delegate.queryForObject(sql, paramSource, Long.class),
                (e, result) -> {
                    e.sql = sql;
                    e.rows = result != null ? 1 : 0;
                    e.params = paramSourceToJson(paramSource);
                });
        return value != null ? value : 0L;
    }

    public <T> Optional<T> querySingle(
            StatementLabel statement, String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {

        List<T> list = query(statement, sql, paramSource, rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public boolean queryExists(StatementLabel statement, String sql, SqlParameterSource paramSource) {
        JdbcQueryEvent event = new JdbcQueryEvent(statement.label(), groupLabel);
        return TracedEvents.emit(event,
                () -> {
                    Long count = delegate.queryForObject(sql, paramSource, Long.class);
                    return count != null && count > 0;
                },
                (e, exists) -> {
                    e.sql = sql;
                    e.rows = Boolean.TRUE.equals(exists) ? 1 : 0;
                    e.params = paramSourceToJson(paramSource);
                });
    }

    /**
     * Streams the statement's rows into {@code consumer} without materializing them, and records
     * the rows that actually flowed through as the event's row count — right whether the stream ran
     * to the end or a failure cut it short.
     */
    public <T> void queryStream(StatementLabel statement, String sql, RowMapper<T> mapper, Consumer<T> consumer) {
        CountingConsumer<T> countingConsumer = new CountingConsumer<>(consumer);

        JdbcStreamEvent event = new JdbcStreamEvent(statement.label(), groupLabel);
        TracedEvents.emit(event,
                () -> {
                    try (Stream<T> stream = delegate.queryForStream(sql, Map.of(), mapper)) {
                        stream.forEach(countingConsumer);
                    }
                },
                e -> {
                    e.sql = sql;
                    e.rows = countingConsumer.rows();
                });
    }

    private static String paramSourceToJson(SqlParameterSource paramSource) {
        if (paramSource != null
                && paramSource.getParameterNames() != null
                && paramSource.getParameterNames().length > 0) {

            ObjectNode json = MAPPER.createObjectNode();
            for (String paramName : paramSource.getParameterNames()) {
                json.put(paramName, resolveParamValue(paramSource.getValue(paramName)));
            }
            return json.toString();
        } else {
            return null;
        }
    }

    private static String resolveParamValue(Object value) {
        if (value instanceof SqlBinaryValue) {
            return LOB_VALUE;
        } else {
            return value == null ? null : value.toString();
        }
    }

    private static long sumRows(int[] updateCount) {
        long count = 0L;
        for (int i : updateCount) {
            if (i == Statement.SUCCESS_NO_INFO) {
                return Statement.SUCCESS_NO_INFO;
            }
            count += i;
        }
        return count;
    }

    private static final class CountingConsumer<T> implements Consumer<T> {

        private final Consumer<T> delegate;
        private long rows;

        private CountingConsumer(Consumer<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(T row) {
            rows++;
            delegate.accept(row);
        }

        private long rows() {
            return rows;
        }
    }

    public enum StatementLabel {
        FIND_BY_ID,
        FIND_ALL_BY_IDS,
        ALL_QUERY,
        ALL_IDS_QUERY,
        COUNT_QUERY,
        REMOVE_QUERY_BY_ID,
        REMOVE_ALL,
        INSERT_PERSON,
        RAW_INSERT,
        BACKUP_DUMP,
        BACKUP_LOAD;

        private final String label = name().toLowerCase();

        public String label() {
            return label;
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_METRICS_DATABASECLIENT_JAVA
cat > server/src/main/java/jeffrey/testapp/server/service/EfficientPersonService.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_EFFICIENTPERSONSERVICE_JAVA'
package jeffrey.testapp.server.service;

import cafe.jeffrey.jfr.events.trace.Tracer;
import jeffrey.testapp.server.Helpers;
import jeffrey.testapp.server.IDHolder;
import jeffrey.testapp.server.Person;
import jeffrey.testapp.server.PersonRepository;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Picks the random ids from the in-memory {@link IDHolder}: the {@code person.pick-id(s)} span
 * holds no SQL, compare it with {@link InefficientPersonService}.
 */
public class EfficientPersonService implements PersonService {

    private static final String SPAN_PICK_ID = "person.pick-id";
    private static final String SPAN_PICK_IDS = "person.pick-ids";

    private final PersonRepository repository;
    private final NativeMemoryAllocator nativeMemoryAllocator;

    public EfficientPersonService(PersonRepository repository, NativeMemoryAllocator nativeMemoryAllocator) {
        this.repository = repository;
        this.nativeMemoryAllocator = nativeMemoryAllocator;
    }

    @Override
    public Optional<Person> getRandomPerson() {
        try (var __ = nativeMemoryAllocator.allocate()) {
            long personId = Tracer.call(SPAN_PICK_ID, () -> {
                int personIndex = Helpers.generateId(IDHolder.IDS.size());
                return safeIdLookup(personIndex);
            });
            return repository.findById(personId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Person> getNPersons(int count) {
        try (var __ = nativeMemoryAllocator.allocate()) {
            List<Long> personIds = Tracer.call(SPAN_PICK_IDS, () -> {
                Collection<Integer> indices = Helpers.generateIds(IDHolder.IDS.size(), count);
                return indices.stream()
                        .map(EfficientPersonService::safeIdLookup)
                        .toList();
            });
            return repository.findByIds(personIds);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long safeIdLookup(int index) {
        try {
            return IDHolder.IDS.get(index);
        } catch (IndexOutOfBoundsException ex) {
            return IDHolder.IDS.getLast();
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_EFFICIENTPERSONSERVICE_JAVA
cat > server/src/main/java/jeffrey/testapp/server/service/InefficientPersonService.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_INEFFICIENTPERSONSERVICE_JAVA'
package jeffrey.testapp.server.service;

import cafe.jeffrey.jfr.events.trace.Tracer;
import jeffrey.testapp.server.Helpers;
import jeffrey.testapp.server.IDHolder;
import jeffrey.testapp.server.Person;
import jeffrey.testapp.server.PersonRepository;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Counts the table on every request before picking the random ids: the {@code person.pick-id(s)}
 * span carries an extra {@code count_query} statement, compare it with {@link EfficientPersonService}.
 */
public class InefficientPersonService implements PersonService {

    private static final String SPAN_PICK_ID = "person.pick-id";
    private static final String SPAN_PICK_IDS = "person.pick-ids";

    private final PersonRepository repository;
    private final NativeMemoryAllocator nativeMemoryAllocator;

    public InefficientPersonService(PersonRepository repository, NativeMemoryAllocator nativeMemoryAllocator) {
        this.repository = repository;
        this.nativeMemoryAllocator = nativeMemoryAllocator;
    }

    @Override
    public Optional<Person> getRandomPerson() {
        try (var __ = nativeMemoryAllocator.allocate()) {
            // Same span name as the efficient service, so the two modes line up in the Traces view:
            // here it carries the extra COUNT query the efficient one does without.
            long personId = Tracer.call(SPAN_PICK_ID, () -> {
                int latestPersonCount = repository.count().intValue();
                int personIndex = Helpers.generateId(latestPersonCount);
                return safeIdLookup(personIndex);
            });
            return repository.findById(personId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Person> getNPersons(int count) {
        try (var __ = nativeMemoryAllocator.allocate()) {
            List<Long> personIds = Tracer.call(SPAN_PICK_IDS, () -> {
                int latestPersonCount = repository.count().intValue();
                Collection<Integer> indices = Helpers.generateIds(latestPersonCount, count);
                return indices.stream()
                        .map(InefficientPersonService::safeIdLookup)
                        .toList();
            });

            return repository.findByIds(personIds);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long safeIdLookup(int index) {
        try {
            return IDHolder.IDS.get(index);
        } catch (IndexOutOfBoundsException ex) {
            return IDHolder.IDS.getLast();
        }
    }
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_INEFFICIENTPERSONSERVICE_JAVA
cat > server/src/main/java/jeffrey/testapp/server/service/PersonService.java <<'TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_PERSONSERVICE_JAVA'
package jeffrey.testapp.server.service;

import jeffrey.testapp.server.Person;

import java.util.List;
import java.util.Optional;

public interface PersonService {

    Optional<Person> getRandomPerson();

    List<Person> getNPersons(int count);
}
TESTAPP_SERVER_SRC_MAIN_JAVA_JEFFREY_TESTAPP_SERVER_SERVICE_PERSONSERVICE_JAVA
cat > server/src/main/resources/application.properties <<'TESTAPP_SERVER_SRC_MAIN_RESOURCES_APPLICATION_PROPERTIES'
server.port=8080

# SQLite database location. Override per environment, e.g. mounted volume in container.
database.file=${java.io.tmpdir}/jeffrey-testapp.db

# Schema + seed loading. Spring Boot's DataSourceInitializer runs this against
# the SQLite DataSource at startup. The script is idempotent (CREATE TABLE IF NOT EXISTS).
# Set spring.sql.init.mode=never to skip seeding when the volume already has data.
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:init.sql
spring.sql.init.continue-on-error=false

# Delete the SQLite files (.db, .db-wal, .db-shm) when the JVM exits.
# Set true for ephemeral / benchmark runs; false to keep state across restarts.
database.cleanup-on-shutdown=false

# HikariCP sizing. SQLite WAL allows concurrent reads while writes serialize naturally.
database.pool.max-size=10
database.pool.min-idle=1

# override program args: --efficient.mode=false
efficient.mode=true

# Starts leaking a small number of native-memory allocations
native-memory-leak.mode=false

# How often the whole person table is dumped, deleted and re-inserted under the write lock
# (one trace with thousands of INSERT spans, and every request waiting on the lock meanwhile).
backup.interval=5m

management.endpoints.web.exposure.include=health

# Jeffrey tracing (jeffrey-tracing-spring-boot-starter): every inbound request is the root span of a
# trace, HikariCP gets the JFR pool metrics tracker. Statements are recorded by DatabaseClient
# (labelled, with parameters and row counts), so the starter's DataSource wrapper is turned off,
# otherwise every statement would be recorded twice.
jeffrey.tracing.jdbc-enabled=false
# This app's query strings and path variables carry nothing sensitive, so keep them on the event.
jeffrey.tracing.capture-query-params=true
jeffrey.tracing.capture-path-params=true
TESTAPP_SERVER_SRC_MAIN_RESOURCES_APPLICATION_PROPERTIES
cat > server/src/main/resources/logback.xml <<'TESTAPP_SERVER_SRC_MAIN_RESOURCES_LOGBACK_XML'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Stop output INFO at start -->
    <statusListener class="ch.qos.logback.core.status.NopStatusListener" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <layout>
            <pattern>App %date %green([%thread]) %highlight(%-5level) %logger{5} - %msg%n</pattern>
        </layout>
    </appender>

    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="CONSOLE"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC" />
    </root>
</configuration>
TESTAPP_SERVER_SRC_MAIN_RESOURCES_LOGBACK_XML
git init -q
git add -A
git -c user.name=eval -c user.email=eval@example.com commit -q -m "persons service"
