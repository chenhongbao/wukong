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

import com.nabiki.ctp4j.jni.flag.ThostTeResumeType;
import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.ctp4j.trader.CThostFtdcTraderApi;
import com.nabiki.ctp4j.trader.CThostFtdcTraderSpi;
import com.nabiki.wukong.annotation.OutTeam;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.user.AliveOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code AliveOrderManager} keeps the status of all alive orders, interacts with
 * JNI interfaces and invoke callback methods to process responses.
 */
public class AliveOrderManager extends CThostFtdcTraderSpi {
    private final Map<String, AliveOrder>
            ref2alive = new HashMap<>();    // Detail ref -> alive order
    private final Map<UUID, AliveOrder>
            uuid2alive = new HashMap<>();   // UUID -> alive order
    private final Map<AliveOrder, Set<String>>
            alive2order = new HashMap<>();  // Alive order -> detail ref
    private final Map<String, CThostFtdcOrderField>
            ref2order = new HashMap<>();    // Detail ref -> detail rtn order
    private final Map<String, CThostFtdcInputOrderField>
            ref2det = new HashMap<>();      // Detail ref -> detail order

    private final Config config;
    private final LoginConfig loginCfg;
    private final CThostFtdcTraderApi traderApi;

    AliveOrderManager(Config cfg) {
        this.config = cfg;
        this.loginCfg = this.config.getLoginConfigs().get("trader");
        this.traderApi = CThostFtdcTraderApi.CreateFtdcTraderApi(
                this.loginCfg.flowDirectory);
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
     * Save the mapping from the specified input order to the specified alive order,
     * then send the specified input order to remote server.
     *
     * <p>If the remote service is temporarily unavailable, the order is saved to
     * send at next market open.
     * </p>
     *
     * @param order input order
     * @param alive alive order
     * @return returned value from JNI call
     */
    public int sendDetailOrder(CThostFtdcInputOrderField order, AliveOrder alive) {
        // TODO send order
        return 0;
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
                               AliveOrder alive) {
        // TODO send action
        return 0;
    }

    /**
     * Get the specified return order of the detail ref. If no order has the UUID,
     * return {@code null}.
     *
     * @param detailRef ref of the order
     * @return last updated return order, or {@code null} if no order has the UUID
     */
    public CThostFtdcOrderField getRtnOrder(String detailRef) {
        // TODO get return order with the ref
        return null;
    }

    /**
     * Get all detail order refs under the specified {@link UUID}. If no mapping
     * found, return an empty set.
     *
     * @param uuid UUID of the alive order that issues the detail orders
     * @return {@link Set} of detail order refs
     */
    public Set<String> getDetailRef(UUID uuid) {
        // TODO get detail refs
        return null;
    }

    /**
     * Get detail order of the specified detail ref. If no mapping found, return
     * {@code null}.
     *
     * @param detailRef detail order reference
     * @return detail order, or {@code null} if no such ref
     */
    public CThostFtdcInputOrderField getDetailOrder(String detailRef) {
        // TODO get detail order
        return null;
    }

    private void doLogin() {
        // TODO login
    }

    private void doAuthentication() {
        // TODO authenticate
    }

    private void doSettlement() {
        // TODO confirm settlement
    }

    private String formatLog(String hint, String orderRef, String errMsg,
                             int errCode) {
        String sb = hint + "[" + orderRef + "]" + errMsg +
                "(" + errCode + ")";
        return sb;
    }

    /*
     Construct a cancel return order from the specified error order.
     */
    private CThostFtdcOrderField toCancelRtnOrder(CThostFtdcInputOrderField rtn) {
        // TODO convert to cancel order
        return null;
    }

    /*
     Callback with the specified return order. The method follows the below steps:
     1. Save the order status.
     2. Check the status. If it is a cancel, check if the detail order has been
        canceled. If the order hasn't been canceled, cancel it.
     3. If it is a normal return, update order.
     */
    private void doRtnOrder(CThostFtdcOrderField rtn) {
        // TODO do rtn order
    }

    private void doRtnTrade(CThostFtdcTradeField trade) {
        // TODO do rtn trade
    }

    @Override
    public void OnFrontConnected() {
        doAuthentication();
    }

    @Override
    public void OnFrontDisconnected(int reason) {
        this.config.getLogger().warning(
                formatLog("trader disconnected", null, null,
                        reason));
    }

    @Override
    public void OnErrRtnOrderAction(CThostFtdcOrderActionField orderAction,
                                    CThostFtdcRspInfoField rspInfo) {
        this.config.getLogger().warning(
                formatLog("failed action", orderAction.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnErrRtnOrderInsert(CThostFtdcInputOrderField inputOrder,
                                    CThostFtdcRspInfoField rspInfo) {
        this.config.getLogger().severe(
                formatLog("failed order insertion", inputOrder.OrderRef,
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
                    formatLog("failed authentication", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField rspInfo, int requestId,
                           boolean isLast) {
        this.config.getLogger().severe(
                formatLog("unknown error", null, rspInfo.ErrorMsg,
                        rspInfo.ErrorID));
    }

    @Override
    public void OnRspOrderAction(CThostFtdcInputOrderActionField inputOrderAction,
                                 CThostFtdcRspInfoField rspInfo, int requestId,
                                 boolean isLast) {
        this.config.getLogger().warning(
                formatLog("failed action", inputOrderAction.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspOrderInsert(CThostFtdcInputOrderField inputOrder,
                                 CThostFtdcRspInfoField rspInfo, int requestId,
                                 boolean isLast) {
        this.config.getLogger().severe(
                formatLog("failed order insertion", inputOrder.OrderRef,
                        rspInfo.ErrorMsg, rspInfo.ErrorID));
        // Failed order results in canceling the order.
        doRtnOrder(toCancelRtnOrder(inputOrder));
    }

    @Override
    public void OnRspQryInstrument(CThostFtdcInstrumentField instrument,
                                   CThostFtdcRspInfoField rspInfo, int requestId,
                                   boolean isLast) {
        // TODO rsp qry
    }

    @Override
    public void OnRspQryInstrumentCommissionRate(
            CThostFtdcInstrumentCommissionRateField instrumentCommissionRate,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        // TODO rsp qry
    }

    @Override
    public void OnRspQryInstrumentMarginRate(
            CThostFtdcInstrumentMarginRateField instrumentMarginRate,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        // TODO rsp qry
    }

    @Override
    public void OnRspSettlementInfoConfirm(
            CThostFtdcSettlementInfoConfirmField settlementInfoConfirm,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        if (rspInfo.ErrorID == 0)
            this.config.getLogger().fine(
                    formatLog("successful login", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
        else
            this.config.getLogger().severe(
                    formatLog("failed settlement confirm", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField rspUserLogin,
                               CThostFtdcRspInfoField rspInfo, int requestId,
                               boolean isLast) {
        if (rspInfo.ErrorID == 0)
            doSettlement();
        else
            this.config.getLogger().severe(
                    formatLog("failed login", null, rspInfo.ErrorMsg,
                            rspInfo.ErrorID));
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField userLogout,
                                CThostFtdcRspInfoField rspInfo, int requestId,
                                boolean isLast) {
        if (rspInfo.ErrorID == 0)
            this.config.getLogger().fine(
                    formatLog("successful logout", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
        else
            this.config.getLogger().warning(
                    formatLog("failed logout", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
    }

    @Override
    public void OnRtnOrder(CThostFtdcOrderField order) {
        doRtnOrder(order);
    }

    @Override
    public void OnRtnTrade(CThostFtdcTradeField trade) {
        doRtnTrade(trade);
    }
}
