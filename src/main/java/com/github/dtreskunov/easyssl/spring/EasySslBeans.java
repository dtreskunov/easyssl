package com.github.dtreskunov.easyssl.spring;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.servlet.Filter;

import com.github.dtreskunov.easyssl.CRLTrustManager;
import com.github.dtreskunov.easyssl.CertificateExpirationWarning;
import com.github.dtreskunov.easyssl.ChainingTrustManager;
import com.github.dtreskunov.easyssl.ClientCertificateCheckingFilter;
import com.github.dtreskunov.easyssl.ExpirationWarningTrustManager;
import com.github.dtreskunov.easyssl.ThreadFactoryFactory;

import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.embedded.jetty.ConfigurableJettyWebServerFactory;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Defines Spring beans that are used for mutual SSL. They are:
 * <ol>
 * <li>{@link #easySslContext} - may be used to configure an SSL-using {@link RestTemplate}</li>
 * <li>{@link #easySslClientCertificateCheckingFilter} - checks that client's certificate has not been revoked</li>
 * <li>{@link #easySslServletContainerCustomizer} - used by Spring Boot to configure Jetty/Tomcat/Undertow to use SSL with client cert auth</li>
 * <li>{@code local.server.protocol} - environment property injectable into managed beans using {@code @Value}</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(value = "easyssl.enabled", matchIfMissing = true)
public class EasySslBeans {

    private static final String PROTOCOL_PROPERTY = "local.server.protocol";

    @ConditionalOnProperty(value = "easyssl.enabled", matchIfMissing = true)
    public static @interface ConditionalOnEnabled {}

    @ConditionalOnEnabled
    @ConditionalOnWebApplication
    @ConditionalOnProperty(value = "easyssl.serverCustomizationEnabled", matchIfMissing = true)
    public static @interface ConditionalOnServerCustomizationEnabled {}

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /** Java APIs require a password when using a {@link KeyStore}. Hard-coded password is fine since the KeyStore is ephemeral. */
    private static final String KEY_PASSWORD = UUID.randomUUID().toString(); // 122 bits of secure random goodness
    private static final String KEY_ALIAS = "easyssl-key";

    public static SSLContext getSSLContext(EasySslProperties config) throws Exception {
        X509TrustManager trustManager = getTrustManager(config);
        TrustStrategy trustStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                trustManager.checkServerTrusted(chain, authType);
                return false; // still use the underlying JSSE verification
            }
        };

        KeyStore keyStore = getKeyStore(config.getCertificate(), config.getKey(), config.getKeyPassword());
        if (config.getCertificateExpirationWarningThreshold() != null) {
            Certificate[] localCerts = keyStore.getCertificateChain(KEY_ALIAS);
            // this will throw an ArrayStoreException if any certificates are not X509Certificates, but this is quite a safe assumption
            X509Certificate[] localX509Certs = Arrays.copyOf(localCerts, localCerts.length, X509Certificate[].class);
            Runnable task = () -> {
                CertificateExpirationWarning.check(localX509Certs, "local", config.getCertificateExpirationWarningThreshold());
            };
            task.run();
            Duration interval = config.getCertificateExpirationWarningInterval();
            if (interval != null) {
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                    ThreadFactoryFactory.createThreadFactory(true, CertificateExpirationWarning.class.getSimpleName() + " daemon"));
                scheduler.scheduleAtFixedRate(task, interval.getSeconds(), interval.getSeconds(), TimeUnit.SECONDS);
            }
        }

        return SSLContexts.custom()
                .loadKeyMaterial(keyStore, KEY_PASSWORD.toCharArray())
                .loadTrustMaterial(getTrustStore(config.getCaCertificate()), trustStrategy)
                .build();
    }

    private static List<Certificate> getCertificates(Resource certificate) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        // if several certs are concatenated together OpenSSL-style, this will only load the first one!
        // cf.generateCertificate(certificate.getInputStream());
        String[] pems = StreamUtils.copyToString(certificate.getInputStream(), StandardCharsets.UTF_8).split("(?=-----BEGIN CERTIFICATE-----)");
        ArrayList<Certificate> certs = new ArrayList<>(pems.length);
        for (String pem: pems) {
            certs.add(cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))));
        }
        return certs;
    }

    private static KeyStore getKeyStore(Resource certificate, Resource key, String keyPassword) throws Exception {
        PrivateKey privateKey = getPrivateKey(key.getInputStream(), keyPassword);
        List<Certificate> certs = getCertificates(certificate);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), certs.toArray(new Certificate[certs.size()]));
        return keyStore;
    }

    private static PrivateKey getPrivateKey(InputStream inputStream, String keyPassword) throws Exception {
        final Object pemObject;
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
            pemObject = pemParser.readObject();
        }
        if (pemObject == null) {
            throw new KeyException("No object was extracted from the input stream by the PEM parser");
        }

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        final PEMKeyPair pemKeyPair;
        final KeyPair keyPair;
        if (pemObject instanceof PEMEncryptedKeyPair) {
            if (keyPassword == null || keyPassword.isEmpty()) {
                throw new KeyException("Need a non-empty password for a PEMEncryptedKeyPair");
            }
            PEMDecryptorProvider decryptor = new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray());
            pemKeyPair = ((PEMEncryptedKeyPair)pemObject).decryptKeyPair(decryptor);
        } else if (pemObject instanceof PEMKeyPair) {
            pemKeyPair = (PEMKeyPair) pemObject;
        } else if (pemObject instanceof PrivateKeyInfo) {
            return converter.getPrivateKey((PrivateKeyInfo) pemObject);
        } else if (pemObject instanceof PKCS8EncryptedPrivateKeyInfo) {
            if (keyPassword == null || keyPassword.isEmpty()) {
                throw new KeyException("Need a non-empty password for a PKCS8EncryptedPrivateKeyInfo");
            }
            InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder().build(keyPassword.toCharArray());
            PrivateKeyInfo privateKeyInfo = ((PKCS8EncryptedPrivateKeyInfo)pemObject).decryptPrivateKeyInfo(decryptor);
            return converter.getPrivateKey(privateKeyInfo);
        } else {
            throw new KeyException("Private key is expected to be either a PEMEncryptedKeyPair or a PEMKeyPair, but is actually a " + pemObject.getClass().getSimpleName());
        }
        keyPair = converter.getKeyPair(pemKeyPair);
        return keyPair.getPrivate();
    }

    private static X509TrustManager getTrustManager(EasySslProperties config) throws Exception {
        List<X509TrustManager> delegates = new ArrayList<>(2);

        if (config.getCertificateExpirationWarningThreshold() != null) {
            delegates.add(new ExpirationWarningTrustManager(config.getCertificateExpirationWarningThreshold()));
        }
        
        if (config.getCertificateRevocationList() != null) {
            ArrayList<PublicKey> publicKeys = new ArrayList<>(config.getCaCertificate().size());
            for (Resource r: config.getCaCertificate()) {
                for (Certificate c: getCertificates(r)) {
                    publicKeys.add(c.getPublicKey());
                }
            }
            delegates.add(new CRLTrustManager(
                    config.getCertificateRevocationList(),
                    publicKeys,
                    config.getCertificateRevocationListCheckTimeoutSeconds(),
                    config.getCertificateRevocationListCheckIntervalSeconds(),
                    TimeUnit.SECONDS
                    ));
        }

        return new ChainingTrustManager(delegates);
    }

    private static KeyStore getTrustStore(Collection<Resource> certificates) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        int index = 0;
        for (Resource certificate: certificates) {
            Certificate ca = cf.generateCertificate(certificate.getInputStream());
            trustStore.setCertificateEntry("easyssl-ca-" + index, ca);
            index++;
        }

        return trustStore;
    }

    @Bean
    @ConditionalOnEnabled
    public EasySslProperties easySslProperties() {
        return new EasySslProperties();
    }

    @Bean
    @ConditionalOnEnabled
    public SSLContext easySslContext(EasySslProperties config) throws Exception {
        return getSSLContext(config);
    }

    @Bean
    @ConditionalOnServerCustomizationEnabled
    public Filter easySslClientCertificateCheckingFilter(EasySslProperties config) throws Exception {
        return new ClientCertificateCheckingFilter(getTrustManager(config));
    }

    @Bean
    @ConditionalOnServerCustomizationEnabled
    public WebServerFactoryCustomizer<ConfigurableWebServerFactory> easySslServletContainerCustomizer(EasySslProperties config, @Autowired(required = false) ServerProperties serverProperties) throws Exception {
        final SslStoreProvider storeProvider = getSslStoreProvider(config);
        final Ssl sslProperties = getSslProperties(config, serverProperties);

        return factory -> {
            factory.setSslStoreProvider(storeProvider);
            factory.setSsl(sslProperties);
        };
    }

    /**
     * Jetty 9.4.15 started doing additional checks on client certificates. This bit of code reverts to the old behavior.
     *
     * See <a href="https://github.com/eclipse/jetty.project/issues/3454">eclipse/jetty.project#3454</a>
     */
    @Bean
    @ConditionalOnServerCustomizationEnabled
    public WebServerFactoryCustomizer<ConfigurableJettyWebServerFactory> jettyServerCustomizer() {
        return container -> {
            container.addServerCustomizers(new JettyServerCustomizer() {
                @Override
                public void customize(Server server) {
                    for (Connector connector: server.getConnectors()) {
                        if (!(connector instanceof ServerConnector)) {
                            continue;
                        }
                        SslConnectionFactory connectionFactory = ((ServerConnector) connector).getConnectionFactory(
                                SslConnectionFactory.class);
                        if (connectionFactory == null) {
                            continue;
                        }
                        SslContextFactory contextFactory = connectionFactory.getSslContextFactory();
                        contextFactory.setEndpointIdentificationAlgorithm(null);
                    }
                }
            });
        };
    }

    @Autowired
    public void setProtocolEnvironmentProperty(ApplicationContext context, @Autowired(required = false) EasySslProperties config) {
        if (config != null && config.isEnabled() && config.isServerCustomizationEnabled()) {
            setEnvironmentProperty(context, PROTOCOL_PROPERTY, "https");
        } else {
            setEnvironmentProperty(context, PROTOCOL_PROPERTY, "http");
        }
    }

    @SuppressWarnings("unchecked")
    private void setEnvironmentProperty(ApplicationContext context, String propertyName, String propertyValue) {
        if (!(context instanceof ConfigurableApplicationContext)) {
            return;
        }
        ConfigurableEnvironment environment = ((ConfigurableApplicationContext) context).getEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        PropertySource<?> source = sources.get("easyssl");
        if (source == null) {
            source = new MapPropertySource("easyssl", new HashMap<String, Object>());
            sources.addFirst(source);
        }
        ((Map<String, Object>) source.getSource()).put(propertyName, propertyValue);
    }

    private SslStoreProvider getSslStoreProvider(EasySslProperties config) throws Exception {
        final KeyStore keyStore = getKeyStore(config.getCertificate(), config.getKey(), config.getKeyPassword());
        final KeyStore trustStore = getTrustStore(config.getCaCertificate());

        return new SslStoreProvider() {
            @Override
            public KeyStore getKeyStore() throws Exception {
                return keyStore;
            }

            @Override
            public KeyStore getTrustStore() throws Exception {
                return trustStore;
            }
        };
    }

    private Ssl getSslProperties(EasySslProperties config, ServerProperties serverProperties) {
        Ssl properties = (serverProperties == null || serverProperties.getSsl() == null) ? new Ssl() : serverProperties.getSsl();
        properties.setEnabled(true);
        properties.setClientAuth(config.getClientAuth());
        properties.setKeyAlias(KEY_ALIAS);
        properties.setKeyPassword(KEY_PASSWORD);
        return properties;
    }
}
