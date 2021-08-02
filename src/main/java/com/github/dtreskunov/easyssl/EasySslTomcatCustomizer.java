package com.github.dtreskunov.easyssl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLStreamHandlerFactory;
import java.util.List;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.tomcat.util.net.AbstractJsseEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

public class EasySslTomcatCustomizer implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, ApplicationListener<EasySslHelper.SSLContextReinitializedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(EasySslTomcatCustomizer.class);
    private final SetOnlyOnce<AbstractJsseEndpoint<?,?>> endpoint = new SetOnlyOnce<>();

    private static Class<?> getClassByName(String name) {
        try {
            return EasySslTomcatCustomizer.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }            
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onApplicationEvent(EasySslHelper.SSLContextReinitializedEvent event) {
        LOG.info("Updating Tomcat with new SSLContext");
        TomcatURLStreamHandlerFactory instance = TomcatURLStreamHandlerFactory.getInstance();
        Field TomcatURLStreamHandlerFactory_userFactories = ReflectionUtils.findField(TomcatURLStreamHandlerFactory.class, "userFactories");
        ReflectionUtils.makeAccessible(TomcatURLStreamHandlerFactory_userFactories);
        List<URLStreamHandlerFactory> factories = (List<URLStreamHandlerFactory>) ReflectionUtils.getField(TomcatURLStreamHandlerFactory_userFactories, instance);

        Class<?> SslStoreProviderUrlStreamHandlerFactory_class = getClassByName("org.springframework.boot.web.embedded.tomcat.SslStoreProviderUrlStreamHandlerFactory");
        Field SslStoreProviderUrlStreamHandlerFactory_sslStoreProvider = ReflectionUtils.findField(SslStoreProviderUrlStreamHandlerFactory_class, "sslStoreProvider");
        ReflectionUtils.makeAccessible(SslStoreProviderUrlStreamHandlerFactory_sslStoreProvider);

        for (URLStreamHandlerFactory factory: factories) {
            if (SslStoreProviderUrlStreamHandlerFactory_class.isInstance(factory)) {
                ReflectionUtils.setField(SslStoreProviderUrlStreamHandlerFactory_sslStoreProvider, factory, event.getHelper().getSslStoreProvider());
            }
        }
        endpoint.get().reloadSslHostConfigs();
    }

    @Override
    public void customize(ConfigurableTomcatWebServerFactory tomcatWebServerFactory) {
        tomcatWebServerFactory.addConnectorCustomizers(this::customizeConnector);
    }
    
    private void customizeConnector(Connector connector) {
        ProtocolHandler handler = connector.getProtocolHandler();
        if (handler instanceof AbstractHttp11JsseProtocol) {
            Method AbstractHttp11JsseProtocol_getEndpoint = ReflectionUtils.findMethod(AbstractHttp11JsseProtocol.class, "getEndpoint");
            ReflectionUtils.makeAccessible(AbstractHttp11JsseProtocol_getEndpoint);
            endpoint.set((AbstractJsseEndpoint<?, ?>) ReflectionUtils.invokeMethod(AbstractHttp11JsseProtocol_getEndpoint, (AbstractHttp11JsseProtocol<?>) handler));
        }
    }
}
