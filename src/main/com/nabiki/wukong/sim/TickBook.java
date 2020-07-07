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
import com.nabiki.wukong.tools.OP;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;

public class TickBook {
    enum SpreadType {
        ASK, BUY
    }

    private final double priceTick;
    private final Random rand = new Random(TickBook.class.hashCode());
    private final CThostFtdcDepthMarketDataField md, book;
    private final DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Buy chance.
    private double buyChance = 0.5;

    public TickBook(CThostFtdcDepthMarketDataField origin, double priceTick,
                    double buyChance) {
        if (origin == null)
            throw new NullPointerException("original market data null");
        this.priceTick = priceTick;
        this.buyChance = buyChance;
        this.book = OP.deepCopy(origin);
        this.md = OP.deepCopy(origin);
        Objects.requireNonNull(this.book, "book depth null");
        Objects.requireNonNull(this.md, "md depth null");
        this.md.HighestPrice = -Double.MAX_VALUE;
        this.md.LowerLimitPrice = Double.MAX_VALUE;
    }

    public void setBuyChance(double chance) {
        this.buyChance = chance;
    }

    public CThostFtdcDepthMarketDataField refresh() {
        SpreadType direction;
        if (this.rand.nextDouble() < this.buyChance)
            direction = SpreadType.ASK;
        else
            direction = SpreadType.BUY;

        int bidAddedVolume = 50 - nextRandomVolume(100);
        int askAddedVolume = 50 - nextRandomVolume(100);
        int tradedVolume = nextRandomVolume(500);

        md.Volume += tradedVolume;
        md.OpenInterest += Math.round(tradedVolume * 0.1 * this.rand.nextDouble());

        if (direction == SpreadType.ASK) {
            if (this.md.AskVolume1 >= tradedVolume)
                this.md.AskVolume1 -= tradedVolume;
            else {
                // Spread moves up.
                 this.md.BidPrice1 = this.md.AskPrice1;
                 this.md.BidVolume1 = tradedVolume - this.md.AskVolume1;
                // Ask spread moves up by 1 price tick.
                 this.md.AskVolume1 = nextRandomVolume(2000);
                 this.md.AskPrice1 += this.priceTick;
            }

            this.md.LastPrice = this.md.AskPrice1;
        } else {
            if (this.md.BidVolume1 >= tradedVolume)
                this.md.BidVolume1 -= tradedVolume;
            else {
                this.md.AskPrice1 = this.md.BidPrice1;
                this.md.AskVolume1 = tradedVolume - this.md.BidVolume1;

                this.md.BidVolume1 = nextRandomVolume(2000);
                this.md.BidPrice1 -= this.priceTick;
            }

            this.md.LastPrice = this.md.BidPrice1;
        }

        // Spread.
        this.md.AskVolume1 += askAddedVolume;
        this.md.BidVolume1 += bidAddedVolume;
        // Can't less than zero.
        this.md.AskVolume1 = Math.max(this.md.AskVolume1, 0);
        this.md.BidVolume1 = Math.max(this.md.BidVolume1, 0);

        // Day and time.
        this.md.TradingDay = getTradingDay();
        this.md.ActionDay = LocalDate.now().format(this.dayFormat);
        this.md.UpdateTime = LocalTime.now().format(this.timeFormat);
        this.md.UpdateMillisec = (int)(System.currentTimeMillis() % 1000);

        // Highest/lowest prices.
        this.md.HighestPrice = Math.max(this.md.LastPrice, this.md.HighestPrice);
        this.md.LowestPrice = Math.min(this.md.LastPrice, this.md.LowestPrice);

        return OP.deepCopy(this.md);
    }

    private String getTradingDay() {
        var n = LocalDate.now();
        if (LocalTime.now().getHour() > 20)
            n.plusDays(1);
        return n.format(this.dayFormat);
    }

    private int nextRandomVolume(int bbound) {
        return this.rand.nextInt(this.rand.nextInt(bbound + 1) + 1);
    }
}
