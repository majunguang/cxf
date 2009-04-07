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

package org.apache.cxf.jaxrs.lifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.apache.cxf.message.Message;

public class PerRequestResourceProvider implements ResourceProvider {
    private Constructor<?> c;
   
    public PerRequestResourceProvider(Class<?> clazz) {
        c = ResourceUtils.findResourceConstructor(clazz);
        if (c == null) {
            throw new RuntimeException("Resource class " + clazz
                                       + " has no valid constructor");
        }
    }
    
    public boolean isSingleton() {
        return false;
    }

    public Object getInstance(Message m) {  
        return createInstance(m);
    }
    
    public Object getInstance() {  
        if (c.getParameterTypes().length > 0) {
            throw new RuntimeException("Resource class constructor has context parameters "
                          + "but no request message is available");
        }
        return createInstance(null);
    }
    
    protected Object createInstance(Message m) {
        
        Class<?>[] params = c.getParameterTypes();
        Type[] genericTypes = c.getGenericParameterTypes();
        Object[] values = new Object[]{params.length};
        for (int i = 0; i < params.length; i++) {
            values[i] = JAXRSUtils.createContextValue(m, genericTypes[i], params[i]);
        }
        try {
            return params.length > 0 ? c.newInstance(values) : c.newInstance(new Object[]{});
        } catch (InstantiationException ex) {
            String msg = "Resource class " + c.getDeclaringClass().getName() + " can not be instantiated";
            throw new WebApplicationException(Response.serverError().entity(msg).build());
        } catch (IllegalAccessException ex) {
            String msg = "Resource class " + c.getDeclaringClass().getName() + " can not be instantiated"
                + " due to IllegalAccessException";
            throw new WebApplicationException(Response.serverError().entity(msg).build());
        } catch (InvocationTargetException ex) {
            String msg = "Resource class "
                + c.getDeclaringClass().getName() + " can not be instantiated"
                + " due to InvocationTargetException";
            throw new WebApplicationException(Response.serverError().entity(msg).build());
        }
        
    }
}
