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
