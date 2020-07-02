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

import com.nabiki.ctp4j.jni.flag.TThostFtdcDirectionType;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.core.UserPositionDetail;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ActivePositionSettlement {
    private final double settledPrice;
    private final String instrID;
    private final Config config;
    private final Collection<UserPositionDetail> position;

    ActivePositionSettlement(String instrID, double settledPrice, Config cfg,
                             Collection<UserPositionDetail> position) {
        this.position = position;
        this.instrID = instrID;
        this.settledPrice = settledPrice;
        this.config = cfg;
    }

    List<UserPositionDetail> settle() {
        // Get info.
        var info = this.config.getInstrInfo(this.instrID);
        Objects.requireNonNull(info, "instr info null");
        var instr = info.instrument;
        var margin = info.margin;
        Objects.requireNonNull(instr, "instrument null");
        Objects.requireNonNull(margin, "margin null");
        // Settled position to bre return.
        var settledPosition =  new LinkedList<UserPositionDetail>();
        for (var p : this.position) {
            // Unset frozen position.
            p.cancel();
            /*
            Keep original volume because the close volume is also kept.
            When the settled position loaded for next day, the volume and close
            volume/amount/profit will be adjusted:
            1. volume -= close volume
            2. close volume = 0
            3. close amount = 0;
            4/ close profits = 0;
             */
            var origin = p.getDeepCopyTotal();
            origin.SettlementPrice = this.settledPrice;
            if (origin.Volume == 0)
                continue;
            if (origin.Volume < 0) {
                this.config.getLogger().severe(
                        OP.formatLog("position volume less than zero",
                                String.valueOf(origin.Volume), origin.InstrumentID,
                                null));
                continue;
            }
            // Calculate new position detail, the close profit/volume/amount are
            // updated on return trade, just calculate the position's fields.
            double token;
            if (origin.Direction == TThostFtdcDirectionType.DIRECTION_BUY) {
                // Margin.
                if (margin.LongMarginRatioByMoney > 0)
                    origin.Margin = origin.Volume * this.settledPrice
                            * instr.VolumeMultiple * margin.LongMarginRatioByMoney;
                else
                    origin.Margin = origin.Volume * margin.LongMarginRatioByVolume;
                // Long position, token is positive.
                token = 1.0D;
            } else {
                // Margin.
                if (margin.ShortMarginRatioByMoney > 0)
                    origin.Margin = origin.Volume * this.settledPrice
                            * instr.VolumeMultiple * margin.ShortMarginRatioByMoney;
                else
                    origin.Margin = origin.Volume * margin.ShortMarginRatioByVolume;
                // Short position, token is negative.
                token = -1.0D;
            }
            // ExchMargin.
            origin.ExchMargin = origin.Margin;
            // Position profit.
            origin.PositionProfitByTrade = token * origin.Volume *
                    (origin.SettlementPrice - origin.OpenPrice)
                    * instr.VolumeMultiple;
            if (origin.TradingDay.compareTo(this.config.getTradingDay()) == 0)
                // Today position, open price is real open price.
                origin.PositionProfitByDate = origin.PositionProfitByTrade;
            else
                // History position, open price is last settlement price.
                origin.PositionProfitByDate = token * origin.Volume *
                        (origin.SettlementPrice - origin.LastSettlementPrice)
                        * instr.VolumeMultiple;
            // Save settled position.
            settledPosition.add(new UserPositionDetail(origin));
        }
        return settledPosition;
    }
}
