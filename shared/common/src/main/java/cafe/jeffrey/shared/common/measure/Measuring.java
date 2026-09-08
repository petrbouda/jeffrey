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

package cafe.jeffrey.shared.common.measure;

import java.time.Duration;
import java.util.function.Supplier;

public abstract class Measuring {

    public static Duration r(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    public static <T> Elapsed<T> s(Supplier<T> supplier) {
        long start = System.nanoTime();
        T entity = supplier.get();
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        return new Elapsed<>(duration, entity);
    }
}
