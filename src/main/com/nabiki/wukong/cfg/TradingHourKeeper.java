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

package com.nabiki.wukong.cfg;

import com.nabiki.wukong.OP;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market's time-related configuration.
 *
 * <p><b>Instance of the class is thread-safe.</b></p>
 */
public class TradingHourKeeper {
    // (from, to]
    static class TradingHour {
        final LocalTime from, to;

        TradingHour(LocalTime from, LocalTime to) {
            this.from = from;
            this.to = to;
        }

        boolean contains(LocalTime now) {
            if (from.isBefore(this.to))
                return this.from.isBefore(now) && !this.to.isBefore(now);
            else if (from.isAfter(to))
                return this.from.isBefore(now) || !this.to.isBefore(now);
            else
                return false;
        }
    }

    final List<TradingHour> tradingHours = new LinkedList<>();
    final Map<Duration, Set<LocalTime>> durationSplits = new ConcurrentHashMap<>();

    TradingHourKeeper(TradingHour... hours) {
        this.tradingHours.addAll(Arrays.asList(hours));
    }

    /**
     * Check if the specified local time {@code now} is in this time range.
     *
     * @param now local time now
     * @return {@code true} if the specified local time is in the range, {@code false}
     * otherwise
     */
    public boolean contains(LocalTime now) {
        for (var hour : this.tradingHours) {
            if (hour.contains(now))
                return true;
        }
        return false;
    }

    /**
     * Check if the specified local time {@code now} is sampled under the specified
     * duration. If the specified duration doesn't exist, the method returns
     * {@code false}.
     *
     * @param du duration
     * @param now local time now
     * @return {@code true} if the specified local time is a sample under the specified
     * duration
     */
    public boolean contains(Duration du, LocalTime now) {
        if (!this.durationSplits.containsKey(du))
            return false;
        return this.durationSplits.get(du).contains(now);
    }

    /**
     * Sample some time points in trading hours with interval of the specified
     * {@link Duration}.
     *
     * @param du duration between sampled time points
     */
    public void sample(Duration du) {
        if (this.durationSplits.containsKey(du))
            return;
        else
            this.durationSplits.put(du, new HashSet<>());

        final Set<LocalTime> times = durationSplits.get(du);
        synchronized (times) {
            // Calculate splits.
            final LocalTime[] next = {null};
            final Duration[] nextDu = {du};
            this.tradingHours.forEach(hour -> {
                if (next[0] == null)
                    next[0] = hour.from;
                while (true) {
                    next[0] = next[0].plus(nextDu[0]);
                    if (hour.contains(next[0])) {
                        times.add(next[0]);
                        nextDu[0] = du;
                    } else {
                        nextDu[0] = OP.between(hour.to, next[0]);
                        break;
                    }
                }
            });
            // Finalize the calculation by adding the end of trading hour if next
            // possible local time exceeds the last trading hour.
            if (!nextDu[0].equals(du))
                times.add(next[0].minus(nextDu[0]));
        }
    }

    /**
     * Check if the specified local time is between two trading days, when the
     * market is closed.
     *
     * @param now local time now
     * @return {@code true} if now is end of the previous trading day, {@code false}
     * otherwise
     */
    public boolean isEndDay(LocalTime now) {
        if (this.tradingHours.size() == 0)
            return true;    // no trading hour so it's always end-of-day
        if (contains(now))
            return false;   // now is trading hour
        var beg = this.tradingHours.get(0);
        var end = this.tradingHours.get(this.tradingHours.size() - 1);
        if (beg.from.isBefore(end.to))
            return beg.from.isAfter(now) || end.to.isBefore(now);
        else
            return end.to.isBefore(now) && beg.from.isAfter(now);
    }
}
