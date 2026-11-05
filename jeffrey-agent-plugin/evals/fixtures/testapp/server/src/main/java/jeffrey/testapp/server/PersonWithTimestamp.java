package jeffrey.testapp.server;

import java.time.Instant;

public record PersonWithTimestamp(Person person, Instant timestamp) {
}
