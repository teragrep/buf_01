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

import com.teragrep.buf_01.buffer.lease.Lease;
import com.teragrep.buf_01.buffer.lease.PoolableLease;
import com.teragrep.buf_01.buffer.pool.DebugMemorySegmentLeasePool;
import com.teragrep.poj_01.pool.Pool;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class DebugLeasePoolTest {

    @Test
    void testPool() {
        final Pool<PoolableLease<MemorySegment>> memorySegmentLeasePool = new DebugMemorySegmentLeasePool();

        final Lease<MemorySegment> lease = memorySegmentLeasePool.get();

        Assertions.assertFalse(lease.isStub());

        Assertions.assertFalse(lease.hasZeroRefs()); // initially 1 refs

        Assertions.assertEquals(1, lease.refs()); // check initial 1 ref

        final Lease<MemorySegment> slice = lease.sliced(2);

        Assertions.assertEquals(2, lease.refs());

        lease.leasedObject().set(ValueLayout.JAVA_BYTE, 0, (byte) 'x');

        Assertions.assertEquals((byte) 'x', lease.leasedObject().get(ValueLayout.JAVA_BYTE, 0));

        Assertions.assertEquals(2, lease.refs());

        Assertions.assertDoesNotThrow(slice::close);

        Assertions.assertFalse(lease.hasZeroRefs()); // initial ref must be still in place

        Assertions.assertEquals(1, lease.refs()); // initial ref must be still in

        Assertions.assertDoesNotThrow(lease::close); // removes initial ref

        Assertions.assertTrue(lease.hasZeroRefs()); // no refs, so isTerminated=true

        memorySegmentLeasePool.close();
        Assertions.assertThrows(IllegalStateException.class, lease::leasedObject);
    }

    @Test
    void testDoesNotReturnTheSameInstance() {
        try (final Pool<PoolableLease<MemorySegment>> memorySegmentLeasePool = new DebugMemorySegmentLeasePool()) {
            final PoolableLease<MemorySegment> lease = memorySegmentLeasePool.get();

            memorySegmentLeasePool.offer(lease);

            final PoolableLease<MemorySegment> lease2 = memorySegmentLeasePool.get();

            memorySegmentLeasePool.offer(lease2);

            Assertions.assertNotEquals(lease, lease2);
        }
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(DebugMemorySegmentLeasePool.class).verify();
    }
}
