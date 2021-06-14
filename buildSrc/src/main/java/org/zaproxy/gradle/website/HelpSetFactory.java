/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
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
package org.zaproxy.gradle.website;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.function.Function;
import javax.help.HelpSet;
import javax.help.HelpSetException;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.extension.help.ExtensionHelp;
import org.zaproxy.zap.utils.LocaleUtils;

/**
 * Factory of {@link HelpSet}, created from an add-on (if present).
 *
 * <p>First checks if the add-on declares the {@code HelpSet}, otherwise it searches in the default
 * locations.
 */
final class HelpSetFactory {

    private HelpSetFactory() {}

    /**
     * Creates the {@code HelpSet} for the given add-on.
     *
     * @param addOn the add-on that might contain the {@code HelpSet}.
     * @param helpAddOn {@code true} if the given add-on has the core help, {@code false} otherwise.
     * @return the {@code HelpSet} or {@code null} if not found.
     * @throws WebsitePageGenerationException if an error occurred while searching or creating the
     *     {@code HelpSet}.
     */
    static HelpSet createHelpSet(AddOn addOn, boolean helpAddOn) {
        try (URLClassLoader classLoader =
                new URLClassLoader(
                        new URL[] {addOn.getFile().toURI().toURL()},
                        HelpSetFactory.class.getClassLoader())) {
            if (helpAddOn) {
                return createHelpSet(classLoader, ExtensionHelp.HELP_SET_FILE_NAME, "");
            }

            AddOn.HelpSetData helpSetData = addOn.getHelpSetData();
            if (!helpSetData.isEmpty()) {
                return createHelpSet(
                        classLoader, helpSetData.getBaseName(), helpSetData.getLocaleToken());
            }

            for (String extension : addOn.getExtensions()) {
                URL url = getHelpSetUrl(classLoader, extension);
                if (url != null) {
                    return createHelpSet(classLoader, url);
                }
            }
            return null;
        } catch (MalformedURLException e) {
            throw new WebsitePageGenerationException("Failed to convert the file path to URL:", e);
        } catch (IOException e) {
            throw new WebsitePageGenerationException(
                    "Failed to read the contents of the add-on:", e);
        }
    }

    private static HelpSet createHelpSet(
            ClassLoader classLoader, String baseName, String localeToken) {
        URL helpSetUrl = findHelpSet(baseName, localeToken, classLoader::getResource);

        if (helpSetUrl == null) {
            throw new WebsitePageGenerationException(
                    "Declared HelpSet not found in the add-on, with base name: "
                            + baseName
                            + (localeToken.isEmpty() ? "" : " and locale token: " + localeToken));
        }
        return createHelpSet(classLoader, helpSetUrl);
    }

    private static URL findHelpSet(
            String baseName, String localeToken, Function<String, URL> function) {
        return LocaleUtils.findResource(
                baseName,
                ExtensionHelp.HELP_SET_FILE_EXTENSION,
                localeToken,
                Locale.ROOT,
                function);
    }

    private static HelpSet createHelpSet(ClassLoader classLoader, URL helpSetUrl) {
        try {
            return new HelpSet(classLoader, helpSetUrl);
        } catch (HelpSetException e) {
            throw new WebsitePageGenerationException(
                    "An error occured while loading the HelpSet from the add-on.", e);
        }
    }

    private static URL getHelpSetUrl(ClassLoader classLoader, String extension) {
        String extensionPackage = extension.substring(0, extension.lastIndexOf('.'));
        String localeToken = "%LC%";
        Function<String, URL> getResource = classLoader::getResource;
        URL helpSetUrl =
                findHelpSet(
                        extensionPackage
                                + ".resources.help"
                                + localeToken
                                + "."
                                + ExtensionHelp.HELP_SET_FILE_NAME,
                        localeToken,
                        getResource);
        if (helpSetUrl == null) {
            // Search in old location
            helpSetUrl =
                    findHelpSet(
                            extensionPackage
                                    + ".resource.help"
                                    + localeToken
                                    + "."
                                    + ExtensionHelp.HELP_SET_FILE_NAME,
                            localeToken,
                            getResource);
        }
        return helpSetUrl;
    }
}
