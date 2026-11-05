package jeffrey.testapp.server.service;

import cafe.jeffrey.jfr.events.trace.Tracer;
import jeffrey.testapp.server.Person;
import jeffrey.testapp.server.PersonRepository;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The {@code n-plus-one} mode: {@code GET /persons/{count}} picks its ids as {@link EfficientPersonService}
 * does, then loads the persons one {@code find_by_id} statement each, inside the {@code person.load-each}
 * span, where the efficient service runs one {@code find_all_by_ids}. The statements' stacks lead to the
 * loop in {@link #getNPersons(int)}.
 */
public class NPlusOnePersonService implements PersonService {

    private static final String SPAN_LOAD_EACH = "person.load-each";

    private final PersonRepository repository;
    private final NativeMemoryAllocator nativeMemoryAllocator;

    public NPlusOnePersonService(PersonRepository repository, NativeMemoryAllocator nativeMemoryAllocator) {
        this.repository = repository;
        this.nativeMemoryAllocator = nativeMemoryAllocator;
    }

    @Override
    public Optional<Person> getRandomPerson() {
        try (var __ = nativeMemoryAllocator.allocate()) {
            return repository.findById(EfficientPersonService.pickId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Person> getNPersons(int count) {
        try (var __ = nativeMemoryAllocator.allocate()) {
            List<Long> personIds = EfficientPersonService.pickIds(count);
            return Tracer.call(SPAN_LOAD_EACH, () -> personIds.stream()
                    .map(repository::findById)
                    .flatMap(Optional::stream)
                    .toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
