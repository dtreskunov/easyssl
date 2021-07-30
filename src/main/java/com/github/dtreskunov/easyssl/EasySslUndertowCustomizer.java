package com.github.dtreskunov.easyssl;

import java.lang.reflect.Field;

import com.github.dtreskunov.easyssl.EasySslHelper.SSLContextReinitializedEvent;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

import io.undertow.Undertow;
import io.undertow.Undertow.ListenerInfo;

public class EasySslUndertowCustomizer implements ApplicationListener<ApplicationEvent> {
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
        UndertowWebServer server = (UndertowWebServer) event.getWebServer();
        Field UndertowWebServer_undertow = ReflectionUtils.findField(UndertowWebServer.class, "undertow");
        ReflectionUtils.makeAccessible(UndertowWebServer_undertow);
        Undertow undertow = (Undertow) ReflectionUtils.getField(UndertowWebServer_undertow, server);
        for (ListenerInfo listenerInfo: undertow.getListenerInfo()) {
            if (listenerInfo.getProtcol().equalsIgnoreCase("https")) {
                httpsListenerInfo.set(listenerInfo);
            }
        }
    }

    private void onSSLContextReinitializedEvent(SSLContextReinitializedEvent event) {
        httpsListenerInfo.get().setSslContext(event.getHelper().getSSLContext());
    }
}
