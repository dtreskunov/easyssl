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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.github.dtreskunov.easyssl.IntegrationTestWhenDisabled.ScanConfiguration;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"easyssl.enabled=false"}, classes = {ScanConfiguration.class})
public class IntegrationTestWhenDisabled {
    @SpringBootApplication(scanBasePackages = {"com.github.dtreskunov.easyssl"})
    public static class ScanConfiguration {}

    @Test
    public void sanity() {}
}
