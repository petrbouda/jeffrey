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

package pbouda.jeffrey.cli.replacer;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.cli.commands.FlameCommand;
import pbouda.jeffrey.cli.commands.FlameDiffCommand;
import pbouda.jeffrey.cli.commands.SubSecondCommand;
import pbouda.jeffrey.cli.commands.SubSecondDiffCommand;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.ResourceUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class SubSecondContentReplacer {

    private static final String HTML_SOURCE = "subsecond/subsecond.html";

    private static final String SUBSECOND_PRIMARY_TOKEN = "{{REPLACE_SUBSECOND_PRIMARY}}";
    private static final String SUBSECOND_COMMAND = "{{REPLACE_SUBSECOND_COMMAND}}";
    private static final String SUBSECOND_SECONDARY_TOKEN = "{{REPLACE_SUBSECOND_SECONDARY}}";

    public static String loadContentAndReplace(String eventType, GraphType graphType) {
        return ResourceUtils.readFromClasspath(HTML_SOURCE)
                .replace(ContentReplacer.GRAPH_TYPE_TOKEN, graphType.name())
                .replace(ContentReplacer.EVENT_TYPE_TOKEN, eventType);
    }

    public static String primary(String content, JsonNode primary) {
        return content.replace(SUBSECOND_PRIMARY_TOKEN, ContentReplacer.compressAndEncode(primary));
    }

    public static String secondary(String content, JsonNode secondary) {
        return content.replace(SUBSECOND_SECONDARY_TOKEN, ContentReplacer.compressAndEncode(secondary));
    }

    public static String primaryCommand(
            String content, String eventType, Path outputDir, Path primaryPath) {

        Path outputFile = outputDir.resolve("flamegraph-<start-time>-<end-time>.html");
        String result = resolveJavaCommand() + FlameCommand.COMMAND_NAME
                + " --start-time=<start-time> --end-time=<end-time>"
                + " --with-timeseries=false"
                + " --event-type=" + eventType
                + " --output=" + outputFile
                + " " + primaryPath;

        return content.replace(SUBSECOND_COMMAND, result);
    }

    public static String secondaryCommand(
            String content, String eventType, Path outputDir, Path primaryPath, Path secondaryPath) {

        Path outputFile = outputDir.resolve("diffgraph-<start-time>-<end-time>.html");
        String result = resolveJavaCommand() + FlameDiffCommand.COMMAND_NAME
                + " --start-time=<start-time> --end-time=<end-time>"
                + " --with-timeseries=false"
                + " --event-type=" + eventType
                + " --output=" + outputFile
                + " " + primaryPath
                + " " + secondaryPath;

        return content.replace(SUBSECOND_COMMAND, result);
    }

    private static String resolveJavaCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (info.command().isEmpty() || info.arguments().isEmpty()) {
            return "";
        }

        String arguments = Arrays.stream(info.arguments().get())
                .takeWhile(isNotSubSecondCommand())
                .collect(Collectors.joining(" "));

        return info.command().get() + " " + arguments + " ";
    }

    private static Predicate<String> isNotSubSecondCommand() {
        return arg -> !arg.equals(SubSecondCommand.COMMAND_NAME)
                && !arg.equals(SubSecondDiffCommand.COMMAND_NAME);
    }
}
