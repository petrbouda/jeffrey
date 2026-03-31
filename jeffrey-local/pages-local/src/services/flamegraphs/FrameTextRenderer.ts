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

import JavaMethodParser from "@/services/flamegraphs/JavaMethodParser";

export interface FrameTextRenderer {
    readonly frameHeight: number;
    drawText(ctx: CanvasRenderingContext2D, title: string, frameType: string | undefined, x: number, y: number, pixelWidth: number): void;
}

const FONT_NORMAL = '11px -apple-system, BlinkMacSystemFont, sans-serif';
const FONT_BOLD = 'bold 11px -apple-system, BlinkMacSystemFont, sans-serif';
const FONT_ITALIC = 'italic 11px -apple-system, BlinkMacSystemFont, sans-serif';

interface ParsedFrame {
    pkg: string | null;
    className: string;
    separator: string;
    methodName: string;
}

function parseFrame(title: string, frameType: string | undefined): ParsedFrame | null {
    // Java: package.Class#method
    if (frameType && JavaMethodParser.isJavaFrame(frameType)) {
        const parsed = JavaMethodParser.parse(title);
        if (parsed) {
            return {
                pkg: parsed.packageName,
                className: parsed.className,
                separator: '.',
                methodName: parsed.methodName
            };
        }
    }

    // C++: Class::method
    const idx = title.indexOf('::');
    if (idx > 0 && idx < title.length - 2) {
        return {
            pkg: null,
            className: title.substring(0, idx),
            separator: '::',
            methodName: title.substring(idx + 2)
        };
    }

    return null;
}

function truncate(text: string, maxChars: number): string {
    return text.length <= maxChars ? text : text.substring(0, maxChars - 2) + '..';
}

function truncatePx(text: string, maxWidth: number, ctx: CanvasRenderingContext2D): string {
    if (ctx.measureText(text).width <= maxWidth) return text;
    for (let i = text.length - 1; i > 0; i--) {
        const candidate = text.substring(0, i) + '..';
        if (ctx.measureText(candidate).width <= maxWidth) return candidate;
    }
    return '';
}

/**
 * Renders bold ClassName + italic separator+method at the given position.
 * Returns true if rendered, false if not enough space.
 */
function drawBoldClassItalicMethod(
    ctx: CanvasRenderingContext2D, parsed: ParsedFrame,
    cx: number, textY: number, maxW: number
): void {
    const methodStr = parsed.separator + parsed.methodName;
    ctx.font = FONT_ITALIC;
    const methodW = ctx.measureText(methodStr).width;
    ctx.font = FONT_BOLD;
    const clsW = ctx.measureText(parsed.className).width;

    if (clsW + methodW <= maxW) {
        ctx.fillStyle = '#000000';
        ctx.font = FONT_BOLD;
        ctx.fillText(parsed.className, cx, textY);
        ctx.font = FONT_ITALIC;
        ctx.fillText(methodStr, cx + clsW, textY);
    } else {
        ctx.fillStyle = '#000000';
        ctx.font = FONT_BOLD;
        const clsTrunc = truncatePx(parsed.className, maxW - methodW, ctx);
        const clsTruncW = ctx.measureText(clsTrunc).width;
        ctx.fillText(clsTrunc, cx, textY);
        ctx.font = FONT_ITALIC;
        ctx.fillText(truncatePx(methodStr, maxW - clsTruncW, ctx), cx + clsTruncW, textY);
    }
}

/**
 * Single-line rendering: 20px frames.
 * Java frames: muted package (0.7) + bold Class + italic .method
 * C++ frames: bold Class + italic ::method
 * Other frames: normal black text.
 */
export class SingleLineFrameTextRenderer implements FrameTextRenderer {
    readonly frameHeight = 20;

    drawText(ctx: CanvasRenderingContext2D, title: string, frameType: string | undefined, x: number, y: number, pixelWidth: number): void {
        if (pixelWidth < 21) return;

        const maxW = pixelWidth - 6;
        const textY = y + 14;
        const parsed = parseFrame(title, frameType);

        if (parsed) {
            const methodStr = parsed.separator + parsed.methodName;
            ctx.font = FONT_ITALIC;
            const methodW = ctx.measureText(methodStr).width;
            ctx.font = FONT_BOLD;
            const clsW = ctx.measureText(parsed.className).width;
            const classMethodW = clsW + methodW;

            if (parsed.pkg && classMethodW <= maxW) {
                // Java: room for package prefix
                const remaining = maxW - classMethodW;
                ctx.font = FONT_NORMAL;
                const pkgText = truncatePx(parsed.pkg + '.', remaining, ctx);
                const pkgW = pkgText ? ctx.measureText(pkgText).width : 0;
                let cx = x;

                if (pkgText) {
                    ctx.fillStyle = 'rgba(0,0,0,0.7)';
                    ctx.font = FONT_NORMAL;
                    ctx.fillText(pkgText, cx, textY);
                    cx += pkgW;
                }
                drawBoldClassItalicMethod(ctx, parsed, cx, textY, maxW - pkgW);
            } else {
                drawBoldClassItalicMethod(ctx, parsed, x, textY, maxW);
            }
            return;
        }

        // Plain fallback
        ctx.fillStyle = '#000000';
        ctx.font = FONT_NORMAL;
        ctx.fillText(truncatePx(title, maxW, ctx), x, textY);
    }
}

/**
 * Two-line rendering: 30px frames.
 * Wide Java frames (>=60px): bold Class + italic .method on line 1, muted package on line 2.
 * Wide C++ frames (>=60px): bold Class + italic ::method on line 1 (no line 2).
 * Narrow frames (>=18px): single centered bold Class + italic method.
 * Other frames (>=18px): single centered title.
 */
export class TwoLineFrameTextRenderer implements FrameTextRenderer {
    readonly frameHeight = 30;

    private static readonly FONT_PACKAGE = '10px -apple-system, BlinkMacSystemFont, sans-serif';

    drawText(ctx: CanvasRenderingContext2D, title: string, frameType: string | undefined, x: number, y: number, pixelWidth: number): void {
        if (pixelWidth < 18) return;

        const textX = x + 4;
        const maxTextWidth = pixelWidth - 10;
        const parsed = parseFrame(title, frameType);

        // Wide frame with parseable class::method or class.method
        if (parsed && pixelWidth >= 60) {
            // Line 1: bold Class + italic method
            const line1Y = parsed.pkg ? y + 13 : y + 19; // center vertically if no package line
            drawBoldClassItalicMethod(ctx, parsed, textX, line1Y, maxTextWidth);

            // Line 2: package (Java only)
            if (parsed.pkg) {
                const charsSmall = Math.floor(pixelWidth / 5.5);
                ctx.fillStyle = 'rgba(0,0,0,0.7)';
                ctx.font = TwoLineFrameTextRenderer.FONT_PACKAGE;
                ctx.fillText(truncate(parsed.pkg, charsSmall), textX, y + 25, maxTextWidth);
            }
            return;
        }

        // Narrow parseable frame: single centered line
        if (parsed && pixelWidth >= 21) {
            drawBoldClassItalicMethod(ctx, parsed, textX, y + 19, maxTextWidth);
            return;
        }

        // Plain fallback
        if (pixelWidth >= 21) {
            ctx.fillStyle = '#000000';
            ctx.font = FONT_NORMAL;
            ctx.fillText(truncatePx(title, maxTextWidth, ctx), textX, y + 19, maxTextWidth);
        }
    }
}
