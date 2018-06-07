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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
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

    private static final List<Platform> PLATFORMS =
            Arrays.asList(Platform.linux, Platform.windows, Platform.mac, Platform.daily);

    @BeforeClass
    public static void suppressLogging() {
        Logger.getRootLogger().addAppender(new NullAppender());
    }

    @Test
    public void shouldLoadCurrentVersion() throws Exception {
        // Given
        File zapVersionsCurr = resource("/ZapVersions-2.7.xml");
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(zapVersionsCurr), platform);
            // Then
            assertReleaseAndAddOnsPresent(zapVersionsCurr, aoc, platform);
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
        File zapVersionsDev = resource("/ZapVersions-dev.xml");
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(zapVersionsDev), platform);
            // Then
            assertReleaseAndAddOnsPresent(zapVersionsDev, aoc, platform);
        }
    }

    @Test
    public void shouldLoadNonAddOnsVariant() throws Exception {
        // Given
        File zapVersions = resource("/ZapVersions.xml");
        for (Platform platform : PLATFORMS) {
            // When
            AddOnCollection aoc =
                    new AddOnCollection(new ZapXmlConfiguration(zapVersions), platform);
            // Then
            assertThat(aoc.getZapRelease(), is(notNullValue()));
        }
    }

    private static File resource(String path) {
        URL resourceURL = ValidateZapVersionsXmlTest.class.getResource(path);
        assertThat("File " + path + " not found.", resourceURL, is(notNullValue()));

        try {
            return Paths.get(resourceURL.toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
