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
package com.teragrep.buf_01.buffer.lease.collection;

import com.teragrep.buf_01.buffer.lease.MemorySegmentLeaseStub;
import com.teragrep.buf_01.buffer.lease.OpenableLease;
import com.teragrep.buf_01.buffer.lease.TrackedLease;
import com.teragrep.buf_01.buffer.pool.OpeningPool;
import com.teragrep.buf_01.buffer.pool.get.LeaseMultiGet;
import com.teragrep.buf_01.buffer.pool.get.MultiGet;
import com.teragrep.buf_01.buffer.pool.get.TrackedLeaseMultiGet;
import com.teragrep.buf_01.buffer.supply.ArenaMemorySegmentLeaseSupplier;
import com.teragrep.poj_01.pool.Pool;
import com.teragrep.poj_01.pool.UnboundPool;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class TrackedMemorySegmentLeaseCollectionTest {

    @Test
    void testCollection() {
        try (
                final Pool<OpenableLease<MemorySegment>> pool = new OpeningPool(
                        new UnboundPool<>(new ArenaMemorySegmentLeaseSupplier(Arena.ofShared(), 1), new MemorySegmentLeaseStub())
                )
        ) {
            final MultiGet<TrackedLease<MemorySegment>> multiGet = new TrackedLeaseMultiGet(new LeaseMultiGet(pool));
            final TrackedLease<MemorySegment>[] leases = multiGet.getAsArray(5);

            try (
                    final TrackedLeaseCollection<MemorySegment> collection = new TrackedMemorySegmentLeaseCollection(
                            leases
                    )
            ) {
                Assertions.assertEquals(5, collection.leases().length);
                Assertions.assertFalse(collection.isStub());
                Assertions.assertTrue(collection.hasNext());

                // Scroll through each of the leases
                {
                    int i;
                    for (i = 0; i < collection.leases().length; i++) {
                        final TrackedLease<MemorySegment> lease = collection.leases()[i];
                        while (lease.hasNext()) {
                            lease.next();
                        }
                    }
                    Assertions.assertEquals(5, i);
                }
                Assertions.assertFalse(collection.hasNext());
            }

            // After close leases should be closed
            {
                int i;
                for (i = 0; i < leases.length; i++) {
                    Assertions.assertThrows(IllegalStateException.class, leases[i]::hasNext);
                }
                Assertions.assertEquals(5, i);
            }
        }
    }

    @Test
    void testStubCollection() {
        final TrackedLeaseCollection<MemorySegment> collection = new TrackedMemorySegmentLeaseCollectionStub();
        Assertions.assertTrue(collection.isStub());
        Assertions.assertThrows(UnsupportedOperationException.class, collection::hasNext);
        Assertions.assertThrows(UnsupportedOperationException.class, collection::leases);
        Assertions.assertThrows(UnsupportedOperationException.class, collection::close);
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(TrackedMemorySegmentLeaseCollection.class).verify();
    }

    @Test
    void testStubEqualsContract() {
        EqualsVerifier.forClass(TrackedMemorySegmentLeaseCollectionStub.class).verify();
    }
}
