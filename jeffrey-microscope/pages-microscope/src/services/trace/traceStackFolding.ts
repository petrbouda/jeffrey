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

import type { TraceStackFrameRow } from '@/services/api/model/trace/TraceModels';

/**
 * Turning a throw's stack into something a reader can take in at a glance.
 *
 * A real server stack is around forty frames of which a handful are the reader's own code, the rest
 * being the runtime and the web stack that carried the call. So runs of consecutive non-application
 * frames collapse into one bar each, leaving the throwing frame and the application frames standing.
 *
 * Nothing is discarded: a bar carries the frames it hid and opens in place, and the caller can turn
 * folding off entirely. That is why this lives here and not in the backend query — a fold the server
 * performed could not be undone without another round trip.
 */

/**
 * Package prefixes that mark a frame as *not* the reader's code.
 *
 * This is a heuristic, and deliberately a visible one. There is no per-frame classifier anywhere in
 * the profile to reuse: {@code StacktraceType} looks like one but classifies a *whole stack by its
 * thread* — JVM, JIT, GC, JFR or application — and says nothing about an individual frame. So this
 * list is the rule, and the toggle beside it is the escape hatch for when the rule is wrong.
 *
 * Two kinds of entry, kept in one list because the reader draws no distinction between them:
 * the Java runtime itself, and the frameworks that own the frames between a request arriving and
 * application code being called.
 */
export const LIBRARY_FRAME_PREFIXES: readonly string[] = [
  // The runtime.
  'java.',
  'javax.',
  'jdk.',
  'sun.',
  'com.sun.',
  'jakarta.',
  // The servlet and framework layers a request passes through on the way in.
  'org.springframework.',
  'org.apache.catalina.',
  'org.apache.coyote.',
  'org.apache.tomcat.',
  'org.eclipse.jetty.',
  'io.undertow.',
  'io.netty.',
  'reactor.core.',
  'ch.qos.logback.',
  'org.slf4j.',
  'org.hibernate.',
  'com.zaxxer.hikari.'
];

/** A frame kept in view, with where it sits in the unfolded stack. */
export interface StackFrameEntry {
  kind: 'frame';
  frame: TraceStackFrameRow;
  /** 0-based position in the full stack — index 0 is the throwing frame. */
  depth: number;
  /** True only for `depth === 0`: the frame that actually threw, and the line a reader wants. */
  throwing: boolean;
  /** Whether this frame is the reader's own code, by {@link isApplicationFrame}. */
  application: boolean;
  /**
   * True only for a frame that is in view because the reader opened the bar that hid it. The panel
   * bands these rows together: spread back into the list they would otherwise be indistinguishable
   * from the library frames the fold rule kept, and the run a bar stands for would lose its edges.
   */
  restored: boolean;
}

/** A collapsed run of consecutive library frames. */
export interface StackFoldEntry {
  kind: 'fold';
  /** The frames this bar stands in for, in stack order. */
  frames: TraceStackFrameRow[];
  /** 0-based position of the first frame it hid, so opening it restores the right order. */
  depth: number;
  /**
   * The distinct packages inside, most-frames-first and already trimmed — what the bar names so a
   * reader can tell "fifteen frames of Spring" from "fifteen frames of my own framework".
   */
  packages: string[];
}

export type StackEntry = StackFrameEntry | StackFoldEntry;

/**
 * Whether a frame is the reader's own code.
 *
 * A frame with no class is native and counts as library: the recording could not attribute it, and a
 * reader cannot act on it either.
 */
export function isApplicationFrame(frame: TraceStackFrameRow): boolean {
  const className = frame.className;
  if (!className) {
    return false;
  }
  return !LIBRARY_FRAME_PREFIXES.some(prefix => className.startsWith(prefix));
}

/**
 * The package a frame belongs to, trimmed to something a fold bar can name.
 *
 * `org.springframework.web.servlet.mvc.method.annotation` is accurate and useless in a bar, so it is
 * cut to the first three segments. A class in the default package has no package at all.
 */
export function framePackage(frame: TraceStackFrameRow, segments = 3): string {
  const className = frame.className;
  if (!className) {
    return 'native';
  }
  const lastDot = className.lastIndexOf('.');
  if (lastDot < 0) {
    return '(default)';
  }
  return className.slice(0, lastDot).split('.').slice(0, segments).join('.');
}

