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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

public class HelpGenerator {
	
	private static final File TEMPLATE_DIR = new File(
			HelpGenerator.class.getResource("resources/help").toString().replace("file:", "")); 
	
	private static void recursiveCopy(File srcFile, File destFile, Map<String, String> tokens) throws IOException {
		if (srcFile.isDirectory()) {
			if (!destFile.isDirectory()) {
				if (destFile.mkdirs()) {
					System.out.println("Created " + destFile.getAbsolutePath());
				} else {
					throw new IOException("Failed to create " + destFile.getAbsolutePath());
				}
			}
			// Recurse through children
			for (File child : srcFile.listFiles()) {
				recursiveCopy(child, new File(destFile, child.getName()), tokens);
			}
		} else if (destFile.getName().equals("cake.png")) {
			String icon = tokens.get("@@icon@@");
			if (icon != null && icon.length() > 0) {
				// Copy and rename
				destFile = new File(destFile.getParentFile(), icon);
				try {
					FileUtils.copyFile(srcFile, destFile);
				} catch (IOException e) {
					System.out.println("Failed to copy file to " + destFile.getAbsolutePath());
					e.printStackTrace();
				}
			}
		} else {
			try {
				String fileContents = FileUtils.readFileToString(srcFile);
				
				for (Entry<String, String> entry : tokens.entrySet()) {
					fileContents = fileContents.replaceAll(entry.getKey(), entry.getValue());
				}
				
				FileUtils.write(destFile, fileContents);
				System.out.println("Created file " + destFile.getAbsolutePath());
				
			} catch (IOException e) {
				System.out.println("Failed to create file " + destFile.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	private static void generateHelp (File addon, String name, String icon, String intro) throws IOException {
		if (! addon.isDirectory()) {
			System.out.println(addon.getAbsolutePath() + " is not a directory");
			return;
		}
		File resdir = new File (addon, "resources");
		File helpdir = new File (resdir, "help");
		if (helpdir.isDirectory()) {
			System.out.println(addon.getName() + " appears to already have help, aborting.");
			return;
		}
		if (helpdir.mkdirs()) {
			System.out.println("Created " + helpdir.getAbsolutePath());
		} else {
			System.out.println("Failed to create " + helpdir.getAbsolutePath());
			return;
		}

		// Set up the tokens to replace in the templates
		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("@@name@@", name);
		tokens.put("@@addon@@", addon.getName());
		tokens.put("@@intro@@", intro);
		if (icon == null) {
			tokens.put("@@icon@@", "");
			tokens.put("@@icon-map@@", "");
			tokens.put("@@icon-image@@", "");
		} else {
			tokens.put("@@icon@@", icon);
			tokens.put("@@icon-map@@", "<mapID target=\"" + addon.getName() + "-icon\" url=\"contents/images/" + icon + "\" />");
			tokens.put("@@icon-image@@", "image=\"" + addon.getName() + "-icon\"");
		}
		
		recursiveCopy(TEMPLATE_DIR, helpdir, tokens);
		
		// Move the relevant file(s)
		File f = null;
		try {
			f = new File(addon, "/resources/help/contents/" + addon.getName() + ".html");
			FileUtils.moveFile(
					new File(addon, "/resources/help/contents/addon.html"), f);
		} catch (IOException e) {
			System.out.println("Failed to move file to " + f.getAbsolutePath());
			e.printStackTrace();
		}

	}
	
	public static void main(String[] args) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String dir = null;
		do {
			String status = null;
			do {
				System.out.print("Enter r/b/a for release/beta or alpha quality:\t");
				String q = in.readLine();
				if (q.equalsIgnoreCase("r")) {
					status = "";
				} else if (q.equalsIgnoreCase("b")) {
					status = "_beta";
				} else if (q.equalsIgnoreCase("a")) {
					status = "_alpha";
				}
			} while (status == null);
			System.out.print("Enter add-on package name:\t");
			dir = "../zap-extensions" + status + "/src/org/zaproxy/zap/extension/" + in.readLine();
			
			if (! new File(dir).isDirectory()) {
				System.out.println("dir is not a directory :( " + new File(dir).getAbsolutePath());
				dir = null;
			}
		} while (dir == null);

		System.out.print("Enter the add-on 'friendly' name:\t");
		String name = in.readLine();

		System.out.print("Enter icon name (eg example.png) or just return for no icon:\t");
		String icon = in.readLine();
		if (icon.length() == 0) {
			icon = null;
		}

		System.out.print("Enter a one line summary of the add-on:\t");
		String intro = in.readLine();

		generateHelp(
				new File(dir), 
				name,		// the friendly name of the add-on
				icon, 		// name to use for the icon, or null if none
				intro);		// the intro text
	}

}
