package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.github.dtreskunov.easyssl.server.Server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(properties = {"spring.profiles.active=test"}, classes = {Server.class})
@AutoConfigureMockMvc
public class IntegrationTestUsingMockMvc {

    @Autowired
    private MockMvc mvc;

    @Test
    public void sanity(@Autowired EasySslBeans beans, @Autowired EasySslProperties config) {
        assertThat(beans, notNullValue());
        assertThat(config, notNullValue());
    }

    @Test
    public void protectedEndpoint_forbidden() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                .get("/whoami"))
            .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    public void protectedEndpoint_revoked() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                .get("/whoami")
                .with(SecurityMockMvcRequestPostProcessors.x509("ssl/revoked_localhost/cert.pem")))
            .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    public void protectedEndpoint_happy() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                .get("/whoami")
                .with(SecurityMockMvcRequestPostProcessors.x509("ssl/localhost1/cert.pem")))
            .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void unprotectedEndpoint() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                .get("/"))
            .andExpect(MockMvcResultMatchers.status().isOk());
    }
}
