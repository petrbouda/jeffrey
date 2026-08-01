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

import type { Severity } from '@shared/services/severityDisplay';
import type { PipelineProgress } from '@shared/services/api/model/PipelineRun';

/** A profile group the Advisor can analyze, as reported by the backend. */
export interface AdvisorEventType {
  eventType: string;
  label: string;
}

/** A cached flamegraph prompt. The frame index stays server-side; the UI only shows the markdown. */
export interface AdvisorPrompt {
  eventType: string;
  label: string;
  samples: number;
  markdown: string;
}

/**
 * A citation a recommendation rests on, after checking. An ungrounded claim is shown rather than
 * hidden — a finding the model could not substantiate is exactly what the reader needs to see.
 */
export interface AdvisorClaim {
  eventType: string;
  title: string;
  citedFrame: string;
  sourcePath: string | null;
  grounded: boolean;
  sourceFound: boolean;
  selfPct: number;
  totalPct: number;
}

export type PatchCheckLevel = 'APPLIES' | 'COMPILES' | 'TESTS_PASS';
export type PatchCheckStatus = 'PASSED' | 'FAILED' | 'SKIPPED';

export interface PatchCheck {
  level: PatchCheckLevel;
  status: PatchCheckStatus;
  detail: string | null;
}

/** The ladder of checks run against the patch, plus the revision they were established at. */
export interface PatchVerification {
  checks: PatchCheck[];
  verifiedAgainstRef: string | null;
}

export interface AdvisorRecommendation {
  eventType: string;
  severity: Severity;
  recommendations: string;
  patch: string | null;
  /** Raw JSON of {@link PatchVerification}; parsed by the view that renders the ladder. */
  verification: string | null;
  sourceRef: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number | null;
  generatedAt: number;
  claims: AdvisorClaim[];
}

export interface AdvisorGenerateResponse {
  started: boolean;
  progress: PipelineProgress;
}

export interface AdvisorSettings {
  sourcePath: string;
  configured: boolean;
  pruneThresholdPct: number;
  regressionThresholdPp: number;
  compileCommand: string;
  testCommand: string;
}

export interface FrameDelta {
  frame: string;
  baselineSelfPct: number;
  currentSelfPct: number;
  deltaPp: number;
  isNew: boolean;
}

export interface RegressionComparison {
  eventType: string;
  thresholdPp: number;
  regressed: FrameDelta[];
  improved: FrameDelta[];
  promptFragment: string;
}

export interface FleetOccurrence {
  profileId: string;
  profileName: string | null;
  workspaceId: string | null;
  projectId: string;
  eventType: string;
  title: string;
  sourcePath: string | null;
  selfPct: number;
}

export interface FleetPattern {
  citedFrame: string;
  projectCount: number;
  occurrenceCount: number;
  peakSelfPct: number;
  occurrences: FleetOccurrence[];
}

export interface FleetPatterns {
  patterns: FleetPattern[];
  analyzedProjectCount: number;
}
