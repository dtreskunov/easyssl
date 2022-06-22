package com.github.dtreskunov.easyssl;

import java.lang.reflect.Method;
import java.security.KeyStore;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.tomcat.util.net.AbstractJsseEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

class EasySslTomcatCustomizer implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, ApplicationListener<EasySslHelper.SSLContextReinitializedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(EasySslTomcatCustomizer.class);
    private final SetOnlyOnce<AbstractJsseEndpoint<?,?>> endpoint = new SetOnlyOnce<>();
    private final SetOnlyOnce<SSLHostConfig[]> sslHostConfigs = new SetOnlyOnce<>();

    @Override
    public void onApplicationEvent(EasySslHelper.SSLContextReinitializedEvent event) {
        LOG.info("Updating Tomcat with new SSLContext");
        final SslStoreProvider sslStoreProvider = event.getHelper().getSslStoreProvider();
        final KeyStore keyStore;
        final KeyStore trustStore;
        try {
            keyStore = sslStoreProvider.getKeyStore();
            trustStore = sslStoreProvider.getTrustStore();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (SSLHostConfig sslHostConfig: sslHostConfigs.get()) {
            sslHostConfig.setTrustStore(trustStore);
            for (SSLHostConfigCertificate certificate: sslHostConfig.getCertificates()) {
                certificate.setCertificateKeystore(keyStore);
            }
            endpoint.get().addSslHostConfig(sslHostConfig, true);
        }
    }

    @Override
    public void customize(ConfigurableTomcatWebServerFactory tomcatWebServerFactory) {
        tomcatWebServerFactory.addConnectorCustomizers(this::customizeConnector);
    }
    
    private void customizeConnector(Connector connector) {
        ProtocolHandler handler = connector.getProtocolHandler();
        if (handler instanceof AbstractHttp11JsseProtocol) {
            sslHostConfigs.set(handler.findSslHostConfigs());
            Method AbstractHttp11JsseProtocol_getEndpoint = ReflectionUtils.findMethod(AbstractHttp11JsseProtocol.class, "getEndpoint");
            ReflectionUtils.makeAccessible(AbstractHttp11JsseProtocol_getEndpoint);
            endpoint.set((AbstractJsseEndpoint<?, ?>) ReflectionUtils.invokeMethod(AbstractHttp11JsseProtocol_getEndpoint, (AbstractHttp11JsseProtocol<?>) handler));
        }
    }
}
