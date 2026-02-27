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
import com.teragrep.buf_01.buffer.lease.MemorySegmentSubLease;
import com.teragrep.buf_01.buffer.lease.PoolableLease;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

final class LeaseTest {

    @Test
    void testOneLease() {
        final PoolableLease<MemorySegment> rootLease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))),
                new PoolFake()
        );

        // refs starts at 0
        Assertions.assertEquals(0L, rootLease.refs());

        // open, then refs=1
        rootLease.open();
        Assertions.assertEquals(1L, rootLease.refs());
        Assertions.assertFalse(rootLease.hasZeroRefs());

        // remove ref
        Assertions.assertDoesNotThrow(rootLease::close);

        // should be 0
        Assertions.assertEquals(0L, rootLease.refs());

        // root lease is in terminated state, since refs=0
        Assertions.assertTrue(rootLease.hasZeroRefs());

        // cannot access leasedObject when refs=0
        Assertions.assertThrows(IllegalStateException.class, rootLease::leasedObject);
    }

    @Test
    void testSubLeaseCreateAndRemove() {
        final PoolableLease<MemorySegment> rootLease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))),
                new PoolFake()
        );

        // refs starts at 0
        Assertions.assertEquals(0L, rootLease.refs());

        // open, then refs=1
        rootLease.open();
        Assertions.assertEquals(1L, rootLease.refs());
        Assertions.assertFalse(rootLease.hasZeroRefs());

        // slice
        final Lease<MemorySegment> slice = rootLease.sliceAt(512);

        Assertions.assertEquals(2L, rootLease.refs());

        Assertions.assertDoesNotThrow(slice::close);
        Assertions.assertEquals(0L, slice.refs());
        Assertions.assertTrue(slice.hasZeroRefs());

        Assertions.assertEquals(1L, rootLease.refs());
        Assertions.assertFalse(rootLease.hasZeroRefs());
    }

    @Test
    void testSubLeaseCreateAndRemoveParentRefs() {
        final PoolableLease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))),
                new PoolFake()
        );

        // refs starts at 0
        Assertions.assertEquals(0L, lease.refs());

        // open, then refs=1
        lease.open();
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.hasZeroRefs());

        // slice
        final Lease<MemorySegment> slice = lease.sliceAt(512);
        Assertions.assertEquals(1L, slice.refs());
        Assertions.assertEquals(2L, lease.refs());

        Assertions.assertDoesNotThrow(() -> {
            slice.close();
            lease.close();
        });

        // Parent lease terminated at close with zero refs
        Assertions.assertTrue(lease.hasZeroRefs());
        Assertions.assertEquals(0, lease.refs());

        // Slice is also terminated, since refs=0
        Assertions.assertTrue(slice.hasZeroRefs());
        Assertions.assertEquals(0, slice.refs());
    }

    @Test
    void testCloseParentLeaseWithSlice() {
        final PoolableLease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))),
                new PoolFake()
        );

        // refs starts at 0
        Assertions.assertEquals(0L, lease.refs());

        // open, then refs=1
        lease.open();
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.hasZeroRefs());

        // slice
        final Lease<MemorySegment> slice = lease.sliceAt(512);
        Assertions.assertEquals(1L, slice.refs());
        Assertions.assertEquals(2L, lease.refs());

        final IllegalStateException ise = Assertions.assertThrows(IllegalStateException.class, lease::close);

        Assertions.assertEquals("Cannot close lease, has <2> references.", ise.getMessage());
        // Parent lease not terminated, with 2 refs. One for itself and one for the slice.
        Assertions.assertFalse(lease.hasZeroRefs());
        Assertions.assertEquals(2, lease.refs());

        // Slice is not terminated, since refs=1
        Assertions.assertFalse(slice.hasZeroRefs());
        Assertions.assertEquals(1, slice.refs());
    }

    @Test
    void testCannotOpenLeaseTwice() {
        final PoolableLease<MemorySegment> lease = new MemorySegmentLease(
                new MemorySegmentContainerImpl(0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))),
                new PoolFake()
        );

        // refs starts at 0
        Assertions.assertEquals(0L, lease.refs());

        // open, then refs=1
        lease.open();
        Assertions.assertEquals(1L, lease.refs());

        // try opening again, should throw an exception and not change ref count
        Assertions.assertThrows(IllegalStateException.class, lease::open);
        Assertions.assertEquals(1L, lease.refs());
    }

    @Test
    void testEqualsContractForParentLease() {
        EqualsVerifier
                .forClass(MemorySegmentLease.class)
                .withPrefabValues(Phaser.class, new Phaser(1), new Phaser(1))
                .verify();
    }

    @Test
    void testEqualsContractForSubLease() {
        EqualsVerifier
                .forClass(MemorySegmentSubLease.class)
                .withPrefabValues(Phaser.class, new Phaser(1), new Phaser(1))
                .verify();
    }
}
