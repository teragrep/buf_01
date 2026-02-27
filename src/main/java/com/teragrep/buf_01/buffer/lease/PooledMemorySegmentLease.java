/*
 * Teragrep Buffer Library for Java
 * Copyright (C) 2026 Suomen Kanuuna Oy
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.buf_01.buffer.lease;

import com.teragrep.poj_01.pool.Pool;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class PooledMemorySegmentLease implements PoolableLease<MemorySegment> {

    private final PoolableLease<MemorySegment> origin;
    private final Pool<PoolableLease<MemorySegment>> pool;

    public PooledMemorySegmentLease(
            final PoolableLease<MemorySegment> origin,
            final Pool<PoolableLease<MemorySegment>> pool
    ) {
        this.origin = origin;
        this.pool = pool;
    }

    @Override
    public long id() {
        return origin.id();
    }

    @Override
    public long refs() {
        return origin.refs();
    }

    @Override
    public MemorySegment leasedObject() {
        return origin.leasedObject();
    }

    @Override
    public boolean hasZeroRefs() {
        return origin.hasZeroRefs();
    }

    @Override
    public boolean isStub() {
        return origin.isStub();
    }

    @Override
    public Lease<MemorySegment> sliceAt(final long offset) {
        return origin.sliceAt(offset);
    }

    @Override
    public void close() throws IOException {
        origin.close();
        pool.offer(origin);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PooledMemorySegmentLease that = (PooledMemorySegmentLease) o;
        return Objects.equals(origin, that.origin) && Objects.equals(pool, that.pool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, pool);
    }
}
