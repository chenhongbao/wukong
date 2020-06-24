package com.nabiki.wukong;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class TransactionList extends ConcurrentLinkedDeque<Transactional<?>> {
    public TransactionList() {
    }

    public TransactionList(Collection<? extends Transactional<?>> c) {
        super(c);
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
