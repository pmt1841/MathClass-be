package com.codegym.mathclass.common.lock.impl;

import com.codegym.mathclass.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private RedissonDistributedLockServiceImpl distributedLockService;

    private final String lockKey = "lock:test:resource";

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getLock(lockKey)).thenReturn(lock);
    }

    @Test
    @DisplayName("executeWithLock: Thành công khi lấy được lock, trả về kết quả và unlock")
    void executeWithLock_Success() throws Exception {
        when(lock.tryLock(eq(5L), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = distributedLockService.executeWithLock(lockKey, 5, 10, () -> "SUCCESS_RESULT");

        assertThat(result).isEqualTo("SUCCESS_RESULT");
        verify(lock).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("executeWithLock: Ném BadRequestException khi không lấy được lock sau timeout")
    void executeWithLock_LockNotAcquired_ThrowsBadRequestException() throws Exception {
        when(lock.tryLock(eq(5L), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThatThrownBy(() -> distributedLockService.executeWithLock(lockKey, 5, 10, () -> "FAIL"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Hệ thống đang xử lý yêu cầu khác");

        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("executeWithLock: Task gặp lỗi ngoại lệ vẫn đảm bảo unlock trong finally")
    void executeWithLock_TaskThrowsException_AlwaysUnlocks() throws Exception {
        when(lock.tryLock(eq(5L), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> distributedLockService.executeWithLock(lockKey, 5, 10, () -> {
            throw new IllegalStateException("Business logic failed");
        })).isInstanceOf(IllegalStateException.class)
           .hasMessage("Business logic failed");

        verify(lock).unlock();
    }

    @Test
    @DisplayName("executeWithLock: Bị gián đoạn (InterruptedException) thì phục hồi interrupt flag và ném BadRequestException")
    void executeWithLock_Interrupted_ThrowsBadRequestException() throws Exception {
        when(lock.tryLock(eq(5L), eq(10L), eq(TimeUnit.SECONDS))).thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> distributedLockService.executeWithLock(lockKey, 5, 10, () -> "RESULT"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Yêu cầu bị gián đoạn");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear interrupt flag after test
        Thread.interrupted();
    }

    @Test
    @DisplayName("runWithLock: Thực thi Runnable thành công với lock")
    void runWithLock_Success() throws Exception {
        when(lock.tryLock(eq(3L), eq(6L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        distributedLockService.runWithLock(lockKey, 3, 6, () -> executed.set(true));

        assertThat(executed.get()).isTrue();
        verify(lock).tryLock(3L, 6L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("Concurrency: Nhiều luồng đồng thời cạnh tranh lock được tuần tự hóa an toàn, không thất thoát dữ liệu")
    void concurrency_MultipleThreadsSerializedByLock() throws Exception {
        java.util.concurrent.locks.ReentrantLock realLock = new java.util.concurrent.locks.ReentrantLock();
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> realLock.tryLock(inv.getArgument(0), inv.getArgument(2)));
        when(lock.isHeldByCurrentThread())
                .thenAnswer(inv -> realLock.isHeldByCurrentThread());
        doAnswer(inv -> {
            realLock.unlock();
            return null;
        }).when(lock).unlock();

        int threadCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        int[] counter = new int[1]; // non-thread-safe counter

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    distributedLockService.runWithLock(lockKey, 5, 10, () -> {
                        int current = counter[0];
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                        counter[0] = current + 1;
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(counter[0]).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("tryRunWithLock: Thực thi và trả về true khi lấy được lock")
    void tryRunWithLock_Acquired_ExecutesAndReturnsTrue() throws Exception {
        when(lock.tryLock(eq(0L), eq(300L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = distributedLockService.tryRunWithLock(lockKey, 0, 300, () -> executed.set(true));

        assertThat(result).isTrue();
        assertThat(executed.get()).isTrue();
        verify(lock).tryLock(0L, 300L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("tryRunWithLock: Bỏ qua và trả về false khi không lấy được lock mà không ném exception")
    void tryRunWithLock_NotAcquired_SkipsAndReturnsFalse() throws Exception {
        when(lock.tryLock(eq(0L), eq(300L), eq(TimeUnit.SECONDS))).thenReturn(false);

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = distributedLockService.tryRunWithLock(lockKey, 0, 300, () -> executed.set(true));

        assertThat(result).isFalse();
        assertThat(executed.get()).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("tryRunWithLock: Trả về false và khôi phục cờ ngắt khi bị InterruptedException")
    void tryRunWithLock_Interrupted_ReturnsFalse() throws Exception {
        when(lock.tryLock(eq(0L), eq(300L), eq(TimeUnit.SECONDS))).thenThrow(new InterruptedException("Interrupted"));

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = distributedLockService.tryRunWithLock(lockKey, 0, 300, () -> executed.set(true));

        assertThat(result).isFalse();
        assertThat(executed.get()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // clear flag
    }
}
