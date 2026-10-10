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

import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';

/**
 * Why a session never reported a heartbeat, read off the two facts the hub can see in the
 * session directory: whether the Provisioner wrote the Jeffrey Agent there, and whether the
 * profiler produced any recording file.
 */
export type HeartbeatVerdict =
  | 'JVM_NEVER_STARTED'
  | 'LIVENESS_DISABLED'
  | 'AGENT_DISABLED'
  | 'JVM_NEVER_STARTED_AGENT_DISABLED';

/** A run of advice text; `code` renders as inline code (a property, a command, a file). */
export interface AdviceSegment {
  text: string;
  code?: boolean;
}

export interface HeartbeatVerdictCopy {
  headline: string;
  advice: AdviceSegment[];
}

export interface HeartbeatDiagnosis {
  /** Epoch millis the Provisioner registered the session. */
  registeredAt: number;
  agentPresent: boolean;
  recordingFileCount: number;
  /** Epoch millis of the newest recording file, null when there is none. */
  lastRecordingFileAt: number | null;
  verdict: HeartbeatVerdict;
}

/** The startup grace the hub waits for a first heartbeat before flagging a session. */
export const HEARTBEAT_GRACE_LABEL = '1m';

/** Where the Provisioner writes the Jeffrey Agent inside the session directory. */
export const AGENT_JAR_FILE = '.jeffrey-agent.jar';

/** Where a heartbeat lands inside the session directory. */
export const HEARTBEAT_FILE = '.heartbeat/heartbeat';

export const VERDICT_COPY: Record<HeartbeatVerdict, HeartbeatVerdictCopy> = {
  JVM_NEVER_STARTED: {
    headline: 'Most likely: the JVM never started, or died before the agent ran.',
    advice: [
      { text: 'The agent was in place but wrote nothing, and the profiler opened no file. ' },
      { text: "Check the container's logs for the previous run, e.g. " },
      { text: 'kubectl logs <pod> --previous', code: true },
      { text: '. The heartbeat configuration itself looks fine.' }
    ]
  },
  LIVENESS_DISABLED: {
    headline: 'Most likely: liveness is switched off in the application.',
    advice: [
      { text: 'The agent is attached and the profiler records, but no heartbeat is written. ' },
      { text: 'Check that the application does not set ' },
      { text: 'jeffrey.heartbeat.enabled=false', code: true },
      { text: ' and that the heartbeat directory is writable.' }
    ]
  },
  AGENT_DISABLED: {
    headline: 'Most likely: the Jeffrey Agent is switched off.',
    advice: [
      { text: 'Remove ' },
      { text: 'JEFFREY_AGENT_ENABLED=false', code: true },
      { text: ' from the container (the agent is on by default), or add ' },
      { text: 'jeffrey-heartbeat', code: true },
      { text: ' (or ' },
      { text: 'jeffrey-heartbeat-spring-boot-starter', code: true },
      { text: ') to the application.' }
    ]
  },
  JVM_NEVER_STARTED_AGENT_DISABLED: {
    headline: 'Most likely: the JVM never started, and the Jeffrey Agent is switched off.',
    advice: [
      { text: "Check the container's logs for the previous run, and re-enable the agent (" },
      { text: 'JEFFREY_AGENT_ENABLED', code: true },
      { text: ') or add ' },
      { text: 'jeffrey-heartbeat', code: true },
      { text: ' to the application.' }
    ]
  }
};

/** Verdict per (agent present, recording files present). */
const VERDICTS: Record<'agent' | 'noAgent', Record<'files' | 'noFiles', HeartbeatVerdict>> = {
  agent: { files: 'LIVENESS_DISABLED', noFiles: 'JVM_NEVER_STARTED' },
  noAgent: { files: 'AGENT_DISABLED', noFiles: 'JVM_NEVER_STARTED_AGENT_DISABLED' }
};

export function heartbeatVerdict(agentPresent: boolean, hasRecordingFiles: boolean): HeartbeatVerdict {
  return VERDICTS[agentPresent ? 'agent' : 'noAgent'][hasRecordingFiles ? 'files' : 'noFiles'];
}

/** Diagnoses a session whose heartbeats are missing from what its directory holds. */
export function diagnoseMissingHeartbeat(session: RecordingSession): HeartbeatDiagnosis {
  const recordingFiles = session.files.filter(file => file.isRecording);
  const lastRecordingFileAt =
    recordingFiles.length > 0 ? Math.max(...recordingFiles.map(file => file.createdAt)) : null;
  const agentPresent = session.heartbeat.agentPresent;

  return {
    registeredAt: session.createdAt,
    agentPresent: agentPresent,
    recordingFileCount: recordingFiles.length,
    lastRecordingFileAt: lastRecordingFileAt,
    verdict: heartbeatVerdict(agentPresent, recordingFiles.length > 0)
  };
}

/**
 * How long a no-heartbeat session demonstrably recorded: from its start to its newest file of any
 * kind. Null when it has no files — then nothing says how long the JVM ran.
 */
export function recordedSpanMillis(session: RecordingSession): number | null {
  if (session.files.length === 0) {
    return null;
  }
  const newest = Math.max(...session.files.map(file => file.createdAt));
  return Math.max(0, newest - session.createdAt);
}
