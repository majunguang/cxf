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

package org.apache.cxf.ws.security.trust;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.binding.BindingFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.binding.soap.model.SoapOperationInfo;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.ServiceImpl;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.ws.addressing.VersionTransformer;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.neethi.Policy;
import org.apache.wss4j.policy.model.IssuedToken;

/**
 * 
 */
public final class STSUtils {
    /**
     * WS-T 1.0 Namespace.
     */
    public static final String WST_NS_05_02 = "http://schemas.xmlsoap.org/ws/2005/02/trust";
    /**
     * WS-T 1.3 Namespace.
     */
    public static final String WST_NS_05_12 = "http://docs.oasis-open.org/ws-sx/ws-trust/200512";
    /**
     * WS-T 1.4 Namespace.
     */
    public static final String WST_NS_08_02 = "http://docs.oasis-open.org/ws-sx/ws-trust/200802";
    public static final String SCT_NS_05_02 = "http://schemas.xmlsoap.org/ws/2005/02/sc";
    public static final String SCT_NS_05_12 
        = "http://docs.oasis-open.org/ws-sx/ws-secureconversation/200512";
    
    public static final String TOKEN_TYPE_SCT_05_02 = SCT_NS_05_02 + "/sct";
    public static final String TOKEN_TYPE_SCT_05_12 = SCT_NS_05_12 + "/sct";

    private STSUtils() {
        //utility class
    }
    
    public static String getTokenTypeSCT(String trustNs) {
        if (WST_NS_05_02.equals(trustNs)) {
            return TOKEN_TYPE_SCT_05_02;
        }
        return TOKEN_TYPE_SCT_05_12;
    }
    
    public static STSClient getClient(Message message, String type) {
        return getClient(message, type, null);
    }
    
    public static STSClient getClient(Message message, String type, IssuedToken itok) {
        STSClient client = (STSClient)message
            .getContextualProperty(SecurityConstants.STS_CLIENT);
        if (client == null) {
            if (type == null) {
                type = "";
            } else {
                type = "." + type + "-client";
            }
            client = new STSClient(message.getExchange().get(Bus.class));
            Endpoint ep = message.getExchange().get(Endpoint.class);
            client.setEndpointName(ep.getEndpointInfo().getName().toString() + type);
            client.setBeanName(ep.getEndpointInfo().getName().toString() + type);
            if (MessageUtils.getContextualBoolean(message, SecurityConstants.STS_CLIENT_SOAP12_BINDING, false)) {
                client.setSoap12();
            }
        }
        
        if (client.getLocation() == null && client.getWsdlLocation() == null 
            && itok != null && itok.getIssuer() != null) {
            EndpointReferenceType epr = null;
            try {
                epr = VersionTransformer.parseEndpointReference(itok.getIssuer());
            } catch (JAXBException e) {
                throw new IllegalArgumentException(e);
            }
            //configure via mex
            boolean useEPRWSAAddrAsMEXLocation = 
                !Boolean.valueOf((String)message.getContextualProperty(
                    SecurityConstants.DISABLE_STS_CLIENT_WSMEX_CALL_USING_EPR_ADDRESS));
            client.configureViaEPR(epr, useEPRWSAAddrAsMEXLocation);
        }
        return client;
    }
    public static Endpoint createSTSEndpoint(Bus bus, 
                                             String namespace,
                                             String transportId,
                                             String location,
                                             String soapVersion,
                                             Policy policy,
                                             QName epName) throws BusException, EndpointException {
        return createSTSEndpoint(bus, namespace, transportId, location, soapVersion, policy, epName, false);
    }     
    public static Endpoint createSCEndpoint(Bus bus, 
                                             String namespace,
                                             String transportId,
                                             String location,
                                             String soapVersion,
                                             Policy policy) throws BusException, EndpointException {
        return createSTSEndpoint(bus, namespace, transportId, location, soapVersion, policy, null, true);
    }     
    
    
    //CHECKSTYLE:OFF
    private static Endpoint createSTSEndpoint(Bus bus, 
                                             String namespace,
                                             String transportId,
                                             String location,
                                             String soapVersion,
                                             Policy policy,
                                             QName epName,
                                             boolean sc) throws BusException, EndpointException {
        //CHECKSTYLE:ON

        Service service = null;
        String ns = namespace + "/wsdl";
        ServiceInfo si = new ServiceInfo();
        
        QName iName = new QName(ns, sc ? "SecureConversationTokenService" : "SecurityTokenService");
        si.setName(iName);
        InterfaceInfo ii = new InterfaceInfo(si, iName);
        
        OperationInfo ioi = addIssueOperation(ii, namespace, ns);
        OperationInfo coi = addCancelOperation(ii, namespace, ns);
        
        si.setInterface(ii);
        service = new ServiceImpl(si);
        
        BindingFactoryManager bfm = bus.getExtension(BindingFactoryManager.class);
        BindingFactory bindingFactory = bfm.getBindingFactory(soapVersion);
        BindingInfo bi = bindingFactory.createBindingInfo(service, 
                                                          soapVersion, null);
        si.addBinding(bi);
        if (transportId == null) {
            ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
            ConduitInitiator ci = cim.getConduitInitiatorForUri(location);
            transportId = ci.getTransportIds().get(0);
        } 
        EndpointInfo ei = new EndpointInfo(si, transportId);
        ei.setBinding(bi);
        ei.setName(epName == null ? iName : epName);
        ei.setAddress(location);
        si.addEndpoint(ei);
        if (policy != null) {
            ei.addExtensor(policy);
        }
        
        BindingOperationInfo boi = bi.getOperation(ioi);
        SoapOperationInfo soi = boi.getExtensor(SoapOperationInfo.class);
        if (soi == null) {
            soi = new SoapOperationInfo();
            boi.addExtensor(soi);
        }
        soi.setAction(namespace + (sc ? "/RST/SCT" : "/RST/Issue"));
        
        boi = bi.getOperation(coi);
        soi = boi.getExtensor(SoapOperationInfo.class);
        if (soi == null) {
            soi = new SoapOperationInfo();
            boi.addExtensor(soi);
        }
        soi.setAction(namespace + (sc ? "/RST/SCT/Cancel" : "/RST/Cancel"));
        service.setDataBinding(new SourceDataBinding());
        return new EndpointImpl(bus, service, ei);
    }
    
