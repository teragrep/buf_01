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
import com.teragrep.buf_01.buffer.lease.MemorySegmentLease;
import com.teragrep.buf_01.buffer.lease.MemorySegmentLeaseStub;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.buf_01.buffer.supply.MemorySegmentLeaseSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Non-blocking pool for {@link MemorySegmentContainer} objects. All objects in the pool are
 * {@link ByteBuffer#clear()}ed before returning to the pool by {@link MemorySegmentLease}.
 */
public final class MemorySegmentLeasePoolImpl implements CountablePool<MemorySegmentLease> {
    // TODO create tests

    private static final Logger LOGGER = LoggerFactory.getLogger(MemorySegmentLeasePoolImpl.class);

    private final MemorySegmentLeaseSupplier memorySegmentLeaseSupplier;

    private final ConcurrentLinkedQueue<MemorySegmentLease> queue;

    private final MemorySegmentLease memorySegmentLeaseStub;
    private final AtomicBoolean close;

    private final int segmentSize;

    private final Lock lock;

    public MemorySegmentLeasePoolImpl() {
        this.segmentSize = 4096;
        this.memorySegmentLeaseSupplier = new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), segmentSize, this);
        this.queue = new ConcurrentLinkedQueue<>();
        this.memorySegmentLeaseStub = new MemorySegmentLeaseStub();
        this.close = new AtomicBoolean();
        this.lock = new ReentrantLock();
    }

    private MemorySegmentLease take() {
        // get or create
        MemorySegmentLease memorySegmentLease = queue.poll();
        if (memorySegmentLease == null || memorySegmentLease.isStub()) {
            // if queue is empty or stub object, create a new MemorySegmentLease
            memorySegmentLease = memorySegmentLeaseSupplier.get();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER
                    .debug(
                            "returning bufferLease id <{}> with refs <{}>", memorySegmentLease.id(),
                            memorySegmentLease.refs()
                    );
        }

        return memorySegmentLease;
    }

    /**
     * @param size minimum size of the {@link MemorySegmentLease}s requested.
     * @return list of {@link MemorySegmentLease}s meeting or exceeding the size requested.
     */
    @Override
    public List<MemorySegmentLease> take(long size) {
        if (close.get()) {
            return Collections.singletonList(memorySegmentLeaseStub);
        }

        LOGGER.debug("requesting take with size <{}>", size);
        long currentSize = 0;
        List<MemorySegmentLease> memorySegmentLeases = new LinkedList<>();
        while (currentSize < size) {
            MemorySegmentLease memorySegmentLease = take();
            memorySegmentLeases.add(memorySegmentLease);
            currentSize = currentSize + memorySegmentLease.memorySegment().byteSize();

        }
        return memorySegmentLeases;

    }

    /**
     * return {@link MemorySegmentContainer} into the pool.
     * 
     * @param memorySegmentContainer {@link MemorySegmentContainer} from {@link MemorySegmentLease} which has been
     *                               {@link ByteBuffer#clear()}ed.
     */
    @Override
    public void offer(MemorySegmentLease memorySegmentContainer) {
        // Add buffer back to pool if it is not a stub object
        if (!memorySegmentContainer.isStub()) {
            queue.add(memorySegmentContainer);
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
     * Closes the {@link MemorySegmentLeasePoolImpl}, deallocating currently residing {@link MemorySegmentContainer}s
     * and future ones when returned.
     */
    @Override
    public void close() {
        LOGGER.debug("close called");
        close.set(true);

        // close all that are in the pool right now
        offer(memorySegmentLeaseStub);

        // close supplier
        memorySegmentLeaseSupplier.close();

    }

    /**
     * Estimate the pool size, due to non-blocking nature of the pool, this is only an estimate.
     * 
     * @return estimate of the pool size, counting only the residing buffers.
     */
    @Override
    public int estimatedSize() {
        return queue.size();
    }
}
