/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.microscope.profile.management.manager.registry;

import cafe.jeffrey.microscope.profile.management.manager.ProfileConfigurationManager;
import cafe.jeffrey.microscope.profile.management.manager.ProfileCustomManager;
import cafe.jeffrey.microscope.profile.management.manager.ProfileFeaturesManager;
import cafe.jeffrey.microscope.profile.management.manager.ProfileToolsManager;
import cafe.jeffrey.microscope.profile.management.perfcounters.PerfCountersManager;
import cafe.jeffrey.microscope.profile.management.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.microscope.profile.management.tools.otlp.OtlpExportManager;
import cafe.jeffrey.microscope.profile.management.tools.pprof.PprofExportManager;


public record ProfileManagerFactoryRegistry(
        VisualizationFactories visualization,
        AnalysisFactories analysis,
        JvmInsightFactories jvmInsight,
        ProfileConfigurationManager.Factory configuration,
        ProfileFeaturesManager.Factory features,
        PerfCountersManager.Factory perfCounters,
        ProfileToolsManager.Factory tools,
        CollapseFramesManager.Factory collapseFrames,
        PprofExportManager.Factory pprofExport,
        OtlpExportManager.Factory otlpExport,
        ProfileCustomManager.Factory custom) {
}
