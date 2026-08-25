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

import { describe, expect, it } from 'vitest';
import type { TraceStackFrameRow } from '@/services/api/model/trace/TraceModels';
import {
  expandFold,
  foldedStack,
  frameLocation,
  stackTraceText,
  throwingFrameIndex,
  framePackage,
  isApplicationFrame,
  shownFrameCount,
  unfoldedStack,
  type StackEntry,
  type StackFoldEntry
} from '@/services/trace/traceStackFolding';

function frame(
  className: string | null,
  methodName = 'run',
  lineNumber: number | null = 1
): TraceStackFrameRow {
  return { className, methodName, frameType: 'JIT', lineNumber };
}

/** The shape of a rendering, as a string per row, so a whole layout is one assertion. */
function shape(entries: StackEntry[]): string[] {
  return entries.map(entry =>
    entry.kind === 'fold'
      ? `fold(${entry.frames.length}: ${entry.packages.join(',')})`
      : `${entry.frame.className}.${entry.frame.methodName}`
  );
}

describe('isApplicationFrame', () => {
  it('treats the runtime and the web stack as library', () => {
    expect(isApplicationFrame(frame('java.util.stream.ReferencePipeline'))).toBe(false);
    expect(isApplicationFrame(frame('jdk.internal.reflect.DirectMethodHandleAccessor'))).toBe(
      false
    );
    expect(isApplicationFrame(frame('org.springframework.web.servlet.DispatcherServlet'))).toBe(
      false
    );
    expect(isApplicationFrame(frame('org.apache.catalina.core.StandardHostValve'))).toBe(false);
  });

  it('treats everything else as the reader own code', () => {
    expect(isApplicationFrame(frame('com.acme.reports.render.PdfRenderer'))).toBe(true);
    expect(isApplicationFrame(frame('cafe.jeffrey.events.servlet.TracingFilter'))).toBe(true);
  });

  it('does not match a package that merely starts with a prefix name', () => {
    // 'java.' with the dot, so an application package called javaagent is not swept up with it.
    expect(isApplicationFrame(frame('javaagent.instrument.Weaver'))).toBe(true);
    expect(isApplicationFrame(frame('jdkmonitor.Probe'))).toBe(true);
  });

  it('counts a frame with no class as library, since a reader cannot act on it either', () => {
    expect(isApplicationFrame(frame(null, 'clone3'))).toBe(false);
  });
});

describe('framePackage', () => {
  it('trims a deep package to something a fold bar can name', () => {
    expect(framePackage(frame('org.springframework.web.servlet.mvc.method.annotation.Foo'))).toBe(
      'org.springframework.web'
    );
  });

  it('handles a class with no package', () => {
    expect(framePackage(frame('Main'))).toBe('(default)');
  });
});

