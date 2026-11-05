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
