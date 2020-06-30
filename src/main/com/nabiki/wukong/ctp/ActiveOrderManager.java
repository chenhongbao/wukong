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

package com.nabiki.wukong.ctp;

import com.nabiki.ctp4j.jni.flag.*;
import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.ctp4j.trader.CThostFtdcTraderApi;
import com.nabiki.ctp4j.trader.CThostFtdcTraderSpi;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.annotation.OutTeam;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.cfg.ConfigLoader;
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.olap.FlowWriter;
import com.nabiki.wukong.user.ActiveOrder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code AliveOrderManager} keeps the status of all alive orders, interacts with
 * JNI interfaces and invoke callback methods to process responses.
 */
public class ActiveOrderManager extends CThostFtdcTraderSpi {
    private final OrderMapper mapper = new OrderMapper();
    private final AtomicInteger orderRef = new AtomicInteger(0);
    private final Config config;
    private final LoginConfig loginCfg;
    private final FlowWriter flowWrt;
    private final CThostFtdcTraderApi traderApi;
    private final Timer qryTimer = new Timer();
    private final List<String> instrs = new LinkedList<>();

    private boolean isConfirmed = false,
            isConnected = false,
            qryInstrLast = false;
    private CThostFtdcRspUserLoginField rspLogin;

    ActiveOrderManager(Config cfg) {
        this.config = cfg;
        this.loginCfg = this.config.getLoginConfigs().get("trader");
        this.flowWrt = new FlowWriter(this.config);
        this.traderApi = CThostFtdcTraderApi.CreateFtdcTraderApi(
                this.loginCfg.flowDirectory);
        // Start query timer task.
        this.qryTimer.scheduleAtFixedRate(new QueryTask(), 0, 3000);
    }

    /**
     * Get order mapper.
     *
     * @return {@link OrderMapper}
     */
    @InTeam
    public OrderMapper getMapper() {
        return this.mapper;
    }

    private void configTrader() {
        for (var fa : this.loginCfg.frontAddresses)
            this.traderApi.RegisterFront(fa);
        this.traderApi.SubscribePrivateTopic(ThostTeResumeType.THOST_TERT_RESUME);
        this.traderApi.SubscribePublicTopic(ThostTeResumeType.THOST_TERT_RESUME);
        this.traderApi.RegisterSpi(this);
    }

    /**
     * Initialize connection to remote counter.
     */
    @OutTeam
    void initialize() {
        configTrader();
        this.traderApi.Init();
    }

    /**
     * Disconnect the trader api and release resources.
     */
    @OutTeam
    void release() {
        this.traderApi.Release();
    }

    /**
     * Request login.
     */
    @OutTeam
    void login() {
        if (!this.isConnected)
            throw new IllegalStateException("not connected");
        if (this.isConfirmed)
            throw new IllegalStateException("duplicated login");
        doAuthentication();
    }

    /**
     * Request logout;
     */
    @OutTeam
    void logout() {
        if (!this.isConfirmed)
            throw new IllegalStateException("duplicated logout");
        doLogout();
    }

    /**
     * Save the mapping from the specified input order to the specified alive order,
     * then send the specified input order to remote server.
     *
     * <p>If the remote service is temporarily unavailable, the order is saved to
     * send at next market open.
     * </p>
     *
     * @param detail input order
     * @param active alive order
     * @return returned value from JNI call
     */
    public int sendDetailOrder(CThostFtdcInputOrderField detail, ActiveOrder active) {
        if (!this.isConfirmed)
            throw new IllegalStateException("unconfirmed yet");
        this.flowWrt.writeReq(detail);
        if (detail.LimitPrice == 0)
        // Set correct users.
        detail.OrderRef = getOrderRef();
        detail.BrokerID = this.rspLogin.BrokerID;
        detail.UserID = this.rspLogin.UserID;
        detail.InvestorID = this.rspLogin.UserID;
        // Adjust flags.
        detail.CombHedgeFlag = TThostFtdcCombHedgeFlagType.SPECULATION;
        detail.ContingentCondition = TThostFtdcContingentConditionType.IMMEDIATELY;
        detail.ForceCloseReason = TThostFtdcForceCloseReasonType.NOT_FORCE_CLOSE;
        detail.IsAutoSuspend = 0;
        detail.MinVolume = 1;
        detail.OrderPriceType = TThostFtdcOrderPriceTypeType.LIMIT_PRICE;
        detail.StopPrice = 0;
        detail.TimeCondition = TThostFtdcTimeConditionType.GFD;
        detail.VolumeCondition = TThostFtdcVolumeConditionType.ANY_VOLUME;
        // Register detail and active orders.
        this.mapper.register(detail, active);
        return this.traderApi.ReqOrderInsert(detail, OP.getIncrementID());
    }

