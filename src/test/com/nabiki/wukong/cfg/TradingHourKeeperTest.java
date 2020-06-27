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

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TradingHourKeeperTest  {

    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    static DateTimeFormatter formatter0 = DateTimeFormatter.ofPattern("HH:mm:ss");

    TradingHourKeeper.TradingHour hour(String from, String to) {
        return new TradingHourKeeper.TradingHour(
                LocalTime.parse(from, formatter),
                LocalTime.parse(to, formatter));
    }

    LocalTime time(String time) {
        return LocalTime.parse(time, formatter0);
    }

    @Test
    public void c() {
        var hours = new TradingHourKeeper.TradingHour[4];

        hours[0] = hour("21:00", "23:00");
        hours[1] = hour("09:00", "10:15");
        hours[2] = hour("10:30", "11:30");
        hours[3] = hour("13:30", "15:00");

        var keeper = new TradingHourKeeper(hours);

        // Test end-of-day
        String[] notEndDay = new String[] {
                "09:00:00", "09:45:11", "10:15:00", "10:30:00", "10:45:15",
                "11:29:59", "12:30:59", "13:29:59", "13:30:00", "14:45:00",
                "14:59:59", "15:00:00", "21:00:00", "21:00:01", "22:59:59",
                "23:00:00", "23:00:01", "00:00:00", "00:00:01", "02:30:00"
        };
        for (var s : notEndDay)
            Assert.assertFalse(s + " shouldn't be end-of day",
                    keeper.isEndDay(time(s)));

        String[] endDay = new String[] {
                "15:00:01", "15:15:00", "15:59:59", "18:00:00", "20:59:59"
        };
        for (var s : endDay)
            Assert.assertTrue(s + " should be end-of-day",
                    keeper.isEndDay(time(s)));

        // Test contains.
        String[] notContains = new String[] {
                "08:59:59", "09:00:00", "10:15:01", "10:29:59", "10:30:00",
                "11:30:01", "13:29:59", "13:30:00", "15:00:01", "15:01:00",
                "20:59:59", "21:00:00", "23:00:01", "23:01:00", "00:00:00",
                "00:00:01", "02:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain",
                    keeper.contains(time(s)));

        String[] contains = new String[] {
                "09:00:01", "09:45:45", "10:14:59", "10:15:00", "10:30:01",
                "11:29:59", "11:30:00", "13:30:01", "14:59:59", "15:00:00",
                "21:00:01", "22:59:59", "23:00:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain",
                    keeper.contains(time(s)));

        // Test sample.
        keeper.sample(Duration.ofMinutes(1));

        notContains = new String[] {
                "08:59:59", "09:00:00", "09:00:01", "10:14:59", "10:30:00",
                "10:30:01", "11:29:59", "13:30:00", "13:30:01", "14:59:59",
                "15:00:01", "15:01:00", "20:59:59", "21:00:00", "21:00:01",
                "22:30:01", "22:59:59", "23:01:00", "00:00:00", "02:05:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        contains = new String[] {
                "09:01:00", "09:59:00", "10:00:00", "10:15:00", "10:31:00",
                "10:59:00", "11:00:00", "11:29:00", "11:30:00", "13:31:00",
                "13:59:00", "14:00:00", "14:59:00", "15:00:00", "21:01:00",
                "21:59:00", "22:00:00", "22:01:00", "22:59:00", "23:00:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        keeper.sample(Duration.ofHours(1));
        notContains = new String[] {
                "09:00:00", "09:59:59", "10:00:01", "10:15:00", "11:00:00",
                "11:14:59", "11:30:00", "13:00:00", "13:29:00", "13:30:00",
                "14:00:00", "14:14:00", "14:45:00", "15:00:01", "20:59:00",
                "21:00:00", "21:30:00", "22:00:01", "22:59:00", "23:00:01",
                "23:30:00", "00:00:00", "00:15:00", "00:30:00", "02:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        contains = new String[] {
                "10:00:00", "11:15:00", "14:15:00", "15:00:00", "22:00:00",
                "23:00:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain for hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        keeper.sample(Duration.ofMinutes(30));
        notContains = new String[] {
                "08:59:59", "09:00:00", "09:15:00", "09:30:01", "09:59:59",
                "10:14:59", "10:15:00", "10:30:00", "10:44:59", "11:00:00",
                "11:30:00", "14:00:00", "14:30:00", "21:00:00", "21:15:00",
                "21:45:00", "22:00:01", "22:29:00", "22:59:59", "23:00:01",
                "23:30:00", "00:00:00", "00:30:00", "01:00:00", "01:15:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for half-hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));

        contains = new String[] {
                "09:30:00", "10:00:00", "10:45:00", "11:15:00", "13:45:00",
                "14:15:00", "14:45:00", "15:00:00", "21:30:00", "22:00:00",
                "22:30:00", "23:00:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));
    }

    @Test
    public void au() {
        var hours = new TradingHourKeeper.TradingHour[4];

        hours[0] = hour("21:00", "02:30");
        hours[1] = hour("09:00", "10:15");
        hours[2] = hour("10:30", "11:30");
        hours[3] = hour("13:30", "15:00");

        var keeper = new TradingHourKeeper(hours);

        // Test end-of-day
        String[] notEndDay = new String[] {
                "09:00:00", "09:45:11", "10:15:00", "10:30:00", "10:45:15",
                "11:29:59", "12:30:59", "13:29:59", "13:30:00", "14:45:00",
                "14:59:59", "15:00:00", "21:00:00", "21:00:01", "22:59:59",
                "23:00:00", "23:00:01", "00:00:00", "00:00:01", "02:30:00"
        };
        for (var s : notEndDay)
            Assert.assertFalse(s + " shouldn't be end-of day",
                    keeper.isEndDay(time(s)));

        String[] endDay = new String[] {
                "15:00:01", "15:15:00", "15:59:59", "18:00:00", "20:59:59"
        };
        for (var s : endDay)
            Assert.assertTrue(s + " should be end-of-day",
                    keeper.isEndDay(time(s)));

        // Test contains.
        String[] notContains = new String[] {
                "08:59:59", "09:00:00", "10:15:01", "10:29:59", "10:30:00",
                "11:30:01", "13:29:59", "13:30:00", "15:00:01", "15:01:00",
                "20:59:59", "21:00:00", "02:30:01"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain",
                    keeper.contains(time(s)));

        String[] contains = new String[] {
                "09:00:01", "09:45:45", "10:14:59", "10:15:00", "10:30:01",
                "11:29:59", "11:30:00", "13:30:01", "14:59:59", "15:00:00",
                "21:00:01", "22:59:59", "23:00:00", "23:00:01", "23:01:00",
                "00:00:00", "00:00:01", "02:30:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain",
                    keeper.contains(time(s)));

        // Test sample.
        keeper.sample(Duration.ofMinutes(1));

        notContains = new String[] {
                "08:59:59", "09:00:00", "09:00:01", "10:14:59", "10:30:00",
                "10:30:01", "11:29:59", "13:30:00", "13:30:01", "14:59:59",
                "15:00:01", "15:01:00", "20:59:59", "21:00:00", "21:00:01",
                "22:30:01", "22:59:59", "23:01:01", "00:00:01", "02:05:01",
                "02:29:59", "02:30:01", "02:31:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        contains = new String[] {
                "09:01:00", "09:59:00", "10:00:00", "10:15:00", "10:31:00",
                "10:59:00", "11:00:00", "11:29:00", "11:30:00", "13:31:00",
                "13:59:00", "14:00:00", "14:59:00", "15:00:00", "21:01:00",
                "21:59:00", "22:00:00", "22:01:00", "22:59:00", "23:00:00",
                "23:59:00", "00:00:00", "00:01:00", "02:29:00", "02:30:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain for minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        keeper.sample(Duration.ofHours(1));
        notContains = new String[] {
                "09:00:00", "09:59:59", "10:00:01", "10:15:00", "11:00:00",
                "11:14:59", "11:30:00", "13:00:00", "13:29:00", "13:30:00",
                "14:00:00", "14:14:00", "14:46:00", "15:00:01", "20:59:00",
                "21:00:00", "21:30:00", "22:00:01", "22:59:00", "23:00:01",
                "23:30:00", "00:01:00", "00:30:00", "01:15:00", "02:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        contains = new String[] {
                "09:30:00", "10:45:00", "13:45:00", "14:45:00", "15:00:00",
                "22:00:00", "23:00:00", "00:00:00", "01:00:00", "02:00:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        keeper.sample(Duration.ofMinutes(30));
        notContains = new String[] {
                "08:59:59", "09:00:00", "09:15:00", "09:30:01", "09:59:59",
                "10:14:59", "10:15:00", "10:30:00", "10:44:59", "11:00:00",
                "11:30:00", "14:00:00", "14:30:00", "21:00:00", "21:15:00",
                "21:45:00", "22:00:01", "22:29:00", "22:59:59", "23:00:01",
                "23:15:00", "00:15:00", "00:15:00", "01:00:01", "01:15:00",
                "02:15:00", "02:45:00", "03:00:00", "03:15:00", "03:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for half-hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));

        contains = new String[] {
                "09:30:00", "10:00:00", "10:45:00", "11:15:00", "13:45:00",
                "14:15:00", "14:45:00", "15:00:00", "21:30:00", "22:00:00",
                "22:30:00", "23:00:00", "23:30:00", "00:00:00", "00:30:00",
                "01:00:00", "01:30:00", "02:00:00", "02:30:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));
    }

    @Test
    public void IF() {
        var hours = new TradingHourKeeper.TradingHour[2];

        hours[0] = hour("09:15", "11:30");
        hours[1] = hour("13:00", "15:15");

        var keeper = new TradingHourKeeper(hours);

        // Test end-of-day
        String[] notEndDay = new String[] {
                "09:15:00", "09:45:11", "10:15:00", "10:30:00", "10:45:15",
                "11:29:59", "12:30:59", "13:29:59", "13:00:01", "14:45:00",
                "14:59:59", "15:00:00", "15:00:01", "15:01:00", "15:14:59",
                "15:15:00"
        };
        for (var s : notEndDay)
            Assert.assertFalse(s + " shouldn't be end-of day",
                    keeper.isEndDay(time(s)));

        String[] endDay = new String[] {
                "08:59:00", "09:00:00", "09:14:59", "15:15:01", "15:30:00",
                "15:59:59", "18:00:00", "20:59:59", "00:00:00", "01:15:00"
        };
        for (var s : endDay)
            Assert.assertTrue(s + " should be end-of-day",
                    keeper.isEndDay(time(s)));

        // Test contains.
        String[] notContains = new String[] {
                "08:59:59", "09:00:00", "09:15:00", "11:30:01", "12:29:59",
                "13:00:00", "15:15:01", "15:30:00", "20:59:59", "21:00:00",
                "00:00:00", "02:30:01"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain",
                    keeper.contains(time(s)));

        String[] contains = new String[] {
                "09:15:01", "09:45:45", "10:14:59", "10:15:00", "10:30:01",
                "11:29:59", "11:30:00", "13:00:01", "14:59:59", "15:00:00",
                "15:00:01", "15:14:59", "15:15:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain",
                    keeper.contains(time(s)));

        // Test sample.
        keeper.sample(Duration.ofMinutes(1));

        notContains = new String[] {
                "08:59:59", "09:00:00", "09:00:01", "10:14:59", "10:30:01",
                "10:59:59", "11:29:59", "12:00:00", "13:00:01", "14:59:59",
                "15:00:01", "15:15:01", "20:59:59", "21:00:00", "21:00:01",
                "22:30:01", "22:59:59", "23:01:01", "00:00:01", "02:05:01",
                "02:29:59", "02:30:01", "02:31:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        contains = new String[] {
                "09:16:00", "09:59:00", "10:00:00", "10:15:00", "10:31:00",
                "10:59:00", "11:00:00", "11:29:00", "11:30:00", "13:01:00",
                "13:30:00", "14:00:00", "14:59:00", "15:00:00", "15:01:00",
                "15:15:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain for minute",
                    keeper.contains(Duration.ofMinutes(1), time(s)));

        keeper.sample(Duration.ofHours(1));
        notContains = new String[] {
                "09:15:00", "09:59:59", "10:00:01", "10:15:01", "11:00:00",
                "11:14:59", "11:30:00", "13:00:00", "13:14:59", "13:30:00",
                "14:00:00", "14:14:00", "14:46:00", "15:00:01", "15:14:59",
                "15:15:01", "15:30:00", "16:15:00", "22:59:00", "23:00:01",
                "23:30:00", "00:01:00", "00:30:00", "01:15:00", "02:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        contains = new String[] {
                "10:15:00", "11:15:00", "13:45:00", "14:45:00", "15:15:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or hour",
                    keeper.contains(Duration.ofHours(1), time(s)));

        keeper.sample(Duration.ofMinutes(30));
        notContains = new String[] {
                "08:59:59", "09:00:00", "09:15:00", "09:30:01", "09:59:59",
                "10:00:00", "10:14:59", "10:30:00", "10:44:59", "11:00:00",
                "11:30:00", "13:14:00", "13:30:00", "14:01:00", "14:30:00",
                "14:46:00", "15:00:00", "15:14:59", "15:15:01", "15:30:00",
                "15:30:00", "16:00:00", "20:15:00", "21:00:00", "21:15:00",
                "21:30:00", "00:00:00", "01:15:00", "03:15:00", "03:30:00"
        };
        for (var s : notContains)
            Assert.assertFalse(s + " shouldn't contain for half-hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));

        contains = new String[] {
                "09:45:00", "10:15:00", "10:45:00", "11:15:00", "13:15:00",
                "13:45:00", "14:15:00", "14:45:00", "15:15:00"
        };
        for (var s : contains)
            Assert.assertTrue(s + " should contain or hour",
                    keeper.contains(Duration.ofMinutes(30), time(s)));
    }
}