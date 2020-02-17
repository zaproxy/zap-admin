/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2015 The ZAP Development Team
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
package org.zaproxy.admin;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Command line tool for generating the addons markdown file for website.
 *
 * @author nirojan
 */
public class GenerateAddonsYAML {

    private static final String ZAP_VERSIONS_FILE_NAME = "ZapVersions-2.9.xml";
    private static final String NEXT_LINE = "\n";
    private static final String EMPTY_STRING = "";
    private static final String OUTPUT_DIR = "addons.yml";

    public static void main(String[] args) throws Exception {

        StringBuilder sb = new StringBuilder("---" + NEXT_LINE);
        File xmlFile = new File(ZAP_VERSIONS_FILE_NAME);
        if (xmlFile.exists()) {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(ZAP_VERSIONS_FILE_NAME);

            NodeList list = doc.getElementsByTagName("addon");

            for (int i = 0; i < list.getLength(); i++) {

                Element addonParentElement = (Element) list.item(i);
                NodeList addonIndividualNode =
                        doc.getElementsByTagName("addon_" + addonParentElement.getTextContent());

                if (addonIndividualNode.getLength() != 0) {
                    Element addonInfo = (Element) addonIndividualNode.item(0);

                    String addonName = getAddonDetails("name", addonInfo);
                    String description = getAddonDetails("description", addonInfo);
                    String author = getAddonDetails("author", addonInfo);
                    String version = getAddonDetails("version", addonInfo);
                    String file = getAddonDetails("file", addonInfo);
                    String status = getAddonDetails("status", addonInfo);
                    String url = getAddonDetails("url", addonInfo);
                    String date = getAddonDetails("date", addonInfo);
                    String size = getAddonDetails("size", addonInfo);

                    sb.append("- name: " + addonName + NEXT_LINE)
                            .append("  description: " + description + NEXT_LINE)
                            .append("  author: " + author + NEXT_LINE)
                            .append("  version: " + version + NEXT_LINE)
                            .append("  file: " + file + NEXT_LINE)
                            .append("  status: " + status + NEXT_LINE)
                            .append("  url: " + url + NEXT_LINE)
                            .append("  date: " + date + NEXT_LINE)
                            .append("  link: " + EMPTY_STRING + NEXT_LINE)
                            .append("  size: " + size + NEXT_LINE);

                    sb.append(NEXT_LINE);
                }
            }

            File file = new File(OUTPUT_DIR);
            try (BufferedWriter writer =
                         Files.newBufferedWriter(file.toPath(), Charset.defaultCharset())) {
                writer.write(sb.toString());
            }
        } else {
            System.out.print("File not found!");
        }
    }

    private static String getAddonDetails(String attributeName, Element addonInfo) {
        if (addonInfo.getElementsByTagName(attributeName).item(0) != null) {
            return addonInfo.getElementsByTagName(attributeName).item(0).getTextContent();
        }
        return EMPTY_STRING;
    }
}
