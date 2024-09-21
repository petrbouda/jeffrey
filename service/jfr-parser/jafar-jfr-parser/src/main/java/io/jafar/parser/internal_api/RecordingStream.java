

package io.jafar.parser.internal_api;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RecordingStream implements AutoCloseable {
  private final ByteBuffer delegate;

  private final io.jafar.parser.internal_api.ParserContext context;

  RecordingStream(ByteBuffer buffer) {
    this(buffer, new io.jafar.parser.internal_api.ParserContext());
  }

  public RecordingStream slice(int pos, int len, io.jafar.parser.internal_api.ParserContext context) {
    return new RecordingStream(delegate.slice(pos, len), context);
  }

  private RecordingStream(ByteBuffer buffer, io.jafar.parser.internal_api.ParserContext context) {
    if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
      this.delegate = buffer.slice().order(ByteOrder.BIG_ENDIAN);
    } else {
      this.delegate = buffer;
    }
    this.context = context;
  }

  public ParserContext getContext() {
    return context;
  }

  public void position(int position) {
    delegate.position(position);
  }

  public int position() {
    return delegate.position();
  }

  public void read(byte[] buffer, int offset, int length) throws IOException {
    try {
      if (delegate.remaining() < length) {
        throw new EOFException("unexpected EOF");
      }
      delegate.get(buffer, offset, length);
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public byte read() throws IOException {
    try {
      return delegate.get();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public short readShort() throws IOException {
    try {
      return delegate.getShort();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public int readInt() throws IOException {
    try {
      return delegate.getInt();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public long readLong() throws IOException {
    try {
      return delegate.getLong();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public float readFloat() throws IOException {
    try {
      return delegate.getFloat();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public double readDouble() throws IOException {
    try {
      return delegate.getDouble();
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public long readVarint() throws IOException {
    try {
      // TODO can optimise this
      long value = 0;
      int readValue = 0;
      int i = 0;
      do {
        readValue = delegate.get();
        value |= (long) (readValue & 0x7F) << (7 * i);
        i++;
      } while ((readValue & 0x80) != 0
          // In fact a fully LEB128 encoded 64bit number could take up to 10 bytes
          // (in order to store 64 bit original value using 7bit slots we need at most 10 of them).
          // However, eg. JMC parser will stop at 9 bytes, assuming that the compressed number is
          // a Java unsigned long (therefore having only 63 bits and they all fit in 9 bytes).
          && i < 9);
      return value;
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public boolean readBoolean() throws IOException {
    return read() != 0;
  }

  public int available() {
    return delegate.remaining();
  }

  public void skip(int bytes) throws IOException {
    try {
      delegate.position(delegate.position() + bytes);
    } catch (BufferUnderflowException | BufferOverflowException e) {
      throw new IOException(e);
    }
  }

  public void mark() {
    delegate.mark();
  }

  public void reset() {
    delegate.reset();
  }

  @Override
  public void close() {
  }
}
