package com.teragrep.buf_01.buffer.pool;

import com.teragrep.buf_01.buffer.lease.PoolableLease;
import com.teragrep.poj_01.pool.Pool;

import java.lang.foreign.MemorySegment;
import java.util.LinkedList;
import java.util.List;

public final class LeaseMultiGet implements MultiGet<PoolableLease<MemorySegment>> {
    private final Pool<PoolableLease<MemorySegment>> leasePool;

    public LeaseMultiGet(final Pool<PoolableLease<MemorySegment>> leasePool) {
        this.leasePool = leasePool;
    }

    @Override
    public List<PoolableLease<MemorySegment>> get(final long count) {
        long currentSize = 0;
        final List<PoolableLease<MemorySegment>> leases = new LinkedList<>();
        while (currentSize < count) {
            final PoolableLease<MemorySegment> lease = leasePool.get();
            if (lease == null || lease.isStub()) {
                throw new IllegalStateException("Got stub or null lease from pool!");
            }
            leases.add(lease);
            currentSize += lease.leasedObject().byteSize();
        }
        return leases;
    }
}
