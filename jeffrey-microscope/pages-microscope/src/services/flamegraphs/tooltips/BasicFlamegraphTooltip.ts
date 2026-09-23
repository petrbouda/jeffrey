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

import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import Frame from '@/services/api/model/Frame';
import FrameType from '@/services/flamegraphs/FrameType';

export default class BasicFlamegraphTooltip extends FlamegraphTooltip {
  private readonly weightTitle: string | null;
  private readonly weightFormatter: ((value: number, base: number) => string) | null;
  private readonly showPositionAndTypes: boolean;

  constructor(
    eventType: string,
    useWeight: boolean,
    weightTitle: string | null = null,
    weightFormatter: ((value: number, base: number) => string) | null = null,
    showPositionAndTypes: boolean = false
  ) {
    super(eventType, useWeight);
    this.weightTitle = weightTitle;
    this.weightFormatter = weightFormatter;
    this.showPositionAndTypes = showPositionAndTypes;
  }

  generate(frame: Frame, levelTotalSamples: number, levelTotalWeight: number): string {
    if (frame.type === FrameType.TRUNCATED_SYNTHETIC) {
      return FlamegraphTooltip.truncated(
        frame,
        levelTotalSamples,
        this.useWeight,
        this.eventType,
        levelTotalWeight
      );
    }

    const selfSamples = frame.selfSamples ?? 0;

    let samplesHtml = `
            <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                <span class="small text-muted">Samples (total):</span>
                <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(frame.totalSamples, levelTotalSamples)}</span>
            </div>`;

    if (selfSamples > 0) {
      samplesHtml += `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">Samples (self):</span>
                    <span class="small fw-semibold ms-2">${FlamegraphTooltip.format_samples(selfSamples, levelTotalSamples)}</span>
                </div>`;
    }

    if (this.weightTitle && this.weightFormatter) {
      samplesHtml += `
                <div class="d-flex justify-content-between align-items-center" style="padding:2px 0">
                    <span class="small text-muted">${this.weightTitle}:</span>
                    <span class="small fw-semibold ms-2">${this.weightFormatter(frame.totalWeight ?? 0, levelTotalWeight)}</span>
                </div>`;
    }

    let extraSections = '';
    if (this.showPositionAndTypes) {
      if (frame.position !== undefined) {
        extraSections += FlamegraphTooltip.position(frame.position, frame.type);
      }
      if (frame.sampleTypes !== undefined) {
        extraSections += FlamegraphTooltip.frame_types(frame.sampleTypes);
      }
      extraSections += FlamegraphTooltip.self_vs_total(selfSamples, frame.totalSamples);
    }

    return `
            ${FlamegraphTooltip.header(frame)}
            <div style="padding:6px 0 6px">
                <div style="padding:2px 10px 6px">
                    ${samplesHtml}
                </div>
                ${extraSections}
            </div>
            ${FlamegraphTooltip.ide_action(frame)}`;
  }
}
