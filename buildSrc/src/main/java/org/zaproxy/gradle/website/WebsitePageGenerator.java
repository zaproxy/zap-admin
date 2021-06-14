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

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.help.HelpSet;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.zaproxy.gradle.website.TocTree.TocItem;
import org.zaproxy.zap.control.AddOn;

/** The generator of the website pages from an add-on. */
public class WebsitePageGenerator {

    private static final FlexmarkHtmlConverter HTML_CONVERTER;

    static {
        MutableDataSet options =
                new MutableDataSet()
                        .set(FlexmarkHtmlConverter.LIST_CONTENT_INDENT, false)
                        .set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
                        .set(FlexmarkHtmlConverter.TYPOGRAPHIC_QUOTES, false)
                        .set(FlexmarkHtmlConverter.TYPOGRAPHIC_SMARTS, false);

        HTML_CONVERTER = FlexmarkHtmlConverter.builder(options).build();
    }

    private final String siteUrl;
    private final String baseUrlPath;
    private final String helpAddOnRegex;
    private final String addOnsDirName;
    private final String pageType;
    private final String redirectPageType;
    private final String redirectPageLayout;
    private final String sectionPageName;
    private final String imagesDirName;
    private final String noticeGeneratedPage;

    public WebsitePageGenerator(
            String siteUrl,
            String baseUrlPath,
            String helpAddOnRegex,
            String addOnsDirName,
            String pageType,
            String redirectPageType,
            String redirectPageLayout,
            String sectionPageName,
            String imagesDirName,
            String noticeGeneratedPage) {
        this.siteUrl = siteUrl;
        this.baseUrlPath = baseUrlPath;
        this.helpAddOnRegex = helpAddOnRegex;
        this.addOnsDirName = addOnsDirName;
        this.pageType = pageType;
        this.redirectPageType = redirectPageType;
        this.redirectPageLayout = redirectPageLayout;
        this.sectionPageName = sectionPageName;
        this.imagesDirName = imagesDirName;
        this.noticeGeneratedPage = noticeGeneratedPage;
    }

    /**
     * Generates the website pages for the given add-on into the provided directory.
     *
     * <p>The actual directory where the pages are created depends on the type of add-on. The main
     * help add-ons are generated directly in the given directory other add-ons are generated into
     * the add-ons directory (as given in the constructor).
     *
     * @param addOn the add-on.
     * @param outputDir the output directory.
     * @return {@code true} if the website pages were generated, {@code false} if no help found.
     */
    public boolean generate(AddOn addOn, Path outputDir) {
        boolean helpAddOn = addOn.getId().matches(helpAddOnRegex);
        HelpSet helpSet = HelpSetFactory.createHelpSet(addOn, helpAddOn);
        if (helpSet == null) {
            return false;
        }

        new Generator(addOn, helpAddOn, helpSet).generate(outputDir);
        return true;
    }

    /**
     * The actual generator of the website pages.
     *
     * <p>First it collects the pages/images from the add-on then the website pages can be {@link
     * #generate(Path) generated}.
     */
    private class Generator {

        private static final String TOC_TOP_LEVEL_ITEM_ID = "toplevelitem";
        private static final String TOC_ADDONS_ITEM_ID = "addons";

        private static final String TITLE_TAG = "title";

        private static final String LINK_SELECTOR = "a[href]";
        private static final String HREF_ATTR = "href";

        private static final String IMG_SELECTOR = "img[src]";
        private static final String SRC_ATTR = "src";

        private static final char URL_ANCHOR = '#';

        private final String urlPath;
        private final TocTree toc;
        private final List<SourcePage> sourcePages;
        private final Map<String, SourcePage> pathToSourcePageMap;
        private final List<SourceImage> sourceImages;
        private final Set<String> sectionPaths;

        private final String contentsDir;

