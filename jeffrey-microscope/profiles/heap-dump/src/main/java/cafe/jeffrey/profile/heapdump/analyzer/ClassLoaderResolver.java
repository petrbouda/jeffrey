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

package cafe.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Instance;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderRef;

/**
 * Resolves the class loader that defined the class of a given heap instance.
 * Bootstrap-loaded classes (where {@code JavaClass.getClassLoader()} is {@code null})
 * are reported as {@link ClassLoaderRef#BOOTSTRAP}.
 */
public final class ClassLoaderResolver {

    private ClassLoaderResolver() {
    }

    public static ClassLoaderRef refFor(Instance instance) {
        if (instance == null) {
            return ClassLoaderRef.BOOTSTRAP;
        }
        Instance loader = instance.getJavaClass().getClassLoader();
        if (loader == null) {
            return ClassLoaderRef.BOOTSTRAP;
        }
        return new ClassLoaderRef(loader.getInstanceId(), loader.getJavaClass().getName());
    }
}
