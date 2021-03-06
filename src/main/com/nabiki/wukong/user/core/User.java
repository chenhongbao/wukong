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

package com.nabiki.wukong.user.core;

import com.nabiki.ctp4j.jni.struct.CThostFtdcRspInfoField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.tools.InTeam;
import com.nabiki.wukong.tools.OutTeam;
import com.nabiki.wukong.user.plain.InstrumentInfoSet;
import com.nabiki.wukong.user.plain.SettlementPrices;
import com.nabiki.wukong.user.plain.UserState;

public class User {
    private UserPosition position;
    private UserAccount account;
    private UserState state = UserState.RENEW;
    private final CThostFtdcRspInfoField panicReason = new CThostFtdcRspInfoField();

    public User() {}

    /**
     * Get current trading account<b>(not settled account)</b>. The method always
     * return account object, not {@code null}.
     *
     * @return current trading account.
     */
    @OutTeam
    public CThostFtdcTradingAccountField getTradingAccount() {
        var total = this.account.getDeepCopyTotal();
        // Calculate fields from account and position.
        var posCurrAcc = this.position.getCurrAccount();
        var posCurrPos = this.position.getCurrPD();
        var accCurr = this.account.getCurrAccount();
        // Summarize.
        total.FrozenCash = accCurr.FrozenCash;
        total.FrozenCommission = accCurr.FrozenCommission
                + posCurrAcc.FrozenCommission;
        total.FrozenMargin = posCurrPos.Margin;
        total.Balance = total.PreBalance + (total.Deposit - total.Withdraw)
                + posCurrPos.CloseProfitByDate - total.Commission;
        total.Available = total.Balance - total.FrozenMargin
                - total.FrozenCommission - total.FrozenCash;
        return total;
    }

    @InTeam
    public void setPanic(int code, String msg) {
        this.state = UserState.PANIC;
        this.panicReason.ErrorID = code;
        this.panicReason.ErrorMsg = msg;
    }

    @InTeam
    CThostFtdcRspInfoField getPanicReason() {
        return this.panicReason;
    }

    @InTeam
    public UserState getState() {
        return this.state;
    }

    @InTeam
    public UserPosition getPosition() {
        return this.position;
    }

    @InTeam
    public UserAccount getAccount() {
        return this.account;
    }

    @InTeam
    public void settle(SettlementPrices prices, InstrumentInfoSet info,
                       String tradingDay) {
        // First position, then account.
        this.position.settle(prices, info, tradingDay);
        this.account.settle(this.position.getCurrPD(), tradingDay);
        this.state = UserState.SETTLED;
    }

    public void setAccount(UserAccount usrAccount) {
        this.account = usrAccount;
    }

    public void setPosition(UserPosition usrPosition) {
        this.position = usrPosition;
    }
}