describe('foldedStack', () => {
  // Throwing frame, two application frames, a runtime run, application again, a framework run,
  // and the thread root -- the shape of every server stack.
  const stack = [
    frame('com.acme.PdfRenderer', 'finish'),
    frame('com.acme.PdfRenderer', 'render'),
    frame('java.util.stream.ReferencePipeline', 'accept'),
    frame('java.util.stream.AbstractPipeline', 'copyInto'),
    frame('java.util.stream.AbstractPipeline', 'evaluate'),
    frame('com.acme.EventReportService', 'append'),
    frame('org.springframework.web.servlet.DispatcherServlet', 'doDispatch'),
    frame('org.apache.catalina.core.StandardHostValve', 'invoke'),
    frame('java.lang.Thread', 'run')
  ];

  it('collapses each run of library frames into one bar', () => {
    expect(shape(foldedStack(stack))).toEqual([
      'com.acme.PdfRenderer.finish',
      'com.acme.PdfRenderer.render',
      'fold(3: java.util.stream)',
      'com.acme.EventReportService.append',
      'fold(2: org.springframework.web,org.apache.catalina)',
      'java.lang.Thread.run'
    ]);
  });

  it('keeps the throwing frame and the thread root even though the root is library code', () => {
    const entries = foldedStack(stack);
    const first = entries[0];
    const last = entries[entries.length - 1];

    expect(first.kind).toBe('frame');
    expect(first.kind === 'frame' && first.throwing).toBe(true);
    // Thread.run is java.*, so only the root rule keeps it -- and without it the stack ends on a bar.
    expect(last.kind === 'frame' && last.frame.className).toBe('java.lang.Thread');
  });

  it('hides nothing: every folded frame is still carried by its bar', () => {
    const entries = foldedStack(stack);
    const carried = entries.flatMap(entry =>
      entry.kind === 'fold' ? entry.frames : [(entry as { frame: TraceStackFrameRow }).frame]
    );

    expect(carried).toEqual(stack);
  });

  it('records where a bar sits, so opening it restores the right order', () => {
    const folds = foldedStack(stack).filter((e): e is StackFoldEntry => e.kind === 'fold');

    expect(folds.map(fold => fold.depth)).toEqual([2, 6]);
  });

  it('names a bar by the package that owns most of it, first', () => {
    const stackWithMixedRun = [
      frame('com.acme.Service', 'call'),
      frame('org.apache.catalina.core.A', 'invoke'),
      frame('org.springframework.web.servlet.B', 'x'),
      frame('org.springframework.web.servlet.C', 'y'),
      frame('java.lang.Thread', 'run')
    ];
    const fold = foldedStack(stackWithMixedRun).find((e): e is StackFoldEntry => e.kind === 'fold');

    // Two Spring frames against one Tomcat, so Spring leads however the run is ordered.
    expect(fold?.packages).toEqual(['org.springframework.web', 'org.apache.catalina']);
  });

  it('does not fold at all when nothing of the reader own code would be left standing', () => {
    // A throw from inside the runtime. Folding would leave a bar between two frames and say
    // nothing, which reads as a broken panel rather than as an answer.
    const libraryOnly = [
      frame('java.util.HashMap', 'resize'),
      frame('java.util.HashMap', 'put'),
      frame('org.springframework.web.servlet.DispatcherServlet', 'doDispatch'),
      frame('java.lang.Thread', 'run')
    ];

    expect(foldedStack(libraryOnly)).toEqual(unfoldedStack(libraryOnly));
    expect(shownFrameCount(foldedStack(libraryOnly))).toBe(libraryOnly.length);
  });

  it('renders a one-frame stack as that frame, not as a fold', () => {
    const single = [frame('java.util.HashMap', 'resize')];

    expect(shape(foldedStack(single))).toEqual(['java.util.HashMap.resize']);
  });

  it('reads an absent stack as empty rather than failing', () => {
    expect(foldedStack([])).toEqual([]);
  });

  it('leaves an all-application stack untouched', () => {
    const allMine = [frame('com.acme.A', 'a'), frame('com.acme.B', 'b'), frame('com.acme.C', 'c')];

    expect(shownFrameCount(foldedStack(allMine))).toBe(3);
    expect(foldedStack(allMine).some(entry => entry.kind === 'fold')).toBe(false);
  });
});

describe('throwingFrameIndex', () => {
  // The shape JFR produces, because the throw event is emitted from inside Throwable's
  // constructor: one <init> per level of the exception's own hierarchy, then the real frame.
  const ognl = [
    frame('java.lang.Throwable', '<init>', 266),
    frame('java.lang.Exception', '<init>', 113),
    frame('java.io.IOException', '<init>', 62),
    frame('org.apache.ibatis.ognl.JavaCharStream', 'fillBuff', 148),
    frame('com.acme.reports.ReportEventsMapper', 'getReportEvents', 88),
    frame('java.lang.Thread', 'run', 1583)
  ];

  it('points past the constructor chain, at the frame that contained the throw', () => {
    expect(throwingFrameIndex(ognl, 'java.io.IOException')).toBe(3);
  });

  it('absorbs fillInStackTrace, which sits above the constructors', () => {
    const withFill = [frame('java.lang.Throwable', 'fillInStackTrace', 1072), ...ognl];

    expect(throwingFrameIndex(withFill, 'java.io.IOException')).toBe(4);
  });

  it('keeps an <init> that is the real answer, when the throw came from a constructor', () => {
    // A blanket "strip every leading <init>" eats com.acme.Foo.<init>. Matching on the thrown
    // class ends the chain at the exception's own constructor and no further.
    const inConstructor = [
      frame('java.lang.Throwable', '<init>', 266),
      frame('java.lang.Exception', '<init>', 113),
      frame('com.acme.MyException', '<init>', 12),
      frame('com.acme.Foo', '<init>', 40),
      frame('java.lang.Thread', 'run', 1583)
    ];

    expect(throwingFrameIndex(inConstructor, 'com.acme.MyException')).toBe(3);
  });

  it('stays at 0 when the stack does not have the shape it reads', () => {
    // Nothing to be confident about, so nothing moves -- better a mark in the old place than a
    // mark somewhere invented.
    expect(throwingFrameIndex(ognl, 'com.acme.SomethingElse')).toBe(0);
    expect(throwingFrameIndex(ognl, null)).toBe(0);
    expect(throwingFrameIndex(ognl, undefined)).toBe(0);
    expect(throwingFrameIndex([], 'java.io.IOException')).toBe(0);
  });

  it('stays at 0 when the whole stack is the chain, leaving no frame to mark', () => {
    const nothingElse = [
      frame('java.lang.Throwable', '<init>', 266),
      frame('java.io.IOException', '<init>', 62)
    ];

    expect(throwingFrameIndex(nothingElse, 'java.io.IOException')).toBe(0);
  });

  it('ends the chain at the last frame naming the thrown class, not the first', () => {
    const repeated = [
      frame('java.lang.Throwable', '<init>', 266),
      frame('com.acme.MyException', '<init>', 12),
      frame('com.acme.MyException', '<init>', 18),
      frame('com.acme.Service', 'call', 40)
    ];

    expect(throwingFrameIndex(repeated, 'com.acme.MyException')).toBe(3);
  });
});

