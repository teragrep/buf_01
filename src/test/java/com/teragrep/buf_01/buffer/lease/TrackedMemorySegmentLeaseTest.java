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

import com.teragrep.buf_01.buffer.pool.OpeningPool;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.poj_01.pool.UnboundPool;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class TrackedMemorySegmentLeaseTest {

    @Test
    void testReadProgressing() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());

        int loops = 0;
        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(i, trackedLease.currentPosition());
            Assertions.assertTrue(trackedLease.hasNext());
            Assertions.assertEquals((byte) 0, trackedLease.next());
            loops++;
        }
        Assertions.assertEquals(5, loops);

        Assertions.assertFalse(trackedLease.hasNext());
        Assertions.assertThrows(IndexOutOfBoundsException.class, trackedLease::next);
        Assertions.assertEquals(5L, trackedLease.currentPosition());
    }

    @Test
    void testWriteProgressing() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());

        int loops = 0;
        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(i, trackedLease.currentPosition());
            Assertions.assertTrue(trackedLease.hasNext());
            Assertions.assertDoesNotThrow(() -> trackedLease.write((byte) 'a'));
            loops++;
        }
        Assertions.assertEquals(5, loops);

        Assertions.assertFalse(trackedLease.hasNext());
        Assertions.assertThrows(IndexOutOfBoundsException.class, trackedLease::next);
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> trackedLease.write((byte) 'b'));
        Assertions.assertEquals(5L, trackedLease.currentPosition());
    }

    @Test
    void testReadingWrittenSegment() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());

        int loops = 0;
        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(i, trackedLease.currentPosition());
            Assertions.assertTrue(trackedLease.hasNext());
            Assertions.assertDoesNotThrow(() -> trackedLease.write((byte) 'a'));
            loops++;
        }
        Assertions.assertEquals(5, loops);

        Assertions.assertFalse(trackedLease.hasNext());
        Assertions.assertThrows(IndexOutOfBoundsException.class, trackedLease::next);
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> trackedLease.write((byte) 'b'));
        Assertions.assertEquals(5L, trackedLease.currentPosition());

        // Re-wrap lease and read it
        final TrackedLease<MemorySegment> readingLease = new TrackedMemorySegmentLease(trackedLease);

        Assertions.assertEquals(0L, readingLease.currentPosition());

        int loops2 = 0;
        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(i, readingLease.currentPosition());
            Assertions.assertTrue(readingLease.hasNext());
            Assertions.assertEquals((byte) 'a', readingLease.next());
            loops2++;
        }
        Assertions.assertEquals(5, loops2);

        Assertions.assertFalse(readingLease.hasNext());
        Assertions.assertThrows(IndexOutOfBoundsException.class, readingLease::next);
        Assertions.assertEquals(5L, readingLease.currentPosition());
    }

    @Test
    void testLimit() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());
        Assertions.assertTrue(trackedLease.hasNext());

        trackedLease.limit(0L);

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(0L, trackedLease.currentLimit());
        Assertions.assertFalse(trackedLease.hasNext());
    }

    @Test
    void testSetPositionPastLimit() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());

        trackedLease.limit(3L);

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(3L, trackedLease.currentLimit());

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> trackedLease.position(4L));
    }

    @Test
    void testSetLimitLowerThanPosition() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());

        trackedLease.position(4L);

        Assertions.assertEquals(4L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());

        trackedLease.limit(2L);

        Assertions.assertEquals(2L, trackedLease.currentPosition());
        Assertions.assertEquals(2L, trackedLease.currentLimit());
    }

    @Test
    void testTurnLimitOffDoesNotAffectPosition() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());

        trackedLease.position(4L);

        Assertions.assertEquals(4L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());

        trackedLease.limit(2L);

        Assertions.assertEquals(2L, trackedLease.currentPosition());
        Assertions.assertEquals(2L, trackedLease.currentLimit());

        trackedLease.limit(-1L);

        Assertions.assertEquals(2L, trackedLease.currentPosition());
        Assertions.assertEquals(-1L, trackedLease.currentLimit());
    }

    @Test
    void testResettingPosition() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        Assertions.assertEquals(0L, trackedLease.currentPosition());

        trackedLease.next();

        Assertions.assertEquals(1L, trackedLease.currentPosition());

        trackedLease.position(0L);

        Assertions.assertEquals(0L, trackedLease.currentPosition());
    }

    @Test
    void testFlip() {
        final OpeningPool pool = new OpeningPool(
                new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 5), new MemorySegmentLeaseStub())
        );
        final TrackedLease<MemorySegment> trackedLease = new TrackedMemorySegmentLease(pool.get());

        // Let's read 3 bytes
        trackedLease.next();
        trackedLease.next();
        trackedLease.next();

        // Position should be 3, limit -1
        Assertions.assertEquals(-1L, trackedLease.currentLimit());
        Assertions.assertEquals(3L, trackedLease.currentPosition());

        // Flip it
        trackedLease.flip();

        // Position should be 0, limit 3
        Assertions.assertEquals(0L, trackedLease.currentPosition());
        Assertions.assertEquals(3L, trackedLease.currentLimit());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.simple().forClass(TrackedMemorySegmentLease.class).verify();
    }
}
