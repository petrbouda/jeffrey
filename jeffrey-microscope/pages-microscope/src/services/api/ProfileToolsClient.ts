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

import axios from 'axios';
import BaseProfileClient from '@/services/api/BaseProfileClient';
import type RenameFramesPreview from '@/services/api/model/RenameFramesPreview';
import type RenameFramesResult from '@/services/api/model/RenameFramesResult';
import type CollapseFramesPreview from '@/services/api/model/CollapseFramesPreview';
import type CollapseFramesResult from '@/services/api/model/CollapseFramesResult';
import type PprofExportEventType from '@/services/api/model/PprofExportEventType';
import type OtlpExportEventType from '@/services/api/model/OtlpExportEventType';
import type OtlpExportSelection from '@/services/api/model/OtlpExportSelection';

export default class ProfileToolsClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'tools');
  }

  // --- Rename Frames ---

  public previewRename(search: string, replacement: string): Promise<RenameFramesPreview> {
    return this.post<RenameFramesPreview>('/rename-frames/preview', { search, replacement });
  }

  public executeRename(search: string, replacement: string): Promise<RenameFramesResult> {
    return this.post<RenameFramesResult>('/rename-frames', { search, replacement });
  }

  // --- Collapse Frames ---

  public previewCollapse(patterns: string[], label: string): Promise<CollapseFramesPreview> {
    return this.post<CollapseFramesPreview>('/collapse-frames/preview', { patterns, label });
  }

  public executeCollapse(patterns: string[], label: string): Promise<CollapseFramesResult> {
    return this.post<CollapseFramesResult>('/collapse-frames', { patterns, label });
  }

  // --- Convert to pprof ---

  /** Lists the profile's stack-based event types eligible for pprof export. */
  public pprofEventTypes(): Promise<PprofExportEventType[]> {
    return this.get<PprofExportEventType[]>('/pprof/event-types');
  }

  /** Generates a pprof (.pb.gz) for a single event type and returns it as a binary blob. */
  public downloadPprof(eventType: string, includeWeight: boolean): Promise<Blob> {
    return axios
      .post<Blob>(
        `${this.baseUrl}/pprof/download`,
        { eventType, includeWeight },
        { responseType: 'blob' }
      )
      .then(response => response.data);
  }

  /** Generates a pprof for a single event type and adds it to the profile's project as a recording. */
  public addPprofToRecordings(
    eventType: string,
    includeWeight: boolean
  ): Promise<{ recordingId: string }> {
    return this.post<{ recordingId: string }>('/pprof/add-to-recordings', {
      eventType,
      includeWeight
    });
  }

  // --- Convert to OTLP ---

  /** Lists the profile's stack-based event types eligible for OTLP export. */
  public otlpEventTypes(): Promise<OtlpExportEventType[]> {
    return this.get<OtlpExportEventType[]>('/otlp/event-types');
  }

  /**
   * Generates an OpenTelemetry profiles (.otlp) file as a binary blob. Each selected event type becomes its
   * own OTLP Profile inside the single file.
   */
  public downloadOtlp(selections: OtlpExportSelection[]): Promise<Blob> {
    return axios
      .post<Blob>(`${this.baseUrl}/otlp/download`, { selections }, { responseType: 'blob' })
      .then(response => response.data);
  }

  /** Generates an OTLP file for the selected event types and adds it to the profile's project as a recording. */
  public addOtlpToRecordings(
    selections: OtlpExportSelection[]
  ): Promise<{ recordingId: string }> {
    return this.post<{ recordingId: string }>('/otlp/add-to-recordings', { selections });
  }
}
