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

import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.annotation.OutTeam;
import com.nabiki.wukong.user.flag.UserState;

public class User {
    private UserPosition position;
    private UserAccount account;

    private UserPosition settledPosition;
    private UserAccount settledAccount;

    private UserState state = UserState.RENEW;

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
    public void settle(UserAccount settledAccount, UserPosition settledPosition) {
        this.state = UserState.SETTLED;
        this.settledAccount = settledAccount;
        this.settledPosition = settledPosition;
    }

    @InTeam
    public UserPosition getPosition() {
        if (this.state == UserState.RENEW)
            return this.position;
        else
            return this.settledPosition;
    }

    @InTeam
    public UserAccount getAccount() {
        if (this.state == UserState.RENEW)
            return this.account;
        else
            return this.settledAccount;
    }

    void setAccount(UserAccount account) {
        this.account = account;
    }

    void setPosition(UserPosition position) {
        this.position = position;
    }
}
