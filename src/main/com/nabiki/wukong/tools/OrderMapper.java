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

package com.nabiki.wukong.tools;

import com.nabiki.ctp4j.jni.struct.CThostFtdcInputOrderField;
import com.nabiki.ctp4j.jni.struct.CThostFtdcOrderField;
import com.nabiki.wukong.active.ActiveOrder;

import java.util.*;

public class OrderMapper {
    private final Map<String, UUID>
            detRef2Uuid = new HashMap<>();     // Detail ref -> UUID
    private final Map<UUID, ActiveOrder>
            uuid2Active = new HashMap<>();   // UUID -> alive order
    private final Map<UUID, Set<String>>
            uuid2DetRef = new HashMap<>();     // UUID -> detail ref
    private final Map<String, CThostFtdcOrderField>
            detRef2Rtn = new HashMap<>();   // Detail ref -> detail rtn order
    private final Map<String, CThostFtdcInputOrderField>
            detRef2Det = new HashMap<>();   // Detail ref -> detail order

    public OrderMapper() {
    }

    /**
     * Register the detailed order and active order, and create mappings.
     *
     * @param order detailed order
     * @param active active order that issues the detailed order
     */
    @InTeam
    public void register(CThostFtdcInputOrderField order, ActiveOrder active) {
        this.detRef2Uuid.put(order.OrderRef, active.getOrderUUID());
        this.uuid2Active.put(active.getOrderUUID(), active);
        this.uuid2DetRef.computeIfAbsent(active.getOrderUUID(),
                k -> new HashSet<>());
        this.uuid2DetRef.get(active.getOrderUUID()).add(order.OrderRef);
        this.detRef2Det.put(order.OrderRef, order);

    }

    /**
     * Register return order and create mapping.
     *
     * @param rtn return order
     */
    @InTeam
    public void register(CThostFtdcOrderField rtn) {
        this.detRef2Rtn.put(rtn.OrderRef, rtn);
    }

    /**
     * Get the specified return order of the detail ref. If no order has the UUID,
     * return {@code null}.
     *
     * @param detailRef ref of the order
     * @return last updated return order, or {@code null} if no order has the UUID
     */
    @InTeam
    public CThostFtdcOrderField getRtnOrder(String detailRef) {
        return this.detRef2Rtn.get(detailRef);
    }

    /**
     * Get all detail order refs under the specified {@link UUID}. If no mapping
     * found, return an empty set.
     *
     * @param uuid UUID of the alive order that issues the detail orders
     * @return {@link Set} of detail order refs
     */
    @InTeam
    public Set<String> getDetailRef(UUID uuid) {
        return OP.deepCopy(this.uuid2DetRef.get(uuid));
    }

    /**
     * Get detail order of the specified detail ref. If no mapping found, return
     * {@code null}.
     *
     * @param detailRef detail order reference
     * @return detail order, or {@code null} if no such ref
     */
    @InTeam
    public CThostFtdcInputOrderField getDetailOrder(String detailRef) {
        return this.detRef2Det.get(detailRef);
    }

    /*
    Get alive order with the specified UUID.
     */
    public ActiveOrder getActiveOrder(UUID uuid) {
        return this.uuid2Active.get(uuid);
    }

    /*
    Get alive order that issued the detail order with the specified detail order
    reference.
     */
    public ActiveOrder getActiveOrder(String detailRef) {
        return getActiveOrder(this.detRef2Uuid.get(detailRef));
    }
}
