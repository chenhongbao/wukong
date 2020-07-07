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

package com.nabiki.wukong.sim;

import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.ctp4j.trader.CThostFtdcTraderApi;
import com.nabiki.ctp4j.trader.CThostFtdcTraderSpi;

class SimTraderApi extends CThostFtdcTraderApi {

    @Override
    public String GetApiVersion() {
        return null;
    }

    @Override
    public String GetTradingDay() {
        return null;
    }

    @Override
    public void Init() {

    }

    @Override
    public void Join() {

    }

    @Override
    public void SubscribePrivateTopic(int type) {

    }

    @Override
    public void SubscribePublicTopic(int type) {

    }

    @Override
    public void RegisterFront(String frontAddress) {

    }

    @Override
    public void RegisterSpi(CThostFtdcTraderSpi spi) {

    }

    @Override
    public void Release() {

    }

    @Override
    public int ReqAuthenticate(CThostFtdcReqAuthenticateField reqAuthenticateField, int requestID) {
        return 0;
    }

    @Override
    public int ReqUserLogin(CThostFtdcReqUserLoginField reqUserLoginField, int requestID) {
        return 0;
    }

    @Override
    public int ReqUserLogout(CThostFtdcUserLogoutField userLogout, int requestID) {
        return 0;
    }

    @Override
    public int ReqSettlementInfoConfirm(CThostFtdcSettlementInfoConfirmField settlementInfoConfirm, int requestID) {
        return 0;
    }

    @Override
    public int ReqOrderInsert(CThostFtdcInputOrderField inputOrder, int requestID) {
        return 0;
    }

    @Override
    public int ReqOrderAction(CThostFtdcInputOrderActionField inputOrderAction, int requestID) {
        return 0;
    }

    @Override
    public int ReqQryInstrument(CThostFtdcQryInstrumentField qryInstrument, int requestID) {
        return 0;
    }

    @Override
    public int ReqQryInstrumentCommissionRate(CThostFtdcQryInstrumentCommissionRateField qryInstrumentCommissionRate, int requestID) {
        return 0;
    }

    @Override
    public int ReqQryInstrumentMarginRate(CThostFtdcQryInstrumentMarginRateField qryInstrumentMarginRate, int requestID) {
        return 0;
    }

    @Override
    public int ReqQryTradingAccount(CThostFtdcQryTradingAccountField qryTradingAccount, int requestID) {
        return 0;
    }

    @Override
    public int ReqQryInvestorPositionDetail(CThostFtdcQryInvestorPositionDetailField qryInvestorPositionDetail, int requestID) {
        return 0;
    }
}
