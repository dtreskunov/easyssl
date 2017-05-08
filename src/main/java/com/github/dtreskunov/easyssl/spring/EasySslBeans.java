package com.github.dtreskunov.easyssl.spring;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.servlet.Filter;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.Ssl;
import org.springframework.boot.context.embedded.SslStoreProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.github.dtreskunov.easyssl.CRLTrustManager;
import com.github.dtreskunov.easyssl.ClientCertificateCheckingFilter;

/**
 * Defines Spring beans that are used for mutual SSL. They are:
 * <ol>
 * <li>{@link #easySslContext} - may be used to configure an SSL-using {@link RestTemplate}</li>
 * <li>{@link #easySslClientCertificateCheckingFilter} - checks that client's certificate has not been revoked</li>
 * <li>{@link #easySslServletContainerCustomizer} - used by Spring Boot to configure Jetty/Tomcat/Undertow to use SSL with client cert auth</li>
 * </ol>
 */
@Configuration
@EasySslBeans.ConditionalOnEnabled
public class EasySslBeans {

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

        return SSLContexts.custom()
                .loadKeyMaterial(getKeyStore(config.getCertificate(), config.getKey(), config.getKeyPassword()), KEY_PASSWORD.toCharArray())
                .loadTrustMaterial(getTrustStore(config.getCaCertificate()), trustStrategy)
                .build();
    }

    private static Certificate getCertificate(Resource certificate) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(certificate.getInputStream());
    }

    private static KeyStore getKeyStore(Resource certificate, Resource key, String keyPassword) throws Exception {
        PrivateKey privateKey = getPrivateKey(key.getInputStream(), keyPassword);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), new Certificate[]{getCertificate(certificate)});
        return keyStore;
    }

    private static PrivateKey getPrivateKey(InputStream inputStream, String keyPassword) throws Exception {
        final Object pemObject;
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
            pemObject = pemParser.readObject();
        }

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        final PEMKeyPair pemKeyPair;
        final KeyPair keyPair;
        if (pemObject instanceof PEMEncryptedKeyPair) {
            PEMDecryptorProvider decryptor = new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray());
            pemKeyPair = ((PEMEncryptedKeyPair)pemObject).decryptKeyPair(decryptor);
        } else if (pemObject instanceof PEMKeyPair) {
            pemKeyPair = (PEMKeyPair) pemObject;
        } else if (pemObject instanceof PKCS8EncryptedPrivateKeyInfo) {
            InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder().build(keyPassword.toCharArray());
            PrivateKeyInfo privateKeyInfo = ((PKCS8EncryptedPrivateKeyInfo)pemObject).decryptPrivateKeyInfo(decryptor);
            return converter.getPrivateKey(privateKeyInfo);
        } else {
            throw new GeneralSecurityException("Private key is expected to be either a PEMEncryptedKeyPair or a PEMKeyPair, but is actually a " + pemObject.getClass().getSimpleName());
        }
        keyPair = converter.getKeyPair(pemKeyPair);
        return keyPair.getPrivate();
    }

    private static X509TrustManager getTrustManager(EasySslProperties config) throws Exception {
        ArrayList<PublicKey> publicKeys = new ArrayList<>(config.getCaCertificate().size());
        for (Resource c: config.getCaCertificate()) {
            publicKeys.add(getCertificate(c).getPublicKey());
        }
        return new CRLTrustManager(config.getCertificateRevocationList(), publicKeys);
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

    @Component
    @ConditionalOnEnabled
    public static class Properties extends EasySslProperties {}

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
    public EmbeddedServletContainerCustomizer easySslServletContainerCustomizer(EasySslProperties config) throws Exception {
        final SslStoreProvider storeProvider = getSslStoreProvider(config);
        final Ssl sslProperties = getSslProperties(config);

        return new EmbeddedServletContainerCustomizer() {
            @Override
            public void customize(ConfigurableEmbeddedServletContainer container) {
                container.setSslStoreProvider(storeProvider);
                container.setSsl(sslProperties);
            }
        };
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

    private Ssl getSslProperties(EasySslProperties config) {
        Ssl properties = new Ssl();
        properties.setEnabled(true);
        properties.setClientAuth(config.getClientAuth());
        properties.setKeyAlias(KEY_ALIAS);
        properties.setKeyPassword(KEY_PASSWORD);
        return properties;
    }
}
