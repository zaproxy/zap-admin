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

import java.io.File;

public class HelpReportMissing {

	private static void reportMissingHelp(File repo) {
		File extDir = new File(repo, "src/org/zaproxy/zap/extension");
		if (! extDir.isDirectory()) {
			System.out.println(extDir.getAbsolutePath() + " is not a directory");
			return;
		}
		for (File addon : extDir.listFiles()) {
			if (addon.isDirectory()) {
				File helpdir = new File (addon, "resources/help");
				if (! helpdir.isDirectory()) {
					System.out.println(addon.getName());
				}
			}
		}
		
	}
	
	public static void main(String[] args) {
		System.out.println("Release add-ons");
		System.out.println("===============");
		reportMissingHelp(new File("../zap-extensions"));

		System.out.println();
		System.out.println("Beta add-ons");
		System.out.println("============");
		reportMissingHelp(new File("../zap-extensions_beta"));

		System.out.println();
		System.out.println("Alpha add-ons");
		System.out.println("============");
		reportMissingHelp(new File("../zap-extensions_alpha"));
	}

}
