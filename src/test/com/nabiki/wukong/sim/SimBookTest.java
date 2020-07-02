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

package com.nabiki.wukong.sim;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class SimBookTest {
    @Test
    public void basic() {
        var ask = new SimBook.Spread();
        ask.price = 2003;
        ask.volume = 1320;
        ask.type = SimBook.SpreadType.ASK;

        var bid = new SimBook.Spread();
        bid.price = 2002;
        bid.volume = 1320;
        bid.type = SimBook.SpreadType.BUY;

        var gson = new GsonBuilder()
                .setPrettyPrinting()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).create();

        var book = new SimBook("x2009", ask, bid);
        book.setBuyChance(0.6);
        for (int i = 0; i < 1000; ++i)
            write(book.refresh().LastPrice);

        book.setBuyChance(0.2);
        for (int i = 0; i < 500; ++i)
            write(book.refresh().LastPrice);

        book.setBuyChance(0.8);
        for (int i = 0; i < 500; ++i)
            write(book.refresh().LastPrice);
    }

    private static void write(double price) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("last_price.txt", true))) {
            pw.println(price);
            pw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}