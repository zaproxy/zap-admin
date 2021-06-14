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

import java.net.URL;
import java.util.Objects;
import org.jsoup.nodes.Document;

/** A source page (HTML) contained in the add-on. */
class SourcePage {

    private final PageFrontMatter frontMatter;
    private final URL path;
    private final String relativePath;
    private final String sitePath;
    private final String siteUrl;
    private final Document document;
    private final boolean section;

    SourcePage(PageFrontMatter frontMatter, String sitePath) {
        this.frontMatter = frontMatter;
        this.path = null;
        this.relativePath = null;
        this.sitePath = sitePath;
        this.siteUrl = null;
        this.document = null;
        this.section = false;
    }

    SourcePage(
            PageFrontMatter frontMatter,
            URL path,
            String relativePath,
            String sitePath,
            String siteUrl,
            Document document,
            boolean section) {
        this.frontMatter = frontMatter;
        this.path = Objects.requireNonNull(path);
        this.relativePath = Objects.requireNonNull(relativePath);
        this.sitePath = Objects.requireNonNull(sitePath);
        this.siteUrl = Objects.requireNonNull(siteUrl);
        this.document = document;
        this.section = section;
    }

    PageFrontMatter getFrontMatter() {
        return frontMatter;
    }

    URL getPath() {
        return path;
    }

    String getRelativePath() {
        return relativePath;
    }

    String getSitePath() {
        return sitePath;
    }

    String getSiteUrl() {
        return siteUrl;
    }

    Document getDocument() {
        return document;
    }

    boolean isSection() {
        return section;
    }

    @Override
    public int hashCode() {
        return relativePath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SourcePage other = (SourcePage) obj;
        return relativePath.equals(other.relativePath);
    }

    @Override
    public String toString() {
        return relativePath;
    }
}
