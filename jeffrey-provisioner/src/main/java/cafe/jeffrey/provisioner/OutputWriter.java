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
                layout, profilerSettings, config.isJdkJavaOptionsEnabled()));

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
