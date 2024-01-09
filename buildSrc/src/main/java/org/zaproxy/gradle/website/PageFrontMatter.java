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
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.RepresentToNode;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

/** The front matter for the website page. */
class PageFrontMatter {

    private static final DumpSettings SETTINGS =
            DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build();
    private static final Dump DUMP = new Dump(SETTINGS, new PageFrontMatterRepresenter(SETTINGS));

    private static final String DELIMITER = "---";

    private final String title;
    private final String type;
    private final String layout;
    private final String redirect;
    private final int weight;

    private AddOnData addOnData;
    private SbomData sbomData;

    PageFrontMatter(String type, String title, int weight) {
        this(type, null, title, weight, null);
    }

    PageFrontMatter(String type, String layout, String title, int weight, String redirect) {
        this.type = type;
        this.layout = layout;
        this.title = title;
        this.weight = weight;
        this.redirect = redirect;
    }

    AddOnData getAddOnData() {
        return addOnData;
    }

    void setAddOnData(AddOnData addOnData) {
        this.addOnData = addOnData;
    }

    SbomData getSbomData() {
        return sbomData;
    }

    void setSbomData(SbomData sbomData) {
        this.sbomData = sbomData;
    }

    void writeTo(String notice, Writer writer) {
        processIoAction(
                () -> {
                    writer.append(DELIMITER).append("\n# ").append(notice).append('\n');
                    DUMP.dump(this, new StreamDataWriterAdapter(writer));
                    writer.append(DELIMITER).append("\n\n");
                });
    }

    static class AddOnData {
        private final String id;
        private final String version;

        AddOnData(String id, String version) {
            super();
            this.id = id;
            this.version = version;
        }

        String getId() {
            return id;
        }

        String getVersion() {
            return version;
        }
    }

    static class SbomData {
        private final String bomFormat;
        private final String downloadUrl;
        private final Set<SbomDataComponent> components;

        SbomData(String bomFormat, String downloadUrl, Set<SbomDataComponent> components) {
            this.bomFormat = bomFormat;
            this.downloadUrl = downloadUrl;
            this.components = components;
        }

        public String getBomFormat() {
            return bomFormat;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public Set<SbomDataComponent> getComponents() {
            return components;
        }
    }

    static class SbomDataComponent implements Comparable<SbomDataComponent> {
        private final String name;
        private final String version;
        private final String licenses;

        SbomDataComponent(String name, String version, String licenses) {
            super();
            this.name = name;
            this.version = version;
            this.licenses = licenses;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getLicenses() {
            return licenses;
        }

        @Override
        public int compareTo(SbomDataComponent o) {
            int result = name.compareTo(o.name);
            if (result != 0) {
                return result;
            }
            result = version.compareTo(o.version);
            if (result != 0) {
                return result;
            }
            return licenses.compareTo(o.licenses);
        }
    }

    private static class PageFrontMatterRepresenter extends StandardRepresenter {

        PageFrontMatterRepresenter(DumpSettings settings) {
            super(settings);
            this.representers.put(PageFrontMatter.class, new RepresentPageFrontMatter());
        }

        private class RepresentPageFrontMatter implements RepresentToNode {

            @Override
            public Node representData(Object data) {
                PageFrontMatter frontMatter = (PageFrontMatter) data;
                List<NodeTuple> pageData = new ArrayList<>(5);
                MappingNode node =
                        new MappingNode(Tag.MAP, pageData, settings.getDefaultFlowStyle());
                pageData.add(new NodeTuple(string("title"), string(frontMatter.title)));
                pageData.add(new NodeTuple(string("type"), string(frontMatter.type)));
                if (frontMatter.layout != null) {
                    pageData.add(new NodeTuple(string("layout"), string(frontMatter.layout)));
                }
                if (frontMatter.redirect != null) {
                    pageData.add(new NodeTuple(string("redirect"), string(frontMatter.redirect)));
                }
                if (frontMatter.weight > 0) {
                    pageData.add(new NodeTuple(string("weight"), integer(frontMatter.weight)));
                }

                PageFrontMatter.AddOnData addOnData = frontMatter.getAddOnData();
                if (addOnData != null) {
                    Map<String, String> addOnDataMap = new LinkedHashMap<>();
                    addOnDataMap.put("id", addOnData.getId());
                    addOnDataMap.put("version", addOnData.getVersion());

                    Map<String, Object> cascade = new LinkedHashMap<>();
                    cascade.put("addon", addOnDataMap);
                    pageData.add(
                            new NodeTuple(
                                    string("cascade"),
                                    PageFrontMatterRepresenter.this.representData(cascade)));
                }

                PageFrontMatter.SbomData sbomData = frontMatter.getSbomData();
                if (sbomData != null) {
                    Map<String, Object> sbom = new LinkedHashMap<>();
                    sbom.put("format", sbomData.getBomFormat());
                    sbom.put("downloadUrl", sbomData.getDownloadUrl());
                    List<Map<String, String>> components = new ArrayList<>();
                    for (SbomDataComponent component : sbomData.getComponents()) {
                        Map<String, String> componentsMap = new LinkedHashMap<>();
                        componentsMap.put("name", component.getName());
                        componentsMap.put("version", component.getVersion());
                        componentsMap.put("licenses", component.getLicenses());
                        components.add(componentsMap);
                    }
                    sbom.put("components", components);
                    pageData.add(
                            new NodeTuple(
                                    string("sbom"),
                                    PageFrontMatterRepresenter.this.representData(sbom)));
                }

                return node;
            }

            private ScalarNode string(String name) {
                return scalarNode(Tag.STR, name);
            }

            private ScalarNode integer(int value) {
                return scalarNode(Tag.INT, String.valueOf(value));
            }

            private ScalarNode scalarNode(Tag tag, String value) {
                return new ScalarNode(tag, value, settings.getDefaultScalarStyle());
            }
        }
    }

    private static class StreamDataWriterAdapter implements StreamDataWriter {

        private final Writer writer;

        StreamDataWriterAdapter(Writer writer) {
            this.writer = writer;
        }

        @Override
        public void write(String str) {
            processIoAction(() -> writer.write(str));
        }

        @Override
        public void write(String str, int off, int len) {
            processIoAction(() -> writer.write(str, off, len));
        }

        @Override
        public void flush() {
            processIoAction(writer::flush);
        }
    }

    private static void processIoAction(IoAction action) {
        try {
            action.apply();
        } catch (IOException e) {
            throw new WebsitePageGenerationException(
                    "An error occurred while writing the front matter.", e);
        }
    }

    private interface IoAction {
        void apply() throws IOException;
    }
}
