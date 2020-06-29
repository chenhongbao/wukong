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
import com.nabiki.ctp4j.jni.flag.TThostFtdcDirectionType;
import com.nabiki.ctp4j.jni.flag.TThostFtdcErrorCode;
import com.nabiki.ctp4j.jni.flag.TThostFtdcOrderStatusType;
import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.ctp.ActiveOrderManager;

import java.util.*;

public class ActiveOrder {
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

    private FrozenCash getFrozenCash(CThostFtdcInputOrderField order) {
        return null;
    }

    private List<FrozenPositionDetail> getFrozenPD(CThostFtdcInputOrderField order) {
        return null;
    }

    private CThostFtdcInputOrderField toCloseOrder(FrozenPositionDetail pd) {
        var cls = OP.deepCopy(getOriginOrder());
        cls.VolumeTotalOriginal = (int) pd.getFrozenShareCount();
        if (pd.getFrozenShare().TradingDay.compareTo(this.config.getTradingDay()) != 0) {
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
        var commissionShare = toCommissionShare(trade);
        var volume = trade.Volume;
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            if (this.frozenCash == null) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen cash",
                                trade.OrderRef, null, 0));
                return;
            }
            // Update frozen cash, user cash and user position.
            this.frozenCash.openShare(volume);
            this.userPos.getUserPD(trade.InstrumentID).add(toUserPosition(trade));
            this.userCash.addShareCommission(commissionShare, volume);
        } else {
            // Close.
            if (this.frozenPD == null || this.frozenPD.size() == 0) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen position",
                                trade.OrderRef, null, 0));
                return;
            }
            // Update user position, frozen position and user cash.
            var p = this.frozenPD.get(trade.OrderRef);
            if (p == null) {
                this.config.getLogger().severe(
                        OP.formatLog("frozen position not found",
                                trade.OrderRef, null, 0));
                return;
            }
            if (p.getFrozenShareCount() < trade.Volume) {
                this.config.getLogger().severe(
                        OP.formatLog("not enough frozen position",
                                trade.OrderRef, null, 0));
                return;
            }
            var share = toPositionShare(p.getFrozenShare(), trade);
            // Check the frozen position OK, here won't throw exception.
            p.closeShare(trade.Volume);
            p.getParent().closeShare(share, trade.Volume);
            this.userCash.addShareCommission(commissionShare, trade.Volume);
        }
    }

    private CThostFtdcInvestorPositionDetailField toPositionShare(
            CThostFtdcInvestorPositionDetailField p, CThostFtdcTradeField trade) {
        var r = OP.deepCopy(p);
        var instr = this.config.getInstrInfo(trade.InstrumentID);
        if (instr == null || instr.instrument == null)
            throw new NullPointerException(
                    "instr(" + trade.InstrumentID + ") info null");

        r.CloseAmount = trade.Price * instr.instrument.VolumeMultiple;
        if (p.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
            // Long position.
            r.CloseProfitByTrade = (trade.Price - p.OpenPrice)
                    * instr.instrument.VolumeMultiple;
            r.CloseProfitByDate = (trade.Price - p.LastSettlementPrice)
                    * instr.instrument.VolumeMultiple;
        } else {
            // Short position.
            r.CloseProfitByTrade = (p.OpenPrice - trade.Price)
                    * instr.instrument.VolumeMultiple;
            r.CloseProfitByDate = (p.LastSettlementPrice - trade.Price)
                    * instr.instrument.VolumeMultiple;
        }
        return r;
    }

    private UserPositionDetail toUserPosition(CThostFtdcTradeField trade) {
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
        var instr = this.config.getInstrInfo(trade.InstrumentID);
        if (instr == null || instr.instrument == null || instr.margin == null)
            throw new NullPointerException(
                    "instr(" + trade.InstrumentID + ") info null");
        // Decide margin rates.
        if (d.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
            d.MarginRateByMoney = instr.margin.LongMarginRatioByMoney;
            d.MarginRateByVolume = instr.margin.LongMarginRatioByVolume;
        } else {
            d.MarginRateByMoney = instr.margin.ShortMarginRatioByMoney;
            d.MarginRateByVolume = instr.margin.ShortMarginRatioByVolume;
        }
        // Calculate margin.
        var depth = this.config.getDepthMarketData(trade.InstrumentID);
        if (depth != null) {
            d.LastSettlementPrice = depth.PreSettlementPrice;
            if (d.MarginRateByMoney > 0)
                d.Margin = d.Volume * instr.instrument.VolumeMultiple
                        * d.LastSettlementPrice * d.MarginRateByMoney;
            else
                d.Margin = d.Volume * d.MarginRateByVolume;
        }
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

    // Only commission.
    private CThostFtdcTradingAccountField toCommissionShare(
            CThostFtdcTradeField trade) {
        var r = new CThostFtdcTradingAccountField();
        r.Commission = 0;
        var instrInfo = this.config.getInstrInfo(trade.InstrumentID);
        try {
            double commission = 0.0D;
            var comm = instrInfo.commission;
            var instr = instrInfo.instrument;
            if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
                if (comm.OpenRatioByMoney > 0)
                    commission = comm.OpenRatioByMoney * instr.VolumeMultiple
                            * trade.Price * trade.Volume;
                else
                    commission = comm.OpenRatioByVolume * trade.Volume;
            } else {
                if (trade.OffsetFlag ==
                        TThostFtdcCombOffsetFlagType.OFFSET_CLOSE_TODAY) {
                    if (comm.CloseRatioByMoney > 0)
                        commission = comm.CloseTodayRatioByMoney
                                * instr.VolumeMultiple * trade.Price * trade.Volume;
                    else
                        commission = comm.CloseTodayRatioByVolume * trade.Volume;
                } else {
                    // close = close yesterday
                    if (comm.CloseRatioByMoney > 0)
                        commission = comm.CloseRatioByMoney
                                * instr.VolumeMultiple * trade.Price * trade.Volume;
                    else
                        commission = comm.CloseRatioByVolume * trade.Volume;
                }
            }
            r.Commission = commission;
        } catch (NullPointerException e) {
            this.config.getLogger().warning(
                    OP.formatLog("failed compute commission", trade.OrderRef,
                            null, 0));
        }
        return r;
    }
}