    private static OperationInfo addIssueOperation(InterfaceInfo ii, 
                                                   String namespace,
                                                   String servNamespace) {
        OperationInfo oi = ii.addOperation(new QName(servNamespace, "RequestSecurityToken"));
        MessageInfo mii = oi.createMessage(new QName(servNamespace, "RequestSecurityTokenMsg"), 
                                           MessageInfo.Type.INPUT);
        oi.setInput("RequestSecurityTokenMsg", mii);
        MessagePartInfo mpi = mii.addMessagePart("request");
        mpi.setElementQName(new QName(namespace, "RequestSecurityToken"));
        
        MessageInfo mio = oi.createMessage(new QName(servNamespace, 
            "RequestSecurityTokenResponseMsg"), 
            MessageInfo.Type.OUTPUT);
        oi.setOutput("RequestSecurityTokenResponseMsg", mio);
        mpi = mio.addMessagePart("response");
        
        if (WST_NS_05_02.equals(namespace)) {
            mpi.setElementQName(new QName(namespace, "RequestSecurityTokenResponse"));
        } else {
            mpi.setElementQName(new QName(namespace, "RequestSecurityTokenResponseCollection"));
        }
        return oi;
    }
    private static OperationInfo addCancelOperation(InterfaceInfo ii, 
                                                    String namespace,
                                                    String servNamespace) {
        OperationInfo oi = ii.addOperation(new QName(servNamespace, "CancelSecurityToken"));
        MessageInfo mii = oi.createMessage(new QName(servNamespace, "CancelSecurityTokenMsg"), 
                                           MessageInfo.Type.INPUT);
        oi.setInput("CancelSecurityTokenMsg", mii);
        MessagePartInfo mpi = mii.addMessagePart("request");
        mpi.setElementQName(new QName(namespace, "RequestSecurityToken"));
        
        MessageInfo mio = oi.createMessage(new QName(servNamespace, 
                                                     "CancelSecurityTokenResponseMsg"), 
                                           MessageInfo.Type.OUTPUT);
        oi.setOutput("CancelSecurityTokenResponseMsg", mio);
        mpi = mio.addMessagePart("response");
        
        if (WST_NS_05_02.equals(namespace)) {
            mpi.setElementQName(new QName(namespace, "RequestSecurityTokenResponse"));
        } else {
            mpi.setElementQName(new QName(namespace, "RequestSecurityTokenResponseCollection"));
        }
        return oi;
    }
}
