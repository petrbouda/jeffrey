/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import FrameType from '@/services/flamegraphs/FrameType';

export default class FrameColorResolver {
  private static readonly FRAME_TYPE_COLORS: Record<string, string> = {
    [FrameType.C1_COMPILED]: '#cce880',
    [FrameType.NATIVE]: '#ffa6a6',
    [FrameType.CPP]: '#e3ed6d',
    [FrameType.INTERPRETED]: '#b2e1b2',
    [FrameType.JIT_COMPILED]: '#94f25a',
    [FrameType.INLINED]: '#8eeded',
    [FrameType.KERNEL]: '#f2af5e',
    [FrameType.PYTHON]: '#a2c8f5',
    [FrameType.JAVASCRIPT]: '#f5e08a',
    [FrameType.GO]: '#8fd9e8',
    [FrameType.DOTNET]: '#c9a8f0',
    [FrameType.RUBY]: '#f0a2b8',
    [FrameType.PHP]: '#b8b3f0',
    [FrameType.PERL]: '#d9cba8',
    [FrameType.BEAM]: '#e3a2e3',
    [FrameType.RUST]: '#e8b48f',
    [FrameType.LUA]: '#96d9c2',
    [FrameType.THREAD_NAME_SYNTHETIC]: '#e17e5a',
    [FrameType.ALLOCATED_OBJECT_SYNTHETIC]: '#00b6ff',
    [FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC]: '#ADE8F4',
    [FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC]: '#00B4D8',
    [FrameType.BLOCKING_OBJECT_SYNTHETIC]: '#e17e5a',
    [FrameType.LAMBDA_SYNTHETIC]: '#b3c6ff',
    [FrameType.COLLAPSED_SYNTHETIC]: '#b3c6ff',
    [FrameType.TRUNCATED_SYNTHETIC]: '#fbcfe8',
    [FrameType.UNKNOWN]: '#000000'
  };

  private static readonly FRAME_TYPE_TITLES: Record<string, string> = {
    [FrameType.C1_COMPILED]: 'JAVA C1-compiled',
    [FrameType.NATIVE]: 'Native',
    [FrameType.CPP]: 'C++ (JVM)',
    [FrameType.INTERPRETED]: 'Interpreted (JAVA)',
    [FrameType.JIT_COMPILED]: 'JIT-compiled (JAVA)',
    [FrameType.INLINED]: 'Inlined (JAVA)',
    [FrameType.KERNEL]: 'Kernel',
    [FrameType.PYTHON]: 'Python',
    [FrameType.JAVASCRIPT]: 'JavaScript (V8)',
    [FrameType.GO]: 'Go',
    [FrameType.DOTNET]: '.NET',
    [FrameType.RUBY]: 'Ruby',
    [FrameType.PHP]: 'PHP',
    [FrameType.PERL]: 'Perl',
    [FrameType.BEAM]: 'Erlang/Elixir (BEAM)',
    [FrameType.RUST]: 'Rust',
    [FrameType.LUA]: 'Lua (LuaJIT)',
    [FrameType.THREAD_NAME_SYNTHETIC]: 'Thread Name (Synthetic)',
    [FrameType.ALLOCATED_OBJECT_SYNTHETIC]: 'Allocated Object (Synthetic)',
    [FrameType.ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC]: 'Allocated in New TLAB (Synthetic)',
    [FrameType.ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC]: 'Allocated Outside TLAB (Synthetic)',
    [FrameType.BLOCKING_OBJECT_SYNTHETIC]: 'Blocking Object (Synthetic)',
    [FrameType.LAMBDA_SYNTHETIC]: 'Lambda (Synthetic)',
    [FrameType.COLLAPSED_SYNTHETIC]: 'Collapsed (Synthetic)',
    [FrameType.TRUNCATED_SYNTHETIC]: 'Truncated (Synthetic)',
    [FrameType.UNKNOWN]: 'Unknown',
    [FrameType.HIGHLIGHTED_WARNING]: 'Highlighted Warning'
  };

