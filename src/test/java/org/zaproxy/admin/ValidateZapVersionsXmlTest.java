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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.control.AddOnCollection.Platform;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/** Validates that ZAP is able to load the {@code ZapVersions.xml} files. */
public class ValidateZapVersionsXmlTest {

    @BeforeAll
    public static void suppressLogging() {
        Logger.getRootLogger().addAppender(new NullAppender());
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class)
    public void shouldLoadCurrentVersion(Platform platform) throws Exception {
        // Given
        File zapVersionsCurr = resource("/ZapVersions-2.7.xml");
        // When
        AddOnCollection aoc =
                new AddOnCollection(new ZapXmlConfiguration(zapVersionsCurr), platform);
        // Then
        assertReleaseAndAddOnsPresent(zapVersionsCurr, aoc, platform);
    }

    private static void assertReleaseAndAddOnsPresent(
            File zapVersionsFile, AddOnCollection aoc, Platform platform) {
        assertThat(aoc.getZapRelease())
                .as(
                        "Release not found in %s using %s platform.",
                        zapVersionsFile.getName(), platform)
                .isNotNull();
        assertThat(aoc.getAddOns())
                .as("No add-ons in %s using %s platform.", zapVersionsFile.getName(), platform)
                .isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class)
    public void shouldLoadDevVersion(Platform platform) throws Exception {
        // Given
        File zapVersionsDev = resource("/ZapVersions-dev.xml");
        // When
        AddOnCollection aoc =
                new AddOnCollection(new ZapXmlConfiguration(zapVersionsDev), platform);
        // Then
        assertReleaseAndAddOnsPresent(zapVersionsDev, aoc, platform);
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class)
    public void shouldLoadNonAddOnsVariant(Platform platform) throws Exception {
        // Given
        File zapVersions = resource("/ZapVersions.xml");
        // When
        AddOnCollection aoc = new AddOnCollection(new ZapXmlConfiguration(zapVersions), platform);
        // Then
        assertThat(aoc.getZapRelease()).isNotNull();
    }

    private static File resource(String path) {
        URL resourceURL = ValidateZapVersionsXmlTest.class.getResource(path);
        assertThat(resourceURL).as("File %s not found.", path).isNotNull();

        try {
            return Paths.get(resourceURL.toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
