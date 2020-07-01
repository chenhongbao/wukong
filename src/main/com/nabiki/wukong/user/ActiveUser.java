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

package com.nabiki.wukong.user;

import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.OutTeam;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.ctp.ActiveOrderManager;
import com.nabiki.wukong.ctp.CThostFtdcInvestorPositionField;
import com.nabiki.wukong.ctp.TThostFtdcPosiDirectionType;
import com.nabiki.wukong.user.core.FrozenAccount;
import com.nabiki.wukong.user.core.FrozenPositionDetail;
import com.nabiki.wukong.user.core.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ActiveUser {
    private final User user;
    private final Config config;
    private final ActiveOrderManager orderMgr;
    private final Map<UUID, ActiveOrder> orders = new HashMap<>();

    public ActiveUser(User user, ActiveOrderManager orderMgr, Config cfg) {
        this.user = user;
        this.config = cfg;
        this.orderMgr = orderMgr;
    }

    public void settle() {
        var settlement = new ActiveUserSettlement(this.user, this.config);
        settlement.settle();
    }

    @OutTeam
    public UUID insertOrder(CThostFtdcInputOrderField order) {
        var active = new ActiveOrder(order, this.user, this.orderMgr, this.config);
        this.orders.put(active.getOrderUUID(), active);
        try {
            active.execOrder();
        } catch (Throwable th) {
            this.config.getLogger().severe(
                    OP.formatLog("failed order insertion", order.UserID,
                            th.getMessage(), null));
        }
        return active.getOrderUUID();
    }

    /**
     * Get the split detail orders sent to remote server, which are a part of
     * the specified order of the given UUID.
     *
     * <p>An input order from client may result in several split orders sent
     * to remote server. Those orders are sub-orders of the original order.
     * </p>
     *
     * @param uuid UUID of the original order
     * @return set of split detail orders
     */
    @OutTeam
    public Set<CThostFtdcInputOrderField> getDetailOrder(UUID uuid) {
        var r = new HashSet<CThostFtdcInputOrderField>();
        var refs = this.orderMgr.getMapper().getDetailRef(uuid);
        if (refs == null || refs.size() == 0)
            return r;
        for (var ref : refs) {
            var o = this.orderMgr.getMapper().getDetailOrder(ref);
            if (o != null)
                r.add(o);
        }
        return r;
    }

    @OutTeam
    public Map<String, FrozenPositionDetail> getFrozenPositionDetail(UUID uuid) {
        var o = this.orders.get(uuid);
        if (o != null)
            return o.getFrozenPD();
        else
            return null;
    }

    @OutTeam
    public FrozenAccount getFrozenAccount(UUID uuid) {
        var o = this.orders.get(uuid);
        if (o != null)
            return o.getFrozenAccount();
        else
            return null;
    }

    @OutTeam
    public CThostFtdcTradingAccountField getTradingAccount() {
        return this.user.getTradingAccount();
    }

    @OutTeam
    public List<CThostFtdcInvestorPositionField> getPosition(String instrID) {
        if (instrID == null || instrID.length() == 0) {
            var ret = new LinkedList<CThostFtdcInvestorPositionField>();
            for (var instr : this.user.getPosition().getAllPD().keySet())
                ret.addAll(getInstrPosition(instr));
            return ret;
        } else
            return getInstrPosition(instrID);
    }

    private List<CThostFtdcInvestorPositionField> getInstrPosition(String instrID) {
        var ret = new LinkedList<CThostFtdcInvestorPositionField>();
        if (instrID == null || instrID.length() == 0)
            return ret;
        var usrPos = this.user.getPosition().getUserPD(instrID);
        if (usrPos == null || usrPos.size() == 0)
            return ret;
        var tradingDay = this.config.getTradingDay();
        if (tradingDay == null || tradingDay.length() == 0)
            throw new IllegalArgumentException("trading day null");
        CThostFtdcInvestorPositionField lp = null, sp = null;
        for (var p : usrPos) {
            var sum = p.getSummarizedPosition(tradingDay);
            if (sum.PosiDirection == TThostFtdcPosiDirectionType.LONG) {
                // Long position.
                if (lp  == null)
                    lp = sum;
                else
                    lp = add(lp, sum);
            } else {
                // Short position.
                if (sp == null)
                    sp = sum;
                else
                    sp = add(sp, sum);
            }
        }
        // Add to result set.
        if (lp != null) {
            lp.TradingDay = this.config.getTradingDay();
            lp.PositionDate = LocalDate.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
            ret.add(lp);
        }
        if (sp != null) {
            sp.TradingDay = this.config.getTradingDay();
            sp.PositionDate = LocalDate.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
            ret.add(sp);
        }
        return ret;
    }

    private CThostFtdcInvestorPositionField add(
            CThostFtdcInvestorPositionField a,
            CThostFtdcInvestorPositionField b) {
        a.CloseAmount += b.CloseAmount;
        a.CloseProfit += b.CloseProfit;
        a.CloseProfitByDate += b.CloseProfitByDate;
        a.CloseProfitByTrade += b.CloseProfitByTrade;
        a.CloseVolume += b.CloseVolume;
        a.Commission += b.Commission;
        a.ExchangeMargin += b.ExchangeMargin;
        a.FrozenCash += b.FrozenCash;
        a.FrozenCommission += b.FrozenCommission;
        a.FrozenMargin += b.FrozenMargin;
        a.LongFrozen += b.LongFrozen;
        a.LongFrozenAmount += b.LongFrozenAmount;
        a.OpenAmount += b.OpenAmount;
        a.OpenVolume += b.OpenVolume;
        a.OpenCost += b.OpenCost;
        a.Position += b.Position;
        a.PositionCost += b.PositionCost;
        a.PositionProfit += b.PositionProfit;
        a.PreMargin += b.PreMargin;
        a.ShortFrozen += b.ShortFrozen;
        a.ShortFrozenAmount += b.ShortFrozenAmount;
        a.TodayPosition += b.TodayPosition;
        a.UseMargin += b.UseMargin;
        a.YdPosition += b.YdPosition;
        return OP.deepCopy(a);
    }
}
