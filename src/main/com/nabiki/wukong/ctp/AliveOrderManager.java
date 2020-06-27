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

import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderActionField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcOrderField;
import com.nabiki.wukong.user.AliveOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code AliveOrderManager} keeps the status of all alive orders, interacts with
 * JNI interfaces and invoke callback methods to process responses.
 */
public class AliveOrderManager {
    private final Map<String, AliveOrder>
            ref2alive = new HashMap<>();    // Detail ref -> alive order
    private final Map<UUID, AliveOrder>
            uuid2alive = new HashMap<>();   // UUID -> alive order
    private final Map<AliveOrder, Set<String>>
            alive2order = new HashMap<>();  // Alive order -> detail ref
    private final Map<String, CThostFtdcOrderField>
            ref2order = new HashMap<>();    // Detail ref -> detail rtn order
    private final Map<String, CThostFtdcInputOrderField>
            ref2det = new HashMap<>();      // Detail ref -> detail order

    /**
     * Save the mapping from the specified input order to the specified alive order,
     * then send the specified input order to remote server.
     *
     * <p>If the remote service is temporarily unavailable, the order is saved to
     * send at next market open.
     * </p>
     *
     * @param order input order
     * @param alive alive order
     * @return returned value from JNI call
     */
    public int sendDetailOrder(CThostFtdcInputOrderField order, AliveOrder alive) {
        // TODO send order
        return 0;
    }

    /**
     * Send action request to remote server. The method first checks the type
     * of the specified order to be canceled. If it is an order, just cancel it. If
     * an action and action can't be canceled, return error code.
     *
     * @param action action to send
     * @param alive alive order
     * @return error code, or 0 if successful
     */
    public int sendOrderAction(CThostFtdcInputOrderActionField action,
                               AliveOrder alive) {
        // TODO send action
        return 0;
    }

    /**
     * Get the specified return order of the detail ref. If no order has the UUID,
     * return {@code null}.
     *
     * @param detailRef ref of the order
     * @return last updated return order, or {@code null} if no order has the UUID
     */
    public CThostFtdcOrderField getRtnOrder(String detailRef) {
        // TODO get return order with the ref
        return null;
    }

    /**
     * Get all detail order refs under the specified {@link UUID}. If no mapping
     * found, return an empty set.
     *
     * @param uuid UUID of the alive order that issues the detail orders
     * @return {@link Set} of detail order refs
     */
    public Set<String> getDetailRef(UUID uuid) {
        // TODO get detail refs
        return null;
    }

    /**
     * Get detail order of the specified detail ref. If no mapping found, return
     * {@code null}.
     *
     * @param detailRef detail order reference
     * @return detail order, or {@code null} if no such ref
     */
    public CThostFtdcInputOrderField getDetailOrder(String detailRef) {
        return null;
    }
}
