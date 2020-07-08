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
import com.nabiki.wukong.ctp4j.jni.struct.CThostFtdcCandleField;
import com.nabiki.wukong.tools.OP;

import java.time.LocalDate;
import java.time.LocalTime;

public class CandleProgress {
    private final CThostFtdcCandleField candle = new CThostFtdcCandleField();

    private int lastVolume = 0, lastVolumeUpdated = 0;
    private double lastClosePrice = 0.0D;
    private boolean popped = true;

    public CandleProgress() {}

    public void update(CThostFtdcDepthMarketDataField md) {
        if (this.lastVolume == 0)
            this.lastVolume = md.Volume;
        synchronized (this.candle) {
            if (this.popped) {
                this.candle.InstrumentID = md.InstrumentID;
                this.candle.ActionDay
                        = OP.getDay(LocalDate.now(), "yyyyMMdd");
                this.candle.TradingDay = md.TradingDay;
                this.candle.OpenPrice
                        = this.candle.HighestPrice
                        = this.candle.LowestPrice
                        = md.LastPrice;
                this.popped = false;
            } else {

                this.candle.HighestPrice = Math.max(this.candle.HighestPrice,
                        md.LastPrice);
                this.candle.LowestPrice = Math.min(this.candle.LowestPrice,
                        md.LastPrice);
            }
            this.candle.Volume = md.Volume - this.lastVolume;
            this.candle.OpenInterest = md.OpenInterest;
            this.candle.ClosePrice = md.LastPrice;
            this.candle.UpdateTime = md.UpdateTime;
        }
        this.lastClosePrice = md.LastPrice;
        this.lastVolumeUpdated = md.Volume;
    }

    public CThostFtdcCandleField peak(String tradingDay) {
        synchronized (this.candle) {
            if (this.popped) {
                // Not updated since last pop.
                this.candle.TradingDay = tradingDay;
                this.candle.ActionDay
                        = OP.getDay(LocalDate.now(), "yyyyMMdd");
                this.candle.UpdateTime
                        = OP.getTime(LocalTime.now(), "HH:mm:ss");
                this.candle.OpenPrice
                        = this.candle.ClosePrice
                        = this.candle.HighestPrice
                        = this.candle.LowestPrice
                        = this.lastClosePrice;
            }
            return OP.deepCopy(this.candle);
        }
    }

    public CThostFtdcCandleField pop(String tradingDay) {
        var r = peak(tradingDay);
        this.lastVolume = this.lastVolumeUpdated;
        this.lastVolumeUpdated = 0;
        this.popped = true;
        return r;
    }
}
