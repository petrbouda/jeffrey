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

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderClasspath;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigIncluderURL;
import com.typesafe.config.ConfigObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

/**
 * An includer that resolves nothing.
 *
 * <p>HOCON can pull another file, resource or URL into a configuration. A hub-published file is
 * written by whoever reaches the hub and parsed inside an application's own container, so honouring
 * that would turn the published file into a way to read arbitrary paths off that container, or to
 * fetch configuration over the network from it. The hub renders these files itself and never needs
 * an include, so the safe answer to every form is the same: nothing.
 *
 * <p>All four include forms are implemented deliberately. {@link ConfigIncluder} alone covers only
 * the heuristic {@code include "name"}; {@code include file(...)}, {@code include url(...)} and
 * {@code include classpath(...)} are dispatched through the three sibling interfaces and fall back
 * to the default includer when they are not implemented — which is to say, they would still be
 * followed.
 */
final class NoIncludes
        implements ConfigIncluder, ConfigIncluderFile, ConfigIncluderURL, ConfigIncluderClasspath {

    private static final Logger LOG = LoggerFactory.getLogger(NoIncludes.class);

    static final NoIncludes INSTANCE = new NoIncludes();

    private NoIncludes() {
    }

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
        // Refusing the fallback is the point: a delegate would be the default includer
        return this;
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
        return refuse(what);
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File file) {
        return refuse(file.getPath());
    }

    @Override
    public ConfigObject includeURL(ConfigIncludeContext context, URL url) {
        return refuse(url.toString());
    }

    @Override
    public ConfigObject includeResources(ConfigIncludeContext context, String resource) {
        return refuse(resource);
    }

    private static ConfigObject refuse(String what) {
        LOG.warn("Ignoring an include directive in a published configuration file: include={}", what);
        return ConfigFactory.empty().root();
    }
}
