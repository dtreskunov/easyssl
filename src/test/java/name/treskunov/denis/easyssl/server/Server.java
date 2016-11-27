package name.treskunov.denis.easyssl.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Loosely based on <a href="http://www.baeldung.com/x-509-authentication-in-spring-security">this tutorial</a>.
 *
 */
@SpringBootApplication(scanBasePackages = {"name.treskunov.denis.easyssl"})
public class Server {
    
    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
