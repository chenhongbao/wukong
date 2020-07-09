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

package com.nabiki.wukong.active;

import com.nabiki.ctp4j.jni.flag.TThostFtdcCombOffsetFlagType;
import com.nabiki.ctp4j.jni.flag.TThostFtdcErrorCode;
import com.nabiki.ctp4j.jni.flag.TThostFtdcErrorMessage;
import com.nabiki.ctp4j.jni.flag.TThostFtdcOrderStatusType;
import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.cfg.plain.InstrumentInfo;
import com.nabiki.wukong.ctp.OrderProvider;
import com.nabiki.wukong.tools.InTeam;
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.core.*;
import com.nabiki.wukong.user.plain.UserState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ActiveRequest {
    private FrozenAccount frozenAccount;
    private Map<String, FrozenPositionDetail> frozenPD;

    private final UUID uuid = UUID.randomUUID();
    private final UserAccount userAccount;
    private final UserPosition userPos;
    private final OrderProvider orderProvider;
    private final Config config;
    private final CThostFtdcInputOrderField order;
    private final CThostFtdcInputOrderActionField action;

    private final CThostFtdcRspInfoField execRsp = new CThostFtdcRspInfoField();

    ActiveRequest(CThostFtdcInputOrderField order, User user, OrderProvider mgr,
                  Config cfg) {
        this.userAccount = user.getAccount();
        this.userPos = user.getPosition();
        this.orderProvider = mgr;
        this.config = cfg;
        this.order = order;
        this.action = null;
    }

    ActiveRequest(CThostFtdcInputOrderActionField action, User user,
                  OrderProvider mgr, Config cfg) {
        this.userAccount = user.getAccount();
        this.userPos = user.getPosition();
        this.orderProvider = mgr;
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
        if (this.order == null)
            throw new IllegalStateException("no order to execute");
        // If the user is panic, some internal error occurred. Don't trade again.
        var usrState = this.userAccount.getParent().getState();
        if ( usrState== UserState.PANIC) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.INCONSISTENT_INFORMATION;
            this.execRsp.ErrorMsg = "internal error caused account panic";
            return;
        }
        // User is settled, but not inited for next day.
        if ( usrState== UserState.SETTLED) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.NOT_INITED;
            this.execRsp.ErrorMsg = TThostFtdcErrorMessage.NOT_INITED;
            return;
        }
        var instrInfo = this.config.getInstrInfo(this.order.InstrumentID);
        Objects.requireNonNull(instrInfo, "instr info null");
        if (this.order.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN)
            insertOpen(this.order, instrInfo);
        else
            insertClose(this.order, instrInfo);
    }

    void execAction() {
        if (this.action == null)
            throw new IllegalStateException("no action to execute");
        if (this.action.OrderSysID == null || this.action.OrderSysID.length() < 1) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.BAD_ORDER_ACTION_FIELD;
            this.execRsp.ErrorMsg = TThostFtdcErrorMessage.BAD_ORDER_ACTION_FIELD;
            return;
        }
        var mapper = this.orderProvider.getMapper();
        var refs = mapper.getDetailRef(UUID.fromString(this.action.OrderSysID));
        if (refs == null || refs.size() < 1) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.ORDER_NOT_FOUND;
            this.execRsp.ErrorMsg = TThostFtdcErrorMessage.ORDER_NOT_FOUND;
            return;
        }
        for (var ref : refs) {
            var rtn = mapper.getRtnOrder(ref);
            var realAction = OP.deepCopy(this.action);
            if (rtn == null) {
                // User order ref.
                realAction.OrderSysID = null;
                realAction.OrderRef = ref;
            } else {
                if (rtn.OrderStatus == TThostFtdcOrderStatusType.CANCELED
                        || rtn.OrderStatus == TThostFtdcOrderStatusType.ALL_TRADED)
                    continue;
                // Use order sys ID.
                realAction.OrderSysID = rtn.OrderSysID;
                realAction.OrderRef = null;
            }
            var r = this.orderProvider.sendOrderAction(realAction, this);
            if (r != 0) {
                this.execRsp.ErrorID = r;
                break;
            }
        }
    }

    private void insertOpen(CThostFtdcInputOrderField order,
                            InstrumentInfo instrInfo) {
        Objects.requireNonNull(instrInfo.instrument, "instrument null");
        Objects.requireNonNull(instrInfo.margin, "margin null");
        Objects.requireNonNull(instrInfo.commission, "commission null");
        this.frozenAccount = this.userAccount.getOpenFrozen(this.order,
                instrInfo.instrument, instrInfo.margin, instrInfo.commission);
        if (this.frozenAccount == null) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.INSUFFICIENT_MONEY;
            this.execRsp.ErrorMsg = TThostFtdcErrorMessage.INSUFFICIENT_MONEY;
        } else {
            // Set valid order ref.
            order.OrderRef = this.orderProvider.getOrderRef();
            this.execRsp.ErrorID
                    = this.orderProvider.sendDetailOrder(order, this);
            if (this.execRsp.ErrorID == 0)
                // Apply frozen account to parent account.
                this.frozenAccount.setFrozen();
        }
    }

    private void insertClose(CThostFtdcInputOrderField order,
                             InstrumentInfo instrInfo) {
        Objects.requireNonNull(instrInfo.instrument, "instrument null");
        Objects.requireNonNull(instrInfo.commission, "commission null");
        var pds = this.userPos.peakCloseFrozen(order, instrInfo.instrument,
                instrInfo.commission, this.config.getTradingDay());
        if (pds == null || pds.size() == 0) {
            this.execRsp.ErrorID = TThostFtdcErrorCode.OVER_CLOSE_POSITION;
            this.execRsp.ErrorMsg = TThostFtdcErrorMessage.OVER_CLOSE_POSITION;
            return;
        }
        this.frozenPD = new HashMap<>();
        // Send close request.
        for (var p : pds) {
            var cls = toCloseOrder(p);
            cls.OrderRef = this.orderProvider.getOrderRef();
            var x = this.orderProvider.sendDetailOrder(cls, this);
            if (x == 0) {
                // Map order reference to frozen position.
                this.frozenPD.put(cls.OrderRef, p);
                // Apply frozen position to parent position.
                // Because the order has been sent, the position must be frozen to
                // ensure no over-close position.
                p.setFrozen();
            } else {
                this.execRsp.ErrorID = x;
                break;
            }
        }
    }

    @InTeam
    public UUID getOrderUUID() {
        return this.uuid;
    }

    @InTeam
    public CThostFtdcRspInfoField getExecRsp() {
        return this.execRsp;
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
                    this.userAccount.getParent().setPanic(
                            TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                            "frozen cash null");
                    return;
                }
                this.frozenAccount.cancel();
            } else {
                if (this.frozenPD == null || this.frozenPD.size() == 0) {
                    this.config.getLogger().severe(
                            OP.formatLog("no frozen position",
                                    rtn.OrderRef, null, null));
                    this.userAccount.getParent().setPanic(
                            TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                            "frozen position null");
                    return;
                }
                // Cancel position.
                var p = this.frozenPD.get(rtn.OrderRef);
                if (p == null) {
                    this.config.getLogger().severe(
                            OP.formatLog("frozen position not found",
                                    rtn.OrderRef, null, null));
                    this.userAccount.getParent().setPanic(
                            TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                            "frozen position not found for order ref");
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
        Objects.requireNonNull(instrInfo, "instr info null");
        Objects.requireNonNull(instrInfo.instrument, "instrument null");
        Objects.requireNonNull(instrInfo.margin, "margin null");
        Objects.requireNonNull(instrInfo.commission, "commission null");
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            if (this.frozenAccount == null) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen cash",
                                trade.OrderRef, null, null));
                this.userAccount.getParent().setPanic(
                        TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                        "frozen cash null");
                return;
            }
            var depth = this.config.getDepthMarketData(trade.InstrumentID);
            Objects.requireNonNull(depth, "depth market data null");
            // Update frozen account, user account and user position.
            // The frozen account handles the update of user account.
            this.frozenAccount.updateOpenTrade(trade, instrInfo.instrument,
                    instrInfo.commission);
            this.userPos.updateOpenTrade(trade, instrInfo.instrument,
                    instrInfo.margin, instrInfo.commission,
                    depth.PreSettlementPrice);
        } else {
            // Close.
            if (this.frozenPD == null || this.frozenPD.size() == 0) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen position",
                                trade.OrderRef, null, null));
                this.userAccount.getParent().setPanic(
                        TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                        "frozen position null");
                return;
            }
            // Update user position, frozen position and user account.
            // The frozen position handles the update of user position.
            var p = this.frozenPD.get(trade.OrderRef);
            if (p == null) {
                this.config.getLogger().severe(
                        OP.formatLog("frozen position not found",
                                trade.OrderRef, null, null));
                this.userAccount.getParent().setPanic(
                        TThostFtdcErrorCode.INCONSISTENT_INFORMATION,
                        "frozen position not found for order ref");
                return;
            }
            if (p.getFrozenShareCount() < trade.Volume) {
                this.config.getLogger().severe(
                        OP.formatLog("not enough frozen position",
                                trade.OrderRef, null, null));
                this.userAccount.getParent().setPanic(
                        TThostFtdcErrorCode.OVER_CLOSE_POSITION,
                        "not enough frozen position for trade");
                return;
            }
            // Check the frozen position OK, here won't throw exception.
            p.updateCloseTrade(trade, instrInfo.instrument);
            this.userAccount.addShareCommission(trade, instrInfo.instrument,
                    instrInfo.commission);
        }
    }
}
