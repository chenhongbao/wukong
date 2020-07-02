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

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class SimBook {
    private final String instrID;
    private final Spread ask, bid;
    private final Random rand;
    private final CThostFtdcDepthMarketDataField md;
    private final DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Buy chance.
    private double buyChance = 0.5;

    public SimBook(String instrID, Spread ask, Spread bid) {
        if (ask.type != SpreadType.ASK || bid.type != SpreadType.BUY)
            throw new IllegalArgumentException("wrong spread type");
        if (ask.price <= bid.price)
            throw new IllegalArgumentException("invalid prices");
        if (ask.volume < 0 || bid.volume < 0)
            throw new IllegalArgumentException("invalid volumes");

        this.ask = ask;
        this.bid = bid;
        this.instrID = instrID;
        this.rand = new Random(this.hashCode());
        this.md = new CThostFtdcDepthMarketDataField();
        this.md.InstrumentID = instrID;
        this.md.Volume = 0;
        this.md.OpenInterest = 0;
        this.md.OpenPrice = this.ask.price;
        this.md.PreSettlementPrice = Math.round(0.5D * (this.ask.price + this.bid.price));
        this.md.PreClosePrice = Math.round(this.md.PreSettlementPrice * (1 + (0.5 - this.rand.nextDouble()) * 0.1));
        this.md.HighestPrice = -Double.MAX_VALUE;
        this.md.LowerLimitPrice = Double.MAX_VALUE;
        // Limit prices.
        this.md.UpperLimitPrice = Math.round(this.md.PreSettlementPrice * 1.05);
        this.md.LowerLimitPrice = Math.round(this.md.PreSettlementPrice * 0.95);
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
            if (this.ask.volume >= tradedVolume)
                this.ask.volume -= tradedVolume;
            else {
                 this.bid.price = this.ask.price;
                 this.bid.volume = tradedVolume - this.ask.volume;

                 this.ask.volume = nextRandomVolume(2000);
                 this.ask.price = this.bid.price + 1.0D;
            }

            this.md.LastPrice = this.ask.price;
        } else {
            if (this.bid.volume >= tradedVolume)
                this.bid.volume -= tradedVolume;
            else {
                this.ask.price = this.bid.price;
                this.ask.volume = tradedVolume - this.bid.volume;

                this.bid.volume = nextRandomVolume(2000);
                this.bid.price = this.bid.price - 1.0D;
            }

            this.md.LastPrice = this.bid.price;
        }

        // Spread.
        this.ask.volume += askAddedVolume;
        this.bid.volume += bidAddedVolume;
        // Can't less than zero.
        this.ask.volume = Math.max(this.ask.volume, 0);
        this.bid.volume = Math.max(this.bid.volume, 0);

        this.md.AskPrice1 = this.ask.price;
        this.md.AskVolume1 = this.ask.volume;
        this.md.BidPrice1 = this.bid.price;
        this.md.BidVolume1 = this.bid.volume;

        // Day and time.
        this.md.TradingDay = getTradingDay();
        this.md.ActionDay = LocalDate.now().format(this.dayFormat);
        this.md.UpdateTime = LocalTime.now().format(this.timeFormat);
        this.md.UpdateMillisec = (int)(System.currentTimeMillis() % 1000);

        // H/L prices.
        this.md.HighestPrice = Math.max(this.md.LastPrice, this.md.HighestPrice);
        this.md.LowestPrice = Math.min(this.md.LastPrice, this.md.LowestPrice);

        return deepCopy(this.md);
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

    enum SpreadType {
        ASK, BUY
    }

    static class Spread {
        double price;
        int volume;
        SpreadType type;
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T copied) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            new ObjectOutputStream(bo).writeObject(copied);
            return (T) new ObjectInputStream(
                    new ByteArrayInputStream(bo.toByteArray())).readObject();
        } catch (IOException | ClassNotFoundException ignored) {
            return null;
        }
    }
}
