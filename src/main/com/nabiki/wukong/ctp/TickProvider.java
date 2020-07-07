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

import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.ctp4j.md.CThostFtdcMdApi;
import com.nabiki.ctp4j.md.CThostFtdcMdSpi;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.journal.MessageWriter;
import com.nabiki.wukong.md.CandleEngine;
import com.nabiki.wukong.md.MarketDataRouter;
import com.nabiki.wukong.tools.InTeam;
import com.nabiki.wukong.tools.OP;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TickProvider extends CThostFtdcMdSpi {
    private final Config config;
    private final LoginConfig loginCfg;
    private final CThostFtdcMdApi mdApi;
    private final MessageWriter flowWrt;
    private final Set<MarketDataRouter> routers = new HashSet<>();
    private final Set<CandleEngine> engines = new HashSet<>();

    private boolean isConnected = false,
            isLogin = false;
    private WorkingState workingState = WorkingState.STOPPED;

    public TickProvider(CThostFtdcMdApi mdApi, Config cfg) {
        this.config = cfg;
        this.mdApi = mdApi;
        this.loginCfg = this.config.getLoginConfigs().get("md");
        this.flowWrt = new MessageWriter(this.config);
    }

    @InTeam
    public void register(CandleEngine engine) {
        synchronized (this.engines) {
            this.engines.add(engine);
        }
    }

    @InTeam
    public void register(MarketDataRouter router) {
        synchronized (this.routers) {
            this.routers.add(router);
        }
    }

    @InTeam
    public void subscribe(List<String> instr) {
        if (instr == null || instr.size() == 0)
            return;
        var ins = new String[50];
        int count = -1;
        var iter = instr.iterator();
        while (true) {
            while (iter.hasNext() && ++count < 50)
                ins[count] = iter.next();
            // Subscribe batch.
            subscribeBatch(ins, count);
            count = -1;
            if (!iter.hasNext())
                break;
            else {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    this.config.getLogger().warning(
                            OP.formatLog("failed sleep", null,
                                    e.getMessage(), null));
                }
            }
        }
    }

    @InTeam
    public void initialize() {
        this.mdApi.RegisterSpi(this);
        for (var addr : this.loginCfg.frontAddresses)
            this.mdApi.RegisterFront(addr);
        this.mdApi.Init();
    }

    @InTeam
    public void release() {
        // Set states.
        this.isLogin = false;
        this.isConnected = false;
        this.workingState = WorkingState.STOPPED;
        setWorking(false);
        // Release resources.
        this.mdApi.Release();
    }

    @InTeam
    public void login() {
        if (!this.isConnected)
            throw new IllegalStateException("not connected");
        this.workingState = WorkingState.STARTING;
        doLogin();
    }

    @InTeam
    public void logout() {
        if (!this.isLogin)
            throw new IllegalStateException("repeated logout");
        this.workingState = WorkingState.STOPPING;
        doLogout();
    }

    @InTeam
    public WorkingState getWorkingState() {
        return this.workingState;
    }

    private void subscribeBatch(String[] instr, int count) {
        this.mdApi.SubscribeMarketData(instr, count);
    }

    private void doLogin() {
        var req = new CThostFtdcReqUserLoginField();
        req.BrokerID = this.loginCfg.brokerID;
        req.UserID = this.loginCfg.userID;
        req.Password = this.loginCfg.password;
        var r = this.mdApi.ReqUserLogin(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().severe(
                    OP.formatLog("failed login request", null,
                            null, r));
    }

    private void doLogout() {
        var req = new CThostFtdcUserLogoutField();
        req.BrokerID = this.loginCfg.brokerID;
        req.UserID = this.loginCfg.userID;
        var r = this.mdApi.ReqUserLogout(req, OP.getIncrementID());
        if (r != 0)
            this.config.getLogger().warning(
                    OP.formatLog("failed logout request", null,
                            null, r));
    }

    private void setWorking(boolean working) {
        synchronized (this.engines) {
            for (var e : this.engines)
                e.setWorking(working);
        }
    }

    @Override
    public void OnFrontConnected() {
        this.isConnected = true;
        if (this.workingState == WorkingState.STARTING
                || this.workingState == WorkingState.STARTED)
            doLogin();
    }

    @Override
    public void OnFrontDisconnected(int reason) {
        this.config.getLogger().warning(
                OP.formatLog("md disconnected", null, null,
                        reason));
        this.isLogin = false;
        this.isConnected = false;
        // If disconnected when or after provider stops, candle engine isn't working.
        // But if disconnected in work time, it is still working.
        if (this.workingState == WorkingState.STOPPING
                || this.workingState == WorkingState.STOPPED)
            setWorking(false);
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
    public void OnRspUserLogin(CThostFtdcRspUserLoginField rspUserLogin,
                               CThostFtdcRspInfoField rspInfo, int requestId,
                               boolean isLast) {
        if (rspInfo.ErrorID == 0) {
            this.isLogin = true;
            this.workingState = WorkingState.STARTED;
            setWorking(true);
        } else {
            this.config.getLogger().severe(
                    OP.formatLog("failed login", null, rspInfo.ErrorMsg,
                            rspInfo.ErrorID));
            this.flowWrt.writeErr(rspInfo);
        }
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField userLogout,
                                CThostFtdcRspInfoField rspInfo, int nRequestID,
                                boolean isLast) {
        if (rspInfo.ErrorID == 0) {
            this.isLogin = false;
            this.workingState = WorkingState.STOPPED;
            setWorking(false);
        } else {
            this.config.getLogger().warning(
                    OP.formatLog("failed logout", null,
                            rspInfo.ErrorMsg, rspInfo.ErrorID));
            this.flowWrt.writeErr(rspInfo);
        }
    }

    @Override
    public void OnRspSubMarketData(
            CThostFtdcSpecificInstrumentField specificInstrument,
            CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        if (rspInfo.ErrorID != 0) {
            this.config.getLogger().warning(OP.formatLog(
                    "failed subscription", specificInstrument.InstrumentID,
                    rspInfo.ErrorMsg, rspInfo.ErrorID));
            this.flowWrt.writeErr(rspInfo);
        }
    }

    @Override
    public void OnRspUnSubMarketData(
            CThostFtdcSpecificInstrumentField specificInstrument,
            CThostFtdcRspInfoField rspInfo, int nRequestID, boolean isLast) {
        if (rspInfo.ErrorID != 0) {
            this.config.getLogger().warning(OP.formatLog(
                    "failed un-subscription", specificInstrument.InstrumentID,
                    rspInfo.ErrorMsg, rspInfo.ErrorID));
            this.flowWrt.writeErr(rspInfo);
        }
    }

    @Override
    public void OnRtnDepthMarketData(CThostFtdcDepthMarketDataField depthMarketData) {
        synchronized (this.routers) {
            for (var r : this.routers)
                r.route(depthMarketData);
        }
        synchronized (this.engines) {
            for (var e : this.engines)
                e.update(depthMarketData);
        }
    }
}
