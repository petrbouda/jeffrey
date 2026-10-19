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

/**
 * Whose throw it was. JVM_INTERNAL is the JVM resolving and linking (class loading, reflection,
 * method-handle linking); everything else, libraries included, is APPLICATION.
 */
export type ThrowOrigin = 'APPLICATION' | 'JVM_INTERNAL';

export interface ExceptionsOverview {
  totalThrowables: number;
  sampledThrowCount: number;
  errorCount: number;
  distinctTypes: number;
  hasExceptionThrowEvents: boolean;
  hasErrorThrowEvents: boolean;
  applicationThrowCount: number;
  jvmInternalThrowCount: number;
}

export interface ExceptionMessageCount {
  message: string;
  count: number;
}

export interface ExceptionTypeStat {
  thrownClass: string;
  count: number;
  error: boolean;
  messages: ExceptionMessageCount[];
  /** Distinct messages left out of `messages` by the server's cap. */
  omittedMessages: number;
  threadCount: number;
  applicationCount: number;
  jvmInternalCount: number;
}

/** One frame of a throw's stack; `className` is null for a native frame. */
export interface ThrowFrame {
  className: string | null;
  methodName: string | null;
  lineNumber: number | null;
}

/** An exception class together with the frame that contained the `throw`. */
export interface ExceptionSiteStat {
  thrownClass: string;
  error: boolean;
  origin: ThrowOrigin;
  /** Null when the recording captured no stack. */
  throwingFrame: ThrowFrame | null;
  /** The site's commonest stack, for the stack endpoint; null when none was captured. */
  stacktraceId: string | null;
  count: number;
  threadCount: number;
  messages: ExceptionMessageCount[];
  omittedMessages: number;
}
