package jeffrey.testapp.server;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeAttributesCustomizer;
import jeffrey.testapp.server.leak.NativeMemoryAllocator;
import jeffrey.testapp.server.leak.NativeMemoryAllocatorImpl;
import jeffrey.testapp.server.leak.NoopNativeMemoryAllocator;
import jeffrey.testapp.server.service.EfficientPersonService;
import jeffrey.testapp.server.service.ExceptionsPersonService;
import jeffrey.testapp.server.service.InefficientPersonService;
import jeffrey.testapp.server.service.LockPersonService;
import jeffrey.testapp.server.service.NPlusOnePersonService;
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

    private static final String NOTIFICATION_NATIVE_MEMORY_LEAK = "NATIVE_MEMORY_LEAK_MODE_ENABLED";
    private static final String NOTIFICATION_CATEGORY = "RESOURCE";
    private static final String NOTIFICATION_SOURCE = "testapp-server";

    /**
     * The service's mode: {@code testapp.mode} ({@code direct}, {@code dom}, {@code n-plus-one}, {@code lock},
     * {@code exceptions}), or, when that is not set, {@code efficient.mode} choosing {@code direct} or {@code dom}.
     */
    @Bean
    public ServiceMode serviceMode(
            @Value("${testapp.mode:}") String mode,
            @Value("${efficient.mode:true}") boolean efficientMode) {

        ServiceMode resolved = ServiceMode.resolve(mode, efficientMode);
        System.out.println("TESTAPP_MODE=" + resolved.value());
        return resolved;
    }

    /**
     * Adds the service mode to every request span's attributes. Jeffrey indexes attributes one key
     * at a time, so {@code testapp.mode} becomes a facet in Traces → Attributes. Low-cardinality by
     * construction: it is one of the {@link ServiceMode} values.
     */
    @Bean
    public HttpExchangeAttributesCustomizer serviceModeAttributesCustomizer(ServiceMode serviceMode) {
        String mode = serviceMode.value();
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
            ServiceMode serviceMode,
            PersonRepository personRepository,
            NativeMemoryAllocator nativeMemoryAllocator) {

        return switch (serviceMode) {
            case DIRECT -> new EfficientPersonService(personRepository, nativeMemoryAllocator);
            case DOM -> new InefficientPersonService(personRepository, nativeMemoryAllocator);
            case N_PLUS_ONE -> new NPlusOnePersonService(personRepository, nativeMemoryAllocator);
            case LOCK -> new LockPersonService(personRepository, nativeMemoryAllocator);
            case EXCEPTIONS -> new ExceptionsPersonService(personRepository, nativeMemoryAllocator);
        };
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