        private Generator(AddOn addOn, boolean helpAddOn, HelpSet helpSet) {
            toc = new TocTree(helpSet);
            List<TocItem> tocItems = toc.getRoot().getChildren();
            if (tocItems.isEmpty()) {
                throw new WebsitePageGenerationException(
                        "Unable to determine main help page, TOC is empty.");
            }

            TocItem mainTocItem = tocItems.get(0);
            validateTocItem(mainTocItem, TOC_TOP_LEVEL_ITEM_ID);
            tocItems = mainTocItem.getChildren();

            if (helpAddOn) {
                this.urlPath = baseUrlPath;
                this.contentsDir = Utils.createContentsDirName(tocItems.get(0));
            } else {
                // Traverse the nodes and get the TOC item of the add-on.
                mainTocItem = tocItems.get(0);
                validateTocItem(mainTocItem, TOC_ADDONS_ITEM_ID);
                TocItem addOnTocItem = mainTocItem.getChildren().get(0);

                String addOnDirName = Utils.createAddOnDirName(addOnTocItem);
                this.urlPath = baseUrlPath + addOnsDirName + "/" + addOnDirName + "/";
                this.contentsDir = Utils.createContentsDirName(addOnTocItem);
            }

            if (contentsDir == null || contentsDir.isEmpty()) {
                throw new WebsitePageGenerationException(
                        "Unable to determine the directory containing the HTML content.");
            }

            sourcePages = new ArrayList<>();
            pathToSourcePageMap = new HashMap<>();
            sourceImages = new ArrayList<>();
            sectionPaths = new HashSet<>();

            mainTocItem.getChildren().forEach(e -> addSourcePages(contentsDir, e));
            sourcePages
                    .get(0)
                    .getFrontMatter()
                    .setAddOnData(
                            new PageFrontMatter.AddOnData(
                                    addOn.getId(), addOn.getVersion().toString()));

            // Use a for because more source pages might be found in the process.
            for (int i = 0, size = sourcePages.size(); i < size; i++) {
                preparePage(sourcePages.get(i));
            }
        }

        private void addSourcePages(String contentsDir, TocItem tocItem) {
            if (tocItem.getTarget() != null) {
                addSourcePage(tocItem.getTarget());
            } else {
                String childPath = getChildPath(tocItem);
                String sectionPath =
                        childPath.substring(0, childPath.lastIndexOf('/') + 1) + sectionPageName;
                sectionPaths.add(sectionPath);

                PageFrontMatter frontMatter =
                        new PageFrontMatter(
                                redirectPageType,
                                redirectPageLayout,
                                tocItem.getText(),
                                tocItem.getIndex(),
                                Utils.createRedirectPath(urlPath, childPath));
                sourcePages.add(new SourcePage(frontMatter, sectionPath));
            }
            tocItem.getChildren().forEach(e -> addSourcePages(contentsDir, e));
        }

        private String getChildPath(TocItem tocItem) {
            List<TocItem> children = tocItem.getChildren();
            if (children.isEmpty()) {
                throw new WebsitePageGenerationException(
                        "Failed to obtain child path from "
                                + tocItem.getText()
                                + " does not have children.");
            }

            Optional<URL> result =
                    children.stream().map(TocItem::getTarget).filter(Objects::nonNull).findFirst();
            if (!result.isPresent()) {
                throw new WebsitePageGenerationException(
                        "Failed to find any child with path for " + tocItem.getText());
            }
            return result.get().toString().substring(contentsDir.length() + 1);
        }

        private SourcePage addSourcePage(URL url) {
            return pathToSourcePageMap.computeIfAbsent(
                    url.toString().substring(contentsDir.length() + 1),
                    path -> createSourcePage(url, path));
        }

