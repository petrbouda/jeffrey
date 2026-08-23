/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.provisioner.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preparing a configuration layer for the merge — what a source means, as opposed to what its
 * settings say.
 */
public abstract class ConfigLayers {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLayers.class);

    /**
     * Drops blank string entries so a file can pre-declare a key it does not set. Config files are
     * routinely written with every key spelled out and the unused ones left empty; without this, a
     * merge reads that empty string as a deliberate value and shadows the layer below it.
     */
    public static Config withoutBlanks(Config config) {
        List<String> blankPaths = new ArrayList<>();
        collectBlankPaths(config.root(), List.of(), blankPaths);

        Config stripped = config;
        for (String path : blankPaths) {
            stripped = stripped.withoutPath(path);
        }
        return stripped;
    }

    /**
     * HOCON discards keys nobody reads, which turns a typo — or a block documenting a feature that
     * was never implemented — into silence. Naming them costs one line and saves the bug report.
     */
    public static void warnAboutUnknownKeys(Config declared, Config known) {
        Set<String> knownKeys = known.root().keySet();
        for (String key : declared.root().keySet()) {
            if (!knownKeys.contains(key)) {
                LOG.warn("Ignoring unknown configuration key: key={} known={}", key, knownKeys);
            }
        }
    }

    private static void collectBlankPaths(ConfigObject object, List<String> prefix, List<String> blankPaths) {
        for (Map.Entry<String, ConfigValue> entry : object.entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(entry.getKey());

            if (entry.getValue() instanceof ConfigObject nested) {
                collectBlankPaths(nested, path, blankPaths);
            } else if (isBlankString(entry.getValue())) {
                blankPaths.add(ConfigUtil.joinPath(path));
            }
        }
    }

    /**
     * Whether the value is a blank string. An unresolved {@code ${...}} substitution cannot be
     * inspected yet and is reported as non-blank, leaving it to the full resolve after the merge.
     */
    private static boolean isBlankString(ConfigValue value) {
        try {
            return value.valueType() == ConfigValueType.STRING && ((String) value.unwrapped()).isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
