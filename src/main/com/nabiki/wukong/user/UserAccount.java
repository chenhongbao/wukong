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

import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;

import java.util.LinkedList;
import java.util.List;

public class UserAccount {
    private final User parent;
    private final CThostFtdcTradingAccountField total;
    private final List<FrozenAccount> frozenAcc = new LinkedList<>();

    UserAccount(CThostFtdcTradingAccountField total, User parent) {
        this.total = total;
        this.parent = parent;
    }

    User getParent() {
        return this.parent;
    }

    void addShareCommission(CThostFtdcTradingAccountField share, long tradeCnt) {
        this.total.Commission += share.Commission * tradeCnt;
    }

    void cancel() {
        for (var acc : this.frozenAcc)
            acc.cancel();
    }

    double getFrozenCash() {
        double frz = 0.0D;
        for (var c : this.frozenAcc)
            frz += c.getFrozenShareCount() * c.getFrozenShare().FrozenCash;
        return frz;
    }

    double getFrozenCommission() {
        double frz = 0.0D;
        for (var c: this.frozenAcc)
            frz += c.getFrozenShareCount() * c.getFrozenShare().FrozenCommission;
        return frz;
    }

    void addFrozenAccount(FrozenAccount frz) {
        this.frozenAcc.add(frz);
    }
}
