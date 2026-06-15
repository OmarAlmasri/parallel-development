package com.ecommerce.ecommerce.inventory;

import com.ecommerce.ecommerce.exception.InventoryConflictException;
import com.ecommerce.ecommerce.logging.AuditLoggers;
import com.ecommerce.ecommerce.logging.LogCategory;
import org.slf4j.Logger;
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

    private static final Logger log = AuditLoggers.forCategory(LogCategory.INVENTORY);

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
            log.info("category=INVENTORY event=inventory_lock_request provider=local productIds={}", orderedProductIds);
            for (Long productId : orderedProductIds) {
                ReentrantLock lock = locks.computeIfAbsent(productId, ignored -> new ReentrantLock());
                if (!lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS)) {
                    log.warn("category=INVENTORY event=inventory_lock_timeout provider=local productId={} waitTimeMs={}", productId, waitTimeMs);
                    throw new InventoryConflictException("Inventory is busy for product " + productId + ". Please retry.");
                }
                acquiredLocks.add(lock);
            }

            log.info("category=INVENTORY event=inventory_lock_acquired provider=local productIds={}", orderedProductIds);
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
        if (!acquiredLocks.isEmpty()) {
            log.info("category=INVENTORY event=inventory_lock_released provider=local lockCount={}", acquiredLocks.size());
        }
    }
}
