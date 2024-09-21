/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package io.jafar.parser.api;
import io.jafar.parser.JafarParserImpl;
import io.jafar.parser.api.types.JFRHandler;

import java.io.IOException;
import java.nio.file.Paths;

public interface JafarParser extends AutoCloseable{
    static JafarParser open(String path) {
        return new JafarParserImpl(Paths.get(path));
    }


    <T> HandlerRegistration<T> handle(Class<T> clz, JFRHandler<T> handler);

    void run() throws IOException;
}
