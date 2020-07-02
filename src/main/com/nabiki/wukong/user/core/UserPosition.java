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
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInvestorPositionDetailField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradeField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.cfg.plain.InstrumentInfo;

import java.util.*;

public class UserPosition {
    private final User parent;
    private final Map<String, List<UserPositionDetail>> userPD = new HashMap<>();

    public UserPosition(Map<String, List<UserPositionDetail>> pd, User parent) {
        this.userPD.putAll(pd);
        this.parent = parent;
    }

    User getParent() {
        return this.parent;
    }

    @InTeam
    public Map<String, List<UserPositionDetail>> getAllPD() {
        return this.userPD;
    }

    /**
     * Get the specified position details of an instrument. If user doesn't have
     * position of the instrument, return {@code null}.
     *
     * @param instrID instrument ID
     * @return position details of the instrument
     */
    @InTeam
    public List<UserPositionDetail> getUserPD(String instrID) {
        return this.userPD.get(instrID);
    }

    CThostFtdcTradingAccountField getCurrAccount() {
        var r = new CThostFtdcTradingAccountField();
        r.FrozenCommission = 0;
        r.FrozenMargin = 0;
        for (var lst : this.userPD.values())
            for (var p : lst) {
                r.FrozenCommission = p.getFrozenCommission();
                r.FrozenMargin += p.getFrozenMargin();
            }
        return r;
    }

    @InTeam
    public CThostFtdcInvestorPositionDetailField getCurrPD() {
        var r = new CThostFtdcInvestorPositionDetailField();
        r.Margin = 0.;
        r.CloseProfitByTrade = 0.;
        r.CloseProfitByDate = 0;
        r.PositionProfitByTrade = 0;
        r.PositionProfitByDate = 0;
        for (var lst : this.userPD.values())
            for (var c : lst) {
                var p = c.getDeepCopyTotal();
                r.Margin += p.Margin;
                r.CloseProfitByDate += p.CloseProfitByDate;
                r.CloseProfitByTrade += p.CloseProfitByTrade;
                r.PositionProfitByDate += p.PositionProfitByDate;
                r.PositionProfitByTrade += p.PositionProfitByTrade;
            }
        return r;
    }

    @InTeam
    public void updateOpenTrade(CThostFtdcTradeField trade,
                                InstrumentInfo instrInfo,
                                double preSettlementPrice) {
        getUserPD(trade.InstrumentID).add(toUserPosition(trade, instrInfo,
                preSettlementPrice));
    }

    /**
     * Calculate the frozen position. But the frozen position is not written to
     * the user position detail. Only after the request is sent successfully, the
     * frozen position is added to the frozen list.
     *
     * @param order input order, must be close order
     * @param instrInfo instrument info
     * @param tradingDay trading day
     * @return list of frozen position detail if the order is sent successfully
     */
    @InTeam
    public List<FrozenPositionDetail> peakCloseFrozen(
            CThostFtdcInputOrderField order, InstrumentInfo instrInfo,
            String tradingDay) {
        // Get position details.
        var avail = getUserPD(order.InstrumentID);
        Objects.requireNonNull(avail, "user position null");
        // Get instr info.
        Objects.requireNonNull(instrInfo, "instr info null");
        var comm = instrInfo.commission;
        var instr = instrInfo.instrument;
        Objects.requireNonNull(instr, "instrument null");
        Objects.requireNonNull(comm, "commission null");
        // Trading day not null.
        Objects.requireNonNull(tradingDay, "trading day null");
        // Calculate frozen position detail.
        int volume = order.VolumeTotalOriginal;
        var r = new LinkedList<FrozenPositionDetail>();
        for (var a : avail) {
            long vol = Math.min(a.getAvailableVolume(), volume);
            // Calculate shares.
            // No need to calculate close profits and amount. They will be updated
            // on return trade.
            var sharePos = a.getDeepCopyTotal();
            sharePos.ExchMargin /= 1.0D * sharePos.Volume;
            sharePos.Margin /= 1.0D * sharePos.Volume;
            sharePos.CloseVolume = 1;
            // Commission.
            var shareCash = new CThostFtdcTradingAccountField();
            if (sharePos.TradingDay.compareTo(tradingDay) == 0) {
                // Today position.
                if (comm.CloseTodayRatioByMoney > 0)
                    shareCash.FrozenCommission = order.LimitPrice
                            * instr.VolumeMultiple * comm.CloseTodayRatioByMoney;
                else
                    shareCash.FrozenCommission = comm.CloseTodayRatioByVolume;
            } else {
                // YD position.
                if (comm.CloseRatioByMoney > 0)
                    shareCash.FrozenCommission = order.LimitPrice
                            * instr.VolumeMultiple * comm.CloseRatioByMoney;
                else
                    shareCash.FrozenCommission = comm.CloseRatioByVolume;
            }
            // Keep frozen position.
            var frz = new FrozenPositionDetail(a, sharePos, shareCash, vol);
            r.add(frz);
            // Reduce volume to zero.
            if ((volume -= vol) <= 0)
                break;
        }
        if (volume > 0)
            return null; // Failed to ensure position to close.
        else
            return r;
    }

    private UserPositionDetail toUserPosition(CThostFtdcTradeField trade,
                                              InstrumentInfo instrInfo,
                                              double preSettlementPrice) {
        var d = new CThostFtdcInvestorPositionDetailField();
        d.InvestorID = trade.InvestorID;
        d.BrokerID = trade.BrokerID;
        d.Volume = trade.Volume;
        d.OpenPrice = trade.Price;
        d.Direction = trade.Direction;
        d.ExchangeID = trade.ExchangeID;
        d.HedgeFlag = trade.HedgeFlag;
        d.InstrumentID = trade.InstrumentID;
        d.OpenDate = trade.TradeDate;
        d.TradeID = trade.TradeID;
        d.TradeType = trade.TradeType;
        d.TradingDay = trade.TradingDay;
        d.InvestUnitID = trade.InvestUnitID;
        d.SettlementID = trade.SettlementID;
        // Calculate margin.
        var instr = instrInfo.instrument;
        var margin = instrInfo.margin;
        var comm = instrInfo.commission;
        Objects.requireNonNull(instrInfo, "instr info null");
        Objects.requireNonNull(instr, "instrument null");
        Objects.requireNonNull(margin, "margin null");
        Objects.requireNonNull(comm, "commission null");
        // Decide margin rates.
        if (d.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
            d.MarginRateByMoney = margin.LongMarginRatioByMoney;
            d.MarginRateByVolume = margin.LongMarginRatioByVolume;
        } else {
            d.MarginRateByMoney = margin.ShortMarginRatioByMoney;
            d.MarginRateByVolume = margin.ShortMarginRatioByVolume;
        }
        // Calculate margin.
        d.LastSettlementPrice = preSettlementPrice;
        if (d.MarginRateByMoney > 0)
            d.Margin = d.Volume * instr.VolumeMultiple
                    * d.LastSettlementPrice * d.MarginRateByMoney;
        else
            d.Margin = d.Volume * d.MarginRateByVolume;
        // Default values.
        d.CloseVolume = 0;
        d.CloseAmount = 0.0D;
        d.PositionProfitByDate = 0.0D;
        d.PositionProfitByTrade = 0.0D;
        d.CloseProfitByDate = 0.0D;
        d.CloseProfitByTrade = 0.0D;
        d.SettlementPrice = 0.0D;
        d.TimeFirstVolume = 0;
        d.CombInstrumentID = "";
        return new UserPositionDetail(d);
    }
}
