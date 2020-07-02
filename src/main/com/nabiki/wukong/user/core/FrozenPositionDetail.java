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
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.flag.AssetState;

public class FrozenPositionDetail {
    // The original position that own this frozen position.
    private final UserPositionDetail parent;
    private final CThostFtdcTradingAccountField frozenShareCash;
    // The share is asset change for each share before real trade happens.
    // When trade returns, use the return trade to generate another share info
    // to update the position.
    // This share info is used to calculate frozen margin or commission.
    private final CThostFtdcInvestorPositionDetailField frozenSharePD;

    private final long totalShareCount;
    private AssetState state = AssetState.ONGOING;
    private long tradedShareCount = 0;

    public FrozenPositionDetail(UserPositionDetail parent,
                                CThostFtdcInvestorPositionDetailField frzShare,
                                CThostFtdcTradingAccountField frzCash,
                                long totalShareCount) {
        this.parent = parent;
        this.frozenSharePD = frzShare;
        this.frozenShareCash = frzCash;
        this.totalShareCount = totalShareCount;
    }

    /**
     * Get frozen volume of this position detail.
     *
     * @return frozen volume
     */
    @InTeam
    public long getFrozenShareCount() {
        if (this.state == AssetState.CANCELED)
            return 0;
        else
            return this.totalShareCount - tradedShareCount;
    }

    /**
     * Close some volume(a part or all) of a close order.
     *
     * <p>When a close order trades, its frozen volume is decreased. If a close
     * order is canceled, all its frozen volume is released.
     * </p>
     *
     * @param tradeCnt traded volume of a close order
     */
    @InTeam
    public void closeShare(long tradeCnt) {
        if (tradeCnt < 0)
            throw new IllegalArgumentException("negative traded share count");
        if (getFrozenShareCount() < tradeCnt)
            throw new IllegalStateException("not enough frozen shares");
        this.tradedShareCount -= tradeCnt;
    }

    /**
     * Cancel a close order whose frozen volume is all released.
     */
    @InTeam
    public void cancel() {
        this.state = AssetState.CANCELED;
    }

    /**
     * Get the original position which this frozen position belongs to.
     *
     * @return the position which this frozen position belongs to
     */
    @InTeam
    public UserPositionDetail getParent() {
        return this.parent;
    }

    /**
     * Get the position detail represents 1 share of the closed position, whose
     * close profit and close amount are pre-calculated. And the margin and exchange
     * margin are set for 1 volume, and close volume is set to 1.
     *
     * @return pre-calculated closed position detail for 1 volume.
     */
    @InTeam
    public CThostFtdcInvestorPositionDetailField getFrozenSharePD() {
        return OP.deepCopy(this.frozenSharePD);
    }

    CThostFtdcTradingAccountField getFrozenShareAcc() {
        return OP.deepCopy(this.frozenShareCash);
    }
}
