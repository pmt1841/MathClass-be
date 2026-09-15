package com.codegym.mathclass.common.lock;

import java.util.function.Supplier;

public interface DistributedLockService {

    <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> task);

    void runWithLock(String lockKey, long waitSeconds, long leaseSeconds, Runnable task);

    boolean tryRunWithLock(String lockKey, long waitSeconds, long leaseSeconds, Runnable task);
}
