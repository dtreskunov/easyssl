[![Build Status](https://travis-ci.org/dtreskunov/easyssl.svg?branch=master)](https://travis-ci.org/dtreskunov/easyssl)
# EasySSL
EasySSL is a small library to help create Spring Boot applications that talk to each other over HTTPS
with mutual, or two-way authentication. There is a central Certificate Authority (CA). Each app has its own
private key, and a certificate signed by the CA. The certificate is used to secure HTTP connections, both
made by the app (to another app), and those served by the app.

This library was motivated by the need to move an existing constellation of HTTP services from the intranet to the
Internet. Existing services were using a hodge-podge of authentication schemes, including service accounts IP
whitelisting, and load-balancer rules. Mutual SSL authentication can be used to solve this problem.

Mutual authentication using client certificates provides:
* *confidentiality* - prevents eavesdropping
* *integrity* - prevents replay
* *authenticity* - prevents impersonation

EasySSL relies on plain `openssl` tools and PEM encodings to make SSL easy for Dev and for Ops.

## Differences From Spring Boot
1. Uses PEM-encoded certificates and key files (rather than Java-specific [JKS](http://docs.oracle.com/javase/8/docs/technotes/tools/windows/keytool.html) files)
2. Supports Certificate Revocation Lists (Spring Boot currently does not - see [SPRING-BOOT 6171](https://github.com/spring-projects/spring-boot/issues/6171))
3. Creates an [SSLContext](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/SSLContext.html) bean to help write client code

## License
EasySSL is [licensed](https://github.com/dtreskunov/easyssl/blob/master/LICENSE) under the terms of Apache 2.0.

## Usage

### 1. Setting up a Certificate Authority
These steps should be done by your Ops people!

#### A note on the usage of ECDSA keys versus RSA keys
> The handshake is almost 100% faster when using the ECDSA certificate! Again, you can run this command a few times to get a baseline but that's an incredible boost
> in performance by just switching out the certificate you use. The other really awesome thing here is that the ECDSA key is only 256bit compared to the RSA key
> which is 2048bit, but, the ECDSA key offers more security. At only 256bit the ECDSA key is almost as strong as a 3072bit RSA key, a considerable step up in
> security with your 50% reduction in overhead!
>
> https://scotthelme.co.uk/ecdsa-certificates/

```bash
# This is a minimal working config file for demonstration purposes
cat > openssl.cnf
[ ca ]
default_ca = CA_default
[ CA_default ]
database = index.txt
default_md = default
default_crl_days = 30
^D

cat > ca-pass.txt
SomeSecurePassword
^D

# Create private key (make a note of the password)
openssl ecparam -genkey -name secp256r1 | openssl ec -out ca-key.pem -aes128 -passout file:ca-pass.txt

# Create CA certificate
openssl req -x509 -new -nodes -key ca-key.pem -passin file:ca-pass.txt -days 3650 -sha256 -out ca-cert.pem -subj '/CN=EasySSL CA'

# Create Certificate Revocation List (CRL)
openssl ca -gencrl -config openssl.cnf -cert ca-cert.pem -keyfile ca-key.pem -passin file:ca-pass.txt -out crl.pem
# It is recommended to publish crl.pem to a publically accessible URL
```

If you need to revoke access for an app, its certificate may be revoked. Here's how:
```bash
# This adds a line to index.txt with the serial number of the revoked cert
openssl ca -revoke app-cert.pem -config openssl.cnf -cert ca-cert.pem -keyfile ca-key.pem -passin file:ca-pass.txt

# Next, rerun the -gencrl command to update the CRL
openssl ca -gencrl -config openssl.cnf -cert ca-cert.pem -keyfile ca-key.pem -passin file:ca-pass.txt -out crl.pem

# Next, distribute the updated CRL to the interested parties (or publish it to the web)
```

### 2. Setting up an application
These steps should be done by the application owner.
```bash
cat > app-pass.txt
AnotherSecurePassword
^D

# Create private key
openssl ecparam -genkey -name secp256r1 | openssl ec -out app-key.pem -aes128 -passin file:app-pass.txt

# Create Certificate Signing Request (CSR)
# It's a good idea (although not required) to provide the app's correct DNS name
openssl req -new -key app-key.pem -passin file:app-pass.txt -out app-csr.pem -subj '/OU=App name/CN=<app.dns.name>'

# Sign the CSR
# This should be done by your Ops people - give them app-csr.pem from previous step, and ask them to sign it
# using the CA's private key
openssl x509 -req -in app-csr.pem -CA ca-cert.pem -CAkey ca-key.pem -passin file:ca-pass.txt -CAcreateserial -days 3650 -sha256 -out app-cert.pem
```

### 3. Using the library
First, you must add `easyssl` to your Spring Boot project.

Maven:
```xml
<dependency>
  <groupId>com.github.dtreskunov<groupId>
  <artifactId>easyssl</artifactId>
  <version>0.6.1</version>
</dependency>
```

Gradle:
```groovy
compile('com.github.dtreskunov:easyssl:0.6.1')
```

Next, add the following section to `application.yml`:
```yml
easyssl:
  # HTTP URL should work here, as well!
  caCertificate: file:/path/to/ca-cert.pem # also supports arrays
  certificate: file:/path/to/app-cert.pem
  key: file:/path/to/app-key.pem
  keyPassword: AnotherSecurePassword
  certificateRevocationList: file:/path/to/crl.pem
  certificateRevocationListCheckIntervalSeconds: 60 # default is 0
  clientAuth: WANT # default is NEED

# There is no need to specify `server.ssl.` properties - they will be managed by EasySSL.
# You may want to set the `enabledProtocols` and `ciphers` properties for improved security, however.
# See https://github.com/ssllabs/research/wiki/SSL-and-TLS-Deployment-Best-Practices
server.ssl:
  enabledProtocols: TLSv1.2
  ciphers:
  - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
  - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
  - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
  - TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
  - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
  - TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
  - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
  - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
  - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
  - TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
  - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
  - TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
  - TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
  - TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
  - TLS_DHE_RSA_WITH_AES_128_CBC_SHA
  - TLS_DHE_RSA_WITH_AES_256_CBC_SHA
  - TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
  - TLS_DHE_RSA_WITH_AES_256_CBC_SHA256

# These settings (including keyPassword) may be specified via any of Boot's Externalized Configuration mechanisms.
# See https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html
```

Next, ensure that the  `com.github.dtreskunov.easyssl` package is getting scanned by Spring. Look at the
[server code](https://github.com/dtreskunov/easyssl/tree/master/src/test/java/com/github/dtreskunov/easyssl/server)
used by this project's integration tests.

This should be enough to configure Tomcat/Jetty/etc to use SSL with client auth, but you still need to
implement business rules for securing access. Look at the
[Spring Security code](https://github.com/dtreskunov/easyssl/blob/master/src/test/java/com/github/dtreskunov/easyssl/server/Security.java)
in this project's integration tests and the links below.

An app doesn't have to be a server. If your app needs to securely call another app using its certificate to
authenticate itself, you can use the `SSLContext` bean to set up the client (e.g. RestTemplate on top of
Apache HttpClient). Look at the
[client code](https://github.com/dtreskunov/easyssl/blob/master/src/test/java/com/github/dtreskunov/easyssl/IntegrationTestUsingRealServer.java)
used by this project's integration tests.

Application code is able to use the `local.server.protocol` property to determine whether the servlet container was started with SSL enabled:
```java
@Value("${local.server.protocol}")
private String protocol; // injected by EasySSL
```

# Testing with cURL
```bash
# Assuming that you've set up client-cert.pem and client-key.pem as above
curl --cacert ca-cert.pem --cert client-cert.pem --key client-key.pem -i https://localhost:8443/
```

# Links
* [X.509 Authentication in Spring Security](http://www.baeldung.com/x-509-authentication-in-spring-security)
* [Java 2-way TLS/SSL](http://blog.palominolabs.com/2011/10/18/java-2-way-tlsssl-client-certificates-and-pkcs12-vs-jks-keystores/)
