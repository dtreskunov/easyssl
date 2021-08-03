package com.github.dtreskunov.easyssl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

public class EasySslHelper implements ApplicationEventPublisherAware {
    private static final Logger LOG = LoggerFactory.getLogger(EasySslHelper.class);

    /** Java APIs require a password when using a {@link KeyStore}. Hard-coded password is fine since the KeyStore is ephemeral. */
    private static final String KEY_PASSWORD = UUID.randomUUID().toString(); // 122 bits of secure random goodness
    private static final String KEY_ALIAS = "easyssl-key";

    public static class SSLContextReinitializedEvent extends ApplicationEvent {
        private EasySslHelper helper;

        public SSLContextReinitializedEvent(Object source, EasySslHelper helper) {
            super(source);
            this.helper = helper;
        }

        public EasySslHelper getHelper() {
            return helper;
        }
    }

    private class SslStoreProviderImpl implements SslStoreProvider {
        @Override
        public KeyStore getKeyStore() throws Exception {
            synchronized(EasySslHelper.this) {
                return keyStore;
            }
        }

        @Override
        public KeyStore getTrustStore() throws Exception {
            synchronized(EasySslHelper.this) {
                return trustStore;
            }
        }
    }

    private final SSLContext sslContext = SSLContext.getInstance("TLS");
    private KeyStore keyStore;
    private KeyStore trustStore;
    private X509TrustManager trustManager;
    private final SslStoreProvider sslStoreProvider = new SslStoreProviderImpl();
    private boolean initialized;
    private ScheduledFuture<?> localCertificateExpirationCheck;
    private ApplicationEventPublisher applicationEventPublisher;
    private EasySslProperties config;

    public EasySslHelper(EasySslProperties config) throws Exception {
        Assert.notNull(config, "config is null");
        this.config = config;

        Scheduler.runAndSchedule(
            "Load EasySSL resources",
            config.getRefreshTimeout().toMillis(), config.getRefreshInterval().toMillis(), TimeUnit.MILLISECONDS,
            this::initialize);
        
        Assert.isTrue(initialized, "initialized was expected to be true");
        Assert.notNull(keyStore, "keyStore was expected to be non-null");
        Assert.notNull(trustStore, "trustStore was expected to be non-null");
        Assert.notNull(trustManager, "trustManager was expected to be non-null");
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    synchronized public void reinitialize() {
        initialize();
    }

    synchronized public SSLContext getSSLContext() {
        return sslContext;
    }

    synchronized public KeyStore getKeyStore() {
        return keyStore;
    }

    synchronized public KeyStore getTrustStore() {
        return trustStore;
    }

    synchronized public X509TrustManager getTrustManager() {
        return trustManager;
    }

    synchronized public SslStoreProvider getSslStoreProvider() {
        return sslStoreProvider;
    }

    private void initialize() {
        LOG.info("{} EasySSL with command {}, certificate from {}, key from {}, CA from {}, and CRL from {} with timeout {} (disabled if zero). Next update in {} (disabled if zero)",
            initialized ? "Reinitializing" : "Initializing", config.getRefreshCommand(),
            config.getCertificate(), config.getKey(), config.getCaCertificate(), config.getCertificateRevocationList(),
            config.getRefreshTimeout(), config.getRefreshInterval());
        try {
            if (config.getRefreshCommand() != null) {
                LOG.info("Refresh command: {}", config.getRefreshCommand());
                Process refreshProcess = Runtime.getRuntime().exec(
                    config.getRefreshCommand().toArray(new String[config.getRefreshCommand().size()]));
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(refreshProcess.getInputStream()))) {
                    reader.lines().forEach(outputLine -> {
                        LOG.info("Refresh command output: {}", outputLine);
                    });
                }
                int refreshProcessExitCode = refreshProcess.waitFor();
                LOG.info("Refresh command exit code: {}", refreshProcessExitCode);
                if (refreshProcessExitCode != 0) {
                    throw new RuntimeException("Refresh command exited with exit code " + refreshProcessExitCode);
                }
            }
            trustStore = getTrustStore(config.getCaCertificate());
            trustManager = getTrustManager(config, trustStore);
            keyStore = getKeyStore(config.getCertificate(), config.getKey(), config.getKeyPassword());
            if (localCertificateExpirationCheck != null) {
                localCertificateExpirationCheck.cancel(false);
            }
            localCertificateExpirationCheck = CertificateExpirationCheck.scheduleCheck(keyStore.getCertificateChain(KEY_ALIAS), "local",
                config.getCertificateExpirationWarningThreshold(), config.getCertificateExpirationCheckInterval());
            sslContext.init(
                getKeyManagers(keyStore, KEY_PASSWORD.toCharArray()),
                new TrustManager[]{trustManager},
                new SecureRandom());
        } catch (Exception e) {
            if (initialized) {
                // ignore the error so that the next Scheduler execution will retry
                LOG.error("Unable to reinitialize SSLContext", e);
                return;
            } else {
                throw new RuntimeException(e);
            }
        }
        if (initialized && applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new SSLContextReinitializedEvent(this, this));
        }
        initialized = true;
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

    private static X509TrustManager getTrustManager(EasySslProperties config, KeyStore trustStore) throws Exception {
        List<X509TrustManager> delegates = new ArrayList<>(3);

        // 1: log a warning if a certificate is about to expire
        if (config.getCertificateExpirationWarningThreshold() != null) {
            delegates.add(new ExpirationCheckTrustManager(config.getCertificateExpirationWarningThreshold()));
        }
        
        // 2: reject revoked certificates
        if (config.getCertificateRevocationList() != null) {
            ArrayList<PublicKey> publicKeys = new ArrayList<>(config.getCaCertificate().size());
            for (Resource r: config.getCaCertificate()) {
                for (Certificate c: getCertificates(r)) {
                    publicKeys.add(c.getPublicKey());
                }
            }
            delegates.add(new CRLTrustManager(config.getCertificateRevocationList(), publicKeys));
        }

        // 3: validate that the certificate is signed by a trusted CA
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        boolean foundCaTrustManager = false;
        for (TrustManager tm: factory.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                delegates.add((X509TrustManager) tm);
                foundCaTrustManager = true;
            }
        }

        // this shouldn't happen - fail early if this assumption is incorrect
        if (!foundCaTrustManager) {
            throw new RuntimeException("TrustManagerFactory didn't create any X509TrustManager instances");
        }

        return new ChainingTrustManager(delegates);
    }

    private static KeyStore getKeyStore(Resource certificate, Resource key, String keyPassword) throws Exception {
        PrivateKey privateKey = getPrivateKey(key.getInputStream(), keyPassword);
        List<Certificate> certs = getCertificates(certificate);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, KEY_PASSWORD.toCharArray(), certs.toArray(new Certificate[certs.size()]));
        return keyStore;
    }

    private static KeyManager[] getKeyManagers(KeyStore keyStore, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        final KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, password);
        return factory.getKeyManagers();
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

    public static Ssl getSslProperties(EasySslProperties config, ServerProperties serverProperties) {
        Ssl properties = (serverProperties == null || serverProperties.getSsl() == null) ? new Ssl() : serverProperties.getSsl();
        properties.setEnabled(true);
        properties.setClientAuth(config.getClientAuth());
        properties.setKeyAlias(EasySslHelper.KEY_ALIAS);
        properties.setKeyPassword(EasySslHelper.KEY_PASSWORD);
        return properties;
    }
}
