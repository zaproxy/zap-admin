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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * Command line tool for generating release notes from GitHub issues and tags. For now the variables
 * are hardcoded - at some point they should be made parameters.
 *
 * @author simon
 */
public class GenerateReleaseNotes {

    static enum IssueType {
        dev,
        bug,
        ignore,
        unk
    };

    private static final String REPO = "zaproxy/zaproxy";
    private static final int MILESTONE_NUMBER = 7;

    public static void main(String[] args) throws Exception {

        GHRepository ghRepo = GitHub.connectAnonymously().getRepository(REPO);
        List<GHIssue> issues =
                ghRepo.getIssues(GHIssueState.ALL, ghRepo.getMilestone(MILESTONE_NUMBER));

        Map<Integer, String> devIssuesMap = new HashMap<Integer, String>();
        Map<Integer, String> bugIssuesMap = new HashMap<Integer, String>();
        Map<Integer, String> unkIssuesMap = new HashMap<Integer, String>();
        Map<Integer, String> issueTagsMap = new HashMap<Integer, String>();

        for (GHIssue issue : issues) {
            IssueType issueType = IssueType.unk;
            Collection<GHLabel> labels = issue.getLabels();
            StringBuilder sb = new StringBuilder();
            for (GHLabel label : labels) {
                String tag = label.getName();
                sb.append(tag);
                sb.append(" ");

                if (tag.equalsIgnoreCase("development")
                        || tag.equalsIgnoreCase("enhancement")
                        || tag.equalsIgnoreCase("Type-enhancement")) {
                    issueType = IssueType.dev;
                    // Carry on in case its got another 'overiding' tag
                }
                if (tag.equalsIgnoreCase("bug") || tag.equalsIgnoreCase("Type-defect")) {
                    issueType = IssueType.bug;
                    // Carry on in case its got another 'overiding' tag
                }
                if (tag.equalsIgnoreCase("API Client")
                        || tag.equalsIgnoreCase("Docker")
                        || tag.equalsIgnoreCase("third-party")
                        || tag.equalsIgnoreCase("jenkins")
                        || tag.equalsIgnoreCase("Component-Docs")
                        || tag.equalsIgnoreCase("invalid")
                        || tag.equalsIgnoreCase("duplicate")
                        || tag.equalsIgnoreCase("historic")
                        || tag.equalsIgnoreCase("wontfix")
                        || tag.equalsIgnoreCase("minor")
                        || tag.equalsIgnoreCase("add-on")
                        || tag.equalsIgnoreCase("Type-Other")
                        || tag.equalsIgnoreCase("Type-review")
                        || tag.equalsIgnoreCase("Type-task")
                        || tag.equalsIgnoreCase("competition")
                        || tag.equalsIgnoreCase("InsufficientEvidence")
                        || tag.equalsIgnoreCase("question")
                        || tag.equalsIgnoreCase("weekly")) {
                    issueType = IssueType.ignore;
                    break;
                }
            }
            int number = issue.getNumber();
            issueTagsMap.put(number, sb.toString());

            switch (issueType) {
                case dev:
                    devIssuesMap.put(number, issue.getTitle());
                    break;
                case bug:
                    bugIssuesMap.put(number, issue.getTitle());
                    break;
                case unk:
                    unkIssuesMap.put(number, issue.getTitle());
                    break;
                case ignore:
                    break;
            }
        }

        System.out.println("<H2>Enhancements</H2>");
        System.out.println("<ul>");
        Object[] devIssues = devIssuesMap.keySet().toArray();
        Arrays.sort(devIssues);
        for (Object key : devIssues) {
            System.out.println(
                    "<li><a href=\"https://github.com/zaproxy/zaproxy/issues/"
                            + key
                            + "\">Issue "
                            + key
                            + "</a> : "
                            + devIssuesMap.get(key)
                            + "</li>");
        }
        System.out.println("</ul>");
        System.out.println("");

        System.out.println("<H2>Bug fixes</H2>");
        System.out.println("<ul>");
        Object[] bugIssues = bugIssuesMap.keySet().toArray();
        Arrays.sort(bugIssues);
        for (Object key : bugIssues) {
            System.out.println(
                    "<li><a href=\"https://github.com/zaproxy/zaproxy/issues/"
                            + key
                            + "\">Issue "
                            + key
                            + "</a> : "
                            + bugIssuesMap.get(key)
                            + "</li>");
        }
        System.out.println("</ul>");
        System.out.println("");

        if (unkIssuesMap.size() > 0) {
            System.out.println("Unclassified:");
            System.out.println("");
            Object[] unkIssues = unkIssuesMap.keySet().toArray();
            Arrays.sort(unkIssues);
            for (Object key : unkIssues) {
                System.out.println("Issue " + key + " : " + unkIssuesMap.get(key));
                System.out.println("\tLink: https://github.com/zaproxy/zaproxy/issues/" + key);
                String tags = issueTagsMap.get(key);
                if (tags != null && tags.length() > 0) {
                    System.out.println("\tTags: " + issueTagsMap.get(key));
                }
            }
        }
    }
}
