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

import com.nabiki.ctp4j.jni.struct.CThostFtdcDepthMarketDataField;
import com.nabiki.wukong.api.TickProvider;
import com.nabiki.wukong.md.CandleEngine;
import com.nabiki.wukong.olap.FlowRouter;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Simple fake market data generator.
 *
 * TODO Provide a higher customizable fake market data generator.
 */
public class SimTickProvider implements TickProvider {
    private final Set<FlowRouter> routers = new HashSet<>();
    private final Set<CandleEngine> engines = new HashSet<>();
    private final Thread daemon;

    private boolean released = false;

    public SimTickProvider() {
        this.daemon = new Thread(new TickGenerator());
    }

    @Override
    public void register(CandleEngine engine) {
        synchronized (this.engines) {
            this.engines.add(engine);
        }
    }

    @Override
    public void register(FlowRouter router) {
        synchronized (this.routers) {
            this.routers.add(router);
        }
    }

    @Override
    public void subscribe(List<String> instr) {

    }

    @Override
    public void initialize() {
        this.released = false;
        this.daemon.start();
    }

    @Override
    public void release() {
        this.released = true;
        this.daemon.interrupt();
        try {
            this.daemon.join(1000);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void login() {
        // nothing
    }

    @Override
    public void logout() {
        // nothing
    }

    class TickGenerator implements Runnable {
        @Override
        public void run() {
            // Fake market data.
            var origin = new CThostFtdcDepthMarketDataField();
            origin.InstrumentID = "x2009";
            origin.AskVolume1 = 1352;
            origin.AskPrice1 = 2100;
            origin.BidVolume1 = 465;
            origin.BidPrice1 = 2099;
            origin.PreClosePrice = 2099;
            origin.PreSettlementPrice = 2104;
            // Market trade book.
            var book = new SimBook(origin, 1.0D, 0.5D);
            var rand = new Random(this.hashCode());
            // Infinite loop.
            while (!released) {
                book.setBuyChance(rand.nextDouble());
                int count = rand.nextInt(5000) + 1500;
                // Change a setting per round.
                while (--count > 0) {
                    var md = book.refresh();
                    // Route market data.
                    synchronized (engines) {
                        for (var e : engines)
                            e.update(md);
                    }
                    synchronized (routers) {
                        for (var r : routers)
                            r.enqueue(md);
                    }
                    try {
                        int wi = rand.nextInt(10) + 1;
                        Thread.sleep(wi * 500);
                    } catch (InterruptedException ignored) {
                        // Possible caused by release. Break the loop and check the
                        // mark.
                        break;
                    }
                }
            }
        }
    }
}
