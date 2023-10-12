/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2023 The ZAP Development Team
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class WebsiteSbomPageGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NOTICE =
            "This page was automatically generated from the add-on's SBOM.";

    public static void generate(
            Path bomPath,
            String bomUrl,
            String pageTitle,
            String addOnId,
            String addOnVersion,
            Path outputFile)
            throws Exception {
        PageFrontMatter frontMatter = new PageFrontMatter("sbom", pageTitle, 1);
        JsonNode bomJson = MAPPER.readTree(bomPath.toFile());
        List<PageFrontMatter.SbomDataComponent> resultComponents = new ArrayList<>();
        var componentsJsonArray = (ArrayNode) bomJson.get("components");
        List<JsonNode> sortedComponentsList =
                StreamSupport.stream(componentsJsonArray.spliterator(), false)
                        .sorted(Comparator.comparing(jsonNode -> jsonNode.get("name").asText()))
                        .collect(Collectors.toList());
        for (JsonNode component : sortedComponentsList) {
            var licenses = (ArrayNode) component.get("licenses");
            String licensesStr =
                    StreamSupport.stream(licenses.spliterator(), false)
                            .map(l -> l.get("license"))
                            .map(
                                    l ->
                                            l.has("id")
                                                    ? l.get("id").asText()
                                                    : l.has("name") ? l.get("name").asText() : "")
                            .collect(Collectors.joining(", "));
            resultComponents.add(
                    new PageFrontMatter.SbomDataComponent(
                            component.get("name").asText(),
                            component.get("version").asText(),
                            licensesStr));
        }
        frontMatter.setSbomData(
                new PageFrontMatter.SbomData(
                        bomJson.get("bomFormat").asText(), bomUrl, resultComponents));
        frontMatter.setAddOnData(new PageFrontMatter.AddOnData(addOnId, addOnVersion));
        var writer = new StringWriter();
        frontMatter.writeTo(NOTICE, writer);
        Files.write(outputFile, writer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
