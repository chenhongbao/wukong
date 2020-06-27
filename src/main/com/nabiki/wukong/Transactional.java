/*
 * Copyright (c) 2020 Hongbao Chen <chenhongbao@outlook.com>
 *
 * Licensed under the  GNU Affero General Public License v3.0 and you may not use
 * this file except in compliance with the  License. You may obtain a copy of the
 * License at
 *
 *                    https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Permission is hereby  granted, free of charge, to any  person obtaining a copy
 * of this software and associated  documentation files (the "Software"), to deal
 * in the Software  without restriction, including without  limitation the rights
 * to  use, copy,  modify, merge,  publish, distribute,  sublicense, and/or  sell
 * copies  of  the Software,  and  to  permit persons  to  whom  the Software  is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE  IS PROVIDED "AS  IS", WITHOUT WARRANTY  OF ANY KIND,  EXPRESS OR
 * IMPLIED,  INCLUDING BUT  NOT  LIMITED TO  THE  WARRANTIES OF  MERCHANTABILITY,
 * FITNESS FOR  A PARTICULAR PURPOSE AND  NONINFRINGEMENT. IN NO EVENT  SHALL THE
 * AUTHORS  OR COPYRIGHT  HOLDERS  BE  LIABLE FOR  ANY  CLAIM,  DAMAGES OR  OTHER
 * LIABILITY, WHETHER IN AN ACTION OF  CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE  OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nabiki.wukong;

import com.nabiki.wukong.annotation.OutTeam;

import java.util.ConcurrentModificationException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link Transactional} class provides transparent transaction support for
 * arbitrary object that implements {@link java.io.Serializable}.
 *
 * @param <T> generic type that is a sub-type of {@link java.io.Serializable}
 */
public abstract class Transactional<T> implements java.io.Serializable {
    private T origin,
            shadow /* shadow is used to mark whether there is transaction
                        undergoing */;
    private final ReentrantLock exclusive = new ReentrantLock();

    protected Transactional(T origin) {
        this.origin = origin;
    }

    /**
     * Get currently active object that could be either the original object when
     * no transaction starts, ot the shadow objects after transaction starts.
     *
     * @return currently active object
     */
    @OutTeam
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
    @OutTeam
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
    @OutTeam
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
    @OutTeam
    public void rollback() {
        if (!exclude())
            throw new ConcurrentModificationException(
                    "Rollback forbids concurrent access");
        if (this.shadow != null)
            this.shadow = null;
        unExclude();
    }
}
