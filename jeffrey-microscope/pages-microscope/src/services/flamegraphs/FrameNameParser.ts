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
