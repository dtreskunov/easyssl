package com.github.dtreskunov.easyssl;

import com.github.dtreskunov.easyssl.EasySslHelper.SSLContextReinitializedEvent;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.jetty.ConfigurableJettyWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;

class EasySslJettyCustomizer implements WebServerFactoryCustomizer<ConfigurableJettyWebServerFactory>, ApplicationListener<EasySslHelper.SSLContextReinitializedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(EasySslJettyCustomizer.class);
    private final SetOnlyOnce<SslContextFactory> contextFactory = new SetOnlyOnce<>();

    @Override
    public void customize(ConfigurableJettyWebServerFactory jettyWebServerFactory) {
        jettyWebServerFactory.addServerCustomizers(this::customizeServer);
    }

    @Override
    public void onApplicationEvent(SSLContextReinitializedEvent event) {
        LOG.info("Updating Jetty with new SSLContext");
        contextFactory.get().setKeyStore(event.getHelper().getKeyStore());
        contextFactory.get().setTrustStore(event.getHelper().getTrustStore());
        try {
            contextFactory.get().reload(scf -> {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void customizeServer(Server server) {
        for (Connector connector: server.getConnectors()) {
            if (!(connector instanceof ServerConnector)) {
                continue;
            }
            SslConnectionFactory connectionFactory = ((ServerConnector) connector).getConnectionFactory(
                    SslConnectionFactory.class);
            if (connectionFactory == null) {
                continue;
            }
            contextFactory.set(connectionFactory.getSslContextFactory());
            contextFactory.get().setEndpointIdentificationAlgorithm(null);
        }
    }
}
