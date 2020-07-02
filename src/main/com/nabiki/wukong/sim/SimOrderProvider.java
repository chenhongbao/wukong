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

package com.nabiki.wukong.sim;

import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderActionField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.wukong.api.OrderProvider;
import com.nabiki.wukong.tools.OrderMapper;
import com.nabiki.wukong.user.ActiveOrder;

/**
 * {@code SimOrderManager} provides simulation for order insertion and action.
 * The simulation account has an account ID with suffix {@code 'S'}. When the user
 * is initialized, its account ID is checked to decide whether the user is a real
 * trader or simulated trader.
 *
 * <p>The simulated trader is initialized with simulation order manager, and the
 * real trader has CTP order manager.
 * </p>
 */
public class SimOrderProvider implements OrderProvider {
    @Override
    public OrderMapper getMapper() {
        return null;
    }

    @Override
    public int sendDetailOrder(CThostFtdcInputOrderField detail, ActiveOrder active) {
        return 0;
    }

    @Override
    public int sendOrderAction(CThostFtdcInputOrderActionField action, ActiveOrder alive) {
        return 0;
    }
}
