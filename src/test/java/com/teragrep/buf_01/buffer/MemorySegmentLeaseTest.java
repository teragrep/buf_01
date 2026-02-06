package com.teragrep.buf_01.buffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

final class MemorySegmentLeaseTest {

    @Test
    void testOneLease() {
        final MemorySegmentLease lease = new MemorySegmentLeaseImpl(
                new MemorySegmentContainerImpl(
                        0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))
                ), new MemorySegmentLeasePool()
        );

        // refs starts at 1
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());
        // add ref
        lease.addRef();

        // should be 2
        Assertions.assertEquals(2L, lease.refs());

        // remove ref
        lease.removeRef();

        // should be 1 again
        Assertions.assertEquals(1L, lease.refs());

        // remove last ref
        lease.removeRef();

        Assertions.assertEquals(0L, lease.refs());
        Assertions.assertTrue(lease.isTerminated());
        Assertions.assertThrows(IllegalStateException.class, lease::memorySegment);
    }

    @Test
    void testSubLeaseCreateAndRemove() {
        final MemorySegmentLease lease = new MemorySegmentLeaseImpl(
                new MemorySegmentContainerImpl(
                        0L, MemorySegment.ofBuffer(ByteBuffer.allocateDirect(1024))
                ), new MemorySegmentLeasePool()
        );

        // refs starts at 1
        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());

        // slice
        final MemorySegmentLease slice = lease.sliced(512);

        Assertions.assertEquals(2L, lease.refs());

        slice.removeRef();
        Assertions.assertEquals(0L, slice.refs());
        Assertions.assertTrue(slice.isTerminated());

        Assertions.assertEquals(1L, lease.refs());
        Assertions.assertFalse(lease.isTerminated());
    }
}
