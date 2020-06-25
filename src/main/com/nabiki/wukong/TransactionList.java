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

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * {@link TransactionList} provides chained transaction support for object that
 * extends {@link Transactional}.
 *
 * <p>Client calls {@link Iterable#forEach(Consumer)} to start a transaction.
 * The method applies {@link Consumer} on each of the elements in order from
 * first to last. If error comes out from the {@link Consumer#accept(Object)},
 * then it will roll back.
 * </p>
 */
public class TransactionList extends ConcurrentLinkedDeque<Transactional<?>> {
    private boolean rollback = false;
    private Throwable cause;

    public TransactionList() {
    }

    public TransactionList(Collection<? extends Transactional<?>> c) {
        super(c);
    }

    /**
     * Check if last transaction has rolled back.
     *
     * @return {@code true} if last transaction has rolled back, {@code false}
     *      other wise.
     */
    public boolean isRollback() {
        return this.rollback;
    }

    /**
     * Get error that caused last transaction rollback.
     *
     * @return {@link Throwable}
     */
    public Throwable getCause() {
        return this.cause;
    }

    /**
     * Start transaction and apply the specified consumer on elements in order
     * from first to last.
     *
     * <p>The method returns immediately if no element in this container, or
     * it locks all elements to achieve ACID. Then if other threads try to
     * do transaction on these elements, they will encounter
     * {@link ConcurrentModificationException}.
     * </p>
     *
     * <p>If consumer works fine for all elements, then they will be committed
     * in order that they are visited. But if any exception thrown from the
     * consumer, transaction will rollback and all changes are discarded.
     * </p>
     *
     * @param action the specified consumer on elements.
     */
    @Override
    public void forEach(Consumer<? super Transactional<?>> action) {
        // Reset error information.
        this.rollback = false;
        this.cause = null;
        if (super.size() == 0)
            return;
        // Lock all elements.
        allExclude();
        // Underlying container doesn't guarantee unchanged during a
        // transaction, I need to remember all touched elements before
        // error so that I can roll them back.
        var stack = new Stack<Transactional<?>>();
        var iterator = super.iterator();
        while (iterator.hasNext()) {
            var n = iterator.next();
            stack.push(n);
            try {
                action.accept(n.transaction());
            } catch (Throwable th) {
                // Roll back when meets error in last-in-first-out order.
                while (!stack.empty())
                    stack.pop().rollback();
                // Save error information.
                this.rollback = true;
                this.cause = th;
                return;
            }
        }
        // Successfully execute task, commit changes.
        iterator = super.iterator();
        while (iterator.hasNext())
            iterator.next().commit();
        // Unlock all elements.
        allUnExclude();
    }

    private void allExclude() {
        var stack = new Stack<Transactional<?>>();
        var iterator = super.iterator();
        while (iterator.hasNext()) {
            var n = iterator.next();
            if (n.exclude())
                stack.push(n);
            else {
                while (!stack.empty())
                    stack.pop().unExclude();
                // Fail locking all elements.
                throw new ConcurrentModificationException(
                        "Transaction forbids concurrent access");
            }
        }
    }

    private void allUnExclude() {
        var descIterator = super.descendingIterator();
        while (descIterator.hasNext()) {
            try {
                descIterator.next().unExclude();
            } catch (IllegalMonitorStateException  ignored) {
            }
        }
    }
}
