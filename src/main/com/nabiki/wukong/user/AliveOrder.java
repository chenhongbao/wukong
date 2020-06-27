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

import com.nabiki.ctp4j.jni.flag.TThostFtdcCombOffsetFlagType;
import com.nabiki.ctp4j.jni.flag.TThostFtdcErrorCode;
import com.nabiki.ctp4j.jni.flag.TThostFtdcOrderStatusType;
import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.wukong.Transactional;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.ctp.AliveOrderManager;

import java.util.List;
import java.util.UUID;

public class AliveOrder {

    static class TransFrozenPD extends Transactional<FrozenPositionDetail> {
        protected TransFrozenPD(FrozenPositionDetail origin) {
            super(origin);
        }
    }

    private FrozenCash frozenCash;
    private List<FrozenPositionDetail> frozenPD;

    private final UUID uuid = UUID.randomUUID();
    private final UserCash userCash;
    private final UserPosition userPos;
    private final AliveOrderManager orderMgr;
    private final Config config;
    private final CThostFtdcInputOrderField order;
    private final CThostFtdcInputOrderActionField action;

    private Integer parseState;

    AliveOrder(CThostFtdcInputOrderField order, UserCash userCash,
               UserPosition userPos, AliveOrderManager mgr, Config cfg) {
        this.userCash = userCash;
        this.userPos = userPos;
        this.orderMgr = mgr;
        this.config = cfg;
        this.order = order;
        this.action = null;
    }

    AliveOrder(CThostFtdcInputOrderActionField action, UserCash userCash,
               UserPosition userPos, AliveOrderManager mgr, Config cfg) {
        this.userCash = userCash;
        this.userPos = userPos;
        this.orderMgr = mgr;
        this.config = cfg;
        this.order = null;
        this.action = action;
    }

    boolean isAction() {
        return this.action != null;
    }

    CThostFtdcInputOrderField originOrder() {
        return this.order;
    }

    CThostFtdcInputOrderActionField originAction() {
        return this.action;
    }

    int execOrder() {
        if (this.order == null || this.config == null)
            throw new NullPointerException("parameter null");
        if (this.order.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            this.frozenCash = getFrozenCash(this.order);
            if (this.frozenCash == null)
                return TThostFtdcErrorCode.INSUFFICIENT_MONEY;
            return this.orderMgr.sendDetailOrder(this.order, this);
        } else {
            this.frozenPD = getFrozenPD(this.order);
            if (this.frozenPD == null || this.frozenPD.size() == 0)
                return TThostFtdcErrorCode.OVER_CLOSE_POSITION;
            // Send close request.
            int ret = 0;
            for (var p : this.frozenPD) {
                var x = this.orderMgr.sendDetailOrder(toCloseOrder(p), this);
                if (x != 0)
                    ret = x;
            }
            return ret;
        }
    }

    UUID getOrderUUID() {
        return this.uuid;
    }

    private FrozenCash getFrozenCash(CThostFtdcInputOrderField order) {
        return null;
    }

    private List<FrozenPositionDetail> getFrozenPD(CThostFtdcInputOrderField order) {
        return null;
    }

    private CThostFtdcInputOrderField toCloseOrder(FrozenPositionDetail pd) {
        return null;
    }

    void updateRtnOrder(CThostFtdcOrderField rtn) {
        if (rtn == null)
            throw new NullPointerException("return order null");
        char flag = (char)rtn.OrderStatus;
        if (flag == TThostFtdcOrderStatusType.CANCELED)
            for (var p : this.frozenPD)
                p.cancel();
    }

    // For cash, just update commission.
    // For position, update both frozen position and user position.
    // When query account, calculate the fields from yesterday's settlement and user
    // position.
    void updateTrade(CThostFtdcTradeField trade) {
        if (trade == null)
            throw new NullPointerException("return trade null");
        var cashShare = toCommissionShare(trade);
        if (cashShare == null)
            throw new NullPointerException("commission share null");
        var volume = trade.Volume;
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            var share = toCashShare(trade);
            if (share == null)
                throw new NullPointerException("cash share null");
            // Update frozen cash, user cash and user position.
            this.frozenCash.tradeShare(share, volume);
            this.userCash.tradeShare(cashShare, volume);
            this.userPos.getUserPD(trade.InstrumentID).add(toUserPosition(trade));
        } else {
            // Close.
            var share = toPositionShare(trade);
            if (share == null)
                throw new IllegalStateException("position share null");
            // Update user position, frozen position and user cash.
            for (var p : frozenPD) {
                var vol = Math.min(p.getFrozenShareCount(), volume);
                // Update cash and position.
                p.tradeShare(vol);
                p.getParent().tradeShare(share, vol);
                this.userCash.tradeShare(cashShare, vol);
                // Count down.
                volume -= vol;
                if (volume == 0)
                    break;
                else if (volume < 0)
                    throw new IllegalStateException(
                            "update more position than closed");
            }
            if (volume > 0)
                throw new IllegalStateException("close more positions then owned");
        }
    }

    private CThostFtdcInvestorPositionDetailField toPositionShare(
            CThostFtdcTradeField trade) {
        return null;
    }

    private UserPositionDetail toUserPosition(CThostFtdcTradeField trade) {
        return null;
    }

    private CThostFtdcTradingAccountField toCashShare(CThostFtdcTradeField trade) {
        return null;
    }

    // Only commission.
    private CThostFtdcTradingAccountField toCommissionShare(CThostFtdcTradeField trade) {
        return null;
    }
}
