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

package com.nabiki.wukong.cnf;

import com.nabiki.wukong.cnf.plain.LoginConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Config {
    final Map<String, LoginConfig> login = new HashMap<>();
    final Map<String, TradingHourKeeper> tradingHour = new HashMap<>();

    // Instrument product ID pattern.
    static Pattern productPattern = Pattern.compile("[a-zA-Z]+");

    /**
     * Get login configurations.
     *
     * @return {@link Map} of {@link LoginConfig#name} and {@link LoginConfig}
     */
    public Map<String, LoginConfig> getLoginConfigs() {
        synchronized (this.login) {
            return this.login;
        }
    }

    /**
     * Get trading hour configuration of the specified product ID and instrument ID.
     * The method first gets the configuration with the specified product ID and
     * returns the result. If the product ID is null, then the method tries with the
     * instrument ID.
     *
     * @param proID product ID, if {@code null} the method turns to instrument ID
     * @param instrID instrument ID
     * @return trading hour configuration
     */
    public TradingHourKeeper getTradingHour(String proID, String instrID) {
        synchronized (this.tradingHour) {
            if (proID != null)
                return this.tradingHour.get(proID);
            else
                return this.tradingHour.get(productID(instrID));
        }
    }

    static String productID(String instrID) {
        var m = productPattern.matcher(instrID);
        if (m.find())
            return instrID.substring(m.start(), m.end()).toLowerCase();
        else
            return null;
    }

    Config() {
    }
}
