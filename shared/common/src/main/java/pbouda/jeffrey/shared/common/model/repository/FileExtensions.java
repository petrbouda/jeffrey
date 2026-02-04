/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common.model.repository;

public abstract class FileExtensions {

    public static final String ASPROF_TEMP = "jfr.(.*)~";
    public static final String JFR = "jfr";
    public static final String LZ4 = "lz4";
    public static final String JFR_LZ4 = "jfr.lz4";
    public static final String HPROF_GZ = "hprof.gz";
    public static final String HPROF = "hprof";
    public static final String PERF_COUNTERS = "hsperfdata";
    public static final String JVM_LOG = "-jvm.log(.[0-9]+)?";
    public static final String HS_JVM_ERROR_LOG = "hs-jvm-err.log";

}
