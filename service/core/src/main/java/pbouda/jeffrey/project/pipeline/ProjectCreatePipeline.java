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

package pbouda.jeffrey.project.pipeline;

import pbouda.jeffrey.common.pipeline.Pipeline;
import pbouda.jeffrey.common.pipeline.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProjectCreatePipeline implements Pipeline<CreateProjectContext> {

    private final List<Stage<CreateProjectContext>> stages = new ArrayList<>();

    @Override
    public CreateProjectContext execute(CreateProjectContext input) {
        CreateProjectContext context = input;
        for (Stage<CreateProjectContext> stage : stages) {
            context = stage.execute(context);
            if (context == null) {
                return null;
            }
        }
        return context;
    }

    @Override
    public Pipeline<CreateProjectContext> addStage(Stage<CreateProjectContext> stage) {
        stages.add(stage);
        return this;
    }
}
