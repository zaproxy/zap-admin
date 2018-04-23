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

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOn.Status;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

public class PendingAddOnReleases extends AddOnsTask {

    private static final String ZAP_VERSIONS_FILE_NAME = "ZapVersions-2.7.xml";

    public static void main(String[] args) throws Exception {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        boolean showChanges = true;

        ZapXmlConfiguration zapVersions =
                new ZapXmlConfiguration(Paths.get(ZAP_VERSIONS_FILE_NAME).toFile());
        AddOnCollection addOnCollection =
                new AddOnCollection(zapVersions, AddOnCollection.Platform.daily);

        zapVersions.setExpressionEngine(new XPathExpressionEngine());

        Set<AddOnData> addOns = getAllAddOns();
        int totalAddOns = addOns.size();

        Set<AddOnData> unreleasedAddOns = new TreeSet<>();
        Set<AddOnData> unchangedAddOns = new TreeSet<>();

        for (Iterator<AddOnData> it = addOns.iterator(); it.hasNext(); ) {
            AddOnData addOnData = it.next();
            AddOn addOn = addOnCollection.getAddOn(addOnData.getId());
            if (addOn == null) {
                unreleasedAddOns.add(addOnData);
                it.remove();
            } else if (addOn.getFileVersion() >= addOnData.getVersion()) {
                it.remove();
            } else if (addOnData.getChanges().isEmpty()) {
                unchangedAddOns.add(addOnData);
                it.remove();
            }
        }

        if (!unreleasedAddOns.isEmpty()) {
            System.out.println("=============================");
            System.out.println(
                    "Unreleased add-ons (" + unreleasedAddOns.size() + " of " + totalAddOns + ")");
            System.out.println("=============================");
            for (AddOnData addOn : unreleasedAddOns) {
                System.out.println(
                        addOn.getStatus() + "\t" + addOn.getName() + " v" + addOn.getVersion());
            }
            System.out.println("=============================\n");
        }

        if (!addOns.isEmpty()) {
            System.out.println("=======================================");
            System.out.println(
                    "New versions pending release (" + addOns.size() + " of " + totalAddOns + ")");
            System.out.println("=======================================");
            Status currentStatus = null;
            for (AddOnData addOn : addOns) {
                if (currentStatus != addOn.getStatus()) {
                    currentStatus = addOn.getStatus();
                    System.out.println(currentStatus);
                }
                LocalDate releaseDate =
                        LocalDate.parse(zapVersions.getString("/addon_" + addOn.getId() + "/date"));
                System.out.println(
                        "  * "
                                + addOn.getName()
                                + " v"
                                + addOn.getVersion()
                                + " ("
                                + Period.between(releaseDate, now)
                                + ")");

                if (showChanges) {
                    for (String change : addOn.getChanges()) {
                        System.out.println("       - " + change);
                    }
                }
            }
            System.out.println("=======================================\n");
        }

        if (!unchangedAddOns.isEmpty()) {
            System.out.println("=============================");
            System.out.println(
                    "Unchanged add-ons (" + unchangedAddOns.size() + " of " + totalAddOns + ")");
            System.out.println("=============================");
            for (AddOnData addOn : unchangedAddOns) {
                System.out.println(
                        addOn.getStatus() + "\t" + addOn.getName() + " v" + addOn.getVersion());
            }
            System.out.println("=============================\n");
        }
    }
}
