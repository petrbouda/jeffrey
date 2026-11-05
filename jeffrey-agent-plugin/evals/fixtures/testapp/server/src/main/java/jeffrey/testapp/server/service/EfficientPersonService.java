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
