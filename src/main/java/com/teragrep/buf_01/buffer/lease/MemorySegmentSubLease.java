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
import java.util.Objects;
import java.util.concurrent.Phaser;

/**
 * Decorator for {@link MemorySegmentContainer} that zeroes the encapsulated {@link MemorySegment} on {@link #close()}.
 * Starts with one initial reference. Internally uses a {@link NonTerminatingPhaser} to track reference count in a
 * non-blocking way. Used for sub-leases, which are not to be returned to a {@link com.teragrep.poj_01.pool.Pool}, which
 * is why it uses a {@link Lease} instead of a {@link PoolableLease}.
 */
public final class MemorySegmentSubLease implements Lease<MemorySegment> {

    private final MemorySegmentContainer memorySegmentContainer;
    private final Phaser phaser;

    public MemorySegmentSubLease(MemorySegmentContainer bc, Phaser parent) {
        this.memorySegmentContainer = bc;
        // initial registered parties set to 1
        this.phaser = new NonTerminatingPhaser(parent, 1);
    }

    @Override
    public Lease<MemorySegment> sliceAt(final long offset) {
        if (phaser.getRegisteredParties() == 0) {
            throw new IllegalStateException("Cannot provide slice, ref count = 0 !");
        }

        return new MemorySegmentSubLease(
                new MemorySegmentContainerImpl(
                        memorySegmentContainer.id(),
                        memorySegmentContainer.memorySegment().asSlice(offset)
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
        if (phaser.getRegisteredParties() > 1) {
            throw new IllegalStateException(
                    "Cannot close sub-lease, has <" + phaser.getRegisteredParties() + "> references."
            );
        }

        if (phaser.arriveAndDeregister() < 0) {
            throw new IllegalStateException("Cannot close lease, MemorySegmentLease phaser was already terminated!");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MemorySegmentSubLease that = (MemorySegmentSubLease) o;
        return Objects.equals(memorySegmentContainer, that.memorySegmentContainer)
                && Objects.equals(phaser, that.phaser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memorySegmentContainer, phaser);
    }
}
