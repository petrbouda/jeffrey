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
  applicationSites,
  filterTypesByOrigin,
  isJvmInternalOnly,
  NO_STACK_LABEL,
  siteKey,
  throwFrameLabel
} from '@/services/exceptions/exceptionViews';
import type { ExceptionSiteStat, ExceptionTypeStat } from '@/services/api/model/ExceptionsModels';

function type(
  thrownClass: string,
  applicationCount: number,
  jvmInternalCount: number
): ExceptionTypeStat {
  return {
    thrownClass,
    count: applicationCount + jvmInternalCount,
    error: false,
    messages: [],
    omittedMessages: 0,
    threadCount: 1,
    applicationCount,
    jvmInternalCount
  };
}

function site(thrownClass: string, origin: ExceptionSiteStat['origin']): ExceptionSiteStat {
  return {
    thrownClass,
    error: false,
    origin,
    throwingFrame: { className: 'com.acme.Service', methodName: 'call', lineNumber: 12 },
    stacktraceId: '000000000000000b',
    count: 3,
    threadCount: 1,
    messages: [],
    omittedMessages: 0
  };
}

describe('throwFrameLabel', () => {
  it('prints a frame the way a stack trace does', () => {
    expect(
      throwFrameLabel({ className: 'com.acme.Service', methodName: 'call', lineNumber: 12 })
    ).toBe('com.acme.Service.call:12');
  });

  it('leaves out a line the recording does not carry', () => {
    expect(
      throwFrameLabel({ className: 'com.acme.Service', methodName: 'call', lineNumber: null })
    ).toBe('com.acme.Service.call');
  });

  it('names a native frame by its method alone', () => {
    expect(throwFrameLabel({ className: null, methodName: 'clone3', lineNumber: null })).toBe(
      'clone3'
    );
  });

  it('says so when there is no stack', () => {
    expect(throwFrameLabel(null)).toBe(NO_STACK_LABEL);
  });
});

describe('filterTypesByOrigin', () => {
  const types = [
    type('java.net.SocketTimeoutException', 5, 0),
    type('java.lang.ClassNotFoundException', 0, 4),
    type('java.io.IOException', 2, 1)
  ];

  it('keeps every type for ALL', () => {
    expect(filterTypesByOrigin(types, 'ALL')).toHaveLength(3);
  });

  it('keeps a class thrown both ways under both filters', () => {
    expect(filterTypesByOrigin(types, 'APPLICATION').map(t => t.thrownClass)).toEqual([
      'java.net.SocketTimeoutException',
      'java.io.IOException'
    ]);
    expect(filterTypesByOrigin(types, 'JVM_INTERNAL').map(t => t.thrownClass)).toEqual([
      'java.lang.ClassNotFoundException',
      'java.io.IOException'
    ]);
  });
});

describe('isJvmInternalOnly', () => {
  it('is true only when every throw was the JVM own', () => {
    expect(isJvmInternalOnly(type('java.lang.ClassNotFoundException', 0, 4))).toBe(true);
    expect(isJvmInternalOnly(type('java.io.IOException', 2, 1))).toBe(false);
  });
});

describe('applicationSites', () => {
  it('drops the JVM internal sites', () => {
    const sites = [
      site('java.io.IOException', 'APPLICATION'),
      site('java.lang.ClassNotFoundException', 'JVM_INTERNAL')
    ];

    expect(applicationSites(sites).map(s => s.thrownClass)).toEqual(['java.io.IOException']);
  });
});

describe('siteKey', () => {
  it('tells two throw sites of one class apart', () => {
    const first = site('java.io.IOException', 'APPLICATION');
    const second = {
      ...first,
      throwingFrame: { className: 'com.acme.Other', methodName: 'run', lineNumber: 3 }
    };

    expect(siteKey(first)).not.toBe(siteKey(second));
  });
});
