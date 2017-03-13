/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.zaproxy.admin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Command line tool for generating release notes from GitHub issues and tags.
 * For now the variables are hardcoded - at some point they should be made parameters.
 * @author simon
 *
 */
public class GenerateReleaseNotes {

	static enum IssueType {dev, bug, ignore, unk};

	private static final String ISSUE_BASE_URL = 
			"https://api.github.com/repos/zaproxy/zaproxy/issues?state=closed&per_page=100&since=";

	private static String readUrl(String urlString) throws Exception {
	    BufferedReader reader = null;
	    try {
	        URL url = new URL(urlString);
	        reader = new BufferedReader(new InputStreamReader(url.openStream()));
	        StringBuffer buffer = new StringBuffer();
	        int read;
	        char[] chars = new char[1024];
	        while ((read = reader.read(chars)) != -1)
	            buffer.append(chars, 0, read); 

	        return buffer.toString();
	    } finally {
	        if (reader != null)
	            reader.close();
	    }
	}
	
	public static void main(String[] args) {
		// For now hardcoding variables - should make these parameters at some point ;)
	    String dateSince = "2015-12-05T00:00:00Z";
		try {
			Map<Integer, String> devIssuesMap = new HashMap<Integer, String>();
			Map<Integer, String> bugIssuesMap = new HashMap<Integer, String>();
			Map<Integer, String> unkIssuesMap = new HashMap<Integer, String>();
			Map<Integer, String> issueTagsMap = new HashMap<Integer, String>();

			int page = 0;
			while (true) {
				page ++;
				String issueStr = readUrl(ISSUE_BASE_URL + dateSince + "&page=" + page);
	
				JSONArray json = JSONArray.fromObject(issueStr);
				
				for (int i=0; i < json.size(); i++) {
					IssueType issueType = IssueType.unk;
					JSONObject issue = json.getJSONObject(i);
					JSONArray labels = issue.getJSONArray("labels");
					StringBuilder sb = new StringBuilder();
					for (int j=0; j < labels.size(); j++) {
						String tag = labels.getJSONObject(j).getString("name");
						sb.append(tag);
						sb.append(" ");
						
						if (tag.equalsIgnoreCase("development") || tag.equalsIgnoreCase("enhancement")
								|| tag.equalsIgnoreCase("Type-enhancement")) {
							issueType = IssueType.dev;
							// Carry on in case its got another 'overiding' tag
						}
						if (tag.equalsIgnoreCase("bug") || tag.equalsIgnoreCase("Type-defect")) {
							issueType = IssueType.bug;
							// Carry on in case its got another 'overiding' tag
						}
						if (tag.equalsIgnoreCase("invalid") 
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
					issueTagsMap.put(issue.getInt("number"), sb.toString());
					
					switch (issueType) {
					case dev:
						devIssuesMap.put(issue.getInt("number"), issue.getString("title"));
						break;
					case bug:
						bugIssuesMap.put(issue.getInt("number"), issue.getString("title"));
						break;
					case unk:
						unkIssuesMap.put(issue.getInt("number"), issue.getString("title"));
						break;
					case ignore:
						break;
					}
					
				}
				if (json.size() < 100) {
					break;
				}
			}
			
			System.out.println("<H2>Enhancements:</H2>");
			System.out.println("<ul>");
			Object[] devIssues = devIssuesMap.keySet().toArray();
			Arrays.sort(devIssues);
			for (Object key : devIssues) {
				System.out.println("<li>Issue " + key + " : " + devIssuesMap.get(key) + "</li>");
				
			}
			System.out.println("</ul>");
			System.out.println("");

			System.out.println("<H2>Bug fixes:</H2>");
			System.out.println("<ul>");
			Object[] bugIssues = bugIssuesMap.keySet().toArray();
			Arrays.sort(bugIssues);
			for (Object key : bugIssues) {
				System.out.println("<li>Issue " + key + " : " + bugIssuesMap.get(key) + "</li>");
				
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

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

}
