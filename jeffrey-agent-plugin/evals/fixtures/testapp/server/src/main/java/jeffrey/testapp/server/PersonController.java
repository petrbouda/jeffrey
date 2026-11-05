package jeffrey.testapp.server;

import cafe.jeffrey.jfr.events.trace.Tracer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jeffrey.testapp.server.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Whether responses go straight out; only the {@code dom} mode builds a JSON tree first. */
    private final boolean efficientMode;
    private final PersonService service;
    private final PersonRepository repository;

    public PersonController(
            ServiceMode serviceMode,
            PersonService service,
            PersonRepository repository) {

        this.efficientMode = !serviceMode.buildsJsonTree();
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
