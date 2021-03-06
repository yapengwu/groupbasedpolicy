/*
 * Copyright (C) 2014 Cisco Systems, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Thomas Bachman
 */
package org.opendaylight.groupbasedpolicy.renderer.opflex.lib.messages;

import java.util.List;

import org.opendaylight.groupbasedpolicy.jsonrpc.RpcMessage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonDeserialize
public class EndpointResolveResponse extends RpcMessage {

    public static final String EPP_RESOLVE_MESSAGE_RESPONSE = "endpoint_resolve_response";

    static public class Result {
        List<ManagedObject> endpoint;

        public List<ManagedObject> getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(List<ManagedObject> endpoint) {
            this.endpoint = endpoint;
        }
    }


    private JsonNode id;
    private Result result;
    private OpflexError error;

    @JsonIgnore
    private String name;
    @JsonIgnore
    private String method;

    @Override
    public JsonNode getId() {
        return this.id;
    }

    @Override
    public void setId(JsonNode id) {
        this.id = id;
    }

    public OpflexError getError() {
        return error;
    }

    public void setError(OpflexError error) {
        this.error = error;
    }

    @Override
    public String getMethod() {
        return null;
    }

    @Override
    public void setMethod(String method) {
    }

    public Result getResult() {
        return this.result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public EndpointResolveResponse(String name) {
        this.name = name;
    }

    public EndpointResolveResponse() {
        this.name = EPP_RESOLVE_MESSAGE_RESPONSE;
    }
    @JsonIgnore
    @Override
    public boolean valid() {
        return true;
    }

}
