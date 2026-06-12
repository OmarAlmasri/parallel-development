package com.ecommerce.ecommerce.inventory;

import com.ecommerce.ecommerce.exception.InventoryConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisInventoryLockServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RLock firstLock;
    @Mock private RLock secondLock;
    @Mock private Supplier<String> action;

    @Test
    void executeWithProductLocks_shouldAcquireDistinctLocksInSortedOrder_andReleaseInReverseOrder() throws Exception {
        RedisInventoryLockService lockService = new RedisInventoryLockService(redissonClient, 2000);

        when(redissonClient.getLock(RedisInventoryLockService.LOCK_PREFIX + 1)).thenReturn(firstLock);
        when(redissonClient.getLock(RedisInventoryLockService.LOCK_PREFIX + 2)).thenReturn(secondLock);
        when(firstLock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(secondLock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(firstLock.isHeldByCurrentThread()).thenReturn(true);
        when(secondLock.isHeldByCurrentThread()).thenReturn(true);
        when(action.get()).thenReturn("done");

        String result = lockService.executeWithProductLocks(List.of(2L, 1L, 1L), action);

        assertEquals("done", result);

        InOrder inOrder = inOrder(redissonClient, firstLock, secondLock, action);
        inOrder.verify(redissonClient).getLock(RedisInventoryLockService.LOCK_PREFIX + 1);
        inOrder.verify(firstLock).tryLock(2000, TimeUnit.MILLISECONDS);
        inOrder.verify(redissonClient).getLock(RedisInventoryLockService.LOCK_PREFIX + 2);
        inOrder.verify(secondLock).tryLock(2000, TimeUnit.MILLISECONDS);
        inOrder.verify(action).get();
        inOrder.verify(secondLock).isHeldByCurrentThread();
        inOrder.verify(secondLock).unlock();
        inOrder.verify(firstLock).isHeldByCurrentThread();
        inOrder.verify(firstLock).unlock();
    }

    @Test
    void executeWithProductLocks_shouldReleaseAlreadyAcquiredLocks_whenLaterLockTimesOut() throws Exception {
        RedisInventoryLockService lockService = new RedisInventoryLockService(redissonClient, 2000);

        when(redissonClient.getLock(RedisInventoryLockService.LOCK_PREFIX + 1)).thenReturn(firstLock);
        when(redissonClient.getLock(RedisInventoryLockService.LOCK_PREFIX + 2)).thenReturn(secondLock);
        when(firstLock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(secondLock.tryLock(2000, TimeUnit.MILLISECONDS)).thenReturn(false);
        when(firstLock.isHeldByCurrentThread()).thenReturn(true);

        assertThrows(
                InventoryConflictException.class,
                () -> lockService.executeWithProductLocks(List.of(1L, 2L), action)
        );

        verify(action, never()).get();
        verify(firstLock).unlock();
        verify(secondLock, never()).unlock();
    }
}
