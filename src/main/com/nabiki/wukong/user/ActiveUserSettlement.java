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

import com.nabiki.ctp4j.jni.struct.CThostFtdcInvestorPositionDetailField;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.olap.FlowWriter;
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.core.User;
import com.nabiki.wukong.user.core.UserAccount;
import com.nabiki.wukong.user.core.UserPosition;
import com.nabiki.wukong.user.core.UserPositionDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ActiveUserSettlement {
    private final User user;
    private final Config config;
    private final FlowWriter flowWrt;

    ActiveUserSettlement(User user, Config cfg) {
        this.user = user;
        this.config = cfg;
        this.flowWrt = new FlowWriter(this.config);
    }

    void settle() {
        // Settle position.
        var settledUserPos = settlePosition();
        CThostFtdcInvestorPositionDetailField pd;
        if (settledUserPos == null) {
            pd = new CThostFtdcInvestorPositionDetailField();
            pd.Margin = 0;
            pd.CloseProfitByDate = 0;
            pd.PositionProfitByDate = 0;
        } else
            pd = settledUserPos.getCurrPD();
        // Settle account.
        var settledUserAcc = settleAccount(pd);
        this.user.settle(settledUserAcc, settledUserPos);
    }

    private UserPosition settlePosition() {
        var allPosition = this.user.getPosition().getAllPD();
        if (allPosition.size() == 0)
            return null;
        // Keep settled position.
        var settledPos = new HashMap<String, List<UserPositionDetail>>();
        // Settle all positions.
        for (var entry : allPosition.entrySet()) {
            var instr = entry.getKey();
            var depth = this.config.getDepthMarketData(instr);
            Objects.requireNonNull(depth, "depth market data null");
            if (!OP.validPrice(depth.SettlementPrice))
                throw new IllegalArgumentException(
                        "no settlement price for " + instr);
            // Create active settlement.
            var activePos = new ActivePositionSettlement(instr,
                    depth.SettlementPrice, this.config, entry.getValue());
            var r = activePos.settle();
            // Only keep the non-zero position.
            if (r.size() > 0) {
                settledPos.put(instr, r);
                // Write flow.
                for(var c : r)
                    this.flowWrt.writeSettle(c.getDeepCopyTotal());
            }
        }
        // Create settled user position.
        return new UserPosition(settledPos, this.user);
    }

    private UserAccount settleAccount(
            CThostFtdcInvestorPositionDetailField settledPD) {
        var account = this.user.getAccount();
        // Unset frozen account.
        account.cancel();
        var today = account.getDeepCopyTotal();
        // Calculate fields.
        today.CurrMargin = settledPD.Margin;
        today.CloseProfit = settledPD.CloseProfitByDate;
        today.PositionProfit = settledPD.PositionProfitByDate;
        today.Balance = today.PreBalance + (today.Deposit - today.Withdraw)
                + (today.CloseProfit + today.PositionProfit) - today.Commission;
        today.Available = today.Balance - today.CurrMargin;
        // Trading day.
        today.TradingDay = this.config.getTradingDay();
        // Write flow.
        this.flowWrt.writeSettle(today);
        // Create settled user account.
        return new UserAccount(today, this.user);
    }
}
