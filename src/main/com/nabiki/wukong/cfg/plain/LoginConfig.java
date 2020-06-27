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

package com.nabiki.wukong.cfg.plain;

/**
 * Configuration for login to remote counter.
 */
public class LoginConfig {
    /**
     * Front address of the remote server. The address uses the format:
     * <p>{@code protocol://ipaddress:port}</p>
     * {@code protocol} is the communication protocol of the front server, they
     * can be {@code tcp} or {@code ssl}. Currently, it is always {@code tcp}.
     * {@code ipaddress} is a typical string representation of IPv4 address.
     * {@code port} is a typical IPv4 port.
     * <p>There are some examples:</p>
     * <ul>
     * <li>tcp://192.168.0.1:41205</li>
     * <li>ssl://192.168.0.1:41205</li>
     * </ul>
     */
    public java.util.List<String> frontAddresses;

    /**
     * Set the communication protocol of market data front.
     * <ul>
     * <li>{@code true}: UDP</li>
     * <li>{@code false}: TCP</li>
     * </ul>
     */
    public boolean isUsingUDP;

    /**
     * Set if the client receives multicast market data.
     * <ul>
     * <li>{@code true}: multicast, only LAN has this option</li>
     * <li>{@code false}: normal internet connection</li>
     * </ul>
     */
    public boolean isMulticast;

    /**
     * Broker ID of the brokerage service provider.
     */
    public String brokerID;

    /**
     * User ID of the login user.
     */
    public String userID;

    /**
     * Password.
     */
    public String password;

    /**
     * Application ID of the software. User needs to contact his or her broker for
     * this ID.
     */
    public String appID;

    /**
     * Authentication code comes with the application ID {@link #appID}.
     */
    public String authCode;

    /**
     * Description of the software. It is usually combination of few words. Overflow
     * letters are truncated.
     */
    public String userProductInfo;

    /**
     * Valid directory to keep flow information during data transmission between
     * client and server.
     */
    public String flowDirectory;

    /**
     * Name of this configuration. The field is used to name the different login
     * configurations.
     */
    public String name;

    public LoginConfig() {}
}
