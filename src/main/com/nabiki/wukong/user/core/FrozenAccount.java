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
import com.nabiki.wukong.user.flag.AssetState;

public class FrozenAccount {
    private final CThostFtdcTradingAccountField frozenShare;
    private final long totalShareCount;

    private AssetState state = AssetState.ONGOING;
    private long tradedShareCount = 0;

    public FrozenAccount(CThostFtdcTradingAccountField share, long shareCount) {
        this.frozenShare = share;
        this.totalShareCount = shareCount;
    }

    double getFrozenShareCount() {
        if (this.state == AssetState.CANCELED)
            return 0;
        else
            return this.totalShareCount - this.tradedShareCount;
    }

    CThostFtdcTradingAccountField getFrozenShare() {
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
     * @param tradeCnt traded volume
     */
    @InTeam
    public void openShare(long tradeCnt) {
        if (tradeCnt < 0)
            throw new IllegalArgumentException("negative traded share count");
        if (getFrozenShareCount() < tradeCnt)
            throw new IllegalStateException("not enough frozen shares");
        this.tradedShareCount -= tradeCnt;
    }
}
