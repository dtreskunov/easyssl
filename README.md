# EasySSL
EasySSL is a small library to help create Spring Boot applications that talk to each other over HTTPS
with mutual, or two-way authentication. There is a central Certificate Authority (CA). Each app has its own
private key, and a certificate signed by the CA. The certificate is used to secure HTTP connections, both
made by the app (to another app), and those served by the app.

This library was a weekend project motivated by the need to move an existing constellation of HTTP services
from the intranet to the internet. Existing services were using a hodge-podge of authentication schemes,
including service accounts IP whitelisting, and load-balancer rules. Mutual SSL authentication can be used
to solve this problem.

Mutual authentication using client certificates provides:
* *confidentiality* - prevents eavesdropping
* *integrity* - prevents replay
* *authenticity* - prevents impersonation

## Usage

### 1. Setting up a Certificate Authority
These steps should be done by your Ops people!
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

# Create private key
openssl genrsa -out ca-key.pem 2048

# Create CA certificate
openssl req -x509 -new -nodes -key ca-key.pem -days 3650 -sha256 -out ca-cert.pem -subj '/CN=EasySSL CA'

# Create Certificate Revocation List (CRL)
# Publish crl.pem to a publically accessible URL
openssl ca -gencrl -config openssl.cnf -cert ca-cert.pem -keyfile ca-key.pem -out crl.pem
```

### 2. Setting up an application
These steps should be done by the application owner.
```bash
# Create private key
openssl genrsa -out app-key.pem 2048

# Create Certificate Signing Request (CSR)
# It's a good idea (although not required) to provide the app's correct DNS name
openssl req -new -key app-key.pem -out app-csr.pem -subj '/OU=App name/CN=<app.dns.name>'

# Sign the CSR
# This should be done by your Ops people - give them app-csr.pem from previous step, and ask them to sign it
# using the CA's private key
openssl x509 -req -in app-csr.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -days 3650 -sha256 -out app-cert.pem
```

### 3. Using the library
First, you must add `easyssl` to your Spring Boot project.

Maven:
```xml
<dependency>
  <groupId>name.treskunov.denis<groupId>
  <artifactId>easyssl</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:
```groovy
compile('name.treskunov.denis:easyssl:0.1.0')
```

Next, add the following section to `application.yml`:
```yml
easyssl:
  # HTTP URL should work here, as well!
  caCertificate: file:/path/to/ca-cert.pem
  certificate: file:/path/to/app-cert.pem
  key: file:/path/to/app-key.pem
  certificateRevocationList: file:/path/to/crl.pem
  
# There is no need to specify `server.ssl.` properties - they will be overridden by EasySSL
```

Next, ensure that the  `name.treskunov.denis.easyssl` package is getting scanned by Spring. Look at the
[server code](https://github.com/dtreskunov/easyssl/tree/master/src/test/java/name/treskunov/denis/easyssl/server)
used by this project's integration tests.

This should be enough to configure Tomcat/Jetty/etc to use SSL with client auth, but you still need to
implement business rules for securing access. Look at the
[Spring Security code](https://github.com/dtreskunov/easyssl/blob/master/src/test/java/name/treskunov/denis/easyssl/server/Security.java)
in this project's integration tests and the links below.

An app doesn't have to be a server. If your app needs to securely call another app using its certificate to
authenticate itself, you can use the `SSLContext` bean to set up the client (e.g. RestTemplate on top of
Apache HttpClient). Look at the
[client code](https://github.com/dtreskunov/easyssl/blob/master/src/test/java/name/treskunov/denis/easyssl/IntegrationTestUsingRealServer.java)
used by this project's integration tests.

# Testing with cURL
```bash
# Assuming that you've set up client-cert.pem and client-key.pem as above
curl --cacert ca-cert.pem --cert client-cert.pem --key client-key.pem -i https://localhost:8443/
```

# Links
* [X.509 Authentication in Spring Security](http://www.baeldung.com/x-509-authentication-in-spring-security)
