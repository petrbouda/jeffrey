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

package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the two artefacts a JVM consumes: the {@code @argfile} it is started with, and the
 * {@code .env} file a shell sources.
 */
public class OutputWriter {

    private static final Logger LOG = LoggerFactory.getLogger(OutputWriter.class);

    private final EnvFileBuilder envFileBuilder = new EnvFileBuilder();
    private final JvmArgsFileBuilder argsFileBuilder = new JvmArgsFileBuilder();

    public void write(InitConfig config, SessionLayout layout, String profilerSettings) throws IOException {
        writeEnvFile(config, layout, profilerSettings);
        writeArgFile(config, layout, profilerSettings);
    }

    /** Built only when someone asked for it — either to a file, to stdout, or both. */
    private void writeEnvFile(InitConfig config, SessionLayout layout, String profilerSettings) throws IOException {
        Path envFilePath = config.getEnvFilePath();
        if (envFilePath == null && !config.isPrintEnv()) {
            return;
        }

        String content = envFileBuilder.build(new EnvFileBuilder.Context(
                layout, profilerSettings, config.isJdkJavaOptionsEnabled(),
                config.isHeartbeatEnabled()));

        if (envFilePath != null) {
            Files.writeString(envFilePath, content);
            LOG.debug("Env file written: envFile={}", envFilePath);
        }
        if (config.isPrintEnv()) {
            System.out.print(content);
        }
    }

    private void writeArgFile(InitConfig config, SessionLayout layout, String profilerSettings) throws IOException {
        Path argFilePath = config.getArgFilePath();
        if (argFilePath == null) {
            return;
        }

        Files.writeString(argFilePath, argsFileBuilder.build(
                new JvmArgsFileBuilder.Context(layout.session(), profilerSettings)));
        LOG.debug("Arg file written: argFile={}", argFilePath);
    }
}
