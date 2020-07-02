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

import com.nabiki.ctp4j.jni.struct.CThostFtdcInvestorPositionDetailField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradingAccountField;
import com.nabiki.wukong.tools.InTeam;
import com.nabiki.wukong.tools.OP;
import com.nabiki.wukong.user.core.User;
import com.nabiki.wukong.user.core.UserAccount;
import com.nabiki.wukong.user.core.UserPosition;
import com.nabiki.wukong.user.core.UserPositionDetail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class UserLoader {
    /**
     * Load user from directory containing the settlement results of the previous
     * trading day.
     *
     * @param cfg {@link Config} configuration
     * @return collection of users
     */
    @InTeam
    public static Collection<User> loadUser(Config cfg) {
        var r = new HashSet<User>();
        for (var dir : getUserDirs(cfg)) {
            var usr = loadUser(dir, cfg);
            if (usr != null)
                r.add(usr);
        }
        return r;
    }

    private static Collection<File> getUserDirs(Config cfg) {
        var dirs = cfg.getRootDirectory()
                .recursiveGet("dir.flow.stl");
        if (dirs.size() != 1)
            throw new IllegalStateException("failed getting settlement dir");
        final String[] latest = {"0"};
        final var dir = dirs.iterator().next();
        dir.file().listFiles(file -> {
            if (file.isDirectory()) {
                var last = Long.parseLong(latest[0]);
                var curr = Long.parseLong(file.getName());
                if (curr > last)
                    latest[0] = file.getName();
            }
            return false;
        });
        final var r = new HashSet<File>();
        final var lastDir = Path.of(dir.path().toString(), latest[0]).toFile();
        lastDir.listFiles(file -> {
            if (file.isDirectory())
                r.add(file);
            return false;
        });
        return r;
    }

    private static User loadUser(File dir, Config config) {
        final CThostFtdcTradingAccountField[] account = {null};
        var pds = new LinkedList<CThostFtdcInvestorPositionDetailField>();
        dir.listFiles(file -> {
            var name = file.getName();
            if (name.compareTo("account.json") == 0) {
                try {
                    account[0] = OP.fromJson(
                            OP.readText(file, StandardCharsets.UTF_8),
                            CThostFtdcTradingAccountField.class);
                } catch (IOException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed loading account",
                                    null, e.getMessage(),
                                    null));
                }
            } else if (name.startsWith("position.")
                    && name.endsWith(".json")) {
                try {
                    pds.add(OP.fromJson(
                            OP.readText(file, StandardCharsets.UTF_8),
                            CThostFtdcInvestorPositionDetailField.class));
                } catch (IOException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed loading position detail",
                                    null, e.getMessage(),
                                    null));
                }
            }
            return false;
        });
        // Check scanning result.
        if (account[0] == null || pds.size() == 0) {
            config.getLogger().warning(
                    OP.formatLog("failed loading user", null,
                            dir.getAbsolutePath(), null));
            return null;
        }
        var m = new HashMap<String, List<UserPositionDetail>>();
        for (var d : pds) {
            if (!m.containsKey(d.InstrumentID))
                m.put(d.InstrumentID, new LinkedList<>());
            m.get(d.InstrumentID).add(new UserPositionDetail(d));
        }
        // Construct user.
        var user = new User();
        var usrAccount = new UserAccount(account[0], user);
        var usrPosition = new UserPosition(m, user);
        user.setAccount(usrAccount);
        user.setPosition(usrPosition);
        return user;
    }
}
