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

import java.io.*;

public class OP {
    /**
     * Get a deep copy of the specified object. The method first serializes the
     * object to a byte array and then recover a new object from it.
     *
     * <p>The object to be copied must implement {@link Serializable}, or the method
     * fails and returns {@code null}.
     * </p>
     *
     * @param copied the specified object to be deeply copied
     * @param <T> generic type of a copied object
     * @return deep copying object
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T copied) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            new ObjectOutputStream(bo).writeObject(copied);
            return (T) new ObjectInputStream(
                    new ByteArrayInputStream(bo.toByteArray())).readObject();
        } catch (IOException | ClassNotFoundException ignored) {
            System.err.println(ignored.getMessage());
            ignored.printStackTrace();
            return null;
        }
    }
}
