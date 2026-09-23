/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository;

public abstract class FileExtensions {

    public static final String ASPROF_TEMP = "jfr.(.*)~";
    public static final String JFR = "jfr";
    public static final String LZ4 = "lz4";
    public static final String JFR_LZ4 = "jfr.lz4";
    public static final String HPROF_GZ = "hprof.gz";
    public static final String HPROF = "hprof";
    public static final String PERF_COUNTERS = "hsperfdata";
    public static final String JVM_LOG = "jvm-log(.[0-9]+)?";
    public static final String HS_JVM_ERROR_LOG = "hs-jvm-err.log";
    public static final String APP_LOG = "log(.rotation)?(.gz|.zip|.zst|.xz|.bz2|.lz4)?";
    public static final String PPROF = "pprof";
    public static final String PPROF_PB_GZ = "pb.gz";
    public static final String OTLP = "otlp";

}
