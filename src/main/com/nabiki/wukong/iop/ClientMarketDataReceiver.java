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

package com.nabiki.wukong.iop;

import com.nabiki.ctp4j.jni.struct.CThostFtdcDepthMarketDataField;
import com.nabiki.wukong.ctp.MarketDataReceiver;
import com.nabiki.wukong.ctp4j.jni.struct.CThostFtdcCandleField;
import com.nabiki.wukong.iop.frame.Body;
import com.nabiki.wukong.iop.frame.MessageType;
import com.nabiki.wukong.tools.OP;

import java.util.HashSet;
import java.util.Set;

public class ClientMarketDataReceiver implements MarketDataReceiver {

    private final IOPSession session;
    private final Set<String> subscribed = new HashSet<>();

    public ClientMarketDataReceiver(IOPSession session) {
        this.session = session;
    }

    public void subscribe(String instrID) {
        this.subscribed.add(instrID);
    }

    public void unSubscribe(String instrID) {
        this.subscribed.remove(instrID);
    }

    private boolean isSubscribed(String instrID) {
        synchronized (this.subscribed) {
            return this.subscribed.contains(instrID);
        }
    }

    private void send(Object obj, MessageType type) {
        var body = new Body();
        body.Type = type;
        body.Json = OP.toJson(obj);
        try {
            this.session.sendResponse(body);
        } catch (Throwable th) {
            if (!this.session.isClosed())
                this.session.fix();
        }
    }

    @Override
    public void depthReceived(CThostFtdcDepthMarketDataField depth) {
        if (!this.session.isClosed() && isSubscribed(depth.InstrumentID))
            send(depth, MessageType.FLOW_DEPTH);
    }

    @Override
    public void candleReceived(CThostFtdcCandleField candle) {
        if (!this.session.isClosed() && isSubscribed(candle.InstrumentID))
            send(candle, MessageType.FLOW_CANDLE);
    }
}
