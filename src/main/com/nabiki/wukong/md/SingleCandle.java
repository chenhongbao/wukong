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

package com.nabiki.wukong.md;

import com.nabiki.ctp4j.jni.struct.CThostFtdcDepthMarketDataField;
import com.nabiki.wukong.md.plain.Candle;
import com.nabiki.wukong.md.plain.CandleProgress;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SingleCandle {
    private final String instrID;
    private final Map<Duration, CandleProgress> progress = new HashMap<>();

    SingleCandle(String instrID) {
        this.instrID = instrID;
    }

    void update(CThostFtdcDepthMarketDataField md) {
        if (md.InstrumentID.compareTo(this.instrID) != 0)
            throw new IllegalArgumentException("wrong instrument");
        synchronized (this.progress) {
            for (var c : this.progress.values())
                c.update(md);
        }
    }

    void register(Duration du) {
        synchronized (this.progress) {
            if (this.progress.containsKey(du))
                return;
            else
                this.progress.put(du, new CandleProgress());
        }
    }

    Candle peak(Duration du) {
        synchronized (this.progress) {
            if (this.progress.containsKey(du))
                return this.progress.get(du).peak();
            else
                throw new IllegalArgumentException("key not found");
        }
    }

    Candle pop(Duration du) {
        synchronized (this.progress) {
            if (this.progress.containsKey(du))
                return this.progress.get(du).pop();
            else
                throw new IllegalArgumentException("key not found");
        }
    }
}
