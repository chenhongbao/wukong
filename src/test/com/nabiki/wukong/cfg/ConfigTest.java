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
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.cfg.plain.TradingHourConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConfigTest {
    static Config config;

    static {
        ConfigLoader.rootPath = "C:\\Users\\chenh\\Desktop\\app_root";
        try {
            config = ConfigLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void dirs() {
        var cfg = config.getRootDirectory();
        Assert.assertFalse("shouldn't be empty", cfg.isEmpty());

        // Test default directory layout.
        var flowTrader = cfg.recursiveGet("dir.flow.ctp.trader");

        Assert.assertEquals("flow trader dir should exist",
                1, flowTrader.size());
        for (var d : flowTrader) {
            Assert.assertFalse("flow trader dir should be directory",
                    d.isFile());
            System.out.println(d.path().toString());
        }
    }

    @Test
    public void cfg() {
        var hour = config.getRootDirectory().recursiveGet("cfg.hour.sample");

        // Test sample config existence.
        Assert.assertEquals("should have at least on config file",
                1, hour.size());
        var h = hour.iterator().next();
        Assert.assertTrue("should be file", h.isFile());
        Assert.assertFalse("should has sample content", h.isEmpty());

        // Test sample config content validity.
        try {
            var hourCfg = OP.fromJson(OP.readText(h.file(), StandardCharsets.UTF_8),
                    TradingHourConfig.class);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }

        var login = config.getRootDirectory().recursiveGet("cfg.login.sample");

        // Test sample config existence.
        Assert.assertEquals("should have at least on config file",
                1, login.size());
        var l = login.iterator().next();
        Assert.assertTrue("should be file", l.isFile());
        Assert.assertFalse("should has sample content", l.isEmpty());

        // Test sample config content validity.
        try {
            var loginCfg = OP.fromJson(OP.readText(l.file(), StandardCharsets.UTF_8),
                    LoginConfig.class);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void log() {
        var logs = config.getRootDirectory().recursiveGet("dir.log");

        Assert.assertEquals("should have 1 element",
                1, logs.size());
        Assert.assertFalse("should be directory",
                logs.iterator().next().isFile());

        // Test logger.
        config.getLogger().info("This is a junit test.");
    }
}