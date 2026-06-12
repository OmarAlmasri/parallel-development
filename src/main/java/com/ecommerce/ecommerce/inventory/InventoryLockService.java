package com.ecommerce.ecommerce.inventory;

import java.util.Collection;
import java.util.function.Supplier;

public interface InventoryLockService {
    <T> T executeWithProductLocks(Collection<Long> productIds, Supplier<T> action);
}
