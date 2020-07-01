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

package com.nabiki.wukong.user.core;

import com.nabiki.ctp4j.jni.flag.TThostFtdcDirectionType;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInvestorPositionDetailField;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.ctp.CThostFtdcInvestorPositionField;
import com.nabiki.wukong.ctp.TThostFtdcPosiDirectionType;

import java.util.LinkedList;
import java.util.List;

public class UserPositionDetail {
    private final CThostFtdcInvestorPositionDetailField total;
    private final List<FrozenPositionDetail> frozenPD = new LinkedList<>();

    public UserPositionDetail(CThostFtdcInvestorPositionDetailField total) {
        this.total = total;
    }

    public void closeShare(CThostFtdcInvestorPositionDetailField share,
                           long tradeCnt) {
        this.total.CloseAmount += share.CloseAmount * tradeCnt;
        this.total.CloseProfitByDate += share.CloseProfitByDate * tradeCnt;
        this.total.CloseProfitByTrade += share.CloseProfitByTrade * tradeCnt;
        this.total.CloseVolume += share.CloseVolume * tradeCnt;
        this.total.ExchMargin -= share.ExchMargin * tradeCnt;
        this.total.Margin -= share.Margin * tradeCnt;
    }

    /**
     * Cancel an close order whose frozen volume is released.
     */
    @InTeam
    public void cancel() {
        for (var frz : this.frozenPD)
            frz.cancel();
    }

    @InTeam
    public int getFrozenVolume() {
        int frozen = 0;
        for (var pd : frozenPD)
            frozen += pd.getFrozenShareCount();
        return frozen;
    }

    /**
     * The currently available volume to close.
     *
     * @return available volume to close
     */
    public int getAvailableVolume() {
        return this.total.Volume - this.total.CloseVolume - getFrozenVolume();
    }

    double getFrozenMargin() {
        double frz = 0.0D;
        for (var c : this.frozenPD)
            frz += c.getFrozenShareCount() * c.getFrozenSharePD().Margin;
        return frz;
    }

    double getFrozenCommission() {
        double frz = 0.0D;
        for (var c : this.frozenPD)
            frz += c.getFrozenShareCount() * c.getFrozenShareAcc().FrozenCommission;
        return frz;
    }

    /**
     * Get a deep copy of the original position detail.
     *
     * @return a deep copy of original position detail
     */
    @InTeam
    public CThostFtdcInvestorPositionDetailField getDeepCopyTotal() {
        return OP.deepCopy(this.total);
    }

    /**
     * Summarize the position details and generate position report. The method needs
     * today's trading day to decide if the position is YD.
     *
     * @param tradingDay today's trading day
     * @return {@link CThostFtdcInvestorPositionField}
     */
    @InTeam
    public CThostFtdcInvestorPositionField getSummarizedPosition(String tradingDay) {
        var r = new CThostFtdcInvestorPositionField();
        // Only need the following 5 fields.
        r.YdPosition = 0;
        r.Position = 0;
        r.TodayPosition = 0;
        r.CloseVolume = 0;
        r.LongFrozen = 0;
        r.ShortFrozen = 0;
        // Prepare other fields.
        r.BrokerID = this.total.BrokerID;
        r.ExchangeID = this.total.ExchangeID;
        r.InvestorID = this.total.InvestorID;
        r.PreSettlementPrice = this.total.LastSettlementPrice;
        r.SettlementPrice = this.total.SettlementPrice;
        r.InstrumentID = this.total.InstrumentID;
        r.HedgeFlag = this.total.HedgeFlag;
        // Calculate fields.
        if (this.total.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
            r.PosiDirection = TThostFtdcPosiDirectionType.LONG;
            r.LongFrozen = getFrozenVolume();
        } else {
            r.PosiDirection = TThostFtdcPosiDirectionType.SHORT;
            r.ShortFrozen = getFrozenVolume();
        }
        r.CloseVolume = this.total.CloseVolume;
        r.Position = this.total.Volume - this.total.CloseVolume;
        if (this.total.TradingDay.compareTo(tradingDay) != 0)
            r.YdPosition = this.total.Volume;
        else
            r.TodayPosition = r.Position;
        return r;
    }

    /**
     * Add frozen position for a close order.
     *
     * @param frzPD new frozen position
     */
    @InTeam
    public void addFrozenPD(FrozenPositionDetail frzPD) {
        this.frozenPD.add(frzPD);
    }
}