describe('foldedStack with a constructor chain', () => {
  const ognl = [
    frame('java.lang.Throwable', '<init>', 266),
    frame('java.lang.Exception', '<init>', 113),
    frame('java.io.IOException', '<init>', 62),
    frame('org.apache.ibatis.ognl.JavaCharStream', 'fillBuff', 148),
    frame('com.acme.reports.ReportEventsMapper', 'getReportEvents', 88),
    frame('java.lang.Thread', 'run', 1583)
  ];

  it('marks the frame that threw and folds the chain away', () => {
    const entries = foldedStack(ognl, 'java.io.IOException');

    expect(shape(entries)).toEqual([
      'fold(3: java.lang,java.io)',
      'org.apache.ibatis.ognl.JavaCharStream.fillBuff',
      'com.acme.reports.ReportEventsMapper.getReportEvents',
      'java.lang.Thread.run'
    ]);
    const throwing = entries.filter(e => e.kind === 'frame' && e.throwing);
    expect(throwing).toHaveLength(1);
    expect(throwing[0].kind === 'frame' && throwing[0].frame.methodName).toBe('fillBuff');
  });

  it('does not leave an application exception constructor standing above the throw', () => {
    // com.acme.MyException is the reader's own package, so the application rule would keep its
    // <init> -- a stray row above the frame that actually threw.
    const appException = [
      frame('java.lang.Throwable', '<init>', 266),
      frame('com.acme.MyException', '<init>', 12),
      frame('com.acme.Service', 'call', 40),
      frame('java.lang.Thread', 'run', 1583)
    ];

    expect(shape(foldedStack(appException, 'com.acme.MyException'))).toEqual([
      'fold(2: java.lang,com.acme)',
      'com.acme.Service.call',
      'java.lang.Thread.run'
    ]);
  });

  it('marks nothing inside an opened bar as the throwing frame', () => {
    const fold = foldedStack(ognl, 'java.io.IOException').find(
      (e): e is StackFoldEntry => e.kind === 'fold'
    );
    if (!fold) {
      throw new Error('the chain should fold');
    }

    expect(expandFold(fold).some(entry => entry.throwing)).toBe(false);
  });

  it('marks the same frame with folding off', () => {
    const entries = unfoldedStack(ognl, 'java.io.IOException');

    expect(entries.findIndex(e => e.kind === 'frame' && e.throwing)).toBe(3);
  });

  it('behaves exactly as before when no thrown class is given', () => {
    expect(foldedStack(ognl)).toEqual(foldedStack(ognl, null));
    const first = foldedStack(ognl)[0];
    expect(first.kind === 'frame' && first.throwing).toBe(true);
  });
});

