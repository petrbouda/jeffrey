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

package cafe.jeffrey.profile.manager.registry;

import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileToolsManager;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.profile.tools.otlp.OtlpExportManager;
import cafe.jeffrey.profile.tools.pprof.PprofExportManager;


public record ProfileManagerFactoryRegistry(
        VisualizationFactories visualization,
        AnalysisFactories analysis,
        JvmInsightFactories jvmInsight,
        ProfileConfigurationManager.Factory configuration,
        ProfileFeaturesManager.Factory features,
        AdditionalFilesManager.Factory additionalFiles,
        ProfileToolsManager.Factory tools,
        CollapseFramesManager.Factory collapseFrames,
        PprofExportManager.Factory pprofExport,
        OtlpExportManager.Factory otlpExport,
        ProfileCustomManager.Factory custom) {
}
