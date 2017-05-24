/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Copyright 2016 The ZAP Development Team
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOn.Status;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.control.ZapAddOnXmlFile;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public class PendingAddOnReleases {

    private static final String ZAP_VERSIONS_FILE_NAME = "ZapVersions-2.6.xml";
    private static final String ZAP_ADD_ON_FILE_NAME = "ZapAddOn.xml";

    static {
        NullAppender na = new NullAppender();
        Logger.getRootLogger().addAppender(na);
        Logger.getRootLogger().setLevel(Level.OFF);
    }

    public static void main(String[] args) throws Exception {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        boolean showChanges = true;

        ZapXmlConfiguration zapVersions = new ZapXmlConfiguration(Paths.get(ZAP_VERSIONS_FILE_NAME).toFile());
        AddOnCollection addOnCollection = new AddOnCollection(zapVersions, AddOnCollection.Platform.daily);

        zapVersions.setExpressionEngine(new XPathExpressionEngine());

        Set<AddOnData> addOns = new TreeSet<>();
        addAddOns(addOns, Paths.get("../zap-extensions/src"), AddOn.Status.release);
        addAddOns(addOns, Paths.get("../zap-extensions_beta/src"), AddOn.Status.beta);
        addAddOns(addOns, Paths.get("../zap-extensions_alpha/src"), AddOn.Status.alpha);
        int totalAddOns = addOns.size();

        Set<AddOnData> unreleasedAddOns = new TreeSet<>();

        for (Iterator<AddOnData> it = addOns.iterator(); it.hasNext();) {
            AddOnData addOnData = it.next();
            AddOn addOn = addOnCollection.getAddOn(addOnData.id);
            if (addOn == null) {
                unreleasedAddOns.add(addOnData);
                it.remove();
            } else if (addOn.getFileVersion() >= addOnData.version) {
                it.remove();
            }
        }

        if (!unreleasedAddOns.isEmpty()) {
            System.out.println("=============================");
            System.out.println("Unreleased add-ons (" + unreleasedAddOns.size() + " of " + totalAddOns + ")");
            System.out.println("=============================");
            for (AddOnData addOn : unreleasedAddOns) {
                System.out.println(addOn.status + "\t" + addOn.name + " v" + addOn.version);
            }
            System.out.println("=============================\n");
        }

        if (!addOns.isEmpty()) {
            System.out.println("=======================================");
            System.out.println("New versions pending release (" + addOns.size() + " of " + totalAddOns + ")");
            System.out.println("=======================================");
            Status currentStatus = null;
            for (AddOnData addOn : addOns) {
                if (currentStatus != addOn.status) {
                    currentStatus = addOn.status;
                    System.out.println(currentStatus);
                }
                LocalDate releaseDate = LocalDate.parse(zapVersions.getString("/addon_" + addOn.id + "/date"));
                System.out.println("  * " + addOn.name + " v" + addOn.version + " (" + Period.between(releaseDate, now) + ")");

                if (showChanges) {
                    for (String change : addOn.changes) {
                        System.out.println("       - " + change);
                    }
                }
            }
            System.out.println("=======================================\n");
        }
    }

    private static void addAddOns(final Set<AddOnData> addOns, Path path, final AddOn.Status status) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isValidZapAddOnXmlFile(file)) {
                    try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                        String addOnId = file.getParent().getFileName().toString();
                        ZapAddOnXmlFile zapAddOnXmlFile = new ZapAddOnXmlFile(is);
                        addOns.add(
                                new AddOnData(
                                        addOnId,
                                        zapAddOnXmlFile.getName(),
                                        zapAddOnXmlFile.getPackageVersion(),
                                        status,
                                        zapAddOnXmlFile.getChanges()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isValidZapAddOnXmlFile(Path file) {
        if (ZAP_ADD_ON_FILE_NAME.equals(file.getFileName().toString())) {
            // Ignore example ZapAddOn.xml file
            return !file.toString().contains("src/org/zaproxy/zap/extension/ZapAddOn.xml");
        }
        return false;
    }

    private static class AddOnData implements Comparable<AddOnData> {

        private final String id;
        private final String name;
        private final int version;
        private final AddOn.Status status;
        private final List<String> changes;

        public AddOnData(String id, String name, int version, Status status, String changes) {
            super();
            this.id = id;
            this.name = name;
            this.version = version;
            this.status = status;
            this.changes = prepareChanges(changes);
        }

        @Override
        public int compareTo(AddOnData other) {
            if (other == null) {
                return 1;
            }

            int result = status.compareTo(other.status);
            if (result != 0) {
                return -result;
            }

            return name.compareTo(other.name);
        }

        private static List<String> prepareChanges(String changes) {
            List<String> preparedChanges = new ArrayList<>(Arrays.asList(changes.split("<br>")));
            for (int i = 0; i < preparedChanges.size(); i++) {
                String string = preparedChanges.get(i).trim();
                if (string.isEmpty()) {
                    preparedChanges.remove(i);
                    i--;
                } else {
                    preparedChanges.set(
                            i,
                            string.replaceAll("^\\t*", "")
                                    .replaceAll("^ *", "")
                                    .replaceAll("^(\\r?\\n)*", "")
                                    .replaceAll("(\\r?\\n)*$", ""));
                }
            }
            return preparedChanges;
        }

    }
}
