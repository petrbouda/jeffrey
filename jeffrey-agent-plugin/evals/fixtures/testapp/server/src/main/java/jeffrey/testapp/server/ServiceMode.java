package jeffrey.testapp.server;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How the service serves its persons. {@code direct} is the efficient baseline; every other mode is that
 * baseline with one problem planted in it, so a recording of a mode shows exactly that problem against a
 * {@code direct} recording taken under the same load. The mode travels on every request span as the
 * {@code testapp.mode} attribute and names the Jeffrey project its sessions go to.
 */
public enum ServiceMode {

    /** Picks the ids in memory, loads them in one statement, writes the persons straight out. */
    DIRECT("direct", false),
    /** Counts the table before every pick and builds each response as a JSON tree. */
    DOM("dom", true),
    /** Loads the persons of {@code GET /persons/{count}} one statement per person. */
    N_PLUS_ONE("n-plus-one", false),
    /** Serializes every lookup behind one monitor that is held across the database call. */
    LOCK("lock", false),
    /** Normalizes every person's phone by trying to parse it as a number and catching the failure. */
    EXCEPTIONS("exceptions", false);

    private final String value;
    private final boolean buildsJsonTree;

    ServiceMode(String value, boolean buildsJsonTree) {
        this.value = value;
        this.buildsJsonTree = buildsJsonTree;
    }

    /** The mode as {@code testapp.mode} spells it. */
    public String value() {
        return value;
    }

    /** Whether responses go through a JSON tree ({@code person.to-json-tree}) before they are written. */
    public boolean buildsJsonTree() {
        return buildsJsonTree;
    }

    /**
     * The mode {@code testapp.mode} names; without one, {@code efficient.mode} picks {@code direct} or
     * {@code dom} as it always has.
     */
    public static ServiceMode resolve(String mode, boolean efficientMode) {
        if (mode == null || mode.isBlank()) {
            return efficientMode ? DIRECT : DOM;
        }
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(mode.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown testapp.mode '" + mode + "', expected one of: "
                        + Arrays.stream(values()).map(ServiceMode::value).collect(Collectors.joining(", "))));
    }
}
