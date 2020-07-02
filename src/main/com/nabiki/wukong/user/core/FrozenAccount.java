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

import com.nabiki.ctp4j.jni.struct.CThostFtdcInstrumentCommissionRateField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInstrumentField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradeField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.tools.InTeam;
import com.nabiki.wukong.user.plain.AssetState;

public class FrozenAccount {
    private final CThostFtdcTradingAccountField frozenShare;
    private final long totalShareCount;
    private final UserAccount parent;

    private AssetState state = AssetState.ONGOING;
    private long tradedShareCount = 0;

    public FrozenAccount(UserAccount parent, CThostFtdcTradingAccountField share, long shareCount) {
        this.parent = parent;
        this.frozenShare = share;
        this.totalShareCount = shareCount;
    }

    /**
     * Add this frozen account to its parent's frozen list. Then this account is
     * calculated as frozen.
     */
    @InTeam
    public void setFrozen() {
        this.parent.addFrozenAccount(this);
    }

    double getFrozenVolume() {
        if (this.state == AssetState.CANCELED)
            return 0;
        else
            return this.totalShareCount - this.tradedShareCount;
    }

    CThostFtdcTradingAccountField getSingleFrozen() {
        return this.frozenShare;
    }

    /**
     * Cancel an open order whose frozen account is also canceled.
     */
    @InTeam
    public void cancel() {
        this.state = AssetState.CANCELED;
    }

    /**
     * An open order is traded(or partly) whose frozen account is also decreased.
     *
     * @param trade trade response
     * @param instr instrument
     * @param comm commission
     */
    @InTeam
    public void updateOpenTrade(CThostFtdcTradeField trade,
                                CThostFtdcInstrumentField instr,
                                CThostFtdcInstrumentCommissionRateField comm) {
        if (trade.Volume < 0)
            throw new IllegalArgumentException("negative traded share count");
        if (getFrozenVolume() < trade.Volume)
            throw new IllegalStateException("not enough frozen shares");
        this.tradedShareCount -= trade.Volume;
        // Update parent.
        this.parent.addShareCommission(trade, instr, comm);
    }
}
