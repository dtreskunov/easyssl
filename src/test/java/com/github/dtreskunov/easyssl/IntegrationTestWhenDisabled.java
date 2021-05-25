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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import com.github.dtreskunov.easyssl.IntegrationTestWhenDisabled.ScanConfiguration;

@SpringBootTest(properties = {"easyssl.enabled=false"}, classes = {ScanConfiguration.class})
public class IntegrationTestWhenDisabled {
    @SpringBootApplication(scanBasePackages = {"com.github.dtreskunov.easyssl"})
    public static class ScanConfiguration {}

    @Test
    public void sanity() {}
}
