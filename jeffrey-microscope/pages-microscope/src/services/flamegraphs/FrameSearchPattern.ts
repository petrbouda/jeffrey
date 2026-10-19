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
 * What a flamegraph search highlights: a frame whose title contains the pattern literally, or in
 * which the pattern, read as a regular expression, is found.
 *
 * The literal reading keeps a frame's own title matching itself although it is no regular expression
 * for itself: the context menu searches for the exact title, and `Outer$Inner#run` or `byte[]` read
 * as regular expressions match nothing. A pattern that does not compile (`parse(`) is searched
 * literally instead of throwing. The backend's `SearchPattern` applies the same contract to the
 * timeseries of the search, so the highlighted frames and the chart agree.
 */
export default class FrameSearchPattern {
  private readonly literal: string;
  private readonly regex: RegExp | null;

  constructor(pattern: string) {
    this.literal = pattern;
    this.regex = FrameSearchPattern.compile(pattern);
  }

  matches(title: string): boolean {
    return title.includes(this.literal) || (this.regex !== null && this.regex.test(title));
  }

  private static compile(pattern: string): RegExp | null {
    try {
      return new RegExp(pattern);
    } catch {
      return null;
    }
  }
}
