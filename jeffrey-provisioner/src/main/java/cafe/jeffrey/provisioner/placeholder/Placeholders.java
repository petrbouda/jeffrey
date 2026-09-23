/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provisioner.placeholder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code <<TYPE:NAME>>} placeholders in configuration values, dispatching each one to
 * the {@link PlaceholderSource} registered for its type.
 *
 * <pre>
 *   &lt;&lt;ENV:SF_CLUSTER&gt;&gt;              the SF_CLUSTER environment variable
 *   &lt;&lt;ENV:SF_CLUSTER:-unknown&gt;&gt;      ... or "unknown" when it is not set
 *   &lt;&lt;JEFFREY:CURRENT_SESSION&gt;&gt;     this run's session directory
 * </pre>
 *
 * <h2>Two phases</h2>
 * The types differ in when they can be answered: {@code ENV} is context-free and resolves while the
 * configuration is being read, whereas {@code JEFFREY} values only exist once the session directory
 * has been created, late in {@code init}. A resolver therefore <b>leaves a
 * placeholder whose type it does not know completely untouched</b>, so an early pass can run over a
 * value that still has to survive for a later one.
 *
 * <h2>Single pass</h2>
 * A substituted value is never re-scanned. That rules out substitution loops and stops content
 * arriving from the environment from injecting further placeholders.
 *
 * <h2>Unresolvable values</h2>
 * A known type that yields nothing falls back to the placeholder's default, or — with no default —
 * to an empty string plus a warning. It never throws: the provisioner's contract is that a
 * misconfiguration starts the application without profiling rather than blocking its startup.
 */
public final class Placeholders {

    private static final Logger LOG = LoggerFactory.getLogger(Placeholders.class);

    /**
     * The name is everything up to the optional default marker, so a bare {@code :} stays part of
     * it rather than being mistaken for a default.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("<<([A-Za-z][A-Za-z0-9_]*):([^>]*)>>");

    private static final String DEFAULT_SEPARATOR = ":-";

    private final Map<String, PlaceholderSource> sourcesByType;

    private Placeholders(Map<String, PlaceholderSource> sourcesByType) {
        this.sourcesByType = sourcesByType;
    }

    public static Placeholders of(PlaceholderSource... sources) {
        Map<String, PlaceholderSource> byType = new LinkedHashMap<>();
        for (PlaceholderSource source : sources) {
            byType.put(source.type().toUpperCase(Locale.ROOT), source);
        }
        return new Placeholders(Map.copyOf(byType));
    }

    /** Resolves every placeholder this resolver recognises, leaving the rest for a later phase. */
    public String resolve(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String type = matcher.group(1).toUpperCase(Locale.ROOT);
            PlaceholderSource source = sourcesByType.get(type);
            String replacement = source == null
                    ? matcher.group()
                    : resolveBody(source, type, matcher.group(2));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String resolveBody(PlaceholderSource source, String type, String body) {
        int separatorIndex = body.indexOf(DEFAULT_SEPARATOR);
        String name = separatorIndex < 0 ? body : body.substring(0, separatorIndex);
        String defaultValue = separatorIndex < 0 ? null : body.substring(separatorIndex + DEFAULT_SEPARATOR.length());

        Optional<String> resolved = source.lookup(name);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        LOG.warn("Unresolved placeholder, substituting an empty value: type={} name={}", type, name);
        return "";
    }

}
