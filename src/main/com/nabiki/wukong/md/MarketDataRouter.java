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

package com.nabiki.wukong.md;

import com.nabiki.ctp4j.jni.struct.CThostFtdcDepthMarketDataField;
import com.nabiki.wukong.ctp.MarketDataReceiver;
import com.nabiki.wukong.ctp4j.jni.struct.CThostFtdcCandleField;
import com.nabiki.wukong.tools.OP;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MarketDataRouter implements Runnable {
    private final Set<MarketDataReceiver> receivers = new HashSet<>();
    private final Queue<CThostFtdcDepthMarketDataField> depths = new LinkedList<>();
    private final Queue<CThostFtdcCandleField> candles = new LinkedList<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    public MarketDataRouter() {}

    public void addReceiver(MarketDataReceiver recv) {
        if (recv == null)
            throw new NullPointerException("receiver null");
        synchronized (this.receivers) {
            this.receivers.add(recv);
        }
    }

    public void route(CThostFtdcDepthMarketDataField depth) {
        offerDepth(depth);
        trySignal();
    }

    public void route(CThostFtdcCandleField candle) {
        offerCandle(candle);
        trySignal();
    }

    public void route(Collection<CThostFtdcCandleField> candles) {
        offerCandle(candles);
        trySignal();
    }

    private void trySignal() {
        if (this.lock.tryLock()) {
            try {
                this.cond.signal();
            } finally {
                this.lock.unlock();
            }
        }
    }

    private void offerDepth(CThostFtdcDepthMarketDataField depth) {
        synchronized (this.depths) {
            this.depths.add(depth);
        }
    }

    private void offerCandle(CThostFtdcCandleField candle) {
        synchronized (this.candles) {
            this.candles.add(candle);
        }
    }

    private void offerCandle(Collection<CThostFtdcCandleField> candles) {
        if (candles == null || candles.size() == 0)
            return;
        synchronized (this.candles) {
            this.candles.addAll(candles);
        }
    }

    private CThostFtdcDepthMarketDataField pollDepth() {
        synchronized (this.depths) {
            return this.depths.poll();
        }
    }

    private CThostFtdcCandleField pollCandle() {
        synchronized (this.candles) {
            return this.candles.poll();
        }
    }

    private boolean hasData() {
        int mdCnt = 0, cndCnt = 0;
        synchronized (this.depths) {
            mdCnt = this.depths.size();
        }
        synchronized (this.candles) {
            cndCnt = this.candles.size();
        }
        return mdCnt + cndCnt > 0;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            this.lock.lock();
            try {
                while (!hasData())
                    this.cond.await(1, TimeUnit.SECONDS);
                CThostFtdcCandleField candle = null;
                CThostFtdcDepthMarketDataField md = null;
                // Depth.
                while ((md = pollDepth()) != null)
                    synchronized (this.receivers) {
                        for ( var recv : this.receivers)
                            recv.depthReceived(OP.deepCopy(md));
                    }
                // Candle.
                while ((candle = pollCandle()) != null)
                    synchronized (this.receivers) {
                        for (var recv : this.receivers)
                            recv.candleReceived(OP.deepCopy(candle));
                    }
            } catch (InterruptedException ignored) {
            } finally {
                this.lock.unlock();
            }
        }
    }
}
