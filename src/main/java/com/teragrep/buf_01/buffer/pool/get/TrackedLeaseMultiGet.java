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
package com.teragrep.buf_01.buffer.pool.get;

import com.teragrep.buf_01.buffer.lease.OpenableLease;
import com.teragrep.buf_01.buffer.lease.TrackedLease;
import com.teragrep.buf_01.buffer.lease.TrackedMemorySegmentLease;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TrackedLeaseMultiGet implements MultiGet<TrackedLease<MemorySegment>> {

    private final MultiGet<OpenableLease<MemorySegment>> origin;

    public TrackedLeaseMultiGet(final MultiGet<OpenableLease<MemorySegment>> origin) {
        this.origin = origin;
    }

    @Override
    public List<TrackedLease<MemorySegment>> getAsList(final long count) {
        final List<OpenableLease<MemorySegment>> leases = origin.getAsList(count);
        final List<TrackedLease<MemorySegment>> rv = new ArrayList<>(leases.size());

        leases.forEach(lease -> {
            rv.add(new TrackedMemorySegmentLease(lease));
        });

        return rv;
    }

    @Override
    public TrackedLease<MemorySegment>[] getAsArray(final long count) {
        final OpenableLease<MemorySegment>[] leases = origin.getAsArray(count);
        final int len = leases.length;
        final TrackedLease<MemorySegment>[] rv = new TrackedMemorySegmentLease[len];

        for (int i = 0; i < len; i++) {
            rv[i] = new TrackedMemorySegmentLease(leases[i]);
        }

        return rv;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TrackedLeaseMultiGet that = (TrackedLeaseMultiGet) o;
        return Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(origin);
    }
}
