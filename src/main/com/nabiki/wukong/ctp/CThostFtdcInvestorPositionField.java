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

public class CThostFtdcInvestorPositionField {
    public String InstrumentID;
    public String BrokerID;
    public String InvestorID;
    public byte PosiDirection;
    public byte HedgeFlag;
    public String PositionDate;
    public int YdPosition;
    public int Position;
    public int LongFrozen;
    public int ShortFrozen;
    public double LongFrozenAmount;
    public double ShortFrozenAmount;
    public int OpenVolume;
    public int CloseVolume;
    public double OpenAmount;
    public double CloseAmount;
    public double PositionCost;
    public double PreMargin;
    public double UseMargin;
    public double FrozenMargin;
    public double FrozenCash;
    public double FrozenCommission;
    public double CashIn;
    public double Commission;
    public double CloseProfit;
    public double PositionProfit;
    public double PreSettlementPrice;
    public double SettlementPrice;
    public String TradingDay;
    public int SettlementID;
    public double OpenCost;
    public double ExchangeMargin;
    public int CombPosition;
    public int CombLongFrozen;
    public int CombShortFrozen;
    public double CloseProfitByDate;
    public double CloseProfitByTrade;
    public int TodayPosition;
    public double MarginRateByMoney;
    public double MarginRateByVolume;
    public int StrikeFrozen;
    public double StrikeFrozenAmount;
    public int AbandonFrozen;
    public String ExchangeID;
    public int YdStrikeFrozen;
    public String InvestUnitID;
    public double PositionCostOffset;

    public CThostFtdcInvestorPositionField() {}
}
