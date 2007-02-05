<<<<<<< .mine
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.transport.http;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.cxf.Bus;
import org.apache.cxf.transports.http.QueryHandler;
import org.apache.cxf.transports.http.QueryHandlerRegistry;

public class QueryHandlerRegistryImpl implements QueryHandlerRegistry {
    
    List<QueryHandler> queryHandlers;
    Bus bus;
    
    @PostConstruct
    public void init() {
        queryHandlers = new ArrayList<QueryHandler>();
        bus.setExtension(this, QueryHandlerRegistry.class);
    }

    public List<QueryHandler> getHandlers() {
        return queryHandlers;
    }

    public void registerHandler(QueryHandler handler) {
        queryHandlers.add(handler);
    }
    
    @Resource
    public void setBus(Bus b) {
        bus = b;
    }

    public Bus getBus() {
        return bus;
    }

}

