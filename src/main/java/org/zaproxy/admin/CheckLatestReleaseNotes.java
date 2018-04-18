/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2017 The ZAP Development Team
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Command line tool for checking the latest release notes dont contain issues in previous ones.
 * 
 * @author simon
 *
 */
public class CheckLatestReleaseNotes {

    private static final String RELEASE_NOTES_PATH = "../zap-core-help/src/help/zaphelp/contents/releases";

    private static Set<Integer> getIssues(File f) throws IOException {
        Set<Integer> set = new HashSet<Integer>();
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith("<li>Issue ")) {
                    String[] split = line.split(" ");
                    set.add(Integer.parseInt(split[1]));
                }
            }
        }
        return set;
    }

    public static void main(String[] args) throws IOException {
        TreeMap<String, Set<Integer>> map = new TreeMap<String, Set<Integer>>();
        File relNotesDir = new File(RELEASE_NOTES_PATH);
        if (!relNotesDir.exists()) {
            System.out.println("No such directory : " + relNotesDir.getAbsolutePath());
            return;
        }
        File[] relNotes = relNotesDir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".html") && !name.startsWith("releases");
            }
        });

        for (File relNote : relNotes) {
            map.put(relNote.getName(), getIssues(relNote));
        }

        Set<Integer> latest = null;
        for (String key : map.descendingKeySet()) {
            if (latest == null) {
                latest = map.get(key);
            } else {
                for (Integer issue : map.get(key)) {
                    if (latest.contains(issue)) {
                        System.out.println("Issue : " + issue + " was included in " + key);
                    }
                }
            }
        }
    }
}
