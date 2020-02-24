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

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.yaml.snakeyaml.Yaml;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public class GenerateWebsiteAddonsData extends DefaultTask {

    private final Property<String> zapVersionFile;
    private final Property<String> outputFile;

    public GenerateWebsiteAddonsData() {
        ObjectFactory objects = getProject().getObjects();
        this.zapVersionFile = objects.property(String.class);
        this.outputFile = objects.property(String.class);

        setGroup("ZAP");
        setDescription("Created the zap addons markdown file for website");
    }

    @Input
    public Property<String> getZapVersionFile() {
        return zapVersionFile;
    }

    @Input
    public Property<String> getOutputFile() {
        return outputFile;
    }

    @TaskAction
    public void update() throws Exception {
        File xmlFile = new File(zapVersionFile.get());
        if (xmlFile.exists()) {
            Map<String, String> addOnData;
            Yaml yaml = new Yaml();
            List<Map<String, String>> addOnList = new ArrayList<>();
            AddOnCollection aoc =
                    new AddOnCollection(
                            new ZapXmlConfiguration(String.valueOf(zapVersionFile)),
                            AddOnCollection.Platform.linux);
            for (AddOn addOn : aoc.getAddOns()) {
                addOnData = new HashMap<>();
                addOnData.put("id", addOn.getId());
                addOnData.put("name", addOn.getName());
                addOnData.put("description", addOn.getDescription());
                addOnData.put("author", addOn.getAuthor());
                addOnData.put("version", addOn.getVersion().toString());
                addOnData.put("file", addOn.getFile().getName());
                addOnData.put("status", addOn.getStatus().name());
                addOnData.put("url", addOn.getUrl().toString());
                addOnData.put("date", "");
                addOnData.put("infoUrl", "");
                addOnData.put("downloadUrl", addOn.getUrl().toString());
                addOnData.put("repoUrl", "");
                addOnData.put("size", String.valueOf(addOn.getSize()));
                addOnList.add(addOnData);
            }

            String output = yaml.dump(addOnList);
            File file = new File(outputFile.get());
            try (BufferedWriter writer =
                    Files.newBufferedWriter(file.toPath(), Charset.defaultCharset())) {
                writer.write(output);
            }
        } else {
            System.out.print("File not found!");
        }
    }
}
