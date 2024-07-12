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

package pbouda.jeffrey.cli.commands;

import pbouda.jeffrey.cli.FlamegraphContentReplacer;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;

@Command(
        name = "flame",
        description = "Generates a Flamegraph (default: jdk.ExecutionSample)",
        mixinStandardHelpOptions = true)
public class FlameCommand extends AbstractFlameCommand {

    public FlameCommand() {
        super(GraphType.PRIMARY, new FlamegraphGeneratorImpl());
    }

    @Parameters(paramLabel = "<jfr_file>", description = "one JFR file for fetching events", arity = "1")
    File file;

    @Option(
            names = {"-t", "--thread"},
            description = "groups stacktraces omitted on the particular thread")
    boolean threadMode = false;

    @Option(
            names = {"-s", "--search-pattern"},
            description = "only for timeseries (timeseries cannot dynamically searches in the generated file, only the flamegraph can)")
    String searchPattern;

    @Override
    protected String customReplace(String content) {
        return searchPattern != null
                ? FlamegraphContentReplacer.replaceSearch(content, searchPattern)
                : content;
    }

    @Override
    Config defineConfig() {
        Path primary = file.toPath();

        return Config.primaryBuilder()
                .withPrimaryRecording(primary)
                .withEventType(Type.fromCode(eventType))
                .withThreadMode(threadMode)
                .withSearchPattern(searchPattern)
                .withCollectWeight(weight)
                .build();
    }
}
