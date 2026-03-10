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

package pbouda.jeffrey.shared.turso;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * Panama FFI bindings for the libsql C API (libsql-c).
 *
 * <p>All struct layouts, function descriptors, and method handles are defined as static constants.
 * Struct field access is provided via {@link VarHandle} constants.
 *
 * <p>Structs that are 16 bytes or smaller (2 pointers) are returned in registers on System V ABI.
 * Larger structs require a {@link SegmentAllocator} as the first implicit parameter.
 */
public final class LibSql {

    private LibSql() {
    }

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LibSqlLoader.symbolLookup();

    // ──────────────────────────────────────────────────────────────────────
    // Struct Layouts
    // ──────────────────────────────────────────────────────────────────────

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_database_t;}
     */
    public static final StructLayout DATABASE_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_connection_t;}
     */
    public static final StructLayout CONNECTION_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_statement_t;}
     */
    public static final StructLayout STATEMENT_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_transaction_t;}
     */
    public static final StructLayout TRANSACTION_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_rows_t;}
     */
    public static final StructLayout ROWS_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; void *inner; } libsql_row_t;}
     */
    public static final StructLayout ROW_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.ADDRESS.withName("inner")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; } libsql_batch_t;}
     */
    public static final StructLayout BATCH_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err")
    );

    /**
     * {@code typedef struct { const void *ptr; size_t len; } libsql_slice_t;}
     */
    public static final StructLayout SLICE_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("ptr"),
            ValueLayout.JAVA_LONG.withName("len")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; uint64_t rows_changed; } libsql_execute_t;}
     */
    public static final StructLayout EXECUTE_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.JAVA_LONG.withName("rows_changed")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; } libsql_bind_t;}
     */
    public static final StructLayout BIND_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; uint64_t frame_no; uint64_t frames_synced; } libsql_sync_t;}
     */
    public static final StructLayout SYNC_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.JAVA_LONG.withName("frame_no"),
            ValueLayout.JAVA_LONG.withName("frames_synced")
    );

    /**
     * {@code typedef struct { libsql_error_t *err; int64_t last_inserted_rowid; uint64_t total_changes; } libsql_connection_info_t;}
     */
    public static final StructLayout CONNECTION_INFO_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            ValueLayout.JAVA_LONG.withName("last_inserted_rowid"),
            ValueLayout.JAVA_LONG.withName("total_changes")
    );

    /**
     * Union of value types. Sized to the largest member (libsql_slice_t = 16 bytes).
     * {@code typedef union { int64_t integer; double real; libsql_slice_t text; libsql_slice_t blob; } libsql_value_union_t;}
     */
    public static final UnionLayout VALUE_UNION_T = MemoryLayout.unionLayout(
            ValueLayout.JAVA_LONG.withName("integer"),
            ValueLayout.JAVA_DOUBLE.withName("real"),
            SLICE_T.withName("text"),
            SLICE_T.withName("blob")
    );

    /**
     * {@code typedef struct { libsql_value_union_t value; libsql_type_t type; } libsql_value_t;}
     * Size: 16 (union) + 4 (int) + 4 (padding) = 24 bytes
     */
    public static final StructLayout VALUE_T = MemoryLayout.structLayout(
            VALUE_UNION_T.withName("value"),
            ValueLayout.JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4)
    );

    /**
     * {@code typedef struct { libsql_error_t *err; libsql_value_t ok; } libsql_result_value_t;}
     * Size: 8 (pointer) + 24 (value_t) = 32 bytes
     */
    public static final StructLayout RESULT_VALUE_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("err"),
            VALUE_T.withName("ok")
    );

    /**
     * {@code typedef struct { void (*logger)(libsql_log_t log); const char *version; } libsql_config_t;}
     */
    public static final StructLayout CONFIG_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("logger"),
            ValueLayout.ADDRESS.withName("version")
    );

    /**
     * Database descriptor for initialization.
     * <pre>{@code
     * typedef struct {
     *     const char *url;
     *     const char *path;
     *     const char *auth_token;
     *     const char *encryption_key;
     *     uint64_t sync_interval;
     *     libsql_cypher_t cypher;       // enum (int)
     *     bool disable_read_your_writes;
     *     bool webpki;
     *     bool synced;
     *     bool disable_safety_assert;
     *     const char *namespace;
     * } libsql_database_desc_t;
     * }</pre>
     */
    public static final StructLayout DATABASE_DESC_T = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("url"),
            ValueLayout.ADDRESS.withName("path"),
            ValueLayout.ADDRESS.withName("auth_token"),
            ValueLayout.ADDRESS.withName("encryption_key"),
            ValueLayout.JAVA_LONG.withName("sync_interval"),
            ValueLayout.JAVA_INT.withName("cypher"),
            ValueLayout.JAVA_BOOLEAN.withName("disable_read_your_writes"),
            ValueLayout.JAVA_BOOLEAN.withName("webpki"),
            ValueLayout.JAVA_BOOLEAN.withName("synced"),
            ValueLayout.JAVA_BOOLEAN.withName("disable_safety_assert"),
            MemoryLayout.paddingLayout(2),
            ValueLayout.ADDRESS.withName("namespace")
    );

    // ──────────────────────────────────────────────────────────────────────
    // VarHandles for struct field access
    // ──────────────────────────────────────────────────────────────────────

    public static final VarHandle ERR_HANDLE = DATABASE_T.varHandle(MemoryLayout.PathElement.groupElement("err"));
    public static final VarHandle INNER_HANDLE = DATABASE_T.varHandle(MemoryLayout.PathElement.groupElement("inner"));

    public static final VarHandle BATCH_ERR = BATCH_T.varHandle(MemoryLayout.PathElement.groupElement("err"));

    public static final VarHandle BIND_ERR = BIND_T.varHandle(MemoryLayout.PathElement.groupElement("err"));

    public static final VarHandle EXECUTE_ERR = EXECUTE_T.varHandle(MemoryLayout.PathElement.groupElement("err"));
    public static final VarHandle EXECUTE_ROWS_CHANGED = EXECUTE_T.varHandle(MemoryLayout.PathElement.groupElement("rows_changed"));

    public static final VarHandle SLICE_PTR = SLICE_T.varHandle(MemoryLayout.PathElement.groupElement("ptr"));
    public static final VarHandle SLICE_LEN = SLICE_T.varHandle(MemoryLayout.PathElement.groupElement("len"));

    public static final VarHandle VALUE_TYPE = VALUE_T.varHandle(MemoryLayout.PathElement.groupElement("type"));

    public static final VarHandle RESULT_VALUE_ERR = RESULT_VALUE_T.varHandle(MemoryLayout.PathElement.groupElement("err"));

    // ──────────────────────────────────────────────────────────────────────
    // Method Handles for C functions
    // ──────────────────────────────────────────────────────────────────────

    // const libsql_error_t *libsql_setup(libsql_config_t config)
    public static final MethodHandle SETUP = downcall(
            "libsql_setup",
            FunctionDescriptor.of(ValueLayout.ADDRESS, CONFIG_T)
    );

    // const char *libsql_error_message(libsql_error_t *self)
    public static final MethodHandle ERROR_MESSAGE = downcall(
            "libsql_error_message",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    // libsql_database_t libsql_database_init(libsql_database_desc_t desc)
    public static final MethodHandle DATABASE_INIT = downcall(
            "libsql_database_init",
            FunctionDescriptor.of(DATABASE_T, DATABASE_DESC_T)
    );

    // libsql_sync_t libsql_database_sync(libsql_database_t self)
    public static final MethodHandle DATABASE_SYNC = downcall(
            "libsql_database_sync",
            FunctionDescriptor.of(SYNC_T, DATABASE_T)
    );

    // libsql_connection_t libsql_database_connect(libsql_database_t self)
    public static final MethodHandle DATABASE_CONNECT = downcall(
            "libsql_database_connect",
            FunctionDescriptor.of(CONNECTION_T, DATABASE_T)
    );

    // void libsql_database_deinit(libsql_database_t self)
    public static final MethodHandle DATABASE_DEINIT = downcall(
            "libsql_database_deinit",
            FunctionDescriptor.ofVoid(DATABASE_T)
    );

    // libsql_transaction_t libsql_connection_transaction(libsql_connection_t self)
    public static final MethodHandle CONNECTION_TRANSACTION = downcall(
            "libsql_connection_transaction",
            FunctionDescriptor.of(TRANSACTION_T, CONNECTION_T)
    );

    // libsql_batch_t libsql_connection_batch(libsql_connection_t self, const char *sql)
    public static final MethodHandle CONNECTION_BATCH = downcall(
            "libsql_connection_batch",
            FunctionDescriptor.of(BATCH_T, CONNECTION_T, ValueLayout.ADDRESS)
    );

    // libsql_connection_info_t libsql_connection_info(libsql_connection_t self)
    public static final MethodHandle CONNECTION_INFO = downcall(
            "libsql_connection_info",
            FunctionDescriptor.of(CONNECTION_INFO_T, CONNECTION_T)
    );

    // libsql_statement_t libsql_connection_prepare(libsql_connection_t self, const char *sql)
    public static final MethodHandle CONNECTION_PREPARE = downcall(
            "libsql_connection_prepare",
            FunctionDescriptor.of(STATEMENT_T, CONNECTION_T, ValueLayout.ADDRESS)
    );

    // void libsql_connection_deinit(libsql_connection_t self)
    public static final MethodHandle CONNECTION_DEINIT = downcall(
            "libsql_connection_deinit",
            FunctionDescriptor.ofVoid(CONNECTION_T)
    );

    // libsql_batch_t libsql_transaction_batch(libsql_transaction_t self, const char *sql)
    public static final MethodHandle TRANSACTION_BATCH = downcall(
            "libsql_transaction_batch",
            FunctionDescriptor.of(BATCH_T, TRANSACTION_T, ValueLayout.ADDRESS)
    );

    // libsql_statement_t libsql_transaction_prepare(libsql_transaction_t self, const char *sql)
    public static final MethodHandle TRANSACTION_PREPARE = downcall(
            "libsql_transaction_prepare",
            FunctionDescriptor.of(STATEMENT_T, TRANSACTION_T, ValueLayout.ADDRESS)
    );

    // void libsql_transaction_commit(libsql_transaction_t self)
    public static final MethodHandle TRANSACTION_COMMIT = downcall(
            "libsql_transaction_commit",
            FunctionDescriptor.ofVoid(TRANSACTION_T)
    );

    // void libsql_transaction_rollback(libsql_transaction_t self)
    public static final MethodHandle TRANSACTION_ROLLBACK = downcall(
            "libsql_transaction_rollback",
            FunctionDescriptor.ofVoid(TRANSACTION_T)
    );

    // libsql_execute_t libsql_statement_execute(libsql_statement_t self)
    public static final MethodHandle STATEMENT_EXECUTE = downcall(
            "libsql_statement_execute",
            FunctionDescriptor.of(EXECUTE_T, STATEMENT_T)
    );

    // libsql_rows_t libsql_statement_query(libsql_statement_t self)
    public static final MethodHandle STATEMENT_QUERY = downcall(
            "libsql_statement_query",
            FunctionDescriptor.of(ROWS_T, STATEMENT_T)
    );

    // void libsql_statement_reset(libsql_statement_t self)
    public static final MethodHandle STATEMENT_RESET = downcall(
            "libsql_statement_reset",
            FunctionDescriptor.ofVoid(STATEMENT_T)
    );

    // size_t libsql_statement_column_count(libsql_statement_t self)
    public static final MethodHandle STATEMENT_COLUMN_COUNT = downcall(
            "libsql_statement_column_count",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, STATEMENT_T)
    );

    // libsql_bind_t libsql_statement_bind_named(libsql_statement_t self, const char *name, libsql_value_t value)
    public static final MethodHandle STATEMENT_BIND_NAMED = downcall(
            "libsql_statement_bind_named",
            FunctionDescriptor.of(BIND_T, STATEMENT_T, ValueLayout.ADDRESS, VALUE_T)
    );

    // libsql_bind_t libsql_statement_bind_value(libsql_statement_t self, libsql_value_t value)
    public static final MethodHandle STATEMENT_BIND_VALUE = downcall(
            "libsql_statement_bind_value",
            FunctionDescriptor.of(BIND_T, STATEMENT_T, VALUE_T)
    );

    // void libsql_statement_deinit(libsql_statement_t self)
    public static final MethodHandle STATEMENT_DEINIT = downcall(
            "libsql_statement_deinit",
            FunctionDescriptor.ofVoid(STATEMENT_T)
    );

    // libsql_row_t libsql_rows_next(libsql_rows_t self)
    public static final MethodHandle ROWS_NEXT = downcall(
            "libsql_rows_next",
            FunctionDescriptor.of(ROW_T, ROWS_T)
    );

    // libsql_slice_t libsql_rows_column_name(libsql_rows_t self, int32_t index)
    public static final MethodHandle ROWS_COLUMN_NAME = downcall(
            "libsql_rows_column_name",
            FunctionDescriptor.of(SLICE_T, ROWS_T, ValueLayout.JAVA_INT)
    );

    // int32_t libsql_rows_column_count(libsql_rows_t self)
    public static final MethodHandle ROWS_COLUMN_COUNT = downcall(
            "libsql_rows_column_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ROWS_T)
    );

    // void libsql_rows_deinit(libsql_rows_t self)
    public static final MethodHandle ROWS_DEINIT = downcall(
            "libsql_rows_deinit",
            FunctionDescriptor.ofVoid(ROWS_T)
    );

    // libsql_result_value_t libsql_row_value(libsql_row_t self, int32_t index)
    public static final MethodHandle ROW_VALUE = downcall(
            "libsql_row_value",
            FunctionDescriptor.of(RESULT_VALUE_T, ROW_T, ValueLayout.JAVA_INT)
    );

    // libsql_slice_t libsql_row_name(libsql_row_t self, int32_t index)
    public static final MethodHandle ROW_NAME = downcall(
            "libsql_row_name",
            FunctionDescriptor.of(SLICE_T, ROW_T, ValueLayout.JAVA_INT)
    );

    // int32_t libsql_row_length(libsql_row_t self)
    public static final MethodHandle ROW_LENGTH = downcall(
            "libsql_row_length",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ROW_T)
    );

    // bool libsql_row_empty(libsql_row_t self)
    public static final MethodHandle ROW_EMPTY = downcall(
            "libsql_row_empty",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ROW_T)
    );

    // void libsql_row_deinit(libsql_row_t self)
    public static final MethodHandle ROW_DEINIT = downcall(
            "libsql_row_deinit",
            FunctionDescriptor.ofVoid(ROW_T)
    );

    // void libsql_error_deinit(libsql_error_t *self)
    public static final MethodHandle ERROR_DEINIT = downcall(
            "libsql_error_deinit",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );

    // void libsql_slice_deinit(libsql_slice_t value)
    public static final MethodHandle SLICE_DEINIT = downcall(
            "libsql_slice_deinit",
            FunctionDescriptor.ofVoid(SLICE_T)
    );

    // libsql_value_t libsql_integer(int64_t integer)
    public static final MethodHandle INTEGER = downcall(
            "libsql_integer",
            FunctionDescriptor.of(VALUE_T, ValueLayout.JAVA_LONG)
    );

    // libsql_value_t libsql_real(double real)
    public static final MethodHandle REAL = downcall(
            "libsql_real",
            FunctionDescriptor.of(VALUE_T, ValueLayout.JAVA_DOUBLE)
    );

    // libsql_value_t libsql_text(const char *ptr, size_t len)
    public static final MethodHandle TEXT = downcall(
            "libsql_text",
            FunctionDescriptor.of(VALUE_T, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    );

    // libsql_value_t libsql_blob(const uint8_t *ptr, size_t len)
    public static final MethodHandle BLOB = downcall(
            "libsql_blob",
            FunctionDescriptor.of(VALUE_T, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    );

    // libsql_value_t libsql_null()
    public static final MethodHandle NULL_VALUE = downcall(
            "libsql_null",
            FunctionDescriptor.of(VALUE_T)
    );

    // ──────────────────────────────────────────────────────────────────────
    // Type constants matching libsql_type_t
    // ──────────────────────────────────────────────────────────────────────

    public static final int TYPE_INTEGER = 1;
    public static final int TYPE_REAL = 2;
    public static final int TYPE_TEXT = 3;
    public static final int TYPE_BLOB = 4;
    public static final int TYPE_NULL = 5;

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(
                LOOKUP.find(name).orElseThrow(() ->
                        new UnsatisfiedLinkError("Symbol not found: " + name)),
                descriptor
        );
    }

    /**
     * Extract the error pointer from a struct that has {@code err} as its first field.
     */
    public static MemorySegment getErr(MemorySegment struct) {
        return (MemorySegment) ERR_HANDLE.get(struct, 0L);
    }

    /**
     * Extract the inner pointer from a struct that has {@code inner} as its second field.
     */
    public static MemorySegment getInner(MemorySegment struct) {
        return (MemorySegment) INNER_HANDLE.get(struct, 0L);
    }

    /**
     * Check if the error pointer in a result struct is non-null and throw if so.
     */
    public static void checkError(MemorySegment errPtr, String context) {
        if (!MemorySegment.NULL.equals(errPtr) && errPtr.address() != 0) {
            String message = extractErrorMessage(errPtr);
            try {
                ERROR_DEINIT.invokeExact(errPtr);
            } catch (Throwable t) {
                // ignore cleanup error
            }
            throw new LibSqlException(context + ": " + message);
        }
    }

    private static String extractErrorMessage(MemorySegment errPtr) {
        try {
            MemorySegment msgPtr = (MemorySegment) ERROR_MESSAGE.invokeExact(errPtr);
            if (MemorySegment.NULL.equals(msgPtr) || msgPtr.address() == 0) {
                return "unknown error";
            }
            return msgPtr.reinterpret(1024).getString(0);
        } catch (Throwable t) {
            return "failed to extract error message: " + t.getMessage();
        }
    }
}
