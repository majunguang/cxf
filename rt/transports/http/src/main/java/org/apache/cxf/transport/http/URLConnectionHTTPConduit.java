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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

import org.apache.cxf.Bus;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CacheAndWriteOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.https.HttpsURLConnectionFactory;
import org.apache.cxf.transport.https.HttpsURLConnectionInfo;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.workqueue.AutomaticWorkQueue;
import org.apache.cxf.workqueue.WorkQueueManager;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * 
 */
public class URLConnectionHTTPConduit extends HTTPConduit {
    private static boolean hasLoggedAsyncWarning;

    /**
     * This field holds the connection factory, which primarily is used to 
     * factor out SSL specific code from this implementation.
     * <p>
     * This field is "protected" to facilitate some contrived UnitTesting so
     * that an extended class may alter its value with an EasyMock URLConnection
     * Factory. 
     */
    protected HttpsURLConnectionFactory connectionFactory;
        
    
    public URLConnectionHTTPConduit(Bus b, EndpointInfo ei) throws IOException {
        super(b, ei);
        connectionFactory = new HttpsURLConnectionFactory();
        CXFAuthenticator.addAuthenticator();
    }

    public URLConnectionHTTPConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
        connectionFactory = new HttpsURLConnectionFactory();
        CXFAuthenticator.addAuthenticator();
    }
    
    /**
     * Close the conduit
     */
    public void close() {
        super.close();
        if (defaultEndpointURL != null) {
            try {
                URLConnection connect = defaultEndpointURL.openConnection();
                if (connect instanceof HttpURLConnection) {
                    ((HttpURLConnection)connect).disconnect();
                }
            } catch (IOException ex) {
                //ignore
            }
            //defaultEndpointURL = null;
        }
    }    
    
    private HttpURLConnection createConnection(Message message, URL url, HTTPClientPolicy csPolicy) throws IOException {
        Proxy proxy = proxyFactory.createProxy(csPolicy , url);
        return connectionFactory.createConnection(tlsClientParameters, proxy, url);
    }
    protected void setupConnection(Message message, URL currentURL, HTTPClientPolicy csPolicy) throws IOException {
        HttpURLConnection connection = createConnection(message, currentURL, csPolicy);
        connection.setDoOutput(true);       
        
        int ctimeout = determineConnectionTimeout(message, csPolicy);
        connection.setConnectTimeout(ctimeout);
        
        int rtimeout = determineReceiveTimeout(message, csPolicy);
        connection.setReadTimeout(rtimeout);
        
        connection.setUseCaches(false);
        // We implement redirects in this conduit. We do not
        // rely on the underlying URLConnection implementation
        // because of trust issues.
        connection.setInstanceFollowRedirects(false);

        // If the HTTP_REQUEST_METHOD is not set, the default is "POST".
        String httpRequestMethod = 
            (String)message.get(Message.HTTP_REQUEST_METHOD);
        if (httpRequestMethod == null) {
            httpRequestMethod = "POST";
            message.put(Message.HTTP_REQUEST_METHOD, "POST");
        }
        connection.setRequestMethod(httpRequestMethod);
        
        // We place the connection on the message to pick it up
        // in the WrappedOutputStream.
        message.put(KEY_HTTP_CONNECTION, connection);
    }

    
    protected OutputStream createOutputStream(Message message, 
                                              boolean needToCacheRequest, 
                                              boolean isChunking,
                                              int chunkThreshold) {
        HttpURLConnection connection = (HttpURLConnection)message.get(KEY_HTTP_CONNECTION);
        
        if (isChunking && chunkThreshold <= 0) {
            chunkThreshold = 0;
            connection.setChunkedStreamingMode(-1);                    
        }
        return new URLConnectionWrappedOutputStream(message, connection,
                                       needToCacheRequest, 
                                       isChunking,
                                       chunkThreshold,
                                       getConduitName());
    }
    
    class URLConnectionWrappedOutputStream extends WrappedOutputStream {
        HttpURLConnection connection;
        public URLConnectionWrappedOutputStream(Message message, HttpURLConnection connection,
                                                boolean needToCacheRequest, boolean isChunking,
                                                int chunkThreshold, String conduitName) {
            super(message, needToCacheRequest, isChunking,
                  chunkThreshold, conduitName,
                  connection.getURL().toString());
            this.connection = connection;
        }
        // This construction makes extending the HTTPConduit more easier 
        protected URLConnectionWrappedOutputStream(URLConnectionWrappedOutputStream wos) {
            super(wos);
            this.connection = wos.connection;
        }
        protected void setupWrappedStream() throws IOException {
            // If we need to cache for retransmission, store data in a
            // CacheAndWriteOutputStream. Otherwise write directly to the output stream.
            if (cachingForRetransmission) {
                cachedStream =
                    new CacheAndWriteOutputStream(connection.getOutputStream());
                wrappedStream = cachedStream;
            } else {
                wrappedStream = connection.getOutputStream();
            }
        }
        @Override
        public void thresholdReached() {
            if (chunking) {
                connection.setChunkedStreamingMode(-1);
            }
        }
        @Override
        protected void onFirstWrite() throws IOException {
            super.onFirstWrite();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Sending "
                    + connection.getRequestMethod() 
                    + " Message with Headers to " 
                    + url
                    + " Conduit :"
                    + conduitName
                    + "\n");
            }
        }
        protected void setProtocolHeaders() throws IOException {
            new Headers(outMessage).setProtocolHeadersInConnection(connection);
        }

        protected HttpsURLConnectionInfo getHttpsURLConnectionInfo() throws IOException {
            connection.connect();
            return new HttpsURLConnectionInfo(connection);
        }
        protected void updateCookies() {
            cookies.readFromConnection(connection);
        }
        protected void updateResponseHeaders(Message inMessage) {
            new Headers(inMessage).readFromConnection(connection);
            inMessage.put(Message.CONTENT_TYPE, connection.getContentType());
            cookies.readFromConnection(connection);
        }
        protected InputStream getInputStream(int responseCode) throws IOException {
            InputStream in = null;
            if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                in = connection.getErrorStream();
                if (in == null) {
                    try {
                        // just in case - but this will most likely cause an exception
                        in = connection.getInputStream();
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            } else {
                in = connection.getInputStream();
            }
            return in;
        }

        
        protected void closeInputStream() throws IOException {
            //try and consume any content so that the connection might be reusable
            InputStream ins = connection.getErrorStream();
            if (ins == null) {
                ins = connection.getInputStream();
            }
            if (ins != null) {
                IOUtils.consume(ins);
                ins.close();
            }
        }
        protected void handleResponseAsync() throws IOException {
            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        handleResponseInternal();
                    } catch (Exception e) {
                        ((PhaseInterceptorChain)outMessage.getInterceptorChain()).abort();
                        ((PhaseInterceptorChain)outMessage.getInterceptorChain()).unwind(outMessage);
                        outMessage.setContent(Exception.class, e);
                        outMessage.getInterceptorChain().getFaultObserver().onMessage(outMessage);
                    }
                }
            };
            HTTPClientPolicy policy = getClient(outMessage);
            try {
                Executor ex = outMessage.getExchange().get(Executor.class);
                if (ex == null) {
                    WorkQueueManager mgr = outMessage.getExchange().get(Bus.class)
                        .getExtension(WorkQueueManager.class);
                    AutomaticWorkQueue qu = mgr.getNamedWorkQueue("http-conduit");
                    if (qu == null) {
                        qu = mgr.getAutomaticWorkQueue();
                    }
                    long timeout = 5000;
                    if (policy != null && policy.isSetAsyncExecuteTimeout()) {
                        timeout = policy.getAsyncExecuteTimeout();
                    }
                    if (timeout > 0) {
                        qu.execute(runnable, timeout);
                    } else {
                        qu.execute(runnable);
                    }
                } else {
                    outMessage.getExchange().put(Executor.class.getName() 
                                             + ".USING_SPECIFIED", Boolean.TRUE);
                    ex.execute(runnable);
                }
            } catch (RejectedExecutionException rex) {
                if (policy != null && policy.isSetAsyncExecuteTimeoutRejection()
                    && policy.isAsyncExecuteTimeoutRejection()) {
                    throw rex;
                }
                if (!hasLoggedAsyncWarning) {
                    LOG.warning("EXECUTOR_FULL_WARNING");
                    hasLoggedAsyncWarning = true;
                }
                LOG.fine("EXECUTOR_FULL");
                handleResponseInternal();
            }
        }
        protected int getResponseCode() throws IOException {
            return connection.getResponseCode();
        }
        protected String getResponseMessage() throws IOException {
            return connection.getResponseMessage();
        }
        protected InputStream getPartialResponse(int responseCode) throws IOException {
            return ChunkedUtil.getPartialResponse(connection, responseCode);
        }
        protected boolean usingProxy() {
            return connection.usingProxy();
        }
        protected void setFixedLengthStreamingMode(int i) {
            connection.setFixedLengthStreamingMode(i);
        }
        protected void setupNewConnection(String newURL) throws IOException {
            HTTPClientPolicy cp = getClient(outMessage);
            URL nurl = new URL(newURL);
            setupConnection(outMessage, nurl, cp);
            url = newURL;
            connection = (HttpURLConnection)outMessage.get(KEY_HTTP_CONNECTION);
        }

        @Override
        protected void retransmitStream() throws IOException {
            OutputStream out = connection.getOutputStream();
            cachedStream.writeCacheTo(out);
        }
    }
    
}
