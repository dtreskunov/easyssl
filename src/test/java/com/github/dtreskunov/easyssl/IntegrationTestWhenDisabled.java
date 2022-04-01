// -----------------------------------------------------------------------------
//
// This file is the copyrighted property of Tableau Software and is protected
// by registered patents and other applicable U.S. and international laws and
// regulations.
//
// Unlicensed use of the contents of this file is prohibited. Please refer to
// the NOTICES.txt file for further details.
//
// -----------------------------------------------------------------------------
package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import com.github.dtreskunov.easyssl.IntegrationTestWhenDisabled.ScanConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"easyssl.enabled=false"}, classes = {ScanConfiguration.class})
public class IntegrationTestWhenDisabled {
    @SpringBootApplication(scanBasePackages = {"com.github.dtreskunov.easyssl"})
    public static class ScanConfiguration {}

    @Test
    public void sanity(@Autowired(required = false) EasySslBeans beans, @Autowired(required = false) EasySslProperties config) {
        assertThat(beans, nullValue());
        assertThat(config, nullValue());
    }
}
