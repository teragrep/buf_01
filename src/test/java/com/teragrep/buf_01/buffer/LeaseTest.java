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

import com.teragrep.buf_01.buffer.container.MemorySegmentContainerImpl;
import com.teragrep.buf_01.buffer.lease.Lease;
import com.teragrep.buf_01.buffer.lease.MemorySegmentLease;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

final class LeaseTest {

    @Test
    void testOneLease() {
        final Lease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024)))
        );

        // refs starts at 1
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());

        // remove ref
        Assertions.assertDoesNotThrow(lease::close);

        // should be 0
        Assertions.assertEquals(0L, lease.refs());
        // FIXME: Assertions.assertTrue(lease.isTerminated());
        Assertions.assertThrows(IllegalStateException.class, lease::leasedObject);
    }

    @Test
    void testSubLeaseCreateAndRemove() {
        final Lease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024)))
        );

        // refs starts at 1
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());

        // slice
        final Lease<MemorySegment> slice = lease.sliced(512);

        Assertions.assertEquals(2L, lease.refs());

        Assertions.assertDoesNotThrow(slice::close);
        Assertions.assertEquals(0L, slice.refs());
        //FIXME: Assertions.assertTrue(slice.isTerminated());

        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());
    }

    @Test
    void testSubLeaseCreateAndRemoveParentRefs() {
        final Lease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024)))
        );

        // refs starts at 1
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());

        // slice
        final Lease<MemorySegment> slice = lease.sliced(512);
        Assertions.assertEquals(1L, slice.refs());
        Assertions.assertEquals(2L, lease.refs());

        Assertions.assertDoesNotThrow(() -> {
            slice.close();
            lease.close();
        });

        // Parent lease should not be terminated even with registeredParties = 0
        Assertions.assertFalse(lease.isTerminated());
        Assertions.assertEquals(0, lease.refs());
        //FIXME: Assertions.assertTrue(slice.isTerminated());
        Assertions.assertEquals(0, slice.refs());
    }
}
