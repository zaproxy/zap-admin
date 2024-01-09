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
import java.util.Set;
import java.util.TreeSet;
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
        Set<PageFrontMatter.SbomDataComponent> resultComponents = new TreeSet<>();
        var componentsJsonArray = (ArrayNode) bomJson.get("components");
        for (JsonNode component : componentsJsonArray) {
            resultComponents.add(
                    new PageFrontMatter.SbomDataComponent(
                            component.get("name").asText(),
                            component.get("version").asText(),
                            createLicensesString(component)));
        }
        frontMatter.setSbomData(
                new PageFrontMatter.SbomData(
                        bomJson.get("bomFormat").asText(), bomUrl, resultComponents));
        frontMatter.setAddOnData(new PageFrontMatter.AddOnData(addOnId, addOnVersion));
        var writer = new StringWriter();
        frontMatter.writeTo(NOTICE, writer);
        Files.write(outputFile, writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String createLicensesString(JsonNode component) {
        var licenses = (ArrayNode) component.get("licenses");
        if (licenses == null) {
            return "";
        }

        return StreamSupport.stream(licenses.spliterator(), false)
                .map(WebsiteSbomPageGenerator::licenseObjectToString)
                .filter(e -> e != null)
                .collect(Collectors.joining(", "));
    }

    private static String licenseObjectToString(JsonNode l) {
        if (!l.has("license")) {
            return get(l, "expression");
        }
        var license = l.get("license");
        var id = get(license, "id");
        if (id != null) {
            return id;
        }
        return get(license, "name");
    }

    private static String get(JsonNode node, String property) {
        if (node.has(property)) {
            return node.get(property).asText();
        }
        return null;
    }
}
