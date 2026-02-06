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

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

/**
 * Decorator for {@link MemorySegmentContainer} that automatically clears (frees) the encapsulated {@link ByteBuffer} and
 * returns the {@link MemorySegmentContainer} to {@link MemorySegmentLeasePool} when reference count hits zero. Starts with one
 * initial reference. Internally uses a {@link Phaser} to track reference count in a non-blocking way.
 */
public final class MemorySegmentLeaseImpl implements MemorySegmentLease {

    private final MemorySegmentContainer memorySegmentContainer;
    private final Phaser phaser;
    private final MemorySegmentLeasePool memorySegmentLeasePool;
    private final List<MemorySegmentLease> subLeases;
    private final MemorySegmentLease parentLease;

    public MemorySegmentLeaseImpl(MemorySegmentContainer bc, MemorySegmentLeasePool memorySegmentLeasePool, MemorySegmentLease parentLease) {
        this.memorySegmentContainer = bc;
        this.memorySegmentLeasePool = memorySegmentLeasePool;

        // initial registered parties set to 1
        this.phaser = new ClearingPhaser(1);
        this.subLeases = new ArrayList<>();
        this.parentLease = parentLease;
    }

    public MemorySegmentLeaseImpl(MemorySegmentContainer bc, MemorySegmentLeasePool memorySegmentLeasePool) {
        this(bc, memorySegmentLeasePool, new MemorySegmentLeaseStub());
    }

    @Override
    public MemorySegmentLease sliced(final long committedOffset) {
        final MemorySegmentLease newSubLease =  new MemorySegmentLeaseImpl(new MemorySegmentContainerImpl(
                memorySegmentContainer.id(),
                memorySegmentContainer.memorySegment().asSlice(committedOffset)
        ), memorySegmentLeasePool, this);

        subLeases.add(newSubLease);

        addRef();

        return newSubLease;
    }

    @Override
    public boolean isParentLease() {
        return parentLease.isStub();
    }

    @Override
    public long id() {
        return memorySegmentContainer.id();
    }

    @Override
    public long refs() {
        // initial number of registered parties is 1
        return phaser.getRegisteredParties();
    }

    @Override
    public MemorySegment memorySegment() {
        if (phaser.isTerminated()) {
            throw new IllegalStateException(
                    "Cannot return wrapped MemorySegment, MemorySegmentLease phaser was already terminated!"
            );
        }
        return memorySegmentContainer.memorySegment();
    }

    @Override
    public void addRef() {
        if (phaser.register() < 0) {
            throw new IllegalStateException("Cannot add reference, MemorySegmentLease phaser was already terminated!");
        }
    }

    @Override
    public void removeRef() {
        if (phaser.arriveAndDeregister() < 0) {
            throw new IllegalStateException("Cannot remove reference, MemorySegmentLease phaser was already terminated!");
        }

        if (phaser.getRegisteredParties() == 0 && !parentLease.isStub()) {
            parentLease.removeRef();
        }
    }

    @Override
    public boolean isTerminated() {
        return phaser.isTerminated();
    }

    @Override
    public boolean isStub() {
        return memorySegmentContainer.isStub();
    }

    /**
     * Phaser that clears the buffer on termination (registeredParties=0)
     */
    private class ClearingPhaser extends Phaser {

        public ClearingPhaser(int initialParties) {
            super(initialParties);
        }

        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            final boolean shouldTerminate;
            if (registeredParties == 0) {
                memorySegment().fill((byte) 0);
                memorySegmentLeasePool.internalOffer(memorySegmentContainer);
                shouldTerminate = true;
            } else {
                shouldTerminate = false;
            }

            return shouldTerminate;
        }
    }

}
