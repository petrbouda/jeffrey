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
