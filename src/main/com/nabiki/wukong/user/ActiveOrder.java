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
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderActionField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradeField;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.api.OrderProvider;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.cfg.plain.InstrumentInfo;
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.core.*;

import java.util.*;

public class ActiveOrder {
    private FrozenAccount frozenAccount;
    private Map<String, FrozenPositionDetail> frozenPD;

    private final UUID uuid = UUID.randomUUID();
    private final UserAccount userAccount;
    private final UserPosition userPos;
    private final OrderProvider orderMgr;
    private final Config config;
    private final CThostFtdcInputOrderField order;
    private final CThostFtdcInputOrderActionField action;

    private Integer retCode;

    ActiveOrder(CThostFtdcInputOrderField order, User user, OrderProvider mgr,
                Config cfg) {
        this.userAccount = user.getAccount();
        this.userPos = user.getPosition();
        this.orderMgr = mgr;
        this.config = cfg;
        this.order = order;
        this.action = null;
    }

    ActiveOrder(CThostFtdcInputOrderActionField action, User user,
                OrderProvider mgr, Config cfg) {
        this.userAccount = user.getAccount();
        this.userPos = user.getPosition();
        this.orderMgr = mgr;
        this.config = cfg;
        this.order = null;
        this.action = action;
    }

    @InTeam
    public boolean isAction() {
        return this.action != null;
    }

    @InTeam
    public CThostFtdcInputOrderField getOriginOrder() {
        return this.order;
    }

    @InTeam
    public CThostFtdcInputOrderActionField getOriginAction() {
        return this.action;
    }

    Map<String, FrozenPositionDetail> getFrozenPosition() {
        return this.frozenPD;
    }

    FrozenAccount getFrozenAccount() {
        return this.frozenAccount;
    }

    void execOrder() {
        var instrInfo = this.config.getInstrInfo(this.order.InstrumentID);
        Objects.requireNonNull(instrInfo, "instr info null");
        if (this.order.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN)
            insertOpen(this.order, instrInfo);
        else
            insertClose(this.order, instrInfo);
    }

    private void insertOpen(CThostFtdcInputOrderField order,
                            InstrumentInfo instrInfo) {
        this.frozenAccount = this.userAccount.getOpenFrozen(this.order, instrInfo);
        if (this.frozenAccount == null) {
            this.retCode = TThostFtdcErrorCode.INSUFFICIENT_MONEY;
        } else {
            this.retCode = this.orderMgr.sendDetailOrder(order, this);
            if (this.retCode == 0)
                // Apply frozen account to parent account.
                this.frozenAccount.setFrozen();
        }
    }

    private void insertClose(CThostFtdcInputOrderField order,
                             InstrumentInfo instrInfo) {
        var pds = this.userPos.peakCloseFrozen(order, instrInfo,
                this.config.getTradingDay());
        if (pds == null || pds.size() == 0) {
            this.retCode = TThostFtdcErrorCode.OVER_CLOSE_POSITION;
            return;
        }
        Set<String> refs = new HashSet<>();
        this.frozenPD = new HashMap<>();
        // Send close request.
        for (var p : pds) {
            var x = this.orderMgr.sendDetailOrder(toCloseOrder(p), this);
            // Map order reference to frozen position.
            var ref = findRef(refs,
                    this.orderMgr.getMapper().getDetailRef(getOrderUUID()));
            this.frozenPD.put(ref, p);
            // Update used ref.
            refs.add(ref);
            if (x == 0) {
                // Apply frozen position to parent position.
                p.setFrozen();
            } else {
                this.retCode = x;
                break;
            }
        }
    }

    private String findRef(Set<String> oldSet, Set<String> newSet) {
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

    @InTeam
    public UUID getOrderUUID() {
        return this.uuid;
    }

    @InTeam
    public Integer getRetCode() {
        return this.retCode;
    }

    private CThostFtdcInputOrderField toCloseOrder(FrozenPositionDetail pd) {
        var cls = OP.deepCopy(getOriginOrder());
        Objects.requireNonNull(cls, "failed deep copy");
        cls.VolumeTotalOriginal = (int) pd.getFrozenShareCount();
        if (pd.getFrozenSharePD().TradingDay
                .compareTo(this.config.getTradingDay()) != 0) {
            // Yesterday.
            cls.CombOffsetFlag = TThostFtdcCombOffsetFlagType.OFFSET_CLOSE_YESTERDAY;
        } else {
            // Today.
            cls.CombOffsetFlag = TThostFtdcCombOffsetFlagType.OFFSET_CLOSE_TODAY;
        }
        return cls;
    }

    /**
     * Update return order. The only flag it cares is {@code CANCEL} because
     * canceling an order affects the position and frozen money.
     *
     * @param rtn return order
     */
    public void updateRtnOrder(CThostFtdcOrderField rtn) {
        if (rtn == null)
            throw new NullPointerException("return order null");
        char flag = (char) rtn.OrderStatus;
        if (flag == TThostFtdcOrderStatusType.CANCELED) {
            if (rtn.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
                // Cancel cash.
                if (this.frozenAccount == null) {
                    this.config.getLogger().severe(
                            OP.formatLog("no frozen cash",
                                    rtn.OrderRef, null, null));
                    return;
                }
                this.frozenAccount.cancel();
            } else {
                if (this.frozenPD == null || this.frozenPD.size() == 0) {
                    this.config.getLogger().severe(
                            OP.formatLog("no frozen position",
                                    rtn.OrderRef, null, null));
                    return;
                }
                // Cancel position.
                var p = this.frozenPD.get(rtn.OrderRef);
                if (p == null) {
                    this.config.getLogger().severe(
                            OP.formatLog("frozen position not found",
                                    rtn.OrderRef, null, null));
                    return;
                }
                p.cancel();
            }
        }
    }

    /**
     * Update position and cash when trade happens. For cash, just update commission.
     * For position, update both frozen position and user position.
     *
     * <p>When query account, calculate the fields from yesterday's settlement and
     * current position.
     * </p>
     *
     * @param trade trade response
     */
    @InTeam
    public void updateTrade(CThostFtdcTradeField trade) {
        if (trade == null)
            throw new NullPointerException("return trade null");
        var instrInfo = this.config.getInstrInfo(trade.InstrumentID);
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            if (this.frozenAccount == null) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen cash",
                                trade.OrderRef, null, null));
                return;
            }
            var depth = this.config.getDepthMarketData(trade.InstrumentID);
            Objects.requireNonNull(depth, "depth market data null");
            // Update frozen cash and user position.
            this.frozenAccount.updateOpenTrade(trade, instrInfo);
            this.userPos.updateOpenTrade(trade, instrInfo, depth.PreSettlementPrice);
        } else {
            // Close.
            if (this.frozenPD == null || this.frozenPD.size() == 0) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen position",
                                trade.OrderRef, null, null));
                return;
            }
            // Update user position, frozen position and user cash.
            var p = this.frozenPD.get(trade.OrderRef);
            if (p == null) {
                this.config.getLogger().severe(
                        OP.formatLog("frozen position not found",
                                trade.OrderRef, null, null));
                return;
            }
            if (p.getFrozenShareCount() < trade.Volume) {
                this.config.getLogger().severe(
                        OP.formatLog("not enough frozen position",
                                trade.OrderRef, null, null));
                return;
            }
            // Check the frozen position OK, here won't throw exception.
            p.updateCloseTrade(trade, instrInfo);
            this.userAccount.addShareCommission(trade, instrInfo);
        }
    }
}
