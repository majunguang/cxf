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

package org.apache.cxf.ws.policy.selector;


import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.ws.policy.AlternativeSelector;
import org.apache.neethi.Assertion;


/**
 * 
 */
public abstract class BaseAlternativeSelector implements AlternativeSelector {
 
    protected boolean isCompatibleWithRequest(List<Assertion> alternative,
                                   List<List<Assertion>> request) {
        if (request == null) {
            return true;
        }
        for (List<Assertion> r : request) {
            if (isCompatible(alternative, r)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isCompatible(List<Assertion> alternative, List<Assertion> r) {
        List<Assertion> r2 = new ArrayList<Assertion>(r);
        for (Assertion a : alternative) {
            r2.remove(a);
        }
        return r2.isEmpty();
    }
    
    
}
