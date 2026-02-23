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
package com.teragrep.buf_01.buffer;

import com.teragrep.buf_01.buffer.lease.MemorySegmentLeaseStub;
import com.teragrep.buf_01.buffer.lease.PoolableLease;
import com.teragrep.buf_01.buffer.pool.LeaseMultiGet;
import com.teragrep.buf_01.buffer.pool.MultiGet;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.poj_01.pool.Pool;
import com.teragrep.poj_01.pool.UnboundPool;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

final class LeaseMultiGetTest {

    @Test
    @DisplayName(value = "Supplies segments with size=5, take 5 bytes. Results in 1x5 bytes buffer.")
    void testTakeOneLease() {
        final Pool<PoolableLease<MemorySegment>> pool = new UnboundPool<>(
                new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5),
                new MemorySegmentLeaseStub()
        );
        final MultiGet<PoolableLease<MemorySegment>> multiGet = new LeaseMultiGet(pool);
        final List<PoolableLease<MemorySegment>> leases = multiGet.get(5);

        Assertions.assertEquals(1, leases.size());
        Assertions.assertEquals(5, leases.getFirst().leasedObject().byteSize());
    }

    @Test
    @DisplayName(value = "Supplies segments with size=3, take 5 bytes. Results in 2x3 bytes buffers.")
    void testTakeTwoLeases() {
        final Pool<PoolableLease<MemorySegment>> pool = new UnboundPool<>(
                new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 3),
                new MemorySegmentLeaseStub()
        );
        final MultiGet<PoolableLease<MemorySegment>> multiGet = new LeaseMultiGet(pool);
        final List<PoolableLease<MemorySegment>> leases = multiGet.get(5);

        Assertions.assertEquals(2, leases.size());
        Assertions.assertEquals(3, leases.getFirst().leasedObject().byteSize());
        Assertions.assertEquals(3, leases.getLast().leasedObject().byteSize());
    }

    @Test
    @DisplayName(value = "Supplies segments with size=3, take 10 bytes. Results in 4x3 bytes buffers.")
    void testTakeFourLeases() {
        final Pool<PoolableLease<MemorySegment>> pool = new UnboundPool<>(
                new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 3),
                new MemorySegmentLeaseStub()
        );
        final MultiGet<PoolableLease<MemorySegment>> multiGet = new LeaseMultiGet(pool);
        final List<PoolableLease<MemorySegment>> leases = multiGet.get(10);

        Assertions.assertEquals(4, leases.size());
        Assertions.assertEquals(4, leases.stream().filter(l -> 3 == l.leasedObject().byteSize()).count());
    }

    @Test
    @DisplayName(value = "Supplies segments with size=3, take 0 bytes. Results in no buffers.")
    void testTakeZeroLeases() {
        final Pool<PoolableLease<MemorySegment>> pool = new UnboundPool<>(
                new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 3),
                new MemorySegmentLeaseStub()
        );
        final MultiGet<PoolableLease<MemorySegment>> multiGet = new LeaseMultiGet(pool);
        final List<PoolableLease<MemorySegment>> leases = multiGet.get(0);

        Assertions.assertEquals(0, leases.size());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(LeaseMultiGet.class).verify();
    }
}