    /**
     * Send action request to remote server. The method first checks the type
     * of the specified order to be canceled. If it is an order, just cancel it. If
     * an action and action can't be canceled, return error code.
     *
     * @param action action to send
     * @param alive alive order
     * @return error code, or 0 if successful
     */
    public int sendOrderAction(CThostFtdcInputOrderActionField action,
                               ActiveOrder alive) {
        if (!this.isConfirmed)
            throw new IllegalStateException("unconfirmed yet");
        this.flowWrt.writeReq(action);
        return this.traderApi.ReqOrderAction(action, OP.getIncrementID());
    }

    private void doLogin() {
        var req = new CThostFtdcReqUserLoginField();
        req.BrokerID = this.loginCfg.brokerID;
        req.UserID = this.loginCfg.userID;
        req.Password = this.loginCfg.password;
        var r = this.traderApi.ReqUserLogin(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().severe(
                    OP.formatLog("failed login request", null,
                            null, r));
    }

    private void doLogout() {
        var req = new CThostFtdcUserLogoutField();
        req.BrokerID = this.loginCfg.brokerID;
        req.UserID = this.loginCfg.userID;
        var r = this.traderApi.ReqUserLogout(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().warning(
                    OP.formatLog("failed logout request", null,
                            null, r));
    }

    private void doAuthentication() {
        var req = new CThostFtdcReqAuthenticateField();
        req.AppID = this.loginCfg.appID;
        req.AuthCode = this.loginCfg.authCode;
        req.BrokerID = this.loginCfg.brokerID;
        req.UserID = this.loginCfg.userID;
        req.UserProductInfo = this.loginCfg.userProductInfo;
        var r = this.traderApi.ReqAuthenticate(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().severe(
                    OP.formatLog("failed authentication", null,
                            null, OP.getIncrementID()));
    }

    private void doSettlement() {
        var req = new CThostFtdcSettlementInfoConfirmField();
        req.BrokerID = this.loginCfg.brokerID;
        req.AccountID = this.loginCfg.userID;
        req.InvestorID = this.loginCfg.userID;
        req.CurrencyID = "CNY";
        var r = this.traderApi.ReqSettlementInfoConfirm(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().severe(
                    OP.formatLog("failed confirm settlement", null,
                            null, OP.getIncrementID()));
    }

    private void doRspLogin(CThostFtdcRspUserLoginField rsp) {
        this.rspLogin = rsp;
        // Update order ref if max order ref goes after it.
        var maxOrderRef = Integer.parseInt(this.rspLogin.MaxOrderRef);
        if (maxOrderRef > this.orderRef.get())
            this.orderRef.set(maxOrderRef);
    }

    private String getOrderRef() {
        if (this.orderRef.get() == Integer.MAX_VALUE)
            this.orderRef.set(0);
        return String.valueOf(this.orderRef.incrementAndGet());
    }

    /*
     Construct a cancel return order from the specified error order.
     */
    private CThostFtdcOrderField toCancelRtnOrder(CThostFtdcInputOrderField rtn) {
        var cancel = new CThostFtdcOrderField();
        cancel.AccountID = rtn.AccountID;
        cancel.BrokerID = rtn.BrokerID;
        cancel.BusinessUnit = rtn.BusinessUnit;
        cancel.ClientID = rtn.ClientID;
        cancel.CombHedgeFlag = rtn.CombHedgeFlag;
        cancel.CombOffsetFlag = rtn.CombOffsetFlag;
        cancel.ContingentCondition = rtn.ContingentCondition;
        cancel.CurrencyID = rtn.CurrencyID;
        cancel.Direction = rtn.Direction;
        cancel.ExchangeID = rtn.ExchangeID;
        cancel.ForceCloseReason = rtn.ForceCloseReason;
        cancel.GTDDate = rtn.GTDDate;
        cancel.InstrumentID = rtn.InstrumentID;
        cancel.InvestorID = rtn.InvestorID;
        cancel.InvestUnitID = rtn.InvestUnitID;
        cancel.IPAddress = rtn.IPAddress;
        cancel.IsAutoSuspend = rtn.IsAutoSuspend;
        cancel.IsSwapOrder = rtn.IsSwapOrder;
        cancel.LimitPrice = rtn.LimitPrice;
        cancel.MacAddress = rtn.MacAddress;
        cancel.MinVolume = rtn.MinVolume;
        cancel.OrderPriceType = rtn.OrderPriceType;
        cancel.OrderRef = rtn.OrderRef;
        cancel.RequestID = rtn.RequestID;
        cancel.StopPrice = rtn.StopPrice;
        cancel.TimeCondition = rtn.TimeCondition;
        cancel.UserForceClose = rtn.UserForceClose;
        cancel.UserID = rtn.UserID;
        cancel.VolumeCondition = rtn.VolumeCondition;
        cancel.VolumeTotalOriginal = rtn.VolumeTotalOriginal;
        // Order status.
        cancel.OrderStatus = TThostFtdcOrderStatusType.CANCELED;
        cancel.OrderSubmitStatus = TThostFtdcOrderSubmitStatusType.CANCEL_SUBMITTED;
        return cancel;
    }

    private void doRtnOrder(CThostFtdcOrderField rtn) {
        var active = this.mapper.getActiveOrder(rtn.OrderRef);
        if (active == null) {
            this.config.getLogger().warning(
                    OP.formatLog("active order not found", rtn.OrderRef,
                            null, null));
            return;
        }
        // Adjust user.
        rtn.BrokerID = active.getOriginOrder().BrokerID;
        rtn.UserID = active.getOriginOrder().UserID;
        rtn.InvestorID = active.getOriginOrder().InvestorID;
        rtn.AccountID = active.getOriginOrder().AccountID;

        try {
            active.updateRtnOrder(rtn);
        } catch (Throwable th) {
            this.config.getLogger().severe(
                    OP.formatLog("failed update rtn order", rtn.OrderRef,
                            th.getMessage(), null));
        }
    }

    private void doRtnTrade(CThostFtdcTradeField trade) {
        var active = this.mapper.getActiveOrder(trade.OrderRef);
        if (active == null) {
            this.config.getLogger().warning(
                    OP.formatLog("active order not found", trade.OrderRef,
                            null, null));
            return;
        }
        // Adjust user.
        trade.BrokerID = active.getOriginOrder().BrokerID;
        trade.UserID = active.getOriginOrder().UserID;
        trade.InvestorID = active.getOriginOrder().InvestorID;

        try {
            active.updateTrade(trade);
        } catch (Throwable th) {
            this.config.getLogger().severe(
                    OP.formatLog("failed update rtn trade", trade.OrderRef,
                            th.getMessage(), null));
        }
    }

    private void doQueryInstr() {
        var req = new CThostFtdcQryInstrumentField();
        var r = this.traderApi.ReqQryInstrument(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().warning(
                    OP.formatLog("failed query instrument", null,
                            null, r));
    }

    @Override
    public void OnFrontConnected() {
        this.isConnected = true;
    }

    @Override
    public void OnFrontDisconnected(int reason) {
        this.config.getLogger().warning(
                OP.formatLog("trader disconnected", null, null,
                        reason));
        this.isConnected = false;
    }

    @Override
    public void OnErrRtnOrderAction(CThostFtdcOrderActionField orderAction,
                                    CThostFtdcRspInfoField rspInfo) {
        this.flowWrt.writeErr(orderAction);
        this.config.getLogger().warning(
                OP.formatLog("failed action", orderAction.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnErrRtnOrderInsert(CThostFtdcInputOrderField inputOrder,
                                    CThostFtdcRspInfoField rspInfo) {
        this.flowWrt.writeErr(inputOrder);
        this.config.getLogger().severe(
                OP.formatLog("failed order insertion", inputOrder.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
        // Failed order results in canceling the order.
        doRtnOrder(toCancelRtnOrder(inputOrder));
    }

    @Override
    public void OnRspAuthenticate(
            CThostFtdcRspAuthenticateField rspAuthenticateField,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        if (rspInfo.ErrorID == 0)
            doLogin();
        else
            this.config.getLogger().severe(
                    OP.formatLog("failed authentication", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField rspInfo, int requestId,
                           boolean isLast) {
        this.flowWrt.writeErr(rspInfo);
        this.config.getLogger().severe(
                OP.formatLog("unknown error", null, rspInfo.ErrorMsg,
                        rspInfo.ErrorID));
    }

    @Override
    public void OnRspOrderAction(CThostFtdcInputOrderActionField inputOrderAction,
                                 CThostFtdcRspInfoField rspInfo, int requestId,
                                 boolean isLast) {
        this.flowWrt.writeErr(inputOrderAction);
        this.config.getLogger().warning(
                OP.formatLog("failed action", inputOrderAction.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspOrderInsert(CThostFtdcInputOrderField inputOrder,
                                 CThostFtdcRspInfoField rspInfo, int requestId,
                                 boolean isLast) {
        this.flowWrt.writeErr(inputOrder);
        this.config.getLogger().severe(
                OP.formatLog("failed order insertion", inputOrder.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
        // Failed order results in canceling the order.
        doRtnOrder(toCancelRtnOrder(inputOrder));
    }

    @Override
    public void OnRspQryInstrument(CThostFtdcInstrumentField instrument,
                                   CThostFtdcRspInfoField rspInfo, int requestId,
                                   boolean isLast) {
        this.flowWrt.writeRsp(instrument);
        ConfigLoader.setInstrConfig(instrument);
        // Sync on instrument set.
        synchronized (this.instrs) {
            if (this.qryInstrLast)
                this.instrs.clear();
            this.instrs.add(instrument.InstrumentID);
            this.qryInstrLast = isLast;
        }
    }

    @Override
    public void OnRspQryInstrumentCommissionRate(
            CThostFtdcInstrumentCommissionRateField instrumentCommissionRate,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        this.flowWrt.writeRsp(instrumentCommissionRate);
        ConfigLoader.setInstrConfig(instrumentCommissionRate);
    }

    @Override
    public void OnRspQryInstrumentMarginRate(
            CThostFtdcInstrumentMarginRateField instrumentMarginRate,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        this.flowWrt.writeRsp(instrumentMarginRate);
        ConfigLoader.setInstrConfig(instrumentMarginRate);
    }

    @Override
    public void OnRspSettlementInfoConfirm(
            CThostFtdcSettlementInfoConfirmField settlementInfoConfirm,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        if (rspInfo.ErrorID == 0) {
            this.config.getLogger().fine(
                    OP.formatLog("successful login", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
            this.isConfirmed = true;
            // Query instruments.
            doQueryInstr();
        } else
            this.config.getLogger().severe(
                    OP.formatLog("failed settlement confirm", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField rspUserLogin,
                               CThostFtdcRspInfoField rspInfo, int requestId,
                               boolean isLast) {
        if (rspInfo.ErrorID == 0) {
            doSettlement();
            doRspLogin(rspUserLogin);
        } else
            this.config.getLogger().severe(
                    OP.formatLog("failed login", null, rspInfo.ErrorMsg,
                            rspInfo.ErrorID));
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField userLogout,
                                CThostFtdcRspInfoField rspInfo, int requestId,
                                boolean isLast) {
        if (rspInfo.ErrorID == 0) {
            this.config.getLogger().fine(
                    OP.formatLog("successful logout", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
            this.isConfirmed = false;
        } else
            this.config.getLogger().warning(
                    OP.formatLog("failed logout", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRtnOrder(CThostFtdcOrderField order) {
        this.flowWrt.writeRtn(order);
        this.mapper.register(order);
        doRtnOrder(order);
    }

    @Override
    public void OnRtnTrade(CThostFtdcTradeField trade) {
        this.flowWrt.writeRtn(trade);
        doRtnTrade(trade);
    }

    private class QueryTask extends TimerTask {
        private final Random rand = new Random();

        @Override
        public void run() {
            if (!qryInstrLast || !isConfirmed)
                return;
            doQuery();
        }

        private void doQuery() {
            String ins = randomGet();
            // Query.
            var req = new CThostFtdcQryInstrumentMarginRateField();
            req.BrokerID = loginCfg.brokerID;
            req.InvestorID = loginCfg.userID;
            req.HedgeFlag = TThostFtdcCombHedgeFlagType.SPECULATION;
            req.InstrumentID = ins;
            int r = traderApi.ReqQryInstrumentMarginRate(req,
                    OP.getIncrementID());
            if (r != 0)
                config.getLogger().warning(
                        OP.formatLog("failed query margin", null,
                                ins, r));
            // Sleep for 1.5 seconds
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                config.getLogger().warning(
                        OP.formatLog("failed sleep", null,
                                e.getMessage(), null));
            }

            var req0 = new CThostFtdcQryInstrumentCommissionRateField();
            req0.BrokerID = loginCfg.brokerID;
            req0.InvestorID = loginCfg.userID;
            req0.InstrumentID = ins;
            r = traderApi.ReqQryInstrumentCommissionRate(req0,
                    OP.getIncrementID());
            if (r != 0)
                config.getLogger().warning(
                        OP.formatLog("failed query commission", null,
                                ins, r));
        }

        private String randomGet() {
            synchronized (instrs) {
                return instrs.get(rand.nextInt() % instrs.size());
            }
        }
    }
}
