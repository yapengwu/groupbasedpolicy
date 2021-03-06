/*
 * Copyright (C) 2014 Cisco Systems, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Thomas Bachman
 */
package org.opendaylight.groupbasedpolicy.jsonrpc;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class RpcMessageMap {
    private ConcurrentMap<String, RpcMessage> messageMap;

    public boolean containsMessages (List<RpcMessage> messages) {
        return messages.containsAll(messageMap.values());
    }

    public RpcMessageMap() {
        messageMap = new ConcurrentHashMap<String, RpcMessage>();
    }

    public RpcMessage get(String messageName) {
        return messageMap.get(messageName);
    }

    public void add(RpcMessage message) {
        messageMap.put(message.getName(), message);
    }

    public void addList(List<RpcMessage> messages) {
        for ( RpcMessage msg : messages ) {
            this.add(msg);
        }
    }
}
