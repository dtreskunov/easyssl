package com.github.dtreskunov.easyssl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.github.dtreskunov.easyssl.server.Server;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.profiles.active=test"}, classes = {Server.class})
@AutoConfigureMockMvc
public class IntegrationTestUsingMockMvc {

    @Autowired
    private MockMvc mvc;

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
                .with(SecurityMockMvcRequestPostProcessors.x509("ssl/localhost2/cert.pem")))
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
