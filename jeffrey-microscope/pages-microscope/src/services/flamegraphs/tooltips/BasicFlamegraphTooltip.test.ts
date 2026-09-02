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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import BasicFlamegraphTooltip from './BasicFlamegraphTooltip';
import Frame from '@/services/api/model/Frame';
import ideConfigStore from '@/stores/ideConfigStore';

vi.mock('@/stores/ideConfigStore', () => ({
  default: {
    loadOnce: vi.fn(),
    isEnabled: vi.fn(() => false),
    isJfrProfilerMode: vi.fn(() => false)
  }
}));

const isEnabledMock = ideConfigStore.isEnabled as unknown as ReturnType<typeof vi.fn>;
const isJfrProfilerModeMock = ideConfigStore.isJfrProfilerMode as unknown as ReturnType<typeof vi.fn>;

function javaFrame(): Frame {
  const frame = new Frame(
    0,
    12345,
    'com.example.shop.order.OrderService.processOrder',
    'JIT_COMPILED',
    0,
    0,
    3210,
    { bci: 37, line: 142 }
  );
  return frame;
}

function constructorFrame(): Frame {
  return new Frame(
    0,
    3043,
    'com.google.gson.stream.JsonReader.<init>',
    'JIT_COMPILED',
    0,
    0,
    3043
  );
}

function nativeFrame(): Frame {
  return new Frame(
    0,
    100,
    '/lib/x86_64-linux-gnu/libc.so.6',
    'NATIVE',
    0,
    0,
    100
  );
}

describe('BasicFlamegraphTooltip — IDE jump button', () => {
  beforeEach(() => {
    isEnabledMock.mockReset();
    isJfrProfilerModeMock.mockReset();
    isJfrProfilerModeMock.mockReturnValue(false);
  });

  it('renders the Open in IDE button for a Java frame when the IDE integration is enabled', () => {
    isEnabledMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(javaFrame(), 27000, 0);

    expect(html).toContain('data-ide-action="open"');
    expect(html).toContain('data-fqn="com.example.shop.order.OrderService"');
    expect(html).toContain('data-method="OrderService.processOrder"');
    expect(html).toContain('data-line="142"');
    expect(html).toContain('Open in IDE');
  });

  it('renders the View Source button for a Java frame when the IDE integration is enabled', () => {
    isEnabledMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(javaFrame(), 27000, 0);

    expect(html).toContain('data-ide-action="source"');
    expect(html).toContain('data-title="OrderService"');
    expect(html).toContain('View Source');
  });

  it('omits the View Source button for non-Java frames even when configured', () => {
    isEnabledMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(nativeFrame(), 27000, 0);

    expect(html).not.toContain('data-ide-action="source"');
    expect(html).not.toContain('View Source');
  });

  it('omits the Open in IDE button when the feature is not configured', () => {
    isEnabledMock.mockReturnValue(false);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(javaFrame(), 27000, 0);

    expect(html).not.toContain('data-ide-action');
    expect(html).not.toContain('Open in IDE');
  });

  it('omits the Open in IDE button for non-Java frames even when configured', () => {
    isEnabledMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(nativeFrame(), 27000, 0);

    expect(html).not.toContain('data-ide-action');
  });

  it('renders the buttons enabled (no gating) in Jeffrey Plugin mode', () => {
    isEnabledMock.mockReturnValue(true);
    isJfrProfilerModeMock.mockReturnValue(false);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(javaFrame(), 27000, 0);

    expect(html).toContain('data-ide-action="open"');
    expect(html).not.toContain('data-ide-gated');
    expect(html).not.toContain('disabled');
  });

  it('renders the buttons disabled and gated in JFR Profiler Plugin mode', () => {
    isEnabledMock.mockReturnValue(true);
    isJfrProfilerModeMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false);

    const html = tooltip.generate(javaFrame(), 27000, 0);

    expect(html).toContain('data-ide-action="open"');
    expect(html).toContain('data-ide-gated="true"');
    expect(html).toContain('disabled');
  });
});

describe('BasicFlamegraphTooltip — header escaping', () => {
  beforeEach(() => {
    isEnabledMock.mockReset();
    isJfrProfilerModeMock.mockReset();
    isJfrProfilerModeMock.mockReturnValue(false);
  });

  it('escapes constructor method names so <init> survives innerHTML parsing', () => {
    isEnabledMock.mockReturnValue(false);
    const tooltip = new BasicFlamegraphTooltip('jdk.ObjectAllocationSample', false);

    const html = tooltip.generate(constructorFrame(), 27000, 0);

    expect(html).toContain('&lt;init&gt;');
    expect(html).not.toContain('.<init>');
    expect(html).toContain('JsonReader');
    expect(html).toContain('com.google.gson.stream');
  });

  it('escapes constructor method names in the IDE jump data attributes', () => {
    isEnabledMock.mockReturnValue(true);
    const tooltip = new BasicFlamegraphTooltip('jdk.ObjectAllocationSample', false);

    const html = tooltip.generate(constructorFrame(), 27000, 0);

    expect(html).toContain('data-method="JsonReader.&lt;init&gt;"');
    expect(html).toContain('data-fqn="com.google.gson.stream.JsonReader"');
  });
});

describe('BasicFlamegraphTooltip — hidden class badge', () => {
  beforeEach(() => {
    isEnabledMock.mockReset();
    isEnabledMock.mockReturnValue(false);
    isJfrProfilerModeMock.mockReset();
    isJfrProfilerModeMock.mockReturnValue(false);
  });

  function hiddenLambdaFrame(): Frame {
    return new Frame(
      0,
      21523,
      'org.springframework.security.web.FilterChainProxy$$Lambda#doFilter',
      'INLINED',
      0,
      0,
      0,
      { bci: 10, line: 0 },
      undefined,
      undefined,
      undefined,
      true
    );
  }

  it('renders the HIDDEN CLASS badge for a frame on a hidden class', () => {
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false, null, null, true);
    const html = tooltip.generate(hiddenLambdaFrame(), 42568, 0);

    expect(html).toContain('HIDDEN CLASS');
  });

  it('keeps the frame-type badge alongside the hidden badge', () => {
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false, null, null, true);
    const html = tooltip.generate(hiddenLambdaFrame(), 42568, 0);

    expect(html).toContain('Inlined (JAVA)');
  });

  it('shows no address in the title — the parser strips it before the wire', () => {
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false, null, null, true);
    const html = tooltip.generate(hiddenLambdaFrame(), 42568, 0);

    expect(html).not.toContain('0x');
    expect(html).toContain('FilterChainProxy$$Lambda');
  });

  it('omits the badge for an ordinary frame', () => {
    const tooltip = new BasicFlamegraphTooltip('jdk.ExecutionSample', false, null, null, true);
    const html = tooltip.generate(javaFrame(), 100000, 0);

    expect(html).not.toContain('HIDDEN CLASS');
  });
});
