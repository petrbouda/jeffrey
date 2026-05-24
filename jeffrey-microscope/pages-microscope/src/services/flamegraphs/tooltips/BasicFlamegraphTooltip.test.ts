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
    isEnabled: vi.fn(() => false)
  }
}));

const isEnabledMock = ideConfigStore.isEnabled as unknown as ReturnType<typeof vi.fn>;

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
});
