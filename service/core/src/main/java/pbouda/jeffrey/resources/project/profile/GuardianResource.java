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

package pbouda.jeffrey.resources.project.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.guardian.guard.Guard.Category;
import pbouda.jeffrey.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.guardian.guard.GuardVisualization;
import pbouda.jeffrey.manager.GuardianManager;

import java.util.*;

public class GuardianResource {

    public record GuardListResponse(String category, List<GuardAnalysisResult> results) {
    }

    private final GuardianManager guardianManager;

    public GuardianResource(GuardianManager guardianManager) {
        this.guardianManager = guardianManager;
    }

    @GET
    public List<GuardListResponse> list() {
        List<GuardAnalysisResult> guardAnalysisResults = guardianManager
                .guardResults()
                .stream()
                .sorted(Comparator.comparing(a -> severityOrder(a.severity())))
                .toList();

        Map<Category, List<GuardAnalysisResult>> output = new TreeMap<>();
        for (GuardAnalysisResult result : guardAnalysisResults) {
            output.compute(result.category(), (category, results) -> {
                if (results != null) {
                    results.add(result);
                    return results;
                } else {
                    List<GuardAnalysisResult> r = new ArrayList<>();
                    r.add(result);
                    return r;
                }
            });
        }

        return output.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getOrder()))
                .map(e -> new GuardListResponse(e.getKey().getLabel(), e.getValue()))
                .toList();
    }

    @POST
    @Path("/flamegraph")
    public JsonNode generateFlamegraph(GuardVisualization request) {
        return guardianManager.generateFlamegraph(request);
    }

    @POST
    @Path("/timeseries")
    public ArrayNode generateTimeseries(GuardVisualization request) {
        return guardianManager.generateTimeseries(request);
    }

    private static int severityOrder(AnalysisResult.Severity severity) {
        return switch (severity) {
            case WARNING -> 1;
            case OK -> 2;
            case INFO -> 3;
            case NA -> 4;
            case IGNORE -> 5;
        };
    }
}
