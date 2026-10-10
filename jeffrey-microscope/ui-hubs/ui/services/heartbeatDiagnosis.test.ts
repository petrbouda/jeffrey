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

import { describe, expect, it } from 'vitest';
import {
  diagnoseMissingHeartbeat,
  heartbeatVerdict,
  VERDICT_COPY
} from '@hubs/services/heartbeatDiagnosis.ts';
import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';
import RecordingFileType from '@hubs/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@hubs/services/api/model/RepositoryFile.ts';
import MissingHeartbeat from '@hubs/services/api/model/MissingHeartbeat.ts';

const CREATED_AT = 1_750_000_000_000;
const MINUTE = 60_000;

function file(id: string, createdAt: number, isRecording: boolean): RepositoryFile {
  return new RepositoryFile(
    id,
    id,
    createdAt,
    RecordingStatus.FINISHED,
    1024,
    isRecording ? RecordingFileType.JFR : RecordingFileType.JVM_LOG,
    isRecording
  );
}

function missingSession(agentPresent: boolean, files: RepositoryFile[]): RecordingSession {
  return new RecordingSession(
    's1',
    's1',
    'inst-1',
    CREATED_AT,
    CREATED_AT,
    RecordingStatus.FINISHED,
    0,
    files,
    false,
    new MissingHeartbeat(agentPresent)
  );
}

describe('heartbeatVerdict', () => {
  it('blames a JVM that never started when the agent was written but nothing was recorded', () => {
    expect(heartbeatVerdict(true, false)).toBe('JVM_NEVER_STARTED');
  });

  it('blames switched-off liveness when the agent was written and the profiler records', () => {
    expect(heartbeatVerdict(true, true)).toBe('LIVENESS_DISABLED');
  });

  it('blames a switched-off agent when the profiler records without it', () => {
    expect(heartbeatVerdict(false, true)).toBe('AGENT_DISABLED');
  });

  it('blames both when there is neither an agent nor a recording', () => {
    expect(heartbeatVerdict(false, false)).toBe('JVM_NEVER_STARTED_AGENT_DISABLED');
  });

  it('has a headline and advice for every verdict', () => {
    for (const copy of Object.values(VERDICT_COPY)) {
      expect(copy.headline).toMatch(/^Most likely: /);
      expect(copy.advice.length).toBeGreaterThan(0);
    }
  });
});

describe('diagnoseMissingHeartbeat', () => {
  it('counts only recording files and reports the newest one', () => {
    const diagnosis = diagnoseMissingHeartbeat(
      missingSession(true, [
        file('a.jfr', CREATED_AT + MINUTE, true),
        file('b.jfr', CREATED_AT + 3 * MINUTE, true),
        file('jvm.log', CREATED_AT + 4 * MINUTE, false)
      ])
    );

    expect(diagnosis.registeredAt).toBe(CREATED_AT);
    expect(diagnosis.agentPresent).toBe(true);
    expect(diagnosis.recordingFileCount).toBe(2);
    expect(diagnosis.lastRecordingFileAt).toBe(CREATED_AT + 3 * MINUTE);
    expect(diagnosis.verdict).toBe('LIVENESS_DISABLED');
  });

  it('treats a session with only non-recording files as having no recording', () => {
    const diagnosis = diagnoseMissingHeartbeat(
      missingSession(false, [file('jvm.log', CREATED_AT + MINUTE, false)])
    );

    expect(diagnosis.recordingFileCount).toBe(0);
    expect(diagnosis.lastRecordingFileAt).toBeNull();
    expect(diagnosis.verdict).toBe('JVM_NEVER_STARTED_AGENT_DISABLED');
  });

  it('reads the agent flag from the missing heartbeat', () => {
    expect(diagnoseMissingHeartbeat(missingSession(true, [])).verdict).toBe('JVM_NEVER_STARTED');
    expect(
      diagnoseMissingHeartbeat(missingSession(false, [file('a.jfr', CREATED_AT, true)])).verdict
    ).toBe('AGENT_DISABLED');
  });
});

describe('MissingHeartbeat.fromJson', () => {
  it('maps an absent or null object to no missing heartbeat', () => {
    expect(MissingHeartbeat.fromJson(undefined)).toBeNull();
    expect(MissingHeartbeat.fromJson(null)).toBeNull();
  });

  it('maps the agent flag of a reported missing heartbeat', () => {
    expect(MissingHeartbeat.fromJson({ agentPresent: true })).toEqual(new MissingHeartbeat(true));
    expect(MissingHeartbeat.fromJson({ agentPresent: false })).toEqual(new MissingHeartbeat(false));
    expect(MissingHeartbeat.fromJson({})).toEqual(new MissingHeartbeat(false));
  });
});