  // Five-stop ramp for SHARED frames; lighter at top so fully-removed frames
  // (REMOVED_COLOR) read as visibly deeper than even the heaviest improvement.
  private static readonly GREEN_COLORS = ['#E5FFCC', '#E5FFBB', '#CCFF99', '#C8FF8C', '#B8FF6E'];
  // Five-stop ramp for SHARED frames; lighter at top so fully-new frames
  // (ADDED_COLOR) read as visibly deeper than even the heaviest regression.
  private static readonly RED_COLORS = ['#FFEEEE', '#FFDDDD', '#FFCCCC', '#FFB8B8', '#FF9F9F'];
  // Dedicated fills for the structural endpoints: a frame that exists only in
  // primary (ADDED) or only in secondary (REMOVED). Visually distinct from any
  // SHARED bucket so brand-new / fully-removed frames don't blend in.
  private static readonly ADDED_COLOR = '#B91C1C';
  private static readonly REMOVED_COLOR = '#15803D';
  private static readonly NEUTRAL_COLOR = '#E6E6E6';
  // Grey color for frames before marker (guardian analysis)
  private static readonly BEFORE_MARKER_COLOR = '#CCCCCC';

  /**
   * Resolves color for regular flamegraphs based on frame type.
   * @param frameType The type of the frame
   * @param beforeMarker If true, frame is before guardian marker and should be grey
   */
  static resolveByType(frameType: string, beforeMarker?: boolean): string {
    if (beforeMarker) {
      return this.BEFORE_MARKER_COLOR;
    }
    return this.FRAME_TYPE_COLORS[frameType] || '#000000';
  }

  /**
   * Resolves the display title for a frame type.
   */
  static resolveTitle(frameType: string): string {
    return this.FRAME_TYPE_TITLES[frameType] || frameType;
  }

  /**
   * True for fills dark enough that black labels stop being readable. Currently
   * the only such fills are the dedicated ADDED / REMOVED diff endpoints; every
   * other palette stop sits in the light end of its hue range.
   */
  static isDarkFill(color: string): boolean {
    return color === this.ADDED_COLOR || color === this.REMOVED_COLOR;
  }

  /**
   * Resolves color for differential flamegraphs based on the difference between primary and secondary values.
   * Frames that exist only in primary (ADDED) or only in secondary (REMOVED) get dedicated deeper fills so
   * they stand out from heavily-regressed / heavily-improved SHARED frames. SHARED frames ramp through the
   * five-stop RED (regression) or GREEN (improvement) scale.
   */
  static resolveDiffColor(primary: number, secondary: number, frameType: string): string {
    // Synthetic frames are categorical markers (TLAB allocation, lambda wrapper,
    // truncated subtree, ...). Their identity is the load-bearing signal — a
    // TLAB-allocation marker must always read as one, even when it was added or
    // removed between the two profiles. Keep the palette color and skip the
    // diff ramp entirely.
    if (FrameType.isSynthetic(frameType)) {
      return this.FRAME_TYPE_COLORS[frameType];
    }

    if (secondary === 0 && primary > 0) {
      return this.ADDED_COLOR;
    }
    if (primary === 0 && secondary > 0) {
      return this.REMOVED_COLOR;
    }

    const total = primary + secondary;
    if (total === 0) {
      return this.NEUTRAL_COLOR;
    }

    const diff = Math.abs(primary - secondary);
    const pct = diff / total;

    if (pct <= 0.02) {
      return this.NEUTRAL_COLOR;
    }

    let index: number;
    if (pct <= 0.05) {
      index = 0;
    } else if (pct <= 0.1) {
      index = 1;
    } else if (pct <= 0.4) {
      index = 2;
    } else if (pct <= 0.8) {
      index = 3;
    } else {
      index = 4;
    }

    return primary < secondary ? this.GREEN_COLORS[index] : this.RED_COLORS[index];
  }
}
