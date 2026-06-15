package com.ecommerce.ecommerce.inventory;

import com.ecommerce.ecommerce.exception.InventoryConflictException;
import com.ecommerce.ecommerce.logging.AuditLoggers;
import com.ecommerce.ecommerce.logging.LogCategory;
import org.slf4j.Logger;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(name = "app.inventory.lock.type", havingValue = "redis")
public class RedisInventoryLockService implements InventoryLockService {

    private static final Logger log = AuditLoggers.forCategory(LogCategory.INVENTORY);

    static final String LOCK_PREFIX = "lock:inventory:product:";

    private final RedissonClient redissonClient;
    private final long waitTimeMs;

    public RedisInventoryLockService(
            RedissonClient redissonClient,
            @Value("${app.inventory.lock.wait-time-ms}") long waitTimeMs) {
        this.redissonClient = redissonClient;
        this.waitTimeMs = waitTimeMs;
    }

    @Override
    public <T> T executeWithProductLocks(Collection<Long> productIds, Supplier<T> action) {
        List<Long> orderedProductIds = orderedProductIds(productIds);
        if (orderedProductIds.isEmpty()) {
            return action.get();
        }

        List<RLock> acquiredLocks = new ArrayList<>();
        try {
            log.info("category=INVENTORY event=inventory_lock_request provider=redis productIds={}", orderedProductIds);
            for (Long productId : orderedProductIds) {
                RLock lock = redissonClient.getLock(LOCK_PREFIX + productId);
                if (!lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS)) {
                    log.warn("category=INVENTORY event=inventory_lock_timeout provider=redis productId={} lockKey={} waitTimeMs={}",
                            productId, LOCK_PREFIX + productId, waitTimeMs);
                    throw new InventoryConflictException("Inventory is busy for product " + productId + ". Please retry.");
                }
                acquiredLocks.add(lock);
            }

            log.info("category=INVENTORY event=inventory_lock_acquired provider=redis productIds={}", orderedProductIds);
            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InventoryConflictException("Interrupted while waiting for distributed inventory lock.");
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

    private void unlockReverse(List<RLock> acquiredLocks) {
        for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
            RLock lock = acquiredLocks.get(i);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        if (!acquiredLocks.isEmpty()) {
            log.info("category=INVENTORY event=inventory_lock_released provider=redis lockCount={}", acquiredLocks.size());
        }
    }
}
