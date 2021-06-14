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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.help.HelpSet;
import javax.help.NavigatorView;
import javax.swing.tree.DefaultMutableTreeNode;
import org.zaproxy.zap.extension.help.ZapTocItem;
import org.zaproxy.zap.extension.help.ZapTocView;

/** The TOC tree of a {@code HelpSet}. */
class TocTree {

    private static final String TOC_VIEW_NAME = "TOC";

    private final TocItem root;
    private final Map<String, TocItem> urlToItemMap;

    /**
     * Constructs a {@code TocTree} from the given {@code HelpSet}.
     *
     * @param helpSet the {@code HelpSet} containing the TOC.
     * @throws WebsitePageGenerationException if the given {@code HelpSet} does not contain a TOC or
     *     if it's not valid.
     */
    TocTree(HelpSet helpSet) {
        this.urlToItemMap = new HashMap<>();
        root = new TocItem(0, "", null, null, null, null);
        root.setChildren(createTocItems(urlToItemMap, root, getTocView(helpSet).getDataAsTree()));
    }

    /**
     * Gets the root TOC item.
     *
     * <p>A synthetic item that gives access to the actual items present in the TOC tree.
     *
     * @return the root TOC item, never {@code null}.
     */
    TocItem getRoot() {
        return root;
    }

    /**
     * Gets a TOC item for the given URL.
     *
     * @param url the URL of the TOC item.
     * @return the TOC item, or {@code null} if no item with the given URL is present.
     */
    TocItem getTocItem(URL url) {
        return urlToItemMap.get(url.toString());
    }

    private static ZapTocView getTocView(HelpSet helpSet) {
        NavigatorView view = helpSet.getNavigatorView(TOC_VIEW_NAME);
        if (view == null) {
            throw new WebsitePageGenerationException("The HelpSet does not contain a TOC.");
        }
        if (!(view instanceof ZapTocView)) {
            throw new WebsitePageGenerationException(
                    "Expected TOC to be a ZapTocView but was " + view.getClass());
        }
        return (ZapTocView) view;
    }

    private static List<TocItem> createTocItems(
            Map<String, TocItem> urlToItemMap, TocItem parent, DefaultMutableTreeNode treeNode) {
        int childCount = treeNode.getChildCount();
        if (childCount == 0) {
            return Collections.emptyList();
        }

        List<TocItem> items = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; ++i) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) treeNode.getChildAt(i);
            ZapTocItem zti = (ZapTocItem) childNode.getUserObject();
            TocItem tocItem =
                    new TocItem(
                            i + 1,
                            zti.getName(),
                            parent,
                            zti.getTocId(),
                            zti.getID(),
                            zti.getImageID());
            tocItem.setChildren(createTocItems(urlToItemMap, tocItem, childNode));
            if (tocItem.getTarget() != null) {
                urlToItemMap.put(tocItem.getTarget().toString(), tocItem);
            }
            items.add(tocItem);
        }

        return Collections.unmodifiableList(items);
    }

    /** An item of the TOC. */
    static final class TocItem {

        private final int index;
        private final String text;
        private final TocItem parent;
        private final String tocId;
        private final URL target;
        private final String targetId;
        private final URL image;
        private List<TocItem> children;

        private TocItem(
                int index,
                String text,
                TocItem parent,
                String tocId,
                javax.help.Map.ID target,
                javax.help.Map.ID image) {
            this.index = index;
            this.text = text;
            this.parent = parent;
            this.tocId = tocId;
            this.target = extractUrl(target);
            this.targetId = target != null ? target.getIDString() : null;
            this.image = extractUrl(image);
            this.children = Collections.emptyList();
        }

        private static URL extractUrl(javax.help.Map.ID id) {
            if (id == null) {
                return null;
            }

            try {
                return id.getURL();
            } catch (MalformedURLException e) {
                throw new WebsitePageGenerationException(
                        "Failed to create URL for TOC item " + id.getIDString());
            }
        }

        int getIndex() {
            return index;
        }

        String getText() {
            return text;
        }

        TocItem getParent() {
            return parent;
        }

        String getTocId() {
            return tocId;
        }

        URL getTarget() {
            return target;
        }

        URL getImage() {
            return image;
        }

        List<TocItem> getChildren() {
            return children;
        }

        private void setChildren(List<TocItem> children) {
            this.children = children;
        }

        @Override
        public String toString() {
            StringBuilder strBuilder = new StringBuilder();
            if (parent == null) {
                children.forEach(e -> e.toString(strBuilder, ""));
            } else {
                toString(strBuilder, "");
            }
            return strBuilder.toString();
        }

        private void toString(StringBuilder strBuilder, String pad) {
            strBuilder.append(pad).append(" - ").append(text);
            if (targetId != null) {
                strBuilder.append("  [").append(targetId).append(']');
            }
            strBuilder.append(' ').append(index).append("\n");
            String childrenPad = pad + "  ";
            children.forEach(e -> e.toString(strBuilder, childrenPad));
        }
    }
}
