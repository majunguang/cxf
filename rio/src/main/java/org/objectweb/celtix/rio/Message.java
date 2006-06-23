package org.objectweb.celtix.rio;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface Message extends Map<String, Object> {
    String getId();
    
    InterceptorChain getInterceptorChain();
    
    Channel getChannel();
    
    Exchange getExchange();
    
    Collection<Attachment> getAttachments();
    
    
    <T> T getSource(Class<T> format);
    
    <T> void setSource(Class<T> format, Object content);
    
    Set<Class> getSourceFormats();
    
    
    <T> T getResult(Class<T> format);
    
    <T> void setResult(Class<T> format, Object content);
    
    Set<Class> getResultFormats();
}
