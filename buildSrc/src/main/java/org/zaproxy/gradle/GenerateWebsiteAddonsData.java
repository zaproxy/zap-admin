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
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public abstract class GenerateWebsiteAddonsData extends DefaultTask {

    private static final DumpSettings SETTINGS =
            DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build();
    private static final Dump DUMP = new Dump(SETTINGS);

    public GenerateWebsiteAddonsData() {
        setGroup("ZAP");
        setDescription("Generates the add-ons data for the website.");
    }

    @OutputFile
    public abstract RegularFileProperty getInto();

    @InputFile
    public abstract RegularFileProperty getZapVersions();

    @Input
    public abstract Property<String> getGeneratedDataComment();

    @Input
    public abstract Property<String> getWebsiteUrl();

    @TaskAction
    public void update() throws Exception {
        File xmlFile = getZapVersions().get().getAsFile();
        if (xmlFile.exists()) {
            List<Map<String, Object>> addOnList = new ArrayList<>();
            ZapXmlConfiguration conf = new ZapXmlConfiguration(xmlFile);
            AddOnCollection aoc = new AddOnCollection(conf, AddOnCollection.Platform.linux);
            for (AddOn addOn : aoc.getAddOns()) {
                Map<String, Object> addOnData = new LinkedHashMap<>();
                addOnData.put("id", addOn.getId());
                addOnData.put("name", addOn.getName());
                addOnData.put("description", addOn.getDescription());
                addOnData.put("author", addOn.getAuthor());
                addOnData.put("status", addOn.getStatus().name());
                addOnData.put("infoUrl", getUrl(addOn.getInfo(), getWebsiteUrl().get()));
                addOnData.put("repoUrl", getUrl(addOn.getRepo(), getWebsiteUrl().get()));
                addOnData.put("downloadUrl", addOn.getUrl().toString());
                addOnData.put("date", conf.getString("addon_" + addOn.getId() + ".date"));
                addOnData.put(
                        "version",
                        convertVersion(conf.getString("addon_" + addOn.getId() + ".version")));
                addOnList.add(addOnData);
            }

            String output = DUMP.dumpToString(addOnList);

            try (BufferedWriter writer =
                    Files.newBufferedWriter(
                            getInto().get().getAsFile().toPath(), Charset.defaultCharset())) {
                writer.write(getGeneratedDataComment().get());
                writer.write("\n---\n");
                writer.write(output);
            }
        } else {
            System.out.print("File not found!");
        }
    }

    private static Object convertVersion(String version) {
        return version.contains(".") ? version : Integer.valueOf(version);
    }

    private static String getUrl(URL url, String websiteUrl) {
        if (url == null) {
            return "";
        }

        String strUrl = url.toString();
        if (strUrl.startsWith(websiteUrl)) {
            return strUrl.substring(websiteUrl.length() - 1);
        }
        return strUrl;
    }
}
