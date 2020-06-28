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
import com.nabiki.wukong.OP;
import com.nabiki.wukong.Transactional;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.ctp.ActiveOrderManager;

import java.util.*;

public class ActiveOrder {

    static class TransFrozenPD extends Transactional<FrozenPositionDetail> {
        protected TransFrozenPD(FrozenPositionDetail origin) {
            super(origin);
        }
    }

    private FrozenCash frozenCash;
    private Map<String, FrozenPositionDetail> frozenPD;

    private final UUID uuid = UUID.randomUUID();
    private final UserCash userCash;
    private final UserPosition userPos;
    private final ActiveOrderManager orderMgr;
    private final Config config;
    private final CThostFtdcInputOrderField order;
    private final CThostFtdcInputOrderActionField action;

    private Integer parseState;

    ActiveOrder(CThostFtdcInputOrderField order, UserCash userCash,
                UserPosition userPos, ActiveOrderManager mgr, Config cfg) {
        this.userCash = userCash;
        this.userPos = userPos;
        this.orderMgr = mgr;
        this.config = cfg;
        this.order = order;
        this.action = null;
    }

    ActiveOrder(CThostFtdcInputOrderActionField action, UserCash userCash,
                UserPosition userPos, ActiveOrderManager mgr, Config cfg) {
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

    CThostFtdcInputOrderField getOriginOrder() {
        return this.order;
    }

    CThostFtdcInputOrderActionField getOriginAction() {
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
            var pds = getFrozenPD(this.order);
            if (pds == null || pds.size() == 0)
                return TThostFtdcErrorCode.OVER_CLOSE_POSITION;
            Set<String> refs = new HashSet<>();
            this.frozenPD = new HashMap<>();
            // Send close request.
            int ret = 0;
            for (var p : pds) {
                var x = this.orderMgr.sendDetailOrder(toCloseOrder(p), this);
                // Map order reference to frozen position.
                var ref = findRef(refs,
                        this.orderMgr.getMapper().getDetailRef(getOrderUUID()));
                this.frozenPD.put(ref, p);
                // Update used ref.
                refs.add(ref);
                if (x != 0)
                    ret = x;
            }
            return ret;
        }
    }

    String findRef(Set<String> oldSet, Set<String> newSet) {
        if (oldSet.size() >= newSet.size())
            throw new IllegalStateException(
                    "old set has more elements than new set");
        for (var s : newSet) {
            if (!oldSet.contains(s))
                return s;
        }
        this.config.getLogger().warning(
                "order ref for new detailed order not found");
        return null;
    }

    public UUID getOrderUUID() {
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

    public void updateRtnOrder(CThostFtdcOrderField rtn) {
        if (rtn == null)
            throw new NullPointerException("return order null");
        char flag = (char)rtn.OrderStatus;
        if (flag == TThostFtdcOrderStatusType.CANCELED) {
            if (rtn.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
                // Cancel cash.
                if (this.frozenCash == null) {
                    this.config.getLogger().severe(
                            OP.formatLog("no frozen cash",
                                    rtn.OrderRef, null, 0));
                    return;
                }
                this.frozenCash.cancel();
            } else {
                if (this.frozenPD == null || this.frozenPD.size() == 0) {
                    this.config.getLogger().severe(
                            OP.formatLog("no frozen position",
                                    rtn.OrderRef, null, 0));
                    return;
                }
                // Cancel position.
                var p = this.frozenPD.get(rtn.OrderRef);
                if (p == null) {
                    this.config.getLogger().severe(
                            OP.formatLog("frozen position not found",
                                    rtn.OrderRef, null, 0));
                    return;
                }
                p.cancel();
            }
        }
    }

    // For cash, just update commission.
    // For position, update both frozen position and user position.
    // When query account, calculate the fields from yesterday's settlement and user
    // position.
    public void updateTrade(CThostFtdcTradeField trade) {
        if (trade == null)
            throw new NullPointerException("return trade null");
        var commissionShare = toCommissionShare(trade);
        if (commissionShare == null)
            throw new NullPointerException("commission share null");
        var volume = trade.Volume;
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            if (this.frozenCash == null) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen cash",
                                trade.OrderRef, null, 0));
                return;
            }
            var share = toCashShare(trade);
            if (share == null)
                throw new NullPointerException("cash share null");
            // Update frozen cash, user cash and user position.
            this.frozenCash.openShare(share, volume);
            this.userCash.tradeShare(commissionShare, volume);
            this.userPos.getUserPD(trade.InstrumentID).add(toUserPosition(trade));
        } else {
            // Close.
            if (this.frozenPD == null || this.frozenPD.size() == 0) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen position",
                                trade.OrderRef, null, 0));
                return;
            }
            var share = toPositionShare(trade);
            if (share == null)
                throw new IllegalStateException("position share null");
            // Update user position, frozen position and user cash.
            var p = this.frozenPD.get(trade.OrderRef);
            if (p == null) {
                this.config.getLogger().severe(
                        OP.formatLog("frozen position not found",
                                trade.OrderRef, null, 0));
                return;
            }
            p.closeShare(trade.Volume);
            p.getParent().closeShare(share, trade.Volume);
            this.userCash.tradeShare(commissionShare, trade.Volume);
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
