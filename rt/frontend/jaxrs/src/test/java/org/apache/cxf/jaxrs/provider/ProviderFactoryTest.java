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

package org.apache.cxf.jaxrs.provider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.validation.Schema;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.Customer;
import org.apache.cxf.jaxrs.CustomerParameterHandler;
import org.apache.cxf.jaxrs.JAXBContextProvider;
import org.apache.cxf.jaxrs.JAXBContextProvider2;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper;
import org.apache.cxf.jaxrs.model.AbstractResourceInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.wadl.WadlGenerator;
import org.apache.cxf.jaxrs.resources.Book;
import org.apache.cxf.jaxrs.resources.SuperBook;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProviderFactoryTest extends Assert {

    
    @Before
    public void setUp() {
        ServerProviderFactory.getInstance().clearProviders();
        AbstractResourceInfo.clearAllMaps();
    }
    
    @Test
    public void testMultipleFactories() {
        assertNotSame(ServerProviderFactory.getInstance(), ServerProviderFactory.getInstance());
    }
    
    @Test
    public void testCustomWadlHandler() {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        assertEquals(1, pf.getPreMatchContainerRequestFilters().size());
        assertTrue(pf.getPreMatchContainerRequestFilters().get(0).getProvider() instanceof WadlGenerator);
        
        WadlGenerator wg = new WadlGenerator();
        pf.setUserProviders(Collections.singletonList(wg));
        assertEquals(1, pf.getPreMatchContainerRequestFilters().size());
        assertTrue(pf.getPreMatchContainerRequestFilters().get(0).getProvider() instanceof WadlGenerator);
        assertSame(wg, pf.getPreMatchContainerRequestFilters().get(0).getProvider());
    }
    
    @Test
    public void testCustomTestHandler() {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        assertEquals(1, pf.getPreMatchContainerRequestFilters().size());
        assertTrue(pf.getPreMatchContainerRequestFilters().get(0).getProvider() instanceof WadlGenerator);
        
        TestHandler th = new TestHandler();
        pf.setUserProviders(Collections.singletonList(th));
        assertEquals(2, pf.getPreMatchContainerRequestFilters().size());
        assertTrue(pf.getPreMatchContainerRequestFilters().get(0).getProvider() instanceof WadlGenerator);
        assertSame(th, pf.getPreMatchContainerRequestFilters().get(1).getProvider());
    }
    
    @Test
    public void testCustomTestAndWadlHandler() {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        assertEquals(1, pf.getPreMatchContainerRequestFilters().size());
        assertTrue(pf.getPreMatchContainerRequestFilters().get(0).getProvider() instanceof WadlGenerator);
        
        List<Object> providers = new ArrayList<Object>();
        WadlGenerator wg = new WadlGenerator();
        providers.add(wg);
        TestHandler th = new TestHandler();
        providers.add(th);
        pf.setUserProviders(providers);
        assertEquals(2, pf.getPreMatchContainerRequestFilters().size());
        assertSame(wg, pf.getPreMatchContainerRequestFilters().get(0).getProvider());
        assertSame(th, pf.getPreMatchContainerRequestFilters().get(1).getProvider());
    }
    
    
    @Test
    public void testCustomJaxbProvider() {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        JAXBElementProvider<Book> provider = new JAXBElementProvider<Book>();
        pf.registerUserProvider(provider);
        MessageBodyReader<Book> customJaxbReader = pf.createMessageBodyReader(Book.class, null, null, 
                                                              MediaType.TEXT_XML_TYPE, new MessageImpl());
        assertSame(customJaxbReader, provider);
        
        MessageBodyWriter<Book> customJaxbWriter = pf.createMessageBodyWriter(Book.class, null, null, 
                                                              MediaType.TEXT_XML_TYPE, new MessageImpl());
        assertSame(customJaxbWriter, provider);
    }
    
    @Test
    public void testDataSourceReader() {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new DataSourceProvider<Object>());
        MessageBodyReader<DataSource> reader = pf.createMessageBodyReader(
              DataSource.class, null, null, 
              MediaType.valueOf("image/png"), new MessageImpl());
        assertTrue(reader instanceof DataSourceProvider);
        MessageBodyReader<DataHandler> reader2 = pf.createMessageBodyReader(
                          DataHandler.class, null, null, 
                          MediaType.valueOf("image/png"), new MessageImpl());
        assertSame(reader, reader2);
    }
    
    @Test
    public void testDataSourceWriter() {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new DataSourceProvider<Object>());
        MessageBodyWriter<DataSource> writer = pf.createMessageBodyWriter(
              DataSource.class, null, null, 
              MediaType.valueOf("image/png"), new MessageImpl());
        assertTrue(writer instanceof DataSourceProvider);
        MessageBodyWriter<DataHandler> writer2 = pf.createMessageBodyWriter(
                          DataHandler.class, null, null, 
                          MediaType.valueOf("image/png"), new MessageImpl());
        assertSame(writer, writer2);
    }
    
    @Test
    public void testNoDataSourceWriter() {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new DataSourceProvider<Object>());
        MessageBodyWriter<DataSource> writer = pf.createMessageBodyWriter(
              DataSource.class, null, null, 
              MediaType.valueOf("multipart/form-data"), new MessageImpl());
        assertFalse(writer instanceof DataSourceProvider);
    }
    
    
    @Test
    public void testSchemaLocations() {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        MessageBodyReader<Book> jaxbReader = pf.createMessageBodyReader(Book.class, null, null, 
                                                              MediaType.TEXT_XML_TYPE, new MessageImpl());
        assertTrue(jaxbReader instanceof JAXBElementProvider);
        pf.setSchemaLocations(Collections.singletonList("classpath:/test.xsd"));
        MessageBodyReader<Book> customJaxbReader = pf.createMessageBodyReader(
            Book.class, null, null, MediaType.TEXT_XML_TYPE, new MessageImpl());
        assertTrue(jaxbReader instanceof JAXBElementProvider);
        assertNotSame(jaxbReader, customJaxbReader);
        
        assertNull(((JAXBElementProvider<Book>)jaxbReader).getSchema());
        assertNotNull(((JAXBElementProvider<Book>)customJaxbReader).getSchema());
    }
    
    @Test
    public void testGetFactoryInboundMessage() {
        ProviderFactory factory = ServerProviderFactory.getInstance();
        Message m = new MessageImpl();
        Exchange e = new ExchangeImpl();
        m.setExchange(e);
        Endpoint endpoint = EasyMock.createMock(Endpoint.class);
        endpoint.get(ServerProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(factory);
        EasyMock.replay(endpoint);
        e.put(Endpoint.class, endpoint);
        assertSame(ProviderFactory.getInstance(m), factory);
    }
    
    @Test
    public void testDefaultUserExceptionMappers() throws Exception {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        ExceptionMapper<?> mapper = 
            pf.createExceptionMapper(WebApplicationException.class, new MessageImpl());
        assertNotNull(mapper);
        WebApplicationExceptionMapper wm = new WebApplicationExceptionMapper(); 
        pf.registerUserProvider(wm);
        ExceptionMapper<?> mapper2 = 
            pf.createExceptionMapper(WebApplicationException.class, new MessageImpl());
        assertNotSame(mapper, mapper2);
        assertSame(wm, mapper2);
    }
    
    @Test
    public void testExceptionMappersHierarchy1() throws Exception {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        WebApplicationExceptionMapper wm = new WebApplicationExceptionMapper(); 
        pf.registerUserProvider(wm);
        assertSame(wm, pf.createExceptionMapper(WebApplicationException.class, new MessageImpl()));
        assertNull(pf.createExceptionMapper(RuntimeException.class, new MessageImpl()));
        TestRuntimeExceptionMapper rm = new TestRuntimeExceptionMapper(); 
        pf.registerUserProvider(rm);
        assertSame(wm, pf.createExceptionMapper(WebApplicationException.class, new MessageImpl()));
        assertSame(rm, pf.createExceptionMapper(RuntimeException.class, new MessageImpl()));
    }
    
    @Test
    public void testExceptionMappersHierarchy2() throws Exception {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        
        TestRuntimeExceptionMapper rm = new TestRuntimeExceptionMapper(); 
        pf.registerUserProvider(rm);
        assertSame(rm, pf.createExceptionMapper(WebApplicationException.class, new MessageImpl()));
        assertSame(rm, pf.createExceptionMapper(RuntimeException.class, new MessageImpl()));
        
        WebApplicationExceptionMapper wm = new WebApplicationExceptionMapper(); 
        pf.registerUserProvider(wm);
        assertSame(wm, pf.createExceptionMapper(WebApplicationException.class, new MessageImpl()));
        assertSame(rm, pf.createExceptionMapper(RuntimeException.class, new MessageImpl()));
    }
    
    @Test
    public void testExceptionMappersHierarchyWithGenerics() throws Exception {
        ServerProviderFactory pf = ServerProviderFactory.getInstance();
        RuntimeExceptionMapper1 exMapper1 = new RuntimeExceptionMapper1(); 
        pf.registerUserProvider(exMapper1);
        RuntimeExceptionMapper2 exMapper2 = new RuntimeExceptionMapper2(); 
        pf.registerUserProvider(exMapper2);
        assertSame(exMapper1, pf.createExceptionMapper(RuntimeException.class, new MessageImpl()));
        assertSame(exMapper2, pf.createExceptionMapper(WebApplicationException.class, new MessageImpl()));
    }
    
    @Test
    public void testMessageBodyHandlerHierarchy() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        List<Object> providers = new ArrayList<Object>();
        BookReaderWriter bookHandler = new BookReaderWriter();
        providers.add(bookHandler);
        SuperBookReaderWriter superBookHandler = new SuperBookReaderWriter();
        providers.add(superBookHandler);
        pf.setUserProviders(providers);
        assertSame(bookHandler, 
                   pf.createMessageBodyReader(Book.class, Book.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
        assertSame(superBookHandler, 
                   pf.createMessageBodyReader(SuperBook.class, SuperBook.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
        assertSame(bookHandler, 
                   pf.createMessageBodyWriter(Book.class, Book.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
        assertSame(superBookHandler, 
                   pf.createMessageBodyWriter(SuperBook.class, SuperBook.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
    }
    
    @Test
    public void testMessageBodyWriterNoTypes() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        List<Object> providers = new ArrayList<Object>();
        SuperBookReaderWriter2<SuperBook> superBookHandler = new SuperBookReaderWriter2<SuperBook>();
        providers.add(superBookHandler);
        pf.setUserProviders(providers);
        assertSame(superBookHandler, 
                   pf.createMessageBodyReader(SuperBook.class, SuperBook.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
        assertSame(superBookHandler, 
                   pf.createMessageBodyWriter(SuperBook.class, SuperBook.class, new Annotation[]{}, 
                                              MediaType.APPLICATION_XML_TYPE, new MessageImpl()));
    }
    
    @Test
    public void testSortEntityProviders() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new TestStringProvider());
        pf.registerUserProvider(new PrimitiveTextProvider<Object>());
        
        List<ProviderInfo<MessageBodyReader<?>>> readers = pf.getMessageReaders();

        assertTrue(indexOf(readers, TestStringProvider.class) 
                   < indexOf(readers, PrimitiveTextProvider.class));
        
        List<ProviderInfo<MessageBodyWriter<?>>> writers = pf.getMessageWriters();

        assertTrue(indexOf(writers, TestStringProvider.class) 
                   < indexOf(writers, PrimitiveTextProvider.class));
        
    }
    
    @Test
    public void testParameterHandlerProvider() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        ParamConverterProvider h = new CustomerParameterHandler();
        pf.registerUserProvider(h);
        ParamConverter<Customer> h2 = pf.createParameterHandler(Customer.class);
        assertSame(h2, h);
    }
    
    @Test
    public void testGetStringProvider() throws Exception {
        verifyProvider(String.class, PrimitiveTextProvider.class, "text/plain");
    }
    
    @Test
    public void testGetBinaryProvider() throws Exception {
        verifyProvider(byte[].class, BinaryDataProvider.class, "*/*");
        verifyProvider(InputStream.class, BinaryDataProvider.class, "image/png");
        MessageBodyWriter<File> writer = ServerProviderFactory.getInstance()
            .createMessageBodyWriter(File.class, null, null, MediaType.APPLICATION_OCTET_STREAM_TYPE, 
                                     new MessageImpl());
        assertTrue(BinaryDataProvider.class == writer.getClass());
    }
    
    private void verifyProvider(ProviderFactory pf, Class<?> type, Class<?> provider, String mediaType) 
        throws Exception {
        
        if (pf == null) {
            pf = ServerProviderFactory.getInstance();
        }
        
        MediaType mType = MediaType.valueOf(mediaType);
        
        MessageBodyReader<?> reader = pf.createMessageBodyReader(type, type, null, mType, new MessageImpl());
        assertSame("Unexpected provider found", provider, reader.getClass());
    
        MessageBodyWriter<?> writer = pf.createMessageBodyWriter(type, type, null, mType, new MessageImpl());
        assertTrue("Unexpected provider found", provider == writer.getClass());
    }
    
    
    private void verifyProvider(Class<?> type, Class<?> provider, String mediaType) 
        throws Exception {
        verifyProvider(null, type, provider, mediaType);
        
    }
       
    @Test
    public void testGetStringProviderWildCard() throws Exception {
        verifyProvider(String.class, PrimitiveTextProvider.class, "text/*");
    }
    
    
    @Test
    public void testGetStringProviderUsingProviderDeclaration() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new TestStringProvider());
        verifyProvider(pf, String.class, TestStringProvider.class, "text/html");
    }    
    
    @Test
    public void testRegisterCustomJSONEntityProvider() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new CustomJSONProvider());
        verifyProvider(pf, Book.class, CustomJSONProvider.class, 
                       "application/json");
    }
    
    
    @Test
    public void testRegisterCustomResolver() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        Message message = prepareMessage("*/*", null);
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, message);
        assertFalse(cr instanceof ProviderFactory.ContextResolverProxy);
        assertTrue("JAXBContext ContextProvider can not be found", 
                   cr instanceof JAXBContextProvider);
        
    }
    
    @Test
    public void testRegisterCustomResolver2() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        pf.registerUserProvider(new JAXBContextProvider2());
        Message message = prepareMessage("text/xml+b", null);
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, message);
        assertFalse(cr instanceof ProviderFactory.ContextResolverProxy);
        assertTrue("JAXBContext ContextProvider can not be found", 
                   cr instanceof JAXBContextProvider2);
        
    }
    
    @Test
    public void testNoCustomResolver() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        pf.registerUserProvider(new JAXBContextProvider2());
        Message message = prepareMessage("text/xml+c", null);
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, message);
        assertNull(cr);
    }
    
    @Test
    public void testCustomResolverOut() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        pf.registerUserProvider(new JAXBContextProvider2());
        Message message = prepareMessage("text/xml+c", "text/xml+a");
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, message);
        assertFalse(cr instanceof ProviderFactory.ContextResolverProxy);
        assertTrue("JAXBContext ContextProvider can not be found", 
                   cr instanceof JAXBContextProvider);
    }
    
    @Test
    public void testCustomResolverProxy() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        pf.registerUserProvider(new JAXBContextProvider2());
        Message message = prepareMessage("text/xml+*", null);
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, message);
        assertTrue(cr instanceof ProviderFactory.ContextResolverProxy);
        assertTrue(((ProviderFactory.ContextResolverProxy<?>)cr).getResolvers().get(0) 
                   instanceof JAXBContextProvider);
        assertTrue(((ProviderFactory.ContextResolverProxy<?>)cr).getResolvers().get(1) 
                   instanceof JAXBContextProvider2);
    }
    
    private Message prepareMessage(String contentType, String acceptType) {
        Message message = new MessageImpl();
        Map<String, List<String>> headers = new MetadataMap<String, String>();
        message.put(Message.PROTOCOL_HEADERS, headers);
        Exchange exchange = new ExchangeImpl();
        exchange.setInMessage(message);
        if (acceptType != null) {
            headers.put("Accept", Collections.singletonList(acceptType));
            exchange.setOutMessage(new MessageImpl());
        } else {
            headers.put("Content-Type", Collections.singletonList(contentType));
        }
        message.setExchange(exchange);
        return message;
    }
    
    @Test
    public void testRegisterCustomEntityProvider() throws Exception {
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(new CustomWidgetProvider());
        
        verifyProvider(pf, org.apache.cxf.jaxrs.resources.Book.class, CustomWidgetProvider.class, 
                       "application/widget");
    }
    
    private int indexOf(List<? extends Object> providerInfos, Class<?> providerType) {
        int index = 0;
        for (Object pi : providerInfos) {
            Object p = ((ProviderInfo<?>)pi).getProvider();
            if (p.getClass().isAssignableFrom(providerType)) {
                break;
            }
            index++;
        }
        return index;
    }

    @Consumes("text/html")
    @Produces("text/html")
    private final class TestStringProvider 
        implements MessageBodyReader<String>, MessageBodyWriter<String>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations,
                                  MediaType m) {
            return type == String.class;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations,
                                   MediaType m) {
            return type == String.class;
        }
        
        public long getSize(String s, Class<?> type, Type genericType, Annotation[] annotations, 
                            MediaType m) {
            return s.length();
        }

        public String readFrom(Class<String> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {
            try {
                return IOUtils.toString(is);
            } catch (IOException e) {
                // TODO: better exception handling
            }
            return null;
        }

        public void writeTo(String obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            try {
                os.write(obj.getBytes());
            } catch (IOException e) {
                // TODO: better exception handling
            }
        }

    }
    
    @Consumes("application/json")
    @Produces("application/json")
    private final class CustomJSONProvider 
        implements MessageBodyReader<Book>, MessageBodyWriter<Book>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations,
                                  MediaType m) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations,
                                   MediaType m) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public long getSize(Book b, Class<?> type, Type genericType, Annotation[] annotations,
                            MediaType m) {
            return -1;
        }

        public Book readFrom(Class<Book> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {    
            //Dummy
            return null;
        }

        public void writeTo(Book obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            //Dummy
        }

    }
    
    @Consumes("application/widget")
    @Produces("application/widget")
    private final class CustomWidgetProvider
        implements MessageBodyReader<Book>, MessageBodyWriter<Book>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations,
                                  MediaType m) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations,
                                   MediaType m) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public long getSize(Book s, Class<?> type, Type genericType, Annotation[] annotations,
                            MediaType m) {
            return -1;
        }


        public Book readFrom(Class<Book> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {    
            //Dummy
            return null;
        }

        public void writeTo(Book obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            //Dummy
        }

    }
    
    @Test
    public void testSetSchemasFromClasspath() {
        JAXBElementProvider<?> provider = new JAXBElementProvider<Object>();
        ProviderFactory pf = ServerProviderFactory.getInstance();
        pf.registerUserProvider(provider);
        
        List<String> locations = new ArrayList<String>();
        locations.add("classpath:/test.xsd");
        pf.setSchemaLocations(locations);
        Schema s = provider.getSchema();
        assertNotNull("schema can not be read from classpath", s);
    }
    
    private static class TestRuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {

        public Response toResponse(RuntimeException exception) {
            // TODO Auto-generated method stub
            return null;
        }
        
    }
    
    @Produces("application/xml")
    @Consumes("application/xml")
    private static class BookReaderWriter 
        implements MessageBodyReader<Book>, MessageBodyWriter<Book> {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, 
                                  MediaType mediaType) {
            return true;
        }

        public Book readFrom(Class<Book> arg0, Type arg1, Annotation[] arg2, 
                             MediaType arg3, MultivaluedMap<String, String> arg4, InputStream arg5) 
            throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
            return null;
        }

        public long getSize(Book t, Class<?> type, Type genericType, Annotation[] annotations, 
                            MediaType mediaType) {
            // TODO Auto-generated method stub
            return 0;
        }

        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, 
                                   MediaType mediaType) {
            return true;
        }

        public void writeTo(Book arg0, Class<?> arg1, Type arg2, Annotation[] arg3, 
                            MediaType arg4, MultivaluedMap<String, Object> arg5, OutputStream arg6) 
            throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
            
        }
    }
    
    @Produces("application/xml")
    @Consumes("application/xml")
    private static class SuperBookReaderWriter 
        implements MessageBodyReader<SuperBook>, MessageBodyWriter<SuperBook> {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, 
                                  MediaType mediaType) {
            return true;
        }

        public SuperBook readFrom(Class<SuperBook> arg0, Type arg1, Annotation[] arg2, MediaType arg3, 
                                  MultivaluedMap<String, String> arg4, InputStream arg5) 
            throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
            return null;
        }

        public long getSize(SuperBook t, Class<?> type, Type genericType, 
                            Annotation[] annotations, MediaType mediaType) {
            // TODO Auto-generated method stub
            return 0;
        }

        public boolean isWriteable(Class<?> type, Type genericType, 
                                   Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        public void writeTo(SuperBook arg0, Class<?> arg1, Type arg2, 
                            Annotation[] arg3, MediaType arg4, MultivaluedMap<String, Object> arg5, 
                            OutputStream arg6) throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
            
        }
        
    }
    
    @Produces("application/xml")
    @Consumes("application/xml")
    private static class SuperBookReaderWriter2<T> 
        implements MessageBodyReader<T>, MessageBodyWriter<T> {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, 
                                  MediaType mediaType) {
            return true;
        }

        public T readFrom(Class<T> arg0, Type arg1, Annotation[] arg2, MediaType arg3, 
                          MultivaluedMap<String, String> arg4, InputStream arg5) 
            throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
            return null;
        }

        public long getSize(T t, Class<?> type, Type genericType, 
                            Annotation[] annotations, MediaType mediaType) {
            // TODO Auto-generated method stub
            return 0;
        }

        public boolean isWriteable(Class<?> type, Type genericType, 
                                   Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        
        public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations,
                            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                            OutputStream entityStream)
            throws IOException, WebApplicationException {
            // TODO Auto-generated method stub
        }
        
    }
    
    @PreMatching
    private static class TestHandler implements ContainerRequestFilter {

        public void filter(ContainerRequestContext context) {
            // complete
        }
        
    }
        
    private static class RuntimeExceptionMapper1 
        extends AbstractTestExceptionMapper<RuntimeException> {
        
    }
    
    private static class RuntimeExceptionMapper2 
        extends AbstractTestExceptionMapper<WebApplicationException> {
        
    }
    
    private static class AbstractTestExceptionMapper<T extends RuntimeException> 
        implements ExceptionMapper<T> {

        public Response toResponse(T arg0) {
            // TODO Auto-generated method stub
            return null;
        }
        
    }
        
}
