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

package cafe.jeffrey.microscope.connector.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/** The connector kinds on the class or module path, found by {@link ServiceLoader}. */
public final class ConnectorProviders {

    private ConnectorProviders() {
    }

    /** Every kind found, ordered by its configuration block. */
    public static List<ConnectorProvider<?>> load() {
        List<ConnectorProvider<?>> found = new ArrayList<>();
        ServiceLoader.load(ConnectorProvider.class, ConnectorProvider.class.getClassLoader())
                .forEach(found::add);
        return of(found);
    }

    /**
     * The kinds given, ordered by their configuration block.
     *
     * @throws IllegalStateException when two of them claim the same block or the same name: either
     *                               would leave a declaration that could mean two kinds
     */
    static List<ConnectorProvider<?>> of(List<ConnectorProvider<?>> providers) {
        Set<String> blocks = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ConnectorProvider<?> provider : providers) {
            if (!blocks.add(provider.kind().block()) || !names.add(provider.kind().name())) {
                throw new IllegalStateException("Two connector providers claim the same kind: kind=" + provider.kind());
            }
        }
        return providers.stream()
                .sorted(Comparator.comparing(provider -> provider.kind().block()))
                .toList();
    }
}
