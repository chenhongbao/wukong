package com.nabiki.wukong;

import java.util.ConcurrentModificationException;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Transactional<T> {
    private T origin,
            shadow /* shadow is used to mark whether there is transaction
                        undergoing */;
    private ReentrantLock exclusive = new ReentrantLock();

    /**
     * Get currently active object that could be either the original object when
     * no transaction starts, ot the shadow objects after transaction starts.
     *
     * @return currently active object
     */
    public T active() {
        synchronized (this) {
            return this.shadow != null ? this.shadow : this.origin;
        }
    }

    boolean exclude() {
        return this.exclusive.tryLock();
    }

    void unExclude() {
        this.exclusive.unlock();
    }

    /**
     * Start transaction on this object. The method tries getting the internal
     * lock of the object. If it succeeds getting the lock, it starts the
     * transaction, or the method throws {@link ConcurrentModificationException}
     * .
     *
     * <p>When transaction starts, it creates a deep copy of the original
     * object. All consequent changes are applied to the shadow object instead
     * of the original one before commit. But {@code rollback()} is called,
     * the shadow object is discarded, so as all the changes made to the shadow
     * object.
     * </p>
     *
     * <p>The active object that could be either the original object or shadow
     * is obtained via {@code active()}.</p>
     *
     * @return this transactional object
     */
    public Transactional<T> transaction() {
        if (!exclude())
            throw new ConcurrentModificationException(
                    "Transaction forbids concurrent access");
        if (this.shadow == null)
            this.shadow = OP.deepCopy(this.origin);
        unExclude();
        return this;
    }

    /**
     * Commit changes to this object. The method replaces the original copy of
     * generic type object with a shadow object. This applies the real changes
     * to the object.
     */
    public void commit() {
        if (!exclude())
            throw new ConcurrentModificationException(
                    "Commit forbids concurrent access");
        if (this.shadow != null) {
            this.origin = this.shadow;
            this.shadow = null;
        }
        unExclude();
    }

    /**
     * Rollback changes to this object. The method erases the shadow object and
     * the original object is not touched.
     */
    public void rollback() {
        if (!exclude())
            throw new ConcurrentModificationException(
                    "Rollback forbids concurrent access");
        if (this.shadow != null)
            this.shadow = null;
        unExclude();
    }
}
