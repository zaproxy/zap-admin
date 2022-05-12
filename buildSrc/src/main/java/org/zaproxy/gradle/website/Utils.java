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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.zaproxy.gradle.website.TocTree.TocItem;

/** Utility methods needed for website page generation. */
final class Utils {

    private static final String ABSOLUTE_SCHEME = "//";
    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";
    private static final String MAILTO_SCHEME = "mailto:";
    private static final String WWW_SUBDOMAIN = "www.";

    private static final Pattern HTML_EXTENSION =
            Pattern.compile("\\.html$", Pattern.CASE_INSENSITIVE);

    private static final String MARKDOWN_EXTENSION = ".md";

    private Utils() {}

    static String createContentsDirName(TocItem tocItem) {
        String url = tocItem.getTarget().toString();
        int idx = url.lastIndexOf('/');
        return url.substring(0, idx);
    }

    static String createAddOnDirName(TocItem addOnTocItem) {
        return addOnTocItem
                .getText()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[ /:]", "-")
                .replaceAll("-{2,}", "-");
    }

    static String createSiteFileName(String path, String newFileExtension) {
        StringBuilder strBuilderFileName = new StringBuilder();
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length - 1; i++) {
            strBuilderFileName.append(StringUtils.capitalize(segments[i]));
        }
        String fileName = StringUtils.capitalize(segments[segments.length - 1]);
        strBuilderFileName.append(HTML_EXTENSION.matcher(fileName).replaceFirst(newFileExtension));

        return strBuilderFileName.toString();
    }

    static String createSitePath(String path) {
        return HTML_EXTENSION
                .matcher(path)
                .replaceFirst(MARKDOWN_EXTENSION)
                .toLowerCase(Locale.ROOT);
    }

    static String createSiteUrl(String path) {
        return HTML_EXTENSION.matcher(path).replaceFirst("/").toLowerCase(Locale.ROOT);
    }

    static String createRedirectPath(String urlPath, String childPath) {
        return urlPath
                + HTML_EXTENSION.matcher(childPath).replaceFirst("/").toLowerCase(Locale.ROOT);
    }

    static URL createUrlFor(URL file, String path) {
        try {
            return new URL(file, path);
        } catch (MalformedURLException e) {
            throw new WebsitePageGenerationException(
                    "Failed to create the URL with " + file + " and " + path, e);
        }
    }

    static String normalisedPath(String baseDir, URL file, String path) {
        return normalisedPath(baseDir, createUrlFor(file, path));
    }

    static String normalisedPath(String baseDir, URL url) {
        return normalisePath(baseDir, url.toString());
    }

    private static String normalisePath(String baseDir, String path) {
        if (!startsWithDir(path, baseDir)) {
            throw new WebsitePageGenerationException(
                    "Path " + path + " not under base dir " + baseDir);
        }
        return path.substring(baseDir.length() + 1);
    }

    static String normalisedImagePath(String baseDir, String imagesDirName, URL url) {
        String path = url.toString();
        if (startsWithDir(path, baseDir)) {
            path = normalisePath(baseDir, path);
            if (path.startsWith(imagesDirName)) {
                path = path.substring(imagesDirName.length() + 1);
            }
        } else {
            path = path.substring(path.lastIndexOf('/') + 1);
        }
        return path;
    }

    private static boolean startsWithDir(String path, String dir) {
        return path.startsWith(normaliseFileSystemPath(dir));
    }

    static String normaliseFileSystemPath(String path) {
        return path.replace('\\', '/');
    }

    static boolean isExternalLink(String href) {
        return StringUtils.startsWithIgnoreCase(href, HTTP_SCHEME)
                || StringUtils.startsWithIgnoreCase(href, HTTPS_SCHEME)
                || StringUtils.startsWithIgnoreCase(href, ABSOLUTE_SCHEME)
                || StringUtils.startsWithIgnoreCase(href, MAILTO_SCHEME)
                || StringUtils.startsWithIgnoreCase(href, WWW_SUBDOMAIN);
    }
}
