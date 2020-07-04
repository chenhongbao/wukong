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

package com.nabiki.wukong.journal;

import com.nabiki.ctp4j.jni.struct.CThostFtdcOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcTradeField;
import com.nabiki.wukong.cfg.Config;
import com.nabiki.wukong.tools.OP;

import java.sql.*;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class MessageWriterDB {
    private final Config config;
    private final Queue<CThostFtdcOrderField> orders;
    private final Queue<CThostFtdcTradeField> trades;
    private final Thread daemon = new Thread(new WriteDB());
    private final ReentrantLock lck = new ReentrantLock();
    private final Condition cond = lck.newCondition();

    MessageWriterDB(Config cfg) {
        this.config = cfg;
        this.orders = new ConcurrentLinkedQueue<>();
        this.trades = new ConcurrentLinkedQueue<>();
        this.daemon.start();
    }

    void write(CThostFtdcOrderField order) {
        this.lck.lock();
        try {
            this.orders.add(order);
            this.cond.signal();
        } finally {
            this.lck.unlock();
        }
    }

    void write(CThostFtdcTradeField trade) {
        this.lck.lock();
        try {
            this.trades.add(trade);
            this.cond.signal();
        } finally {
            this.lck.unlock();
        }
    }

    class WriteDB implements Runnable {
        private Connection jdbc;
        private PreparedStatement preStmtOrder, preStmtTrade;

        WriteDB() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver").getConstructor()
                        .newInstance();
            } catch (Throwable e) {
                config.getLogger().severe(
                        OP.formatLog("failed loading JDBC", null,
                                null, null));
            }
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                lck.lock();
                try {
                    while (orders.size() == 0 && trades.size() == 0)
                        cond.await();
                    // Check connection valid.
                    checkConnection();
                    while (orders.size() > 0)
                            execSQL(orders.poll());
                    while (trades.size() > 0)
                        execSQL(trades.poll());
                } catch (InterruptedException e) {
                    config.getLogger().warning(
                            OP.formatLog("condition await interrupted",
                                    null, e.getMessage(), null));
                } catch (SQLException e) {
                    config.getLogger().warning(
                            OP.formatLog("failed SQL execution",
                                    null, e.getMessage(),
                                    e.getErrorCode()));
                } catch (Throwable th) {
                    config.getLogger().warning(
                            OP.formatLog("null pointer", null,
                                    th.getMessage(), null));
                }finally {
                    lck.unlock();
                }
            }
        }

        private void checkConnection() throws SQLException {
            if (this.jdbc != null && this.jdbc.isValid(1))
                return;
            // Clear old stuff.
            if (this.jdbc != null && !this.jdbc.isClosed()) {
                this.preStmtTrade.close();
                this.preStmtOrder.close();
                this.jdbc.close();
            }
            openConnection();
            checkSchema();
            checkTables();
            prepareStatements();
        }

        private void openConnection() throws SQLException {
            var cfg = config.getJdbcLoginConfig();
            if (cfg == null)
                throw new NullPointerException("jdbc config null");
            if (cfg.user == null || cfg.password == null || cfg.URL == null
                    || cfg.schema == null)
                throw new NullPointerException("broken jdbc config");
            this.jdbc = DriverManager.getConnection(cfg.URL, cfg.user,
                    cfg.password);
        }

        private void prepareStatements() {
            try {
                this.preStmtOrder = this.jdbc.prepareStatement(
                        "INSERT INTO `" + config.getJdbcLoginConfig().schema
                                + "`.`cthost_ftdc_order_field` VALUES(" +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?,?)");
                this.preStmtTrade = this.jdbc.prepareStatement(
                        "INSERT INTO `" + config.getJdbcLoginConfig().schema
                                + "`.`cthost_ftdc_trade_field` VALUES(" +
                                "?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?,?," +
                                "?,?,?,?,?,?,?)");
            } catch (SQLException e) {
                config.getLogger().severe(
                        OP.formatLog("failed prepare SQL statement",
                                null, e.getMessage(), e.getErrorCode()));
            }
        }

        private void checkSchema() throws SQLException {
            try (Statement stmt = this.jdbc.createStatement()) {
                var rs = stmt.executeQuery("SHOW DATABASES");
                var dbs = new HashSet<String>();
                while (rs.next())
                    dbs.add(rs.getString(1).toLowerCase());
                rs.close();
                var schema = config.getJdbcLoginConfig().schema;
                if (!dbs.contains(schema))
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " +
                            "`" + schema + "` " +
                            "DEFAULT CHARACTER SET UTF8MB4");
            }
        }

        private void checkTables() throws SQLException {
            try (Statement stmt = this.jdbc.createStatement()) {
                stmt.execute("USE " + config.getJdbcLoginConfig().schema);
                stmt.execute("SET NAMES UTF8MB4");
                // Check tables.
                var tables = new HashSet<String>();
                var rs = stmt.executeQuery("SHOW TABLES");
                while (rs.next())
                    tables.add(rs.getString(1).toLowerCase());
                rs.close();
                // Create tables if they don't exist.
                if (!tables.contains("cthost_ftdc_order_field"))
                    stmt.executeUpdate(WriteDB.createOrderTableSql);
                if (!tables.contains("cthost_ftdc_trade_field"))
                    stmt.executeUpdate(WriteDB.createTradeTableSql);
            }
        }

        private void execSQL(CThostFtdcOrderField order) {
            try {
                this.preStmtOrder.setString(1, order.BrokerID);
                this.preStmtOrder.setString(2, order.InvestorID);
                this.preStmtOrder.setString(3, order.InstrumentID);
                this.preStmtOrder.setString(4, order.OrderRef);
                this.preStmtOrder.setString(5, order.UserID);
                this.preStmtOrder.setString(6, String.valueOf(order.OrderPriceType));
                this.preStmtOrder.setString(7, String.valueOf(order.Direction));
                this.preStmtOrder.setString(8, String.valueOf(order.CombOffsetFlag));
                this.preStmtOrder.setString(9, String.valueOf(order.CombHedgeFlag));
                this.preStmtOrder.setDouble(10, order.LimitPrice);
                this.preStmtOrder.setInt(11, order.VolumeTotalOriginal);
                this.preStmtOrder.setString(12, String.valueOf(order.TimeCondition));
                this.preStmtOrder.setString(13, order.GTDDate);
                this.preStmtOrder.setString(14, String.valueOf(order.VolumeCondition));
                this.preStmtOrder.setInt(15, order.MinVolume);
                this.preStmtOrder.setString(16, String.valueOf(order.ContingentCondition));
                this.preStmtOrder.setDouble(17, order.StopPrice);
                this.preStmtOrder.setString(18, String.valueOf(order.ForceCloseReason));
                this.preStmtOrder.setInt(19, order.IsAutoSuspend);
                this.preStmtOrder.setString(20, order.BusinessUnit);
                this.preStmtOrder.setInt(21, order.RequestID);
                this.preStmtOrder.setString(22, order.OrderLocalID);
                this.preStmtOrder.setString(23, order.ExchangeID);
                this.preStmtOrder.setString(24, order.ParticipantID);
                this.preStmtOrder.setString(25, order.ClientID);
                this.preStmtOrder.setString(26, order.ExchangeInstID);
                this.preStmtOrder.setString(27, order.TraderID);
                this.preStmtOrder.setInt(28, order.InstallID);
                this.preStmtOrder.setString(29, String.valueOf(order.OrderSubmitStatus));
                this.preStmtOrder.setInt(30, order.NotifySequence);
                this.preStmtOrder.setString(31, order.TradingDay);
                this.preStmtOrder.setInt(32, order.SettlementID);
                this.preStmtOrder.setString(33, order.OrderSysID);
                this.preStmtOrder.setString(34, String.valueOf(order.OrderSource));
                this.preStmtOrder.setString(35, String.valueOf(order.OrderStatus));
                this.preStmtOrder.setString(36, String.valueOf(order.OrderType));
                this.preStmtOrder.setInt(37, order.VolumeTraded);
                this.preStmtOrder.setInt(38, order.VolumeTotal);
                this.preStmtOrder.setString(39, order.InsertDate);
                this.preStmtOrder.setString(40, order.InsertTime);
                this.preStmtOrder.setString(41, order.ActiveTime);
                this.preStmtOrder.setString(42, order.SuspendTime);
                this.preStmtOrder.setString(43, order.UpdateTime);
                this.preStmtOrder.setString(44, order.CancelTime);
                this.preStmtOrder.setString(45, order.ActiveTraderID);
                this.preStmtOrder.setString(46, order.ClearingPartID);
                this.preStmtOrder.setInt(47, order.SequenceNo);
                this.preStmtOrder.setInt(48, order.FrontID);
                this.preStmtOrder.setInt(49, order.SessionID);
                this.preStmtOrder.setString(50, order.UserProductInfo);
                this.preStmtOrder.setString(51, order.StatusMsg);
                this.preStmtOrder.setInt(52, order.UserForceClose);
                this.preStmtOrder.setString(53, order.ActiveUserID);
                this.preStmtOrder.setInt(54, order.BrokerOrderSeq);
                this.preStmtOrder.setString(55, order.RelativeOrderSysID);
                this.preStmtOrder.setInt(56, order.ZCETotalTradedVolume);
                this.preStmtOrder.setInt(57, order.IsSwapOrder);
                this.preStmtOrder.setString(58, order.BranchID);
                this.preStmtOrder.setString(59, order.InvestUnitID);
                this.preStmtOrder.setString(60, order.AccountID);
                this.preStmtOrder.setString(61, order.CurrencyID);
                this.preStmtOrder.setString(62, order.IPAddress);
                this.preStmtOrder.setString(63, order.MacAddress);
                // Execute.
                if (this.preStmtOrder.executeUpdate() != 1)
                    config.getLogger().warning(
                            OP.formatLog("failed table insertion",
                                    null, null, null));
            } catch (SQLException e) {
                config.getLogger().warning(
                        OP.formatLog("failed open jdbc connection",
                                null, e.getMessage(), e.getErrorCode()));
            }
        }

        private void execSQL(CThostFtdcTradeField trade) {
            try {
                this.preStmtTrade.setString(1, trade.BrokerID);
                this.preStmtTrade.setString(2, trade.InvestorID);
                this.preStmtTrade.setString(3, trade.InstrumentID);
                this.preStmtTrade.setString(4, trade.OrderRef);
                this.preStmtTrade.setString(5, trade.UserID);
                this.preStmtTrade.setString(6, trade.ExchangeID);
                this.preStmtTrade.setString(7, trade.TradeID);
                this.preStmtTrade.setString(8, String.valueOf(trade.Direction));
                this.preStmtTrade.setString(9, trade.OrderSysID);
                this.preStmtTrade.setString(10, trade.ParticipantID);
                this.preStmtTrade.setString(11, trade.ClientID);
                this.preStmtTrade.setString(12, String.valueOf(trade.TradingRole));
                this.preStmtTrade.setString(13, trade.ExchangeInstID);
                this.preStmtTrade.setString(14, String.valueOf(trade.OffsetFlag));
                this.preStmtTrade.setString(15, String.valueOf(trade.HedgeFlag));
                this.preStmtTrade.setDouble(16, trade.Price);
                this.preStmtTrade.setInt(17, trade.Volume);
                this.preStmtTrade.setString(18, trade.TradeDate);
                this.preStmtTrade.setString(19, trade.TradeTime);
                this.preStmtTrade.setString(20, String.valueOf(trade.TradeType));
                this.preStmtTrade.setString(21, String.valueOf(trade.PriceSource));
                this.preStmtTrade.setString(22, trade.TraderID);
                this.preStmtTrade.setString(23, trade.OrderLocalID);
                this.preStmtTrade.setString(24, trade.ClearingPartID);
                this.preStmtTrade.setString(25, trade.BusinessUnit);
                this.preStmtTrade.setInt(26, trade.SequenceNo);
                this.preStmtTrade.setString(27, trade.TradingDay);
                this.preStmtTrade.setInt(28, trade.SettlementID);
                this.preStmtTrade.setInt(29, trade.BrokerOrderSeq);
                this.preStmtTrade.setString(30, String.valueOf(trade.TradeSource));
                this.preStmtTrade.setString(31, trade.InvestUnitID);
                // Execute.
                if (this.preStmtOrder.executeUpdate() != 1)
                    config.getLogger().warning(
                            OP.formatLog("failed table insertion",
                                    null, null, null));
            } catch (SQLException e) {
                config.getLogger().warning(
                        OP.formatLog("failed open jdbc connection",
                                null, e.getMessage(), e.getErrorCode()));
            }
        }
        
        private static final String createOrderTableSql =
                "CREATE TABLE IF NOT EXISTS `cthost_ftdc_order_field` (" +
                "    BrokerID CHAR(16)," +
                "    InvestorID CHAR(32)," +
                "    InstrumentID CHAR(16)," +
                "    OrderRef CHAR(16)," +
                "    UserID CHAR(32)," +
                "    OrderPriceType CHAR," +
                "    Direction CHAR," +
                "    CombOffsetFlag CHAR," +
                "    CombHedgeFlag CHAR," +
                "    LimitPrice DOUBLE," +
                "    VolumeTotalOriginal INT," +
                "    TimeCondition CHAR," +
                "    GTDDate CHAR(16)," +
                "    VolumeCondition CHAR," +
                "    MinVolume INT," +
                "    ContingentCondition CHAR," +
                "    StopPrice DOUBLE," +
                "    ForceCloseReason CHAR," +
                "    IsAutoSuspend INT," +
                "    BusinessUnit CHAR(32)," +
                "    RequestID INT," +
                "    OrderLocalID CHAR(32)," +
                "    ExchangeID CHAR(16)," +
                "    ParticipantID CHAR(32)," +
                "    ClientID CHAR(32)," +
                "    ExchangeInstID CHAR(16)," +
                "    TraderID CHAR(32)," +
                "    InstallID INT," +
                "    OrderSubmitStatus CHAR," +
                "    NotifySequence INT," +
                "    TradingDay CHAR(16)," +
                "    SettlementID INT," +
                "    OrderSysID CHAR(32)," +
                "    OrderSource CHAR," +
                "    OrderStatus CHAR," +
                "    OrderType CHAR," +
                "    VolumeTraded INT," +
                "    VolumeTotal INT," +
                "    InsertDate CHAR(16)," +
                "    InsertTime CHAR(16)," +
                "    ActiveTime CHAR(16)," +
                "    SuspendTime CHAR(16)," +
                "    UpdateTime CHAR(16)," +
                "    CancelTime CHAR(16)," +
                "    ActiveTraderID CHAR(32)," +
                "    ClearingPartID CHAR(32)," +
                "    SequenceNo INT," +
                "    FrontID INT," +
                "    SessionID INT," +
                "    UserProductInfo CHAR(32)," +
                "    StatusMsg CHAR(128)," +
                "    UserForceClose INT," +
                "    ActiveUserID CHAR(32)," +
                "    BrokerOrderSeq INT," +
                "    RelativeOrderSysID CHAR(32)," +
                "    ZCETotalTradedVolume INT," +
                "    IsSwapOrder INT," +
                "    BranchID CHAR(32)," +
                "    InvestUnitID CHAR(32)," +
                "    AccountID CHAR(32)," +
                "    CurrencyID CHAR(8)," +
                "    IPAddress CHAR(32)," +
                "    MacAddress CHAR(32)" +
                ") DEFAULT CHARACTER SET UTF8MB4, ENGINE = MyISAM";

        private static final String createTradeTableSql =
                "CREATE TABLE IF NOT EXISTS `cthost_ftdc_trade_field` (" +
                "    BrokerID CHAR(16)," +
                "    InvestorID CHAR(32)," +
                "    InstrumentID CHAR(16)," +
                "    OrderRef CHAR(16)," +
                "    UserID CHAR(32)," +
                "    ExchangeID CHAR(16)," +
                "    TradeID CHAR(32)," +
                "    Direction CHAR," +
                "    OrderSysID CHAR(32)," +
                "    ParticipantID CHAR(32)," +
                "    ClientID CHAR(32)," +
                "    TradingRole CHAR," +
                "    ExchangeInstID CHAR(16)," +
                "    OffsetFlag CHAR," +
                "    HedgeFlag CHAR," +
                "    Price DOUBLE," +
                "    Volume INT," +
                "    TradeDate CHAR(16)," +
                "    TradeTime CHAR(16)," +
                "    TradeType CHAR," +
                "    PriceSource CHAR," +
                "    TraderID CHAR(32)," +
                "    OrderLocalID CHAR(32)," +
                "    ClearingPartID CHAR(32)," +
                "    BusinessUnit CHAR(32)," +
                "    SequenceNo INT," +
                "    TradingDay CHAR(16)," +
                "    SettlementID INT," +
                "    BrokerOrderSeq INT," +
                "    TradeSource CHAR," +
                "    InvestUnitID CHAR(32)" +
                ")  DEFAULT CHARACTER SET UTF8MB4, ENGINE=MYISAM";
    }
}
