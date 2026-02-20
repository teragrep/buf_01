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
package com.teragrep.buf_01.buffer.pool;

import com.teragrep.buf_01.buffer.container.MemorySegmentContainer;
import com.teragrep.buf_01.buffer.lease.Lease;
import com.teragrep.buf_01.buffer.lease.MemorySegmentLeaseStub;
import com.teragrep.buf_01.buffer.lease.PoolableLease;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.buf_01.buffer.supply.MemorySegmentLeaseSupplier;
import com.teragrep.poj_01.pool.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Non-blocking pool for {@link MemorySegmentContainer} objects. All objects in the pool are
 * {@link ByteBuffer#clear()}ed before returning to the pool by {@link Lease}.
 */
public final class DebugMemorySegmentLeasePool implements Pool<PoolableLease<MemorySegment>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugMemorySegmentLeasePool.class);

    private final Map<Long, MemorySegmentLeaseSupplier> suppliers = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<PoolableLease<MemorySegment>> queue;

    private final PoolableLease<MemorySegment> leaseStub;
    private final AtomicBoolean close;

    private final int segmentSize;

    private final AtomicLong bufferId;

    private final Lock lock;

    public DebugMemorySegmentLeasePool() {
        this.segmentSize = 4096;
        this.queue = new ConcurrentLinkedQueue<>();
        this.leaseStub = new MemorySegmentLeaseStub();
        this.close = new AtomicBoolean();
        this.bufferId = new AtomicLong();
        this.lock = new ReentrantLock();
    }

    public PoolableLease<MemorySegment> get() {
        if (close.get()) {
            return new MemorySegmentLeaseStub();
        }
        // get or create
        PoolableLease<MemorySegment> lease = queue.poll();
        if (lease == null) {
            // if queue is empty or stub object, create a new BufferContainer and BufferLease.
            final MemorySegmentLeaseSupplier supplier = new ArenaMemorySegmentLeaseSupplier(
                    Arena.ofShared(),
                    segmentSize,
                    bufferId
            );
            lease = supplier.get();
            suppliers.put(bufferId.get(), supplier);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("returning bufferLease id <{}> with refs <{}>", lease.id(), lease.refs());
        }

        return lease;
    }

    /**
     * return {@link MemorySegmentContainer} into the pool.
     * 
     * @param lease {@link MemorySegmentContainer} from {@link Lease} which has been {@link ByteBuffer#clear()}ed.
     */
    @Override
    public void offer(PoolableLease<MemorySegment> lease) {
        // debug pool, instead of returning to pool arena is closed and memorySegment is discarded.
        if (!lease.isStub()) {
            MemorySegmentLeaseSupplier supplier = suppliers.get(lease.id());
            supplier.close(); // closes Arena
        }

        if (close.get()) {
            LOGGER.debug("closing in offer");
            while (!queue.isEmpty()) {
                if (lock.tryLock()) {
                    queue.clear();
                    lock.unlock();
                }
                else {
                    break;
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            long queueSegments = queue.size();
            long queueBytes = queueSegments * segmentSize;
            LOGGER.debug("offer complete, queueSegments <{}>, queueBytes <{}>", queueSegments, queueBytes);
        }
    }

    /**
     * Closes the {@link DebugMemorySegmentLeasePool}, deallocating currently residing {@link MemorySegmentContainer}s
     * and future ones when returned.
     */
    public void close() {
        LOGGER.debug("close called");
        close.set(true);

        // close all that are in the pool right now
        offer(leaseStub);
    }
}
