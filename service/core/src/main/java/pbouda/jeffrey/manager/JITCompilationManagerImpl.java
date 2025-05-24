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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.manager.model.JITCompilationStats;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

public class JITCompilationManagerImpl implements JITCompilationManager {

    private final ProfileEventRepository profileEventRepository;

    public JITCompilationManagerImpl(ProfileEventRepository profileEventRepository) {
        this.profileEventRepository = profileEventRepository;
    }

    @Override
    public JITCompilationStats jitCompilationStats() {
        return Json.read(JIT_DATA, JITCompilationStats.class);
    }

    private static final String JIT_DATA = """
            {
                "compileCount": 8742,
                "bailoutCount": 124,
                "invalidatedCount": 89,
                "osrCompileCount": 1245,
                "standardCompileCount": 7497,
                "osrBytesCompiled": 3245678,
                "standardBytesCompiled": 19876543,
                "nmethodsSize": 25678432,
                "nmethodCodeSize": 18345678,
                "peakTimeSpent": 842,
                "totalTimeSpent": 12478,
                "longCompilations": [
                    {
                        "compileId": 1,
                        "compiler": "C2",
                        "method": "java.lang.String::substring",
                        "compileLevel": 4,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 5120,
                        "inlinedBytes": 2048,
                        "arenaBytes": 1024,
                        "timeSpent": 150
                    },
                    {
                        "compileId": 2,
                        "compiler": "C1",
                        "method": "java.util.ArrayList::add",
                        "compileLevel": 1,
                        "succeeded": true,
                        "isOsr": true,
                        "codeSize": 2560,
                        "inlinedBytes": 1024,
                        "arenaBytes": 512,
                        "timeSpent": 75
                    },
                    {
                        "compileId": 3,
                        "compiler": "JVMCI",
                        "method": "java.util.HashMap::get",
                        "compileLevel": 4,
                        "succeeded": false,
                        "isOsr": false,
                        "codeSize": 4096,
                        "inlinedBytes": 2048,
                        "arenaBytes": 1024,
                        "timeSpent": 200
                    },
                    {
                        "compileId": 4,
                        "compiler": "C2",
                        "method": "org.apache.commons.lang3.StringUtils::containsIgnoreCase",
                        "compileLevel": 4,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 8192,
                        "inlinedBytes": 3584,
                        "arenaBytes": 2048,
                        "timeSpent": 320
                    },
                    {
                        "compileId": 5,
                        "compiler": "C1",
                        "method": "java.util.concurrent.ConcurrentHashMap::putVal",
                        "compileLevel": 3,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 4352,
                        "inlinedBytes": 1792,
                        "arenaBytes": 1024,
                        "timeSpent": 95
                    },
                    {
                        "compileId": 6,
                        "compiler": "C2",
                        "method": "java.io.BufferedInputStream::read",
                        "compileLevel": 4,
                        "succeeded": true,
                        "isOsr": true,
                        "codeSize": 3072,
                        "inlinedBytes": 1536,
                        "arenaBytes": 768,
                        "timeSpent": 180
                    },
                    {
                        "compileId": 7,
                        "compiler": "JVMCI",
                        "method": "com.fasterxml.jackson.databind.ObjectMapper::readValue",
                        "compileLevel": 4,
                        "succeeded": false,
                        "isOsr": false,
                        "codeSize": 12288,
                        "inlinedBytes": 6144,
                        "arenaBytes": 4096,
                        "timeSpent": 450
                    },
                    {
                        "compileId": 8,
                        "compiler": "C2",
                        "method": "java.util.regex.Pattern$CharProperty::match",
                        "compileLevel": 4,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 7168,
                        "inlinedBytes": 3072,
                        "arenaBytes": 1536,
                        "timeSpent": 220
                    },
                    {
                        "compileId": 9,
                        "compiler": "C2",
                        "method": "org.hibernate.collection.internal.PersistentSet::size",
                        "compileLevel": 4,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 2048,
                        "inlinedBytes": 1024,
                        "arenaBytes": 512,
                        "timeSpent": 120
                    },
                    {
                        "compileId": 10,
                        "compiler": "C1",
                        "method": "java.util.concurrent.locks.ReentrantLock::lock",
                        "compileLevel": 2,
                        "succeeded": true,
                        "isOsr": false,
                        "codeSize": 1536,
                        "inlinedBytes": 768,
                        "arenaBytes": 384,
                        "timeSpent": 65
                    }
                ]
            }
            """;
}
