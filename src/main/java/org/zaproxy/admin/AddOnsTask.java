/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2018 The ZAP Development Team
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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOn.Status;
import org.zaproxy.zap.control.ZapAddOnXmlFile;

/** A task for add-ons in zap-extensions repo. */
public abstract class AddOnsTask {

    static {
        NullAppender na = new NullAppender();
        Logger.getRootLogger().addAppender(na);
        Logger.getRootLogger().setLevel(Level.OFF);
    }

    private static final String ZAP_ADD_ON_FILE_NAME = "ZapAddOn.xml";

    protected static Set<AddOnData> getAllAddOns() throws IOException {
        Set<AddOnData> addOns = new TreeSet<>();
        addAddOns(addOns, Paths.get("../zap-extensions/src"));
        addAddOns(addOns, Paths.get("../zap-extensions_beta/src"));
        addAddOns(addOns, Paths.get("../zap-extensions_alpha/src"));
        return addOns;
    }

    private static void addAddOns(final Set<AddOnData> addOns, Path path) throws IOException {
        Files.walkFileTree(
                path,
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (isValidZapAddOnXmlFile(file)) {
                            try (InputStream is =
                                    new BufferedInputStream(Files.newInputStream(file))) {
                                Path addOnDir = file.getParent();
                                String addOnId = addOnDir.getFileName().toString();
                                ZapAddOnXmlFile zapAddOnXmlFile = new ZapAddOnXmlFile(is);
                                addOns.add(
                                        new AddOnData(
                                                addOnDir,
                                                addOnId,
                                                zapAddOnXmlFile.getName(),
                                                zapAddOnXmlFile.getPackageVersion(),
                                                AddOn.Status.valueOf(zapAddOnXmlFile.getStatus()),
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

    protected static class AddOnData implements Comparable<AddOnData> {

        private final Path dir;
        private final String id;
        private final String name;
        private final int version;
        private final AddOn.Status status;
        private final List<String> changes;

        public AddOnData(
                Path dir, String id, String name, int version, Status status, String changes) {
            super();
            this.dir = dir;
            this.id = id;
            this.name = name;
            this.version = version;
            this.status = status;
            this.changes = prepareChanges(changes);
        }

        public Path getDir() {
            return dir;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getVersion() {
            return version;
        }

        public AddOn.Status getStatus() {
            return status;
        }

        public List<String> getChanges() {
            return changes;
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

            return Collator.getInstance(Locale.ENGLISH).compare(id, other.id);
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
