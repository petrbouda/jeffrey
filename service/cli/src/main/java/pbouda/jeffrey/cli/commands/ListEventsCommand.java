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

import pbouda.jeffrey.common.FileUtils;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.EventInformationProvider;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.settings.ParsingActiveSettingsProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Command(
        name = "events",
        description = "List all event-types containing a stacktrace for building a flamegraph",
        mixinStandardHelpOptions = true)
public class ListEventsCommand implements Runnable {

    @Parameters(paramLabel = "<jfr_file>", description = "One JFR file for listing stack-based events", arity = "1")
    File file;

    @Override
    public void run() {
        Path recording = CommandUtils.replaceTilda(file.toPath());
        CommandUtils.checkPathExists(recording);

        List<Path> recordings = Files.isDirectory(recording)
                ? FileUtils.listJfrFiles(recording)
                : List.of(recording);

        try {
            List<EventSummary> eventSummaries =
                    new EventInformationProvider(
                            new ParsingActiveSettingsProvider(recordings), recordings, ProcessableEvents.all()).get()
                            .stream()
                            .sorted(Comparator.comparing(EventSummary::samples).reversed())
                            .toList();

            EventSummariesTablePrinter.print(eventSummaries);
        } catch (Exception e) {
            System.out.println("Cannot read events: file=" + file + " error=" + e.getMessage());
        }
    }
}
