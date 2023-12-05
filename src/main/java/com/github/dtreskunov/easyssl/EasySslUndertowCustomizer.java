package com.github.dtreskunov.easyssl;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

import com.github.dtreskunov.easyssl.EasySslHelper.SSLContextReinitializedEvent;

import io.undertow.Undertow;
import io.undertow.Undertow.ListenerInfo;

class EasySslUndertowCustomizer implements ApplicationListener<ApplicationEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(EasySslUndertowCustomizer.class);
    private final SetOnlyOnce<ListenerInfo> httpsListenerInfo = new SetOnlyOnce<>();

    @Override
    public void onApplicationEvent(ApplicationEvent genericEvent) {
        if (genericEvent instanceof WebServerInitializedEvent) {
            onWebServerInitializedEvent((WebServerInitializedEvent) genericEvent);
        } else if (genericEvent instanceof SSLContextReinitializedEvent) {
            onSSLContextReinitializedEvent((SSLContextReinitializedEvent) genericEvent);
        }
    }

    private void onWebServerInitializedEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof UndertowWebServer)) {
            LOG.warn("Undertow isn't being used - remove it from classpath if not using");
            return;
        }
        UndertowWebServer server = (UndertowWebServer) event.getWebServer();
        Field UndertowWebServer_undertow = ReflectionUtils.findField(UndertowWebServer.class, "undertow");
        ReflectionUtils.makeAccessible(UndertowWebServer_undertow);
        Undertow undertow = (Undertow) ReflectionUtils.getField(UndertowWebServer_undertow, server);
        if (undertow == null) {
            throw new RuntimeException("Unable to reflectively configure Undertow to use EasySSL (class UndertowWebServer has no field undertow");
        }
        for (ListenerInfo listenerInfo: undertow.getListenerInfo()) {
            if (listenerInfo.getProtcol().equalsIgnoreCase("https")) {
                httpsListenerInfo.set(listenerInfo);
            }
        }
    }

    private void onSSLContextReinitializedEvent(SSLContextReinitializedEvent event) {
        if (!httpsListenerInfo.isSet()) {
            LOG.warn("Undertow hasn't been configured yet - remove it from classpath if not using");
            return;
        }
        LOG.info("Updating Undertow with new SSLContext");
        httpsListenerInfo.get().setSslContext(event.getHelper().getSSLContext());
    }
}
