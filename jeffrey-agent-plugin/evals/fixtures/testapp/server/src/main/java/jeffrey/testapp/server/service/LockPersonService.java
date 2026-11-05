package jeffrey.testapp.server.service;

import jeffrey.testapp.server.Person;
import jeffrey.testapp.server.PersonRepository;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The {@code lock} mode: picks its ids as {@link EfficientPersonService} does, but every lookup runs in a
 * {@code synchronized} method of this one service instance, so the monitor is held across the database call
 * and concurrent requests queue on it ({@code jdk.JavaMonitorEnter} on {@code LockPersonService}) while one of
 * them waits for SQLite.
 */
public class LockPersonService implements PersonService {

    private final PersonRepository repository;
    private final NativeMemoryAllocator nativeMemoryAllocator;

    public LockPersonService(PersonRepository repository, NativeMemoryAllocator nativeMemoryAllocator) {
        this.repository = repository;
        this.nativeMemoryAllocator = nativeMemoryAllocator;
    }

    @Override
    public Optional<Person> getRandomPerson() {
        try (var __ = nativeMemoryAllocator.allocate()) {
            return lookup(EfficientPersonService.pickId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Person> getNPersons(int count) {
        try (var __ = nativeMemoryAllocator.allocate()) {
            return lookupAll(EfficientPersonService.pickIds(count));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized Optional<Person> lookup(long personId) {
        return repository.findById(personId);
    }

    private synchronized List<Person> lookupAll(List<Long> personIds) {
        return repository.findByIds(personIds);
    }
}
