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
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.api.OrderProvider;
import com.nabiki.wukong.cfg.Config;
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

    Map<String, FrozenPositionDetail> getFrozenPD() {
        return this.frozenPD;
    }

    FrozenAccount getFrozenAccount() {
        return this.frozenAccount;
    }

    void execOrder() {
        if (this.order == null || this.config == null)
            throw new NullPointerException("parameter null");
        if (this.order.CombOffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN)
            insertOpen();
        else
            insertClose();
    }

    private void insertOpen() {
        this.frozenAccount = getOpenFrozen(this.order);
        if (this.frozenAccount == null) {
            this.retCode = TThostFtdcErrorCode.INSUFFICIENT_MONEY;
        } else {
            this.retCode = this.orderMgr.sendDetailOrder(this.order, this);
            if (this.retCode == 0)
                // Apply frozen account to parent account.
                this.userAccount.addFrozenAccount(this.frozenAccount);
        }
    }

    private void insertClose() {
        var pds = getCloseFrozen(this.order);
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
                p.getParent().addFrozenPD(p);
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

    private FrozenAccount getOpenFrozen(CThostFtdcInputOrderField order) {
        var instrInfo = this.config.getInstrInfo(order.InstrumentID);
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
        var account = this.userAccount.getParent().getTradingAccount();
        if (account.Available < needMoney)
            return null;
        else
            return new FrozenAccount(c, order.VolumeTotalOriginal);
    }

    private List<FrozenPositionDetail> getCloseFrozen(
            CThostFtdcInputOrderField order) {
        // Get position details.
        var avail = this.userPos.getUserPD(order.InstrumentID);
        Objects.requireNonNull(avail, "user position null");
        // Get instr info.
        var instrInfo = this.config.getInstrInfo(order.InstrumentID);
        Objects.requireNonNull(instrInfo, "instr info null");
        var comm = instrInfo.commission;
        var instr = instrInfo.instrument;
        Objects.requireNonNull(instr, "instrument null");
        Objects.requireNonNull(comm, "commission null");
        // Trading day not null.
        Objects.requireNonNull(this.config.getTradingDay(),
                "trading day null");
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
            if (sharePos.TradingDay.compareTo(this.config.getTradingDay()) == 0) {
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
        var commissionShare = toCommissionShare(trade);
        var volume = trade.Volume;
        if (trade.OffsetFlag == TThostFtdcCombOffsetFlagType.OFFSET_OPEN) {
            // Open.
            if (this.frozenAccount == null) {
                this.config.getLogger().severe(
                        OP.formatLog("no frozen cash",
                                trade.OrderRef, null, null));
                return;
            }
            // Update frozen cash, user cash and user position.
            this.frozenAccount.openShare(volume);
            this.userPos.getUserPD(trade.InstrumentID).add(toUserPosition(trade));
            this.userAccount.addShareCommission(commissionShare, volume);
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
            var share = toPositionShare(p.getFrozenSharePD(), trade);
            // Check the frozen position OK, here won't throw exception.
            p.closeShare(trade.Volume);
            p.getParent().closeShare(share, trade.Volume);
            this.userAccount.addShareCommission(commissionShare, trade.Volume);
        }
    }

    private CThostFtdcInvestorPositionDetailField toPositionShare(
            CThostFtdcInvestorPositionDetailField p, CThostFtdcTradeField trade) {
        var r = OP.deepCopy(p);
        Objects.requireNonNull(r, "failed deep copy");
        var instrInfo = this.config.getInstrInfo(trade.InstrumentID);
        Objects.requireNonNull(instrInfo, "instr info null");
        Objects.requireNonNull(instrInfo.instrument, "instrument null");
        // Calculate position detail.
        r.CloseAmount = trade.Price * instrInfo.instrument.VolumeMultiple;
        double token;
        if (p.Direction == TThostFtdcDirectionType.DIRECTION_BUY)
            token = 1.0D;   // Long position.
        else
            token = -1.0D;  // Short position.
        r.CloseProfitByTrade = token * (trade.Price - p.OpenPrice)
                * instrInfo.instrument.VolumeMultiple;
        if (p.TradingDay.compareTo(trade.TradingDay) == 0)
            // Today's position.
            r.CloseProfitByDate = r.CloseProfitByTrade;
        else
            // History position.
            r.CloseProfitByDate = token * (trade.Price - p.LastSettlementPrice)
                    * instrInfo.instrument.VolumeMultiple;
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
        var instrInfo = this.config.getInstrInfo(trade.InstrumentID);
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
        var depth = this.config.getDepthMarketData(trade.InstrumentID);
        if (depth != null) {
            d.LastSettlementPrice = depth.PreSettlementPrice;
            if (d.MarginRateByMoney > 0)
                d.Margin = d.Volume * instr.VolumeMultiple
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
            double commission;
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
                            null, null));
        }
        return r;
    }
}
