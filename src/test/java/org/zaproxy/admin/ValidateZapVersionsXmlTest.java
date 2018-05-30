/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2018 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.admin;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.BeforeClass;
import org.junit.Test;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.control.AddOnCollection.Platform;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/** Validates that ZAP is able to load the {@code ZapVersions.xml} files. */
public class ValidateZapVersionsXmlTest {

    private static final File ZAP_VERSIONS_CURR = resource("/ZapVersions-2.7.xml");
    private static final File ZAP_VERSIONS_DEV = resource("/ZapVersions-dev.xml");
    private static final File ZAP_VERSIONS = resource("/ZapVersions.xml");

    private static final List<Platform> PLATFORMS =
            Arrays.asList(Platform.linux, Platform.windows, Platform.mac, Platform.daily);

    @BeforeClass
    public static void suppressLogging() {
        Logger.getRootLogger().addAppender(new NullAppender());
    }

    @Test
    public void shouldLoadCurrentVersion() throws Exception {
        // Given
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(ZAP_VERSIONS_CURR), platform);
            // Then
            assertReleaseAndAddOnsPresent(ZAP_VERSIONS_CURR, aoc, platform);
        }
    }

    private static void assertReleaseAndAddOnsPresent(
            File zapVersionsFile, AddOnCollection aoc, Platform platform) {
        assertThat(
                "Release not found for: " + zapVersionsFile.getName() + " [" + platform + "]",
                aoc.getZapRelease(),
                is(notNullValue()));
        assertThat(
                "No add-ons for: " + zapVersionsFile.getName() + " [" + platform + "]",
                aoc.getAddOns(),
                is(not(empty())));
    }

    @Test
    public void shouldLoadDevVersion() throws Exception {
        // Given
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(ZAP_VERSIONS_DEV), platform);
            // Then
            assertReleaseAndAddOnsPresent(ZAP_VERSIONS_DEV, aoc, platform);
        }
    }

    @Test
    public void shouldLoadNonAddOnsVariant() throws Exception {
        // Given
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(ZAP_VERSIONS), platform);
            // Then
            assertThat(aoc.getZapRelease(), is(notNullValue()));
        }
    }

    private static File resource(String path) {
        return new File(ValidateZapVersionsXmlTest.class.getResource(path).getPath());
    }
}
