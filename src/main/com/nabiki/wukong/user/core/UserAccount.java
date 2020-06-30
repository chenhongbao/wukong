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
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.InTeam;

import java.util.LinkedList;
import java.util.List;

public class UserAccount {
    private final User parent;
    private final CThostFtdcTradingAccountField total;
    private final List<FrozenAccount> frozenAcc = new LinkedList<>();

    public UserAccount(CThostFtdcTradingAccountField total, User parent) {
        this.total = total;
        this.parent = parent;
    }

    /**
     * Get the {@link User} that owns this account.
     *
     * @return user object that owns this account
     */
    @InTeam
    public User getParent() {
        return this.parent;
    }

    @InTeam
    public CThostFtdcTradingAccountField getDeepCopyTotal() {
        return OP.deepCopy(this.total);
    }

    /**
     * Add commission for traded order. The order can be open or close.
     *
     * @param share the account having the commission for 1 traded volume
     * @param tradeCnt traded volume
     */
    @InTeam
    public void addShareCommission(CThostFtdcTradingAccountField share, long tradeCnt) {
        this.total.Commission += share.Commission * tradeCnt;
    }

    @InTeam
    public void cancel() {
        for (var acc : this.frozenAcc)
            acc.cancel();
    }

    CThostFtdcTradingAccountField getCurrAccount() {
        var r = new CThostFtdcTradingAccountField();
        r.FrozenCash = 0;
        r.FrozenCommission = 0;
        for (var c : this.frozenAcc) {
            r.FrozenCash += c.getFrozenShareCount() * c.getFrozenShare().FrozenCash;
            r.FrozenCommission += c.getFrozenShareCount()
                    * c.getFrozenShare().FrozenCommission;
        }
        return r;
    }

    /**
     * Add frozen account for a new open order.
     *
     * @param frz new frozen account
     */
    @InTeam
    public void addFrozenAccount(FrozenAccount frz) {
        this.frozenAcc.add(frz);
    }
}
