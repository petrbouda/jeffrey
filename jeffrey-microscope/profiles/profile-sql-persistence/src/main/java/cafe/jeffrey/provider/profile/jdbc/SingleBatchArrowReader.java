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

package cafe.jeffrey.provider.profile.jdbc;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * One-shot {@link ArrowReader} over an already populated {@link VectorSchemaRoot}.
 * Serves the root exactly once and then reports end-of-stream. Used to export a single
 * in-memory batch through the Arrow C Data Interface
 * ({@link org.apache.arrow.c.Data#exportArrayStream}).
 *
 * <p>The {@link VectorSchemaRoot} is owned by the caller — this reader never closes it.
 */
class SingleBatchArrowReader extends ArrowReader {

    private final VectorSchemaRoot root;
    private boolean batchServed;

    SingleBatchArrowReader(BufferAllocator allocator, VectorSchemaRoot root) {
        super(allocator);
        this.root = root;
    }

    @Override
    public VectorSchemaRoot getVectorSchemaRoot() {
        return root;
    }

    @Override
    public boolean loadNextBatch() {
        if (batchServed) {
            return false;
        }
        batchServed = true;
        return true;
    }

    @Override
    public long bytesRead() {
        return 0;
    }

    @Override
    protected void closeReadSource() {
        // The VectorSchemaRoot is owned and closed by the caller.
    }

    @Override
    protected Schema readSchema() {
        return root.getSchema();
    }
}
