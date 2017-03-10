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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Command line tool for printing a summary of the tag assets downloaded from GitHub.
 * @author simon
 *
 */
public class CountDownloads {

	// Note 100 is the maximum page size allowed - will need to use paging to get any more
	private static final String RELEASES_URL = 
			"https://api.github.com/repos/zaproxy/zaproxy/releases";

	private static String readUrl(String urlString) throws Exception {
	    BufferedReader reader = null;
	    try {
	        URL url = new URL(urlString);
	        reader = new BufferedReader(new InputStreamReader(url.openStream()));
	        StringBuffer buffer = new StringBuffer();
	        int read;
	        char[] chars = new char[1024];
	        while ((read = reader.read(chars)) != -1) {
	            buffer.append(chars, 0, read); 
	        }
	        return buffer.toString();
	    } finally {
	        if (reader != null) {
	            reader.close();
	        }
	    }
	}
	
	public static void main(String[] args) {
		// Loop through tags, print names
		try {
			String jsonStr = readUrl(RELEASES_URL);
			
			JSONArray json = JSONArray.fromObject(jsonStr);
			
			if (json.size() >= 100) {
				System.out.println("WARNING: 100 tags returned - will need to implement paging to get the rest!");
			}
			
			for (int i=0; i < json.size(); i++) {
				JSONObject tag = json.getJSONObject(i);
				String tagName = tag.getString("tag_name");
				System.out.println("Tag " + tagName);

				JSONArray assets = tag.getJSONArray("assets");
				int total = 0;
				for (int j=0; j < assets.size(); j++) {
					JSONObject asset = assets.getJSONObject(j);
					int count = asset.getInt("download_count");
					total += count;
					System.out.println("\t" + asset.getString("name") + " : " + count);
				}
				System.out.println("\tTotal Downloads: " + total);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
