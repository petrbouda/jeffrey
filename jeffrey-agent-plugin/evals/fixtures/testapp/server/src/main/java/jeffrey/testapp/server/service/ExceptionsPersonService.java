package jeffrey.testapp.server.service;

import cafe.jeffrey.jfr.events.trace.Tracer;
import jeffrey.testapp.server.Person;
import jeffrey.testapp.server.PersonRepository;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The {@code exceptions} mode: picks and loads as {@link EfficientPersonService} does, then normalizes every
 * person's phone in the {@code person.normalize-phones} span by trying {@link Long#parseLong} and falling
 * back on the {@link NumberFormatException}. The stored phones are formatted ({@code (898) 957-6965}), so
 * every person served throws one: exceptions used as control flow, thrown in the service's own code.
 */
public class ExceptionsPersonService implements PersonService {

    private static final String SPAN_NORMALIZE_PHONES = "person.normalize-phones";
    private static final Pattern NOT_A_DIGIT = Pattern.compile("[^0-9]");

    private final PersonRepository repository;
    private final NativeMemoryAllocator nativeMemoryAllocator;

    public ExceptionsPersonService(PersonRepository repository, NativeMemoryAllocator nativeMemoryAllocator) {
        this.repository = repository;
        this.nativeMemoryAllocator = nativeMemoryAllocator;
    }

    @Override
    public Optional<Person> getRandomPerson() {
        try (var __ = nativeMemoryAllocator.allocate()) {
            Optional<Person> person = repository.findById(EfficientPersonService.pickId());
            return Tracer.call(SPAN_NORMALIZE_PHONES, () -> person.map(ExceptionsPersonService::withNormalizedPhone));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Person> getNPersons(int count) {
        try (var __ = nativeMemoryAllocator.allocate()) {
            List<Person> persons = repository.findByIds(EfficientPersonService.pickIds(count));
            return Tracer.call(SPAN_NORMALIZE_PHONES, () -> persons.stream()
                    .map(ExceptionsPersonService::withNormalizedPhone)
                    .toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Person withNormalizedPhone(Person person) {
        return new Person(person.id(), person.firstname(), person.lastname(), person.city(), person.country(),
                normalizePhone(person.phone()), person.politicalOpinion());
    }

    /** The phone as digits only: kept when it already parses as a number, stripped when parsing throws. */
    static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        try {
            Long.parseLong(phone);
            return phone;
        } catch (NumberFormatException notDigitsOnly) {
            return NOT_A_DIGIT.matcher(phone).replaceAll("");
        }
    }
}
