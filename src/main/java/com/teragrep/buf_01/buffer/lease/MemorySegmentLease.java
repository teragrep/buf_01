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
package com.teragrep.buf_01.buffer.lease;

import com.teragrep.buf_01.buffer.container.MemorySegmentContainer;
import com.teragrep.buf_01.buffer.container.MemorySegmentContainerImpl;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.Phaser;

/**
 * Decorator for {@link MemorySegmentContainer} that automatically clears (frees) the encapsulated {@link ByteBuffer}
 * and returns the {@link MemorySegmentContainer} to {@link com.teragrep.poj_01.pool.Pool} when reference count hits
 * zero. Starts with one initial reference. Internally uses a {@link Phaser} to track reference count in a non-blocking
 * way.
 */
public final class MemorySegmentLease implements PoolableLease<MemorySegment> {

    private final MemorySegmentContainer memorySegmentContainer;
    private final Phaser phaser;

    public MemorySegmentLease(MemorySegmentContainer bc) {
        this.memorySegmentContainer = bc;
        // initial registered parties set to 1
        this.phaser = new NonTerminatingPhaser<>(1);
    }

    @Override
    public Lease<MemorySegment> sliced(final long committedOffset) {
        return new MemorySegmentSubLease(
                new MemorySegmentContainerImpl(
                        memorySegmentContainer.id(),
                        memorySegmentContainer.memorySegment().asSlice(committedOffset)
                ),
                phaser
        );
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
    public MemorySegment leasedObject() {
        if (phaser.getRegisteredParties() == 0) {
            throw new IllegalStateException(
                    "Cannot return wrapped MemorySegment, MemorySegmentLease phaser was already terminated!"
            );
        }
        return memorySegmentContainer.memorySegment();
    }

    @Override
    public boolean hasZeroRefs() {
        return phaser.getRegisteredParties() == 0;
    }

    @Override
    public boolean isStub() {
        return memorySegmentContainer.isStub();
    }

    @Override
    public void close() {
        if (phaser.getParent() == null && phaser.getRegisteredParties() == 1) {
            leasedObject().fill((byte) 0);
        }

        if (phaser.arriveAndDeregister() < 0) {
            throw new IllegalStateException("Cannot close lease, MemorySegmentLease phaser was already terminated!");
        }
    }
}
