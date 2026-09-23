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

import JavaMethodParser from '@/services/flamegraphs/JavaMethodParser';

/** A frame label split into its rendered parts: muted package, bold class, italic method. */
export interface ParsedFrameName {
  pkg: string | null;
  className: string;
  /** Delimiter shown before the method: `.` for Java, `::` for C++. */
  separator: string;
  methodName: string;
}

const NAME_DELIMITER = '#';
const CPP_SEPARATOR = '::';

/**
 * Parses a pprof (UNKNOWN) frame whose name carries the `#` boundary set by the backend `FrameNames`:
 * - `package.Class#method` (Java) → package + class + method
 * - `module#Class::method` (C++ in a shared library) → the module/filename becomes the package,
 *   then the `Class::method` is split on `::`
 *
 * Returns null when there is no `#` (native/Go-style names stay flat).
 */
export function parseUnknownFrame(title: string): ParsedFrameName | null {
  const hashIndex = title.indexOf(NAME_DELIMITER);
  if (hashIndex < 0) {
    return null;
  }
  const left = title.substring(0, hashIndex);
  const right = title.substring(hashIndex + 1);

  const cppIndex = right.indexOf(CPP_SEPARATOR);
  if (cppIndex > 0) {
    return {
      pkg: left,
      className: right.substring(0, cppIndex),
      separator: CPP_SEPARATOR,
      methodName: right.substring(cppIndex + CPP_SEPARATOR.length)
    };
  }

  const parsed = JavaMethodParser.parse(title);
  if (!parsed) {
    return null;
  }
  return {
    pkg: parsed.packageName,
    className: parsed.className,
    separator: '.',
    methodName: parsed.methodName
  };
}
