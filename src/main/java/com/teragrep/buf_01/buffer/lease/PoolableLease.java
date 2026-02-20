package com.teragrep.buf_01.buffer.lease;

import com.teragrep.poj_01.pool.Poolable;

public interface PoolableLease<T> extends Lease<T>, Poolable {
}
