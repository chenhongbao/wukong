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

import com.nabiki.ctp4j.jni.struct.CThostFtdcDepthMarketDataField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInstrumentCommissionRateField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInstrumentField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInstrumentMarginRateField;
import com.nabiki.wukong.EasyFile;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.annotation.InTeam;
import com.nabiki.wukong.cfg.plain.InstrumentInfo;
import com.nabiki.wukong.cfg.plain.JdbcLoginConfig;
import com.nabiki.wukong.cfg.plain.LoginConfig;
import com.nabiki.wukong.cfg.plain.TradingHourConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ConfigLoader {
    public static String rootPath;

    static AtomicBoolean configLoaded = new AtomicBoolean(false);
    final static Config config = new Config();

    /**
     * Get single {@link Config} instance. If the instance exists, the method first
     * clears the internal data then initializes again.
     *
     * @return instance of {@link Config}
     * @throws IOException fail to read or process configuration files, or content
     * in the configuration file is corrupted or invalid
     */
    @InTeam
    public static Config loadConfig() throws IOException {
        synchronized (config) {
            // Clear old config.
            if (configLoaded.get())
                clearConfig();
            // First create dirs, then logger.
            setDirectories();
            setConfigLogger();
            // Config below uses logger to keep error info.
            setLoginConfig();
            setJdbcLoginConfig();
            setTradingHourConfig();
            setInstrConfig();
            // Set mark.
            configLoaded.set(true);
        }
        return config;
    }

    @InTeam
    public static void setDepthMarketData(CThostFtdcDepthMarketDataField md) {
        config.depths.put(md.InstrumentID, md);
    }

    @InTeam
    public static void setTradingDay(String day) {
        config.tradingDay = day;
    }

    @InTeam
    public static void setInstrConfig(CThostFtdcInstrumentField instr) {
        synchronized (config.instrInfo) {
            if (!config.instrInfo.containsKey(instr.InstrumentID))
                config.instrInfo.put(instr.InstrumentID, new InstrumentInfo());
            config.instrInfo.get(instr.InstrumentID).instrument = instr;
        }
    }

    @InTeam
    public static void setInstrConfig(
            CThostFtdcInstrumentMarginRateField margin) {
        synchronized (config.instrInfo) {
            if (!config.instrInfo.containsKey(margin.InstrumentID))
                config.instrInfo.put(margin.InstrumentID, new InstrumentInfo());
            config.instrInfo.get(margin.InstrumentID).margin = margin;
        }
    }

    @InTeam
    public static void setInstrConfig(
            CThostFtdcInstrumentCommissionRateField commission) {
        synchronized (config.instrInfo) {
            if (!config.instrInfo.containsKey(commission.InstrumentID))
                config.instrInfo.put(commission.InstrumentID, new InstrumentInfo());
            config.instrInfo.get(commission.InstrumentID).commission = commission;
        }
    }

    private static void setInstrConfig() {
        var dirs = config.getRootDirectory().recursiveGet("dir.flow.rsp");
        if (dirs.size() == 0)
            return;
        for (var d : dirs) {
            d.path().toFile().listFiles(file -> {
                try {
                    if (file.getName().startsWith("instrument")) {
                        setInstrConfig(OP.fromJson(
                                OP.readText(file, StandardCharsets.UTF_8),
                                CThostFtdcInstrumentField.class));
                    } else if (file.getName().startsWith("commission")) {
                        setInstrConfig(OP.fromJson(
                                OP.readText(file, StandardCharsets.UTF_8),
                                CThostFtdcInstrumentCommissionRateField.class));
                    } else if (file.getName().startsWith("margin")) {
                        setInstrConfig(OP.fromJson(
                                OP.readText(file, StandardCharsets.UTF_8),
                                CThostFtdcInstrumentMarginRateField.class));
                    }
                } catch (IOException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed instr config", null,
                                    e.getMessage(), null));
                }
                return false;
            });
        }
    }

    /*
     Only clear the config-defined settings. For those updated in runtime, don't
     clear them.
     */
    private static void clearConfig() {
        config.rootDirectory = null;
        config.tradingDay = null;
        config.jdbcLoginConfig = null;
        config.tradingHour.clear();
        config.login.clear();
    }

    private static void setTradingHourConfig() throws IOException {
        var s = config.getRootDirectory().recursiveGet("dir.cfg.hour");
        if (s.size() == 0)
            throw new IOException("directory for trading hour configs not found");
        // Iterate over all dirs.
       for (var cfg : s) {
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
                    // All product IDs are lower case.
                    for (var p : c.productID)
                        config.tradingHour.put(p.toLowerCase(), h);
                } catch (IOException | NullPointerException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed trading hour config",
                                    null, e.getMessage(), null));
                }
                return false;
            });
        }
        // Write sample config.
        if (config.tradingHour.size() == 0) {
            var cfg = s.iterator().next();
            cfg.setFile("cfg.hour.sample", "hour.sample.json");
            OP.writeText(OP.toJson(new LoginConfig()),
                    cfg.get("cfg.hour.sample").file(), StandardCharsets.UTF_8,
                    false);
        }
    }

    private static void setLoginConfig() throws IOException {
        var s = config.getRootDirectory().recursiveGet("dir.cfg.login");
        if (s.size() == 0)
            throw new IOException("directory for login configs not found");
        // Iterate over all files under dir.
        for (var cfg : s) {
            cfg.file().listFiles(file -> {
                try {
                    if (!file.isFile() || file.length() == 0)
                        return false;
                    var c = OP.fromJson(
                            OP.readText(file, StandardCharsets.UTF_8),
                            LoginConfig.class);
                    config.login.put(c.name, c);
                } catch (IOException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed login config",
                                    null, e.getMessage(), null));
                }
                return false;
            });
        }
        // Write a configuration sample.
        if (config.login.size() == 0) {
            var cfg = s.iterator().next();
            cfg.setFile("cfg.login.sample", "login.sample.json");
            OP.writeText(OP.toJson(new LoginConfig()),
                    cfg.get("cfg.login.sample").file(), StandardCharsets.UTF_8,
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
        root.setDirectory("dir.log", ".log");

        var cfg = root.get("dir.cfg");
        cfg.setDirectory("dir.cfg.login", ".login");
        cfg.setDirectory("dir.cfg.hour", ".hour");
        cfg.setDirectory("dir.cfg.jdbc", ".jdbc");

        var flow = root.get("dir.flow");
        flow.setDirectory("dir.flow.ctp", ".ctp");
        flow.setDirectory("dir.flow.req", ".req");
        flow.setDirectory("dir.flow.rtn", ".rtn");
        flow.setDirectory("dir.flow.rsp", ".rsp");
        flow.setDirectory("dir.flow.err", ".err");
        flow.setDirectory("dir.flow.stl", ".stl");

        var ctp = flow.get("dir.flow.ctp");
        ctp.setDirectory("dir.flow.ctp.trader", ".trader");
        ctp.setDirectory("dir.flow.ctp.md", ".md");

        // Set config.
        config.rootDirectory = root;
    }

    private static void setConfigLogger() {
        if (Config.logger == null) {
            // Get logging directory.
            String fp;
            var iter = config.getRootDirectory().recursiveGet("dir.log")
                    .iterator();
            if (!iter.hasNext())
                fp ="system.log";
            else
                fp = Path.of(iter.next().path().toString(), "system.log")
                        .toAbsolutePath().toString();

            try {
                // File and format.
                var fh = new FileHandler(fp);
                fh.setFormatter(new SimpleFormatter());
                // Get logger with config's name.
                Config.logger = Logger.getLogger(Config.class.getCanonicalName());
                Config.logger.addHandler(fh);
            } catch (IOException e) {
                Config.logger = Logger.getGlobal();
            }
        }
    }

    private static void setJdbcLoginConfig() throws IOException {
        var s = config.getRootDirectory().recursiveGet("dir.cfg.jdbc");
        if (s.size() == 0)
            throw new IOException("jdbc config not found");
        // Just use the first file in first dir.
        s.iterator().next().file().listFiles(file -> {
            try {
                if (config.jdbcLoginConfig == null
                        && file.getName().endsWith(".json"))
                    config.jdbcLoginConfig = OP.fromJson(
                            OP.readText(file, StandardCharsets.UTF_8),
                            JdbcLoginConfig.class);
            } catch (IOException e) {
                config.getLogger().warning(
                        OP.formatLog("failed jdbc login config",
                                null, e.getMessage(), null));
            }
            return false;
        });
        // Write an sample config if not exists.
        if (config.jdbcLoginConfig == null) {
            var cfg = s.iterator().next();
            cfg.setFile("cfg.jdbc.sample", "jdbc.sample.json");
            OP.writeText(OP.toJson(new JdbcLoginConfig()),
                    cfg.get("cfg.jdbc.sample").file(), StandardCharsets.UTF_8,
                    false);
        }
    }
}
