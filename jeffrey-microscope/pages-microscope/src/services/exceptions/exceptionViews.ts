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

import type {
  ExceptionSiteStat,
  ExceptionTypeStat,
  ThrowFrame
} from '@/services/api/model/ExceptionsModels';

/** Which throws a type table shows, by the origin the server classified them with. */
export type OriginFilter = 'ALL' | 'APPLICATION' | 'JVM_INTERNAL';

/** What a site's "Thrown at" column says when the recording captured no stack for it. */
export const NO_STACK_LABEL = 'no stack recorded';

/**
 * A frame the way a stack trace prints it — `Class.method:line`, without the line when the
 * recording carries none, and without the class for a native frame.
 */
export function throwFrameLabel(frame: ThrowFrame | null): string {
  if (!frame) {
    return NO_STACK_LABEL;
  }
  const member = frame.className
    ? `${frame.className}.${frame.methodName ?? '?'}`
    : (frame.methodName ?? '?');
  return frame.lineNumber !== null ? `${member}:${frame.lineNumber}` : member;
}

/** Whether every throw of this type came from the JVM's own resolution and linking. */
export function isJvmInternalOnly(type: ExceptionTypeStat): boolean {
  return type.count > 0 && type.jvmInternalCount === type.count;
}

/**
 * The types a filter keeps: a type stays under APPLICATION when any of its throws were the
 * application's, and under JVM_INTERNAL when any were the JVM's, so a class thrown both ways shows
 * under both rather than vanishing from either.
 */
export function filterTypesByOrigin(
  types: ExceptionTypeStat[],
  filter: OriginFilter
): ExceptionTypeStat[] {
  if (filter === 'APPLICATION') {
    return types.filter(type => type.applicationCount > 0);
  }
  if (filter === 'JVM_INTERNAL') {
    return types.filter(type => type.jvmInternalCount > 0);
  }
  return types;
}

/** The sites the Application tab lists: the application's throws, not the JVM's own. */
export function applicationSites(sites: ExceptionSiteStat[]): ExceptionSiteStat[] {
  return sites.filter(site => site.origin === 'APPLICATION');
}

/** What a site row is found by in the table's filter box. */
export function siteSearchText(site: ExceptionSiteStat): string {
  return `${site.thrownClass} ${throwFrameLabel(site.throwingFrame)}`;
}

/** A stable row key: one class can be thrown from many frames. */
export function siteKey(site: ExceptionSiteStat): string {
  return `${site.thrownClass}@${throwFrameLabel(site.throwingFrame)}`;
}
