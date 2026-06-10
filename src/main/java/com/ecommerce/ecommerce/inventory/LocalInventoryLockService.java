package com.ecommerce.ecommerce.inventory;

import com.ecommerce.ecommerce.exception.InventoryConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@ConditionalOnProperty(name = "app.inventory.lock.type", havingValue = "local", matchIfMissing = true)
public class LocalInventoryLockService implements InventoryLockService {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final long waitTimeMs;

    public LocalInventoryLockService(@Value("${app.inventory.lock.wait-time-ms}") long waitTimeMs) {
        this.waitTimeMs = waitTimeMs;
    }

    @Override
    public <T> T executeWithProductLocks(Collection<Long> productIds, java.util.function.Supplier<T> action) {
        List<Long> orderedProductIds = orderedProductIds(productIds);
        if (orderedProductIds.isEmpty()) {
            return action.get();
        }

        List<ReentrantLock> acquiredLocks = new ArrayList<>();
        try {
            for (Long productId : orderedProductIds) {
                ReentrantLock lock = locks.computeIfAbsent(productId, ignored -> new ReentrantLock());
                if (!lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS)) {
                    throw new InventoryConflictException("Inventory is busy for product " + productId + ". Please retry.");
                }
                acquiredLocks.add(lock);
            }

            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InventoryConflictException("Interrupted while waiting for inventory lock.");
        } finally {
            unlockReverse(acquiredLocks);
        }
    }

    private List<Long> orderedProductIds(Collection<Long> productIds) {
        if (productIds == null) {
            return List.of();
        }

        return productIds.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private void unlockReverse(List<ReentrantLock> acquiredLocks) {
        for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
            ReentrantLock lock = acquiredLocks.get(i);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
