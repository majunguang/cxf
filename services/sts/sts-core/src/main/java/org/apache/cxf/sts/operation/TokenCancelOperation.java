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

package org.apache.cxf.sts.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.ws.WebServiceContext;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.sts.QNameConstants;
import org.apache.cxf.sts.STSConstants;
import org.apache.cxf.sts.request.KeyRequirements;
import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.cxf.sts.request.ReceivedToken.STATE;
import org.apache.cxf.sts.request.RequestParser;
import org.apache.cxf.sts.request.TokenRequirements;
import org.apache.cxf.sts.token.canceller.TokenCanceller;
import org.apache.cxf.sts.token.canceller.TokenCancellerParameters;
import org.apache.cxf.sts.token.canceller.TokenCancellerResponse;
import org.apache.cxf.ws.security.sts.provider.STSException;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenCollectionType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenResponseCollectionType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenResponseType;
import org.apache.cxf.ws.security.sts.provider.model.RequestSecurityTokenType;
import org.apache.cxf.ws.security.sts.provider.model.RequestedTokenCancelledType;
import org.apache.cxf.ws.security.sts.provider.operation.CancelOperation;
import org.apache.ws.security.WSSecurityException;

/**
 *  An implementation for Cancel operation interface.
 */
public class TokenCancelOperation extends AbstractOperation implements CancelOperation {

    private static final Logger LOG = LogUtils.getL7dLogger(TokenCancelOperation.class);

    private List<TokenCanceller> tokencancellers = new ArrayList<TokenCanceller>();
    
    public void setTokenCancellers(List<TokenCanceller> tokenCancellerList) {
        this.tokencancellers = tokenCancellerList;
    }
    
    public List<TokenCanceller> getTokenCancellers() {
        return tokencancellers;
    }
    
    public RequestSecurityTokenResponseCollectionType cancel(
        RequestSecurityTokenCollectionType requestCollection, WebServiceContext context
    ) {
        RequestSecurityTokenResponseCollectionType responseCollection = 
            QNameConstants.WS_TRUST_FACTORY.createRequestSecurityTokenResponseCollectionType();
        for (RequestSecurityTokenType request : requestCollection.getRequestSecurityToken()) {
            RequestSecurityTokenResponseType response = cancel(request, context);
            responseCollection.getRequestSecurityTokenResponse().add(response);
        }
        return responseCollection;
    }
    
    public RequestSecurityTokenResponseType cancel(
        RequestSecurityTokenType request, WebServiceContext context
    ) {
        RequestParser requestParser = parseRequest(request, context);
        
        KeyRequirements keyRequirements = requestParser.getKeyRequirements();
        TokenRequirements tokenRequirements = requestParser.getTokenRequirements();
        
        ReceivedToken cancelTarget = tokenRequirements.getCancelTarget();
        if (cancelTarget == null || cancelTarget.getToken() == null) {
            throw new STSException("No element presented for cancellation", STSException.INVALID_REQUEST);
        }
        if (tokenRequirements.getTokenType() == null) {
            tokenRequirements.setTokenType(STSConstants.STATUS);
            LOG.fine(
                "Received TokenType is null, falling back to default token type: " + STSConstants.STATUS
            );
        }
        
        TokenCancellerParameters cancellerParameters = new TokenCancellerParameters();
        cancellerParameters.setStsProperties(stsProperties);
        cancellerParameters.setPrincipal(context.getUserPrincipal());
        cancellerParameters.setWebServiceContext(context);
        cancellerParameters.setTokenStore(getTokenStore());
        
        cancellerParameters.setKeyRequirements(keyRequirements);
        cancellerParameters.setTokenRequirements(tokenRequirements);   
        cancellerParameters.setToken(cancelTarget);
        
        //
        // Cancel token
        //
        TokenCancellerResponse tokenResponse = null;
        for (TokenCanceller tokenCanceller : tokencancellers) {
            if (tokenCanceller.canHandleToken(cancelTarget)) {
                try {
                    tokenResponse = tokenCanceller.cancelToken(cancellerParameters);
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "", ex);
                    throw new STSException(
                        "Error while cancelling a token", ex, STSException.REQUEST_FAILED
                    );
                }
                break;
            }
        }
        if (tokenResponse == null || tokenResponse.getToken() == null) {
            LOG.fine("No Token Canceller has been found that can handle this token");
            throw new STSException(
                "No token canceller found for requested token type: " 
                + tokenRequirements.getTokenType(), 
                STSException.REQUEST_FAILED
            );
        }
        
        if (tokenResponse.getToken().getState() != STATE.CANCELLED) {
            LOG.log(Level.WARNING, "Token cancellation failed.");
            throw new STSException("Token cancellation failed.");
        }
        
        // prepare response
        try {
            return createResponse(tokenRequirements);
        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "", ex);
            throw new STSException("Error in creating the response", ex, STSException.REQUEST_FAILED);
        }
    }

    
    private RequestSecurityTokenResponseType createResponse(
        TokenRequirements tokenRequirements
    ) throws WSSecurityException {
        RequestSecurityTokenResponseType response = 
            QNameConstants.WS_TRUST_FACTORY.createRequestSecurityTokenResponseType();
        String context = tokenRequirements.getContext();
        if (context != null) {
            response.setContext(context);
        }
        RequestedTokenCancelledType cancelType = 
            QNameConstants.WS_TRUST_FACTORY.createRequestedTokenCancelledType();
        JAXBElement<RequestedTokenCancelledType> cancel = 
            QNameConstants.WS_TRUST_FACTORY.createRequestedTokenCancelled(cancelType);
        response.getAny().add(cancel);            
        return response;
    }
}
