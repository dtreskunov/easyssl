package com.github.dtreskunov.easyssl.server;

import com.github.dtreskunov.easyssl.EasySslBeans;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Loosely based on <a href="http://www.baeldung.com/x-509-authentication-in-spring-security">this tutorial</a>.
 *
 */
@SpringBootApplication
@Import(value = {EasySslBeans.class})
public class Server {
    
    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
