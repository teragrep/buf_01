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

import com.teragrep.buf_01.buffer.lease.MemorySegmentLeaseStub;
import com.teragrep.buf_01.buffer.lease.OpenableLease;
import com.teragrep.buf_01.buffer.lease.TrackedLease;
import com.teragrep.buf_01.buffer.lease.TrackedMemorySegmentLease;
import com.teragrep.buf_01.buffer.pool.OpeningPool;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.poj_01.pool.Pool;
import com.teragrep.poj_01.pool.UnboundPool;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

public final class TrackedLeaseMultiGetTest {

    @Test
    void testDecorating() {
        final Pool<OpenableLease<MemorySegment>> pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final MultiGet<OpenableLease<MemorySegment>> multiGet = new LeaseMultiGet(pool);
        final MultiGet<TrackedLease<MemorySegment>> trackedMultiGet = new TrackedLeaseMultiGet(multiGet);

        final TrackedLease<MemorySegment>[] leases = trackedMultiGet.getAsArray(5);
        final List<TrackedLease<MemorySegment>> leasesList = trackedMultiGet.getAsList(5);

        Assertions.assertEquals(1, leases.length);
        Assertions.assertEquals(5, leases[0].leasedObject().byteSize());
        Assertions.assertEquals(TrackedMemorySegmentLease.class, leases[0].getClass());
        Assertions.assertEquals(0L, leases[0].currentPosition());
        Assertions.assertEquals(-1L, leases[0].currentLimit());

        Assertions.assertEquals(1, leasesList.size());
        Assertions.assertEquals(5, leasesList.getFirst().leasedObject().byteSize());
        Assertions.assertEquals(TrackedMemorySegmentLease.class, leasesList.getFirst().getClass());
        Assertions.assertEquals(0L, leasesList.getFirst().currentPosition());
        Assertions.assertEquals(-1L, leasesList.getFirst().currentLimit());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(TrackedLeaseMultiGet.class).verify();
    }
}
