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

package cafe.jeffrey.agent.tracing;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Loads the fixtures itself while hiding chosen classes from them, which is how a test reproduces
 * the application that has the agent but not {@code jeffrey-events}.
 * <p>
 * Defining the fixtures here rather than delegating is the point: a class's tracing runtime is
 * resolved through the loader that defined it, so only a class defined by this loader is subject to
 * what this loader refuses to find.
 */
final class FilteringClassLoader extends ClassLoader {

    private static final String FIXTURES_PACKAGE = "cafe.jeffrey.agent.tracing.fixtures.";

    private final Set<String> hidden;

    FilteringClassLoader(ClassLoader parent, Set<String> hidden) {
        super(parent);
        this.hidden = Set.copyOf(hidden);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (hidden.contains(name)) {
            throw new ClassNotFoundException(name + " is hidden from " + this);
        }
        if (!name.startsWith(FIXTURES_PACKAGE)) {
            return super.loadClass(name, resolve);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) {
                return alreadyLoaded;
            }

            Class<?> defined = define(name);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
        }
    }

    private Class<?> define(String name) throws ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream bytecode = getParent().getResourceAsStream(resource)) {
            if (bytecode == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = bytecode.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