describe('expandFold', () => {
  const stack = [
    frame('com.acme.PdfRenderer', 'finish'),
    frame('java.util.stream.ReferencePipeline', 'accept'),
    frame('java.util.stream.AbstractPipeline', 'copyInto'),
    frame('java.lang.Thread', 'run')
  ];

  function onlyFold(): StackFoldEntry {
    const fold = foldedStack(stack).find((e): e is StackFoldEntry => e.kind === 'fold');
    if (!fold) {
      throw new Error('the fixture should fold');
    }
    return fold;
  }

  it('restores each frame at its real depth, not counting from zero again', () => {
    expect(expandFold(onlyFold()).map(entry => entry.depth)).toEqual([1, 2]);
  });

  it('never announces an expanded frame as the one that threw', () => {
    // The bug this pins: counting from zero inside the run made its first frame `throwing`, so a
    // java.util.stream frame was painted in the throwing frame's red halfway down the stack.
    expect(expandFold(onlyFold()).some(entry => entry.throwing)).toBe(false);
  });
});

describe('shownFrameCount', () => {
  it('counts frames on screen and not the bars standing in for the rest', () => {
    const stack = [
      frame('com.acme.A', 'a'),
      frame('java.util.X', 'x'),
      frame('java.util.Y', 'y'),
      frame('java.lang.Thread', 'run')
    ];

    expect(shownFrameCount(foldedStack(stack))).toBe(2);
    expect(shownFrameCount(unfoldedStack(stack))).toBe(4);
  });
});

describe('frameLocation', () => {
  it('names the file after the outer class, since that is where the source lives', () => {
    expect(frameLocation(frame('org.apache.ibatis.ognl.JavaCharStream$1', 'accept', 148))).toBe(
      'JavaCharStream.java:148'
    );
  });

  it('says the frame type when the recording captured no line', () => {
    const native: TraceStackFrameRow = {
      className: null,
      methodName: 'clone3',
      frameType: 'Native',
      lineNumber: null
    };

    expect(frameLocation(native)).toBe('Native');
  });
});

describe('stackTraceText', () => {
  const frames = [
    frame('org.apache.ibatis.ognl.JavaCharStream', 'fillBuff', 148),
    frame('com.acme.reports.ReportEventsMapper', 'getReportEvents', 88)
  ];

  it('leads with the header line a JVM prints, so the result is a stack trace and not a list', () => {
    expect(stackTraceText(frames, 'java.io.IOException', 'Stream closed')).toBe(
      [
        'java.io.IOException: Stream closed',
        '\tat org.apache.ibatis.ognl.JavaCharStream.fillBuff(JavaCharStream.java:148)',
        '\tat com.acme.reports.ReportEventsMapper.getReportEvents(ReportEventsMapper.java:88)'
      ].join('\n')
    );
  });

  it('drops the colon when the throw carried no message, as the JVM does', () => {
    expect(stackTraceText(frames, 'java.io.IOException', null).split('\n')[0]).toBe(
      'java.io.IOException'
    );
  });

  it('copies every frame, not the folded reading', () => {
    // Someone pasting this into an issue wants the evidence, not what the panel chose to show.
    const deep = [...frames, frame('java.lang.Thread', 'run', 1583)];

    expect(stackTraceText(deep, 'java.io.IOException', 'x').split('\n')).toHaveLength(4);
  });

  it('drops the constructor chain, because a JVM does not print it either', () => {
    // fillInStackTrace captures the stack below the constructors, so a real trace starts at the
    // frame that threw. Leaving them in emits something no JVM produces, and an IDE parsing it
    // would jump to Throwable.java.
    const withChain = [
      frame('java.lang.Throwable', '<init>', 266),
      frame('java.io.IOException', '<init>', 62),
      ...frames
    ];

    const lines = stackTraceText(withChain, 'java.io.IOException', 'Stream closed').split('\n');

    expect(lines[0]).toBe('java.io.IOException: Stream closed');
    expect(lines[1]).toContain('JavaCharStream.fillBuff');
    expect(lines.some(line => line.includes('Throwable.<init>'))).toBe(false);
  });

  it('keeps every frame when the chain cannot be located', () => {
    const withChain = [frame('java.lang.Throwable', '<init>', 266), ...frames];

    expect(stackTraceText(withChain, 'com.acme.Unrelated', null)).toContain('Throwable.<init>');
  });

  it('falls back to frames alone when there is no class to head them with', () => {
    expect(stackTraceText(frames).startsWith('\tat ')).toBe(true);
  });
});
