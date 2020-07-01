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

import com.nabiki.ctp4j.jni.struct.CThostFtdcInvestorPositionDetailField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.annotation.InTeam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserPosition {
    private final User parent;
    private final Map<String, List<UserPositionDetail>> userPD = new HashMap<>();

    public UserPosition(Map<String, List<UserPositionDetail>> pd, User parent) {
        this.userPD.putAll(pd);
        this.parent = parent;
    }

    User getParent() {
        return this.parent;
    }

    @InTeam
    public Map<String, List<UserPositionDetail>> getAllPD() {
        return this.userPD;
    }

    /**
     * Get the specified position details of an instrument. If user doesn't have
     * position of the instrument, return {@code null}.
     *
     * @param instrID instrument ID
     * @return position details of the instrument
     */
    @InTeam
    public List<UserPositionDetail> getUserPD(String instrID) {
        return this.userPD.get(instrID);
    }

    CThostFtdcTradingAccountField getCurrAccount() {
        var r = new CThostFtdcTradingAccountField();
        r.FrozenCommission = 0;
        r.FrozenMargin = 0;
        for (var lst : this.userPD.values())
            for (var p : lst) {
                r.FrozenCommission = p.getFrozenCommission();
                r.FrozenMargin += p.getFrozenMargin();
            }
        return r;
    }

    @InTeam
    public CThostFtdcInvestorPositionDetailField getCurrPD() {
        var r= new CThostFtdcInvestorPositionDetailField();
        r.Margin = 0.;
        r.CloseProfitByTrade = 0.;
        r.CloseProfitByDate = 0;
        r.PositionProfitByTrade = 0;
        r.PositionProfitByDate = 0;
        for (var lst : this.userPD.values())
            for (var c : lst) {
                var p = c.getDeepCopyTotal();
                r.Margin += p.Margin;
                r.CloseProfitByDate += p.CloseProfitByDate;
                r.CloseProfitByTrade += p.CloseProfitByTrade;
                r.PositionProfitByDate += p.PositionProfitByDate;
                r.PositionProfitByTrade += p.PositionProfitByTrade;
            }
        return r;
    }
}
