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
import com.nabiki.ctp4j.md.CThostFtdcMdSpi;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.md.CandleEngine;
import com.nabiki.wukong.olap.FlowRouter;
import com.nabiki.wukong.tools.InTeam;

public class CTPTickProvider extends CThostFtdcMdSpi implements com.nabiki.wukong.api.TickProvider {
    private final Config config;

    public CTPTickProvider(Config cfg) {
        this.config = cfg;
    }

    @Override
    @InTeam
    public void register(CandleEngine engine) {

    }

    @Override
    @InTeam
    public void register(FlowRouter router) {

    }

    @Override
    public void OnFrontConnected() {
        super.OnFrontConnected();
    }

    @Override
    public void OnFrontDisconnected(int reason) {
        super.OnFrontDisconnected(reason);
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        super.OnRspError(rspInfo, requestId, isLast);
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField rspUserLogin, CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        super.OnRspUserLogin(rspUserLogin, rspInfo, requestId, isLast);
    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField userLogout, CThostFtdcRspInfoField rspInfo, int nRequestID, boolean isLast) {
        super.OnRspUserLogout(userLogout, rspInfo, nRequestID, isLast);
    }

    @Override
    public void OnRspSubMarketData(CThostFtdcSpecificInstrumentField specificInstrument, CThostFtdcRspInfoField rspInfo, int requestId, boolean isLast) {
        super.OnRspSubMarketData(specificInstrument, rspInfo, requestId, isLast);
    }

    @Override
    public void OnRspUnSubMarketData(CThostFtdcSpecificInstrumentField specificInstrument, CThostFtdcRspInfoField rspInfo, int nRequestID, boolean isLast) {
        super.OnRspUnSubMarketData(specificInstrument, rspInfo, nRequestID, isLast);
    }

    @Override
    public void OnRtnDepthMarketData(CThostFtdcDepthMarketDataField depthMarketData) {
        super.OnRtnDepthMarketData(depthMarketData);
    }
}
