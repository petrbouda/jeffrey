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

/** The wire types of the protobuf encoding; groups (3, 4) are long deprecated and not read. */
export const WireType = {
  VARINT: 0,
  FIXED64: 1,
  LENGTH_DELIMITED: 2,
  FIXED32: 5
} as const;

const TWO_POW_32 = 4294967296;
const FIXED64_BYTES = 8;
const FIXED32_BYTES = 4;
const VARINT_CONTINUATION = 0x80;
const VARINT_PAYLOAD = 0x7f;

/**
 * A cursor over protobuf-encoded bytes, reading the scalar types flamegraph.proto uses. Signed
 * 64-bit integers come back as numbers: exact up to 2^53, which no sample count or weight reaches.
 */
export default class ProtoWireReader {
  readonly bytes: Uint8Array;
  pos: number;
  private readonly view: DataView;
  private readonly textDecoder = new TextDecoder();

  constructor(bytes: Uint8Array, pos: number = 0) {
    this.bytes = bytes;
    this.pos = pos;
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }

  /** A varint of at most 32 bits: a tag, a length, an enum. */
  uint32(): number {
    let value = 0;
    for (let shift = 0; shift < 35; shift += 7) {
      const b = this.nextByte();
      value = (value | ((b & VARINT_PAYLOAD) << shift)) >>> 0;
      if (b < VARINT_CONTINUATION) {
        return value;
      }
    }
    throw new Error('Malformed protobuf: varint longer than 32 bits');
  }

  /** An int64 varint; a negative value is ten bytes of two's complement. */
  int64(): number {
    let lo = 0;
    let hi = 0;
    // Bytes 0-3 fill bits 0-27 of the low word
    for (let i = 0; i < 4; i++) {
      const b = this.nextByte();
      lo = (lo | ((b & VARINT_PAYLOAD) << (i * 7))) >>> 0;
      if (b < VARINT_CONTINUATION) {
        return ProtoWireReader.toNumber(lo, hi);
      }
    }
    // Byte 4 straddles the words: bits 28-31 of the low one, bits 0-2 of the high one
    let b = this.nextByte();
    lo = (lo | ((b & VARINT_PAYLOAD) << 28)) >>> 0;
    hi = (hi | ((b & VARINT_PAYLOAD) >> 4)) >>> 0;
    if (b < VARINT_CONTINUATION) {
      return ProtoWireReader.toNumber(lo, hi);
    }
    // Bytes 5-9 fill the rest of the high word
    for (let i = 0; i < 5; i++) {
      b = this.nextByte();
      hi = (hi | ((b & VARINT_PAYLOAD) << (i * 7 + 3))) >>> 0;
      if (b < VARINT_CONTINUATION) {
        return ProtoWireReader.toNumber(lo, hi);
      }
    }
    throw new Error('Malformed protobuf: varint longer than 64 bits');
  }

  /** An int32 varint; protobuf sign-extends a negative one to ten bytes, as an int64. */
  int32(): number {
    return this.int64() | 0;
  }

  bool(): boolean {
    return this.uint32() !== 0;
  }

  float(): number {
    this.require(FIXED32_BYTES);
    const value = this.view.getFloat32(this.pos, true);
    this.pos += FIXED32_BYTES;
    return value;
  }

  /** The length prefix of a length-delimited field, returned as where the field ends. */
  delimitedEnd(): number {
    const length = this.uint32();
    const end = this.pos + length;
    if (end > this.bytes.length) {
      throw new Error('Malformed protobuf: field runs past the end of the message');
    }
    return end;
  }

  string(): string {
    const end = this.delimitedEnd();
    const value = this.textDecoder.decode(this.bytes.subarray(this.pos, end));
    this.pos = end;
    return value;
  }

  /** Steps over a field this reader has no use for. */
  skip(wireType: number): void {
    switch (wireType) {
      case WireType.VARINT:
        this.int64();
        return;
      case WireType.FIXED64:
        this.require(FIXED64_BYTES);
        this.pos += FIXED64_BYTES;
        return;
      case WireType.LENGTH_DELIMITED:
        this.pos = this.delimitedEnd();
        return;
      case WireType.FIXED32:
        this.require(FIXED32_BYTES);
        this.pos += FIXED32_BYTES;
        return;
      default:
        throw new Error(`Malformed protobuf: unsupported wire type ${wireType}`);
    }
  }

  private nextByte(): number {
    if (this.pos >= this.bytes.length) {
      throw new Error('Malformed protobuf: message ends inside a varint');
    }
    return this.bytes[this.pos++];
  }

  private require(byteCount: number): void {
    if (this.pos + byteCount > this.bytes.length) {
      throw new Error('Malformed protobuf: message ends inside a fixed-width field');
    }
  }

  private static toNumber(lo: number, hi: number): number {
    if (hi >>> 31 === 0) {
      return hi * TWO_POW_32 + lo;
    }
    // Negative: the magnitude is the two's complement of the 64 bits
    let magnitudeLo = (~lo + 1) >>> 0;
    let magnitudeHi = ~hi >>> 0;
    if (magnitudeLo === 0) {
      magnitudeHi = (magnitudeHi + 1) >>> 0;
    }
    return -(magnitudeHi * TWO_POW_32 + magnitudeLo);
  }
}
