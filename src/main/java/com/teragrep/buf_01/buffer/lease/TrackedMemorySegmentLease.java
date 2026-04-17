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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// spotless:off
/**
 * @class TrackedMemorySegmentLease
 * @brief Decorates a MemorySegmentLease, providing position, limit and iteration methods.
 *
 * @responsibilities
 * - Decorates MemorySegmentLease, providing position, limit and iteration.
 *
 * @collaborators
 * - MemorySegmentLease
 *
 * @startuml
 * class TrackedMemorySegmentLease {
 * + id();
 * + refs();
 * + leasedObject();
 * + hasZeroRefs();
 * + sliceAt(offset);
 * + isStub();
 * + close();
 * + hasNext();
 * + next();
 * + write(byte);
 * + currentPosition();
 * + position(pos);
 * + currentLimit();
 * + limit(lim);
 * }
 *
 * TrackedMemorySegmentLease --> MemorySegmentLease : decorates
 * TrackedMemorySegmentLease --> TrackedLease : implements
 *
 * note right of TrackedMemorySegmentLease
 * Responsibilities:
 * - Decorates MemorySegmentLease, providing position, limit and iteration.
 *
 * Collaborators:
 * - MemorySegmentLease
 * end note
 *
 * @enduml
*/
// spotless:on
public final class TrackedMemorySegmentLease implements TrackedLease<MemorySegment> {

    private final Lease<MemorySegment> origin;
    // AtomicReferenceFieldUpdater requires boxed type
    private volatile Long currentOffset;
    private volatile Long limit;

    private static final AtomicReferenceFieldUpdater<TrackedMemorySegmentLease, Long> offsetUpdater = AtomicReferenceFieldUpdater
            .newUpdater(TrackedMemorySegmentLease.class, Long.class, "currentOffset");
    private static final AtomicReferenceFieldUpdater<TrackedMemorySegmentLease, Long> limitUpdater = AtomicReferenceFieldUpdater
            .newUpdater(TrackedMemorySegmentLease.class, Long.class, "limit");

    public TrackedMemorySegmentLease(final Lease<MemorySegment> origin) {
        this(origin, 0L);
    }

    public TrackedMemorySegmentLease(final Lease<MemorySegment> origin, final long currentOffset) {
        this(origin, currentOffset, -1L);
    }

    public TrackedMemorySegmentLease(final Lease<MemorySegment> origin, final long currentOffset, final long limit) {
        this.origin = origin;
        this.currentOffset = currentOffset;
        this.limit = limit;
    }

    @Override
    public long id() {
        return origin.id();
    }

    @Override
    public long refs() {
        return origin.refs();
    }

    @Override
    public MemorySegment leasedObject() {
        return origin.leasedObject();
    }

    @Override
    public boolean hasZeroRefs() {
        return origin.hasZeroRefs();
    }

    @Override
    public TrackedLease<MemorySegment> sliceAt(final long offset) {
        return new TrackedMemorySegmentLease(origin.sliceAt(offset));
    }

    @Override
    public boolean isStub() {
        return origin.isStub();
    }

    @Override
    public void close() throws Exception {
        origin.close();
    }

    @Override
    public boolean hasNext() {
        final boolean rv;
        if (limit == -1) {
            // limit not set, ignore
            rv = currentOffset < origin.leasedObject().byteSize();
        }
        else {
            rv = currentOffset < Math.min(limit, origin.leasedObject().byteSize());
        }
        return rv;
    }

    @Override
    public byte next() {
        if (!hasNext()) {
            throw new IndexOutOfBoundsException("Reached end of segment or limit, cannot provide next byte");
        }
        final long nextIndex = offsetUpdater.getAndAccumulate(this, 1L, Long::sum);

        return origin.leasedObject().get(ValueLayout.JAVA_BYTE, nextIndex);
    }

    @Override
    public void write(final byte b) {
        if (!hasNext()) {
            throw new IndexOutOfBoundsException("Reached end of segment or limit, cannot write to next byte");
        }

        final long nextIndex = offsetUpdater.getAndAccumulate(this, 1L, Long::sum);

        origin.leasedObject().set(ValueLayout.JAVA_BYTE, nextIndex, b);
    }

    @Override
    public long currentPosition() {
        return currentOffset;
    }

    @Override
    public void position(final long newPosition) {
        final long segmentByteSize = leasedObject().byteSize();
        if (newPosition < 0 || newPosition > segmentByteSize) {
            throw new IndexOutOfBoundsException(
                    "New position was out of bounds; expected value between 0 and " + segmentByteSize + "; was "
                            + newPosition
            );
        }

        final long currentLimit = limit;
        if (newPosition > currentLimit && currentLimit != -1) {
            throw new IndexOutOfBoundsException(
                    "New position was larger than the limit, limit=" + limit + " newPosition=" + newPosition
            );
        }

        offsetUpdater.set(this, newPosition);
    }

    @Override
    public long currentLimit() {
        return limit;
    }

    @Override
    public void limit(final long newLimit) {
        final long segmentByteSize = leasedObject().byteSize();
        if (newLimit < -1 || newLimit > segmentByteSize) {
            throw new IndexOutOfBoundsException(
                    "New limit was out of bounds; expected value between -1 and " + segmentByteSize + "; was "
                            + newLimit
            );
        }

        limitUpdater.set(this, newLimit);

        if (newLimit < currentPosition() && newLimit != -1) {
            position(newLimit);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TrackedMemorySegmentLease that = (TrackedMemorySegmentLease) o;
        return Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin);
    }
}
