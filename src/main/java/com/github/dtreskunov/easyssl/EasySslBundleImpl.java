package com.github.dtreskunov.easyssl;

import java.security.KeyStore;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.web.server.Ssl;

/**
 * Implementations of the Spring Boot SSL bundle interfaces.
 */
final class EasySslBundleImpl {
    static final String BUNDLE_NAME = "easyssl";

    private EasySslBundleImpl() {
        // Prevent instantiation
    }

    static class SslBundleImpl implements SslBundle {
        private final EasySslHelper helper;
        private final Ssl sslProperties;

        public SslBundleImpl(EasySslHelper helper, Ssl sslProperties) {
            this.helper = helper;
            this.sslProperties = sslProperties;
        }

        @Override
        public SslStoreBundle getStores() {
            return new SslStoreBundleImpl(helper);
        }

        @Override
        public SslBundleKey getKey() {
            return new SslBundleKeyImpl();
        }

        @Override
        public SslOptions getOptions() {
            return new SslOptionsImpl(sslProperties);
        }

        @Override
        public String getProtocol() {
            return "TLS";
        }

        @Override
        public SslManagerBundle getManagers() {
            return new SslManagerBundleImpl(helper);
        }
    }

    static class SslStoreBundleImpl implements SslStoreBundle {
        private final EasySslHelper helper;

        public SslStoreBundleImpl(EasySslHelper helper) {
            this.helper = helper;
        }

        @Override
        public KeyStore getKeyStore() {
            return helper.getKeyStore();
        }

        @Override
        public String getKeyStorePassword() {
            return EasySslHelper.KEY_PASSWORD;
        }

        @Override
        public KeyStore getTrustStore() {
            return helper.getTrustStore();
        }
    }

    static class SslBundleKeyImpl implements SslBundleKey {

        @Override
        public String getAlias() {
            return EasySslHelper.KEY_ALIAS;
        }

        @Override
        public String getPassword() {
            return EasySslHelper.KEY_PASSWORD;
        }
    }

    static class SslOptionsImpl implements SslOptions {
        private final Ssl sslProperties;

        public SslOptionsImpl(Ssl sslProperties) {
            this.sslProperties = sslProperties;
        }

        @Override
        public String[] getEnabledProtocols() {
            return sslProperties.getEnabledProtocols();
        }

        @Override
        public String[] getCiphers() {
            return sslProperties.getCiphers();
        }
    }

    static class SslManagerBundleImpl implements SslManagerBundle {
        private final EasySslHelper helper;

        public SslManagerBundleImpl(EasySslHelper helper) {
            this.helper = helper;
        }

        @Override
        public KeyManagerFactory getKeyManagerFactory() {
            try {
                KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                factory.init(helper.getKeyStore(), EasySslHelper.KEY_PASSWORD.toCharArray());
                return factory;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public TrustManagerFactory getTrustManagerFactory() {
            try {
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(helper.getTrustStore());
                return factory;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class SslBundlesImpl implements SslBundles {
        private final SslBundle sslBundle;

        public SslBundlesImpl(SslBundle sslBundle) {
            this.sslBundle = sslBundle;
        }

        @Override
        public SslBundle getBundle(String name) throws NoSuchSslBundleException {
            if (BUNDLE_NAME.equals(name)) {
                return sslBundle;
            }
            throw new UnsupportedOperationException("No such SSL bundle: " + name);
        }

        @Override
        public void addBundleUpdateHandler(String name, Consumer<SslBundle> updateHandler) throws NoSuchSslBundleException {
            if (!BUNDLE_NAME.equals(name)) {
                throw new UnsupportedOperationException("No such SSL bundle: " + name);
            }
            // No dynamic updates in this implementation
        }

        @Override
        public List<String> getBundleNames() {
            return List.of(BUNDLE_NAME);
        }
        
        @Override
        public void addBundleRegisterHandler(BiConsumer<String, SslBundle> registerHandler) {
            // No dynamic updates in this implementation
        }
    }
} 