/**
 * The distinct packages of a run, ordered by how much of the run each accounts for.
 *
 * Ordered rather than alphabetical because a bar has room for two or three names, and the ones worth
 * showing are the ones that own the frames.
 */
function packagesOf(frames: TraceStackFrameRow[]): string[] {
  const counts = new Map<string, number>();
  for (const frame of frames) {
    const pkg = framePackage(frame);
    counts.set(pkg, (counts.get(pkg) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([pkg]) => pkg);
}

/**
 * Methods that only ever appear while an exception is building its own stack.
 *
 * They sit on top of every throw, so folding them away is what makes the remaining
 * frames the ones that actually explain where the exception came from.
 */
const CONSTRUCTOR_CHAIN_METHODS: readonly string[] = ['<init>', 'fillInStackTrace'];

/**
 * Where the stack actually starts — the first frame below the exception's constructor chain.
 *
 * `jdk.JavaExceptionThrow` and `jdk.JavaErrorThrow` are emitted from inside `Throwable`'s
 * constructor, so every throw's stack opens with one `<init>` per level of the exception's own
 * hierarchy: `Throwable.<init>`, `Exception.<init>`, `IOException.<init>`, and only then the frame
 * that contained the `throw`. Treating index 0 as the throwing frame puts the mark on JDK plumbing
 * every single time.
 *
 * The boundary is found rather than guessed, because `thrownClass` names exactly the most-derived
 * constructor in that chain. Which is what makes the awkward case come out right: a throw from
 * *inside* a constructor leaves an `<init>` frame that is the real answer, and a blanket "strip
 * every leading `<init>`" would eat it.
 *
 * @returns the index of the throwing frame, or 0 when the stack does not have this shape — a stack
 *          this rule cannot read is one it should not move the mark on
 */
export function throwingFrameIndex(
  frames: TraceStackFrameRow[],
  thrownClass?: string | null
): number {
  if (!thrownClass || frames.length === 0) {
    return 0;
  }

  let chainEnd = -1;
  for (let index = 0; index < frames.length; index++) {
    const frame = frames[index];
    if (!CONSTRUCTOR_CHAIN_METHODS.includes(frame.methodName)) {
      break;
    }
    if (frame.className === thrownClass) {
      // Not a break: a chain can name the same class more than once, and it is the last of them
      // that ends it.
      chainEnd = index;
    }
  }

  // The whole stack being the chain would leave no frame to mark, so the mark stays where it is.
  return chainEnd >= 0 && chainEnd + 1 < frames.length ? chainEnd + 1 : 0;
}

function frameEntry(
  frame: TraceStackFrameRow,
  depth: number,
  throwingIndex: number
): StackFrameEntry {
  return {
    kind: 'frame',
    frame,
    depth,
    throwing: depth === throwingIndex,
    application: isApplicationFrame(frame),
    restored: false
  };
}

/** Every frame, unfolded, in stack order. What `Fold libraries` off produces. */
export function unfoldedStack(
  frames: TraceStackFrameRow[],
  thrownClass?: string | null
): StackEntry[] {
  const throwingIndex = throwingFrameIndex(frames, thrownClass);
  return frames.map((frame, index) => frameEntry(frame, index, throwingIndex));
}

/**
 * The frames a bar was standing in for, as entries at their real depths.
 *
 * Depth has to be restored from the bar rather than counted from zero, because `throwing` is
 * derived from it: a run expanded in place would otherwise announce its first frame as the one
 * that threw, and paint a `java.util.stream` frame in the throwing frame's red.
 */
export function expandFold(fold: StackFoldEntry): StackFrameEntry[] {
  // A bar only ever holds frames the fold rule declined to keep, and the throwing frame is always
  // kept — so nothing inside a bar can be it, whatever the throwing index turns out to be.
  const noneAreThrowing = -1;
  return fold.frames.map((frame, index) => ({
    ...frameEntry(frame, fold.depth + index, noneAreThrowing),
    restored: true
  }));
}

/**
 * The folded stack: application frames kept, runs of library frames collapsed.
 *
 * Two frames always survive whatever the rule says, because a stack that starts or ends nowhere is
 * harder to read than a long one:
 *
 * - **the throwing frame**, which is the line the reader opened the stack for;
 * - **the root frame**, which says which thread's stack this is.
 *
 * And the whole thing has a floor: a throw from inside a library has no application frames at all,
 * so folding would leave a bar and nothing else — which reads as a broken panel rather than as an
 * answer. When that would happen, folding does not run and the stack renders in full.
 */
export function foldedStack(
  frames: TraceStackFrameRow[],
  thrownClass?: string | null
): StackEntry[] {
  if (frames.length === 0) {
    return [];
  }

  const throwingIndex = throwingFrameIndex(frames, thrownClass);

  // The floor: a throw from inside a library has no frames of the reader's own anywhere, so
  // folding would leave bars and the two structural frames — a panel that looks broken rather
  // than one that answers. The rule has nothing to say about such a stack, so it stands aside.
  //
  // Asked of every frame including the throwing one and the root: if the reader's own code threw,
  // folding the framework beneath it is exactly the useful case, even when nothing else is theirs.
  if (!frames.some(isApplicationFrame)) {
    return unfoldedStack(frames, thrownClass);
  }

  const lastIndex = frames.length - 1;
  const keep = frames.map((frame, index) => {
    // Chain frames never stand, whatever their package. An application exception type puts
    // `com.acme.MyException.<init>` in the chain, and the application rule would otherwise keep it
    // hanging above the frame that actually threw.
    if (index < throwingIndex) {
      return false;
    }
    return index === throwingIndex || index === lastIndex || isApplicationFrame(frame);
  });

  const entries: StackEntry[] = [];
  let run: TraceStackFrameRow[] = [];
  let runDepth = 0;

  const flushRun = () => {
    if (run.length === 0) {
      return;
    }
    entries.push({ kind: 'fold', frames: run, depth: runDepth, packages: packagesOf(run) });
    run = [];
  };

  frames.forEach((frame, index) => {
    if (keep[index]) {
      flushRun();
      entries.push(frameEntry(frame, index, throwingIndex));
      return;
    }
    if (run.length === 0) {
      runDepth = index;
    }
    run.push(frame);
  });
  flushRun();

  return entries;
}

/**
 * Where a frame is, as `Foo.java:148` — or its type when the recording captured no line.
 *
 * `Native` in that column says why a frame has no location, where a blank would read as missing
 * data. The nested-class suffix is dropped because the file is named for the outer class:
 * `JavaCharStream$1` lives in `JavaCharStream.java`.
 */
export function frameLocation(frame: TraceStackFrameRow): string {
  if (frame.lineNumber === null) {
    return frame.frameType;
  }
  const className = frame.className;
  if (!className) {
    return String(frame.lineNumber);
  }
  const simple = className.slice(className.lastIndexOf('.') + 1).split('$')[0];
  return `${simple}.java:${frame.lineNumber}`;
}

/**
 * The stack as a JVM prints it, for pasting into an issue.
 *
 * The header line is not decoration: `class: message` followed by `\tat …` lines is the shape every
 * log scraper, IDE stack-trace parser and issue tracker recognises. Frames alone are not a stack
 * trace, they are a list.
 *
 * Every frame from the throw downwards, never what the fold left standing — someone pasting this
 * into an issue wants the evidence, not the reading.
 *
 * The constructor chain is the one thing dropped, because a JVM does not print it either:
 * `fillInStackTrace` captures the stack *below* the constructors, so a real trace starts at the
 * frame that threw. Leaving it in would produce something no JVM ever emits and send an IDE that
 * parsed it to `Throwable.java`.
 */
export function stackTraceText(
  frames: TraceStackFrameRow[],
  thrownClass?: string | null,
  message?: string | null
): string {
  const lines = frames
    .slice(throwingFrameIndex(frames, thrownClass))
    .map(
      frame => `\tat ${frame.className ?? '(native)'}.${frame.methodName}(${frameLocation(frame)})`
    );

  if (!thrownClass) {
    return lines.join('\n');
  }
  // No message is `java.io.IOException` on its own, exactly as the JVM prints a throw without one —
  // a trailing colon would be a colon with nothing after it.
  const header = message ? `${thrownClass}: ${message}` : thrownClass;
  return [header, ...lines].join('\n');
}

/** How many frames a rendering actually puts on screen — fold bars do not count as frames. */
export function shownFrameCount(entries: StackEntry[]): number {
  return entries.filter(entry => entry.kind === 'frame').length;
}
