/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
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
package org.zaproxy.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

class ReleaseData {

    private static final DumpSettings SETTINGS =
            DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build();
    private static final Dump DUMP = new Dump(SETTINGS, new ReleaseFileRepresenter(SETTINGS));

    private final List<ReleaseFile> releaseFiles;

    public ReleaseData(List<ReleaseFile> releaseFiles) {
        this.releaseFiles = new ArrayList<>(releaseFiles);
    }

    public void save(Path file, String comment) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write(comment);
            writer.write("\n---\n");
            DUMP.dump(releaseFiles, new StreamDataWriterAdapter(writer));
        } catch (UncheckedIOException e) {
            throw new IOException("Failed to save to: " + file, e.getCause());
        }
    }

    static class ReleaseFile {
        private final String name;
        private final String id;
        private final String size;
        private final String link;

        ReleaseFile(String name, String id, String size, String link) {
            this.name = name;
            this.id = id;
            this.size = size;
            this.link = link;
        }
    }

    private static class ReleaseFileRepresenter extends StandardRepresenter {

        ReleaseFileRepresenter(DumpSettings settings) {
            super(settings);
            this.representers.put(ReleaseFile.class, new RepresentReleaseFile());
        }

        private class RepresentReleaseFile implements RepresentToNode {

            @Override
            public Node representData(Object data) {
                ReleaseFile releaseFile = (ReleaseFile) data;
                List<NodeTuple> nodeData = new ArrayList<>(4);
                MappingNode node =
                        new MappingNode(Tag.MAP, nodeData, settings.getDefaultFlowStyle());
                nodeData.add(new NodeTuple(string("name"), string(releaseFile.name)));
                nodeData.add(new NodeTuple(string("id"), string(releaseFile.id)));
                nodeData.add(new NodeTuple(string("size"), string(releaseFile.size)));
                nodeData.add(new NodeTuple(string("link"), string(releaseFile.link)));
                return node;
            }

            private ScalarNode string(String value) {
                return new ScalarNode(Tag.STR, value, settings.getDefaultScalarStyle());
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

        private static void processIoAction(IoAction action) {
            try {
                action.apply();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private interface IoAction {
        void apply() throws IOException;
    }
}
