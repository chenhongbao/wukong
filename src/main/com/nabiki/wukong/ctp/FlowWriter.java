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

package com.nabiki.wukong.ctp;

import com.nabiki.ctp4j.jni.struct.*;
import com.nabiki.wukong.OP;
import com.nabiki.wukong.cfg.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FlowWriter {
    private final Config config;
    private final Path reqDir, rtnDir, rspDir, errDir;
    private final DateTimeFormatter formatter
            = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSSSSS");

    FlowWriter(Config cfg) {
        this.config = cfg;
        this.reqDir = getPath("dir.flow.req");
        this.rtnDir = getPath("dir.flow.rtn");
        this.rspDir = getPath("dir.flow.rsp");
        this.errDir = getPath("dir.flow.err");
    }

    Path getPath(String key) {
        var dirs = this.config.getRootDirectory().recursiveGet(key);
        if (dirs.size() > 0)
            return dirs.iterator().next().path();
        else
            return Path.of("");
    }

    void write(String text, File file) {
        try {
            OP.writeText(text, file, StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            this.config.getLogger()
                    .warning(file.toString() + ". " + e.getMessage());
        }
    }

    File ensureFile(Path root, String fn) {
        try {
            if (!Files.exists(root))
                Files.createDirectories(root);
            var r = Path.of(root.toAbsolutePath().toString(), fn).toFile();
            if (!r.exists())
                r.createNewFile();
            return r;
        } catch (IOException e) {
            this.config.getLogger().warning(root.toString() + "/" + fn + ". "
                    + e.getMessage());
            return new File(".failover");
        }
    }

    String getTimeStamp() {
        return LocalDateTime.now().format(this.formatter);
    }

    void writeRtn(CThostFtdcOrderField rtn) {
        write(OP.toJson(rtn),
                ensureFile(this.rtnDir,
                        "order." + getTimeStamp() + ".json"));
    }

    void writeRtn(CThostFtdcTradeField rtn) {
        write(OP.toJson(rtn),
                ensureFile(this.rtnDir,
                        "trade." + getTimeStamp() + ".json"));
    }

    void writeReq(CThostFtdcInputOrderField req) {
        write(OP.toJson(req),
                ensureFile(this.reqDir,
                        "inputorder." + getTimeStamp() + ".json"));
    }

    void writeReq(CThostFtdcInputOrderActionField req) {
        write(OP.toJson(req),
                ensureFile(this.reqDir,
                        "action." + getTimeStamp() + ".json"));
    }

    void writeRsp(CThostFtdcInstrumentMarginRateField rsp) {
        write(OP.toJson(rsp),
                ensureFile(this.rspDir,
                        "margin." + getTimeStamp() + ".json"));
    }

    void writeRsp(CThostFtdcInstrumentCommissionRateField rsp) {
        write(OP.toJson(rsp),
                ensureFile(this.rspDir,
                        "commission." + getTimeStamp() + ".json"));
    }

    void writeRsp(CThostFtdcInstrumentField rsp) {
        write(OP.toJson(rsp),
                ensureFile(this.rspDir,
                        "instrument." + getTimeStamp() + ".json"));
    }

    void writeErr(CThostFtdcOrderActionField err) {
        write(OP.toJson(err),
                ensureFile(this.errDir,
                        "orderaction." + getTimeStamp() + ".json"));
    }

    void writeErr(CThostFtdcInputOrderActionField err) {
        write(OP.toJson(err),
                ensureFile(this.errDir,
                        "action." + getTimeStamp() + ".json"));
    }

    void writeErr(CThostFtdcInputOrderField err) {
        write(OP.toJson(err),
                ensureFile(this.errDir,
                        "inputorder." + getTimeStamp() + ".json"));
    }

    void writeErr(CThostFtdcRspInfoField err) {
        write(OP.toJson(err),
                ensureFile(this.errDir,
                        "info." + getTimeStamp() + ".json"));
    }
}
