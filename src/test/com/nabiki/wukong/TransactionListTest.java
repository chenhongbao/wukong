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

import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class TransactionListTest implements java.io.Serializable {
    static class Food implements Serializable {
        enum Type {
            Candy, Poison
        }

        public final Type kind;
        public int number;

        public Food(Type kind, int number) {
            this.kind = kind;
            this.number = number;
        }
    }

    class Box extends Transactional<Food> {
        protected Box(Food origin) {
            super(origin);
        }
    }

    class PoisonFound extends RuntimeException {
        public PoisonFound(String msg) {
            super(msg);
        }
    }

    // Box contains Food. You open boxes one by one and eat the food if it is Candy,
    // or spit out all the eaten food if it is Poison.
    @Test
    public void candy() {
        TransactionList boxes = new TransactionList();

        boxes.add(new Box(new Food(Food.Type.Candy, 1)));
        boxes.add(new Box(new Food(Food.Type.Candy, 2)));
        boxes.add(new Box(new Food(Food.Type.Candy, 3)));
        boxes.add(new Box(new Food(Food.Type.Candy, 4)));
        boxes.add(new Box(new Food(Food.Type.Candy, 5)));

        boxes.forEach(new Consumer<>() {
            @Override
            public void accept(Transactional<?> action) {
                var food = (Food)action.active();
                if (food.kind == Food.Type.Candy)
                    food.number = 0;
                else
                    throw new PoisonFound("found poison, has " + food.number);
            }
        });

        // Shouldn't roll back.
        Assert.assertFalse(boxes.isRollback());

        // All food are eaten.
        for (var box : boxes)
            Assert.assertEquals(((Food)box.active()).number, 0);
    }

    @Test
    public void poison() {
        TransactionList boxes = new TransactionList();

        boxes.add(new Box(new Food(Food.Type.Candy, 1)));
        boxes.add(new Box(new Food(Food.Type.Candy, 2)));
        // Poison, roll back here.
        boxes.add(new Box(new Food(Food.Type.Poison, 3)));
        boxes.add(new Box(new Food(Food.Type.Candy, 4)));
        boxes.add(new Box(new Food(Food.Type.Candy, 5)));

        var old = OP.deepCopy(boxes);
        // Should get copy.
        Assert.assertNotNull(old);

        boxes.forEach(new Consumer<>() {
            @Override
            public void accept(Transactional<?> action) {
                var food = (Food)action.active();
                if (food.kind == Food.Type.Candy)
                    food.number = 0;
                else
                    throw new PoisonFound("found poison, has " + food.number);
            }
        });

        // Should roll back.
        Assert.assertTrue(boxes.isRollback());
        Assert.assertNotNull(boxes.getCause());

        // Print message.
        System.err.println(boxes.getCause().getMessage());

        // All food are eaten.
        var iter = boxes.iterator();
        var oldIter = old.iterator();

        // No change should be made to original data.
        while (iter.hasNext() && oldIter.hasNext())
            assertEquals(((Food)iter.next().active()).number,
                    ((Food)oldIter.next().active()).number);
    }
}