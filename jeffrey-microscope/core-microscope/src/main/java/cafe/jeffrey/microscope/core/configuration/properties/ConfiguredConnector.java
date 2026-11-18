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

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.microscope.connector.spi.ConnectorProvider;
import cafe.jeffrey.microscope.connector.spi.DeclaredConnector;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The one connector declared under {@code jeffrey.microscope.connector.*}: an optional {@code name}
 * and one block for the kind it is,
 * <pre>
 * jeffrey.microscope.connector.name=&lt;what the UI calls it&gt;
 * jeffrey.microscope.connector.&lt;block&gt;.&lt;setting&gt;=...
 * </pre>
 * where each kind is a {@link ConnectorProvider} found on the class path, naming its block and the
 * settings record the block binds to — the kinds document their own settings.
 * This configuration is the only place the connector is declared: it is read once at startup and
 * never stored, and the UI shows it but never connects, changes or disconnects one. Nothing here
 * names a kind; a secret a kind signs in with stays inside its {@link DeclaredConnector}.
 */
public final class ConfiguredConnector {

    static final String PREFIX = "jeffrey.microscope.connector";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredConnector.class);

    private static final String NAME_PROPERTY = PREFIX + ".name";
    private static final String BLOCK_SEPARATOR = ".";
    private static final String BLOCKS_JOINER = " and ";

    private static final ConfiguredConnector NONE = new ConfiguredConnector(Optional.empty(), Optional.empty());

    private final Optional<ConnectorInfo> connector;
    private final Optional<DeclaredConnector> declared;

    private ConfiguredConnector(Optional<ConnectorInfo> connector, Optional<DeclaredConnector> declared) {
        this.connector = connector;
        this.declared = declared;
    }

    /**
     * The connector the configuration declares, or none — also when the declaration is unusable,
     * which is logged and left out rather than failing startup: Microscope is primarily a local
     * profile viewer, and a typo in an optional connector should not cost the user their recordings.
     */
    public static ConfiguredConnector read(Binder binder, List<ConnectorProvider<?>> providers) {
        List<Block<?>> blocks = providers.stream()
                .map(provider -> Block.bound(binder, provider))
                .flatMap(Optional::stream)
                .toList();
        if (blocks.isEmpty()) {
            return NONE;
        }
        if (blocks.size() > 1) {
            // Microscope reaches exactly one connector, so the blocks exclude each other
            LOG.warn("Ignoring the declared connector: reason={} are all declared; declare one connector kind",
                    blocks.stream().map(Block::path).collect(Collectors.joining(BLOCKS_JOINER)));
            return NONE;
        }
        Block<?> block = blocks.getFirst();
        DeclaredConnector declared;
        try {
            declared = block.declare();
        } catch (IllegalArgumentException e) {
            LOG.warn("Ignoring the declared connector: reason={}: {}", block.path(), e.getMessage());
            return NONE;
        }
        String declaredName = binder.bind(NAME_PROPERTY, String.class).map(String::trim).orElse("");
        String name = declaredName.isEmpty() ? declared.defaultName() : declaredName;
        return new ConfiguredConnector(
                Optional.of(new ConnectorInfo(name, declared.address())), Optional.of(declared));
    }

    /** The connector as the UI and the MCP tools show it; empty when none is declared. */
    public Optional<ConnectorInfo> connector() {
        return connector;
    }

    /** The connector as its kind opens it; empty when none is declared. */
    public Optional<DeclaredConnector> declared() {
        return declared;
    }

    /** One kind's block, bound to the settings record its provider reads. */
    private record Block<S>(ConnectorProvider<S> provider, S settings) {

        /** The kind's block, or empty when the configuration has nothing under it. */
        static <S> Optional<Block<?>> bound(Binder binder, ConnectorProvider<S> provider) {
            return binder.bind(pathOf(provider), Bindable.of(provider.settingsType()))
                    .<Optional<Block<?>>>map(settings -> Optional.of(new Block<>(provider, settings)))
                    .orElse(Optional.empty());
        }

        static String pathOf(ConnectorProvider<?> provider) {
            return PREFIX + BLOCK_SEPARATOR + provider.kind().block();
        }

        String path() {
            return pathOf(provider);
        }

        DeclaredConnector declare() {
            return provider.declare(settings);
        }
    }
}
