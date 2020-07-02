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

import com.nabiki.ctp4j.jni.flag.TThostFtdcCombOffsetFlagType;
import com.nabiki.ctp4j.jni.flag.TThostFtdcDirectionType;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradeField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.cfg.plain.InstrumentInfo;
import com.nabiki.wukong.tools.OP;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class UserAccount {
    private final User parent;
    private final CThostFtdcTradingAccountField total;
    private final List<FrozenAccount> frozenAcc = new LinkedList<>();

    public UserAccount(CThostFtdcTradingAccountField total, User parent) {
        this.total = total;
        this.parent = parent;
    }

    /**
     * Get the {@link User} that owns this account.
     *
     * @return user object that owns this account
     */
    @InTeam
    public User getParent() {
        return this.parent;
    }

    @InTeam
    public CThostFtdcTradingAccountField getDeepCopyTotal() {
        return OP.deepCopy(this.total);
    }

    /**
     * Add commission for traded order. The order can be open or close.
     *
     * @param trade the trade response
     * @param instrInfo instrument info
     */
    @InTeam
    public void addShareCommission(CThostFtdcTradeField trade, InstrumentInfo instrInfo) {
        var share = toTradeCommission(trade, instrInfo);
        this.total.Commission += share.Commission * trade.Volume;
    }

    /**
     * Cancel an open order and release all frozen cashes and commission.
     */
    @InTeam
    public void cancel() {
        for (var acc : this.frozenAcc)
            acc.cancel();
    }

    CThostFtdcTradingAccountField getCurrAccount() {
        var r = new CThostFtdcTradingAccountField();
        r.FrozenCash = 0;
        r.FrozenCommission = 0;
        for (var c : this.frozenAcc) {
            r.FrozenCash += c.getFrozenVolume() * c.getSingleFrozen().FrozenCash;
            r.FrozenCommission += c.getFrozenVolume()
                    * c.getSingleFrozen().FrozenCommission;
        }
        return r;
    }

    /**
     * Add frozen account for a new open order.
     *
     * @param frz new frozen account
     */
    @InTeam
    public void addFrozenAccount(FrozenAccount frz) {
        this.frozenAcc.add(frz);
    }

    @InTeam
    public FrozenAccount getOpenFrozen(CThostFtdcInputOrderField order,
                                        InstrumentInfo instrInfo) {
        Objects.requireNonNull(instrInfo, "instr info null");
        var comm = instrInfo.commission;
        var margin = instrInfo.margin;
        var instr = instrInfo.instrument;
        Objects.requireNonNull(instr, "instrument null");
        Objects.requireNonNull(margin, "margin null");
        Objects.requireNonNull(comm, "commission null");
        // Calculate commission, cash.
        var c = new CThostFtdcTradingAccountField();
        if (order.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
            if (margin.LongMarginRatioByMoney > 0)
                c.FrozenCash = order.LimitPrice * instr.VolumeMultiple
                        * margin.LongMarginRatioByMoney;
            else
                c.FrozenCash = margin.LongMarginRatioByVolume;
        } else {
            if (margin.ShortMarginRatioByMoney > 0)
                c.FrozenCash = order.LimitPrice * instr.VolumeMultiple
                        * margin.ShortMarginRatioByMoney;
            else
                c.FrozenCash = margin.ShortMarginRatioByVolume;
        }
        if (comm.OpenRatioByMoney > 0)
            c.FrozenCommission = order.LimitPrice * instr.VolumeMultiple
                    * comm.OpenRatioByMoney;
        else
            c.FrozenCommission = comm.OpenRatioByVolume;
        // Check if available money is enough.
        var needMoney = c.FrozenCash + c.FrozenCommission;
        var account = this.parent.getTradingAccount();
        if (account.Available < needMoney)
            return null;
        else
            return new FrozenAccount(this, c, order.VolumeTotalOriginal);
    }

    // Only calculate commission.
    private CThostFtdcTradingAccountField toTradeCommission(
            CThostFtdcTradeField trade, InstrumentInfo instrInfo) {
        Objects.requireNonNull(instrInfo, "instr info null");
        var comm = instrInfo.commission;
        var instr = instrInfo.instrument;
        Objects.requireNonNull(comm, "commission null");
        Objects.requireNonNull(instr, "instrument null");
        var r = new CThostFtdcTradingAccountField();
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            if (comm.OpenRatioByMoney > 0)
                r.Commission = comm.OpenRatioByMoney * instr.VolumeMultiple
                        * trade.Price * trade.Volume;
            else
                r.Commission = comm.OpenRatioByVolume * trade.Volume;
        } else {
            if (trade.OffsetFlag ==
                    TThostFtdcCombOffsetFlagType.OFFSET_CLOSE_TODAY) {
                if (comm.CloseRatioByMoney > 0)
                    r.Commission = comm.CloseTodayRatioByMoney
                            * instr.VolumeMultiple * trade.Price * trade.Volume;
                else
                    r.Commission = comm.CloseTodayRatioByVolume * trade.Volume;
            } else {
                // close = close yesterday
                if (comm.CloseRatioByMoney > 0)
                    r.Commission = comm.CloseRatioByMoney
                            * instr.VolumeMultiple * trade.Price * trade.Volume;
                else
                    r.Commission = comm.CloseRatioByVolume * trade.Volume;
            }
        }
        return r;
    }
}
