package com.codegym.mathclass.common.lock.impl;

import com.codegym.mathclass.common.lock.DistributedLockService;
import com.codegym.mathclass.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonDistributedLockServiceImpl implements DistributedLockService {

    private final RedissonClient redissonClient;

    @Override
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            if (leaseSeconds > 0) {
                acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            } else {
                acquired = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            }

            if (!acquired) {
                log.warn("Không thể lấy khóa phân tán '{}' sau {} giây chờ", lockKey, waitSeconds);
                throw new BadRequestException("Hệ thống đang xử lý yêu cầu khác cho tài nguyên này, vui lòng không gửi yêu cầu liên tục.");
            }

            log.debug("Đã lấy thành công khóa phân tán '{}'", lockKey);
            return task.get();

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Luồng bị gián đoạn khi đang chờ lấy khóa phân tán '{}'", lockKey, ex);
            throw new BadRequestException("Yêu cầu bị gián đoạn, vui lòng thử lại.");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("Đã giải phóng khóa phân tán '{}'", lockKey);
                } catch (Exception ex) {
                    log.warn("Lỗi khi giải phóng khóa phân tán '{}': {}", lockKey, ex.getMessage());
                }
            }
        }
    }

    @Override
    public void runWithLock(String lockKey, long waitSeconds, long leaseSeconds, Runnable task) {
        executeWithLock(lockKey, waitSeconds, leaseSeconds, () -> {
            task.run();
            return null;
        });
    }

    @Override
    public boolean tryRunWithLock(String lockKey, long waitSeconds, long leaseSeconds, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            if (leaseSeconds > 0) {
                acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            } else {
                acquired = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            }

            if (!acquired) {
                log.info("[Distributed Lock] Không thể lấy khóa '{}' (đang được xử lý bởi node khác), bỏ qua task định kỳ", lockKey);
                return false;
            }

            log.debug("[Distributed Lock] Đã lấy thành công khóa '{}' cho task định kỳ", lockKey);
            task.run();
            return true;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("[Distributed Lock] Luồng bị gián đoạn khi đang chờ lấy khóa '{}'", lockKey, ex);
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("[Distributed Lock] Đã giải phóng khóa '{}'", lockKey);
                } catch (Exception ex) {
                    log.warn("[Distributed Lock] Lỗi khi giải phóng khóa '{}': {}", lockKey, ex.getMessage());
                }
            }
        }
    }
}
