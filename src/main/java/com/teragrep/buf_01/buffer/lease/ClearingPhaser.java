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

import com.teragrep.buf_01.buffer.pool.CountablePool;

import java.util.concurrent.Phaser;

/**
 * Phaser that clears the MemorySegment on termination (registeredParties=0), if the lease is not the parent lease.
 * Otherwise, the lease is not fully terminated so it can be reused.
 */
class ClearingPhaser extends Phaser {

    private final MemorySegmentLease lease;
    private final CountablePool<MemorySegmentLease> pool;

    ClearingPhaser(int initialParties, MemorySegmentLease lease, CountablePool<MemorySegmentLease> pool) {
        super(initialParties);
        this.lease = lease;
        this.pool = pool;
    }

    ClearingPhaser(
            Phaser parent,
            int initialParties,
            MemorySegmentLease lease,
            CountablePool<MemorySegmentLease> pool
    ) {
        super(parent, initialParties);
        this.lease = lease;
        this.pool = pool;
    }

    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
        final boolean shouldTerminate;
        if (this.getParent() == null && registeredParties == 0) {
            shouldTerminate = false;
        }
        else if (registeredParties == 0) {
            shouldTerminate = true;
        }
        else {
            shouldTerminate = false;
        }
        return shouldTerminate;
    }
}
