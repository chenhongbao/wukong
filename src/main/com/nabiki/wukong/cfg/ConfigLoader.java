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

import com.nabiki.wukong.EasyFile;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.cfg.plain.TradingHourConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigLoader {
    public static String rootPath;

    static AtomicBoolean configLoaded = new AtomicBoolean(false);
    final static Config config = new Config();

    public static Config load() throws IOException {
        synchronized (config) {
            // Clear old config.
            if (configLoaded.get())
                clearConfig();

            setDirectories();
            setLoginConfig();
            setTradingHourConfig();
        }
        return config;
    }

    private static void clearConfig() {
        config.rootDirectory = null;
        config.tradingHour.clear();
        config.login.clear();
    }

    private static void setTradingHourConfig() throws IOException {
        var s = config.getRootDirectory().recursiveGet("dir.cfg.hour");
        if (s.size() != 1)
            throw new IOException("directory for trading hour configs not found");

        var cfg = s.iterator().next();
        cfg.file().listFiles(file -> {
            try {
                if (!file.isFile() || file.length() == 0)
                    return false;
                var c = OP.fromJson(
                        OP.readText(file, StandardCharsets.UTF_8),
                        TradingHourConfig.class);
                // Not null.
                Objects.requireNonNull(c);
                Objects.requireNonNull(c.tradingHour);
                Objects.requireNonNull(c.productID);
                // Empty config.
                if (c.tradingHour.size() == 0 || c.productID.size() == 0)
                    return false;
                // Prepare parameters to construct keeper.
                var index = 0;
                var hours = new TradingHourKeeper
                        .TradingHour[c.tradingHour.size()];
                for (var hour : c.tradingHour)
                    hours[index++] = new TradingHourKeeper
                            .TradingHour(hour.from, hour.to);
                var h = new TradingHourKeeper(hours);
                // Save mapping into config.
                for (var p : c.productID)
                    config.tradingHour.put(p, h);
            } catch (IOException | NullPointerException ignored) {
            }
            return false;
        });
        // Write sample config.
        if (config.tradingHour.size() == 0) {
            cfg.setFile("cfg.hour.sample", "hour.sample.json");
            OP.writeText(OP.toJson(new LoginConfig()),
                    cfg.get("cfg.hour.sample").file(),
                    StandardCharsets.UTF_8,
                    false);
        }
    }

    private static void setLoginConfig() throws IOException {
        var s = config.getRootDirectory().recursiveGet("dir.cfg.login");
        if (s.size() != 1)
            throw new IOException("directory for login configs not found");
        // Iterate over all files under dir.
        var cfg = s.iterator().next();
        cfg.file().listFiles(file -> {
            try {
                if (!file.isFile() || file.length() == 0)
                    return false;
                var c = OP.fromJson(
                        OP.readText(file, StandardCharsets.UTF_8),
                        LoginConfig.class);
                config.login.put(c.name, c);
            } catch (IOException ignored) {
            }
            return false;
        });
        // Write a configuration sample.
        if (config.login.size() == 0) {
            cfg.setFile("cfg.login.sample", "login.sample.json");
            OP.writeText(OP.toJson(new LoginConfig()),
                    cfg.get("cfg.login.sample").file(),
                    StandardCharsets.UTF_8,
                    false);
        }
    }

    private static void setDirectories() throws IOException {
        if (rootPath == null)
            rootPath = "";
        var root = new EasyFile(rootPath, false);
        root.setDirectory("dir.cfg", ".cfg");
        root.setDirectory("dir.flow", ".flow");
        root.setDirectory("dir.cdl", ".cdl");

        var cfg = root.get("dir.cfg");
        cfg.setDirectory("dir.cfg.login", ".login");
        cfg.setDirectory("dir.cfg.hour", ".hour");

        var flow = root.get("dir.flow");
        flow.setDirectory("dir.flow.ctp", ".ctp");
        flow.setDirectory("dir.flow.req", ".req");
        flow.setDirectory("dir.flow.rsp", ".rsp");
        flow.setDirectory("dir.flow.qry", ".qry");
        flow.setDirectory("dir.flow.rtn", ".rtn");

        var ctp = flow.get("dir.flow.ctp");
        ctp.setDirectory("dir.flow.ctp.trader", ".trader");
        ctp.setDirectory("dir.flow.ctp.md", ".md");

        // Set config.
        config.rootDirectory = root;
    }
}
