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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationPhaseTest {

    /**
     * The profile pipeline publishes its phase from common-profile, which knows nothing of this enum;
     * the code it writes is pinned to the constant that reads it back.
     */
    @Test
    void readsThePhaseTheProfilePipelinePublishes() {
        assertEquals(OperationPhase.PIPELINE, OperationPhase.ofCode(PipelineRunRegistry.OPERATION_PHASE));
    }

    @Test
    void everyPhaseReadsBackFromTheCodeItIsRecordedAs() {
        for (OperationPhase phase : OperationPhase.values()) {
            assertEquals(phase, OperationPhase.ofCode(phase.code()));
        }
    }

    @Test
    void refusesACodeNoPhaseRecords() {
        assertThrows(IllegalStateException.class, () -> OperationPhase.ofCode("downloading chunk 3"));
    }
}