        private SourcePage createSourcePage(URL url, String path) {
            Document doc;
            try (InputStream is = url.openStream()) {
                doc = Jsoup.parse(is, StandardCharsets.UTF_8.name(), "http://example.com");
            } catch (IOException e) {
                throw new WebsitePageGenerationException("Failed to parse the file: " + url, e);
            }

            int weight = -1;
            boolean sectionPage = false;
            TocItem item = toc.getTocItem(url);
            String sitePath = Utils.createSitePath(path);
            String siteUrl = Utils.createSiteUrl(path);
            if (item != null) {
                weight = item.getIndex();
                String dirPath =
                        path.substring(0, path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                String sectionPath = dirPath + sectionPageName;
                if (!sectionPaths.contains(sectionPath)) {
                    siteUrl = dirPath;
                    sectionPaths.add(sectionPath);
                    sectionPage = true;
                    sitePath = sectionPath;
                }
            }

            Element docTitle = doc.selectFirst(TITLE_TAG);
            if (docTitle == null) {
                throw new WebsitePageGenerationException(
                        String.format("The page %s does not have a title", path));
            }

            SourcePage sourcePage =
                    new SourcePage(
                            new PageFrontMatter(pageType, docTitle.text(), weight),
                            url,
                            path,
                            sitePath,
                            siteUrl,
                            doc,
                            sectionPage);
            sourcePages.add(sourcePage);
            return sourcePage;
        }

        private void preparePage(SourcePage sourcePage) {
            Document doc = sourcePage.getDocument();
            if (doc == null) {
                return;
            }

            URL url = sourcePage.getPath();

            for (Element a : doc.select(LINK_SELECTOR)) {
                String href = a.attr(HREF_ATTR);
                if (href.isEmpty()) {
                    continue;
                }

                if (Utils.isExternalLink(href)) {
                    if (href.startsWith(siteUrl)) {
                        a.attr(HREF_ATTR, href.substring(siteUrl.length() - 1));
                    }
                } else {
                    String anchor = "";
                    int idx = href.indexOf(URL_ANCHOR);
                    if (idx != -1) {
                        anchor = href.substring(idx);
                        href = href.substring(0, idx);
                    }
                    if (!href.isEmpty()) {
                        href = Utils.normalisedPath(contentsDir, url, href);

                        SourcePage to = pathToSourcePageMap.get(href);
                        if (to == null) {
                            String newSourceUrl = contentsDir + "/" + href;

                            try {
                                to = addSourcePage(new URL(newSourceUrl));
                            } catch (MalformedURLException e) {
                                throw new WebsitePageGenerationException(
                                        "Failed to create URL from: " + newSourceUrl);
                            }
                            preparePage(to);
                        }
                        a.attr(HREF_ATTR, urlPath + to.getSiteUrl() + anchor);
                    }
                }
            }

            for (Element img : doc.select(IMG_SELECTOR)) {
                String src = img.attr(SRC_ATTR);
                if (src.isEmpty() || Utils.isExternalLink(src)) {
                    continue;
                }

                URL imageUrl = Utils.createUrlFor(url, src);
                sourceImages.add(new SourceImage(src, imageUrl));
                String path = Utils.normalisedImagePath(contentsDir, imagesDirName, imageUrl);
                img.attr(SRC_ATTR, urlPath + imagesDirName + "/" + path);
            }

            // Normalise content to remove newlines which cause incorrect Markdown conversion.
            for (Element h : doc.select("h1, h2, h3, h4, h5")) {
                h.html(h.html());
            }

            // Add missing headers to tables for proper conversion to Markdown.
            for (Element table : doc.select("table:not(:has(thead))")) {
                Element thead = doc.createElement("thead");
                Element tr = thead.appendElement("tr");
                for (int i = table.select("tbody tr:first-child td").size(); i > 0; i--) {
                    tr.appendElement("th");
                }
                table.insertChildren(0, thead);
            }
        }

        void generate(Path siteContentDir) {
            Path destDir = siteContentDir.resolve(removeLeadingSlash(urlPath));

            try {
                Files.createDirectories(destDir);
            } catch (IOException e) {
                throw new WebsitePageGenerationException(
                        "Failed to create the destination directory: " + destDir, e);
            }

            Path imagesDir = destDir.resolve(imagesDirName);
            for (SourceImage image : sourceImages) {
                URL url = image.getUrl();
                String path = Utils.normalisedImagePath(contentsDir, imagesDirName, url);

                Path imageFile = imagesDir.resolve(path);
                createDirectories(imageFile);

                try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                    Files.copy(inputStream, imageFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new WebsitePageGenerationException(
                            String.format("Failed to copy image %s to %s", url, imageFile), e);
                }
            }

            for (SourcePage sourcePage : sourcePages) {
                Path destFile = destDir.resolve(sourcePage.getSitePath());
                createDirectories(destFile);
                writeSitePage(sourcePage, destFile);
            }
        }

        private void writeSitePage(SourcePage sourcePage, Path destSiteFile) {
            StringWriter writer = new StringWriter();
            try {
                sourcePage.getFrontMatter().writeTo(noticeGeneratedPage, writer);

                Document doc = sourcePage.getDocument();
                if (doc != null) {
                    HTML_CONVERTER.convert(doc.root(), writer, 0);
                }

                String contents = writer.toString();
                // Remove empty comments and HTML break lines added while converting.
                contents = contents.replace("<!-- -->\n", "").replace("<br />\n", "");
                // Normalise line endings
                contents = contents.replace("\r\n", "\n");

                Files.write(destSiteFile, contents.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new WebsitePageGenerationException(
                        "Failed to convert file: " + sourcePage.getRelativePath(), e);
            }
        }
    }

    private static void createDirectories(Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new WebsitePageGenerationException(
                    "An error occurred while creating the directories for file: " + file, e);
        }
    }

    private static void validateTocItem(TocItem tocItem, String id) {
        if (!id.equals(tocItem.getTocId())) {
            throw new WebsitePageGenerationException(
                    String.format(
                            "Expected TOC item to have ID '%s', it was '%s'.",
                            id, tocItem.getTocId()));
        }

        if (tocItem.getChildren().isEmpty()) {
            throw new WebsitePageGenerationException(
                    String.format("TOC item '%s' has no children.", id));
        }
    }

    private static String removeLeadingSlash(String value) {
        return StringUtils.removeStart(value, "/");
    }
}
