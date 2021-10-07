#!/usr/bin/env python

"""
Generates the contents of https://github.com/zaproxy/zaproxy/blob/main/zap/src/main/add-ons.txt

Currently uses a hardcoded version file - expect to change this when its moved into the build.
For now it should be run from the top of the zap-admin repo and the contents copied into the add-ons.txt file.
"""

import xml.etree.ElementTree as ET

versions_file = "ZapVersions-2.11.xml"

# All of the add-ons to be included in the release
addons = ["alertFilters", "automation", "ascanrules", "bruteforce", "commonlib", "diff", "directorylistv1", 
          "domxss", "encoder", "formhandler", "fuzz", "gettingStarted", "graaljs", "graphql", "help", "hud", "importurls", 
          "invoke", "oast", "onlineMenu", "openapi", "pscanrules", "quickstart", "replacer", "reports", "retest", "retire", 
          "reveal", "saverawmessage", "savexmlmessage", "scripts", "selenium", "soap", "spiderAjax", "tips", 
          "webdriverlinux", "webdrivermacos", "webdriverwindows", "websocket", "zest"
          ]

tree = ET.parse(versions_file)
root = tree.getroot()

for ao in addons:
    addon_node = root.find('addon_' + ao)
    print(addon_node.find('url').text + " " + addon_node.find('hash').text.split(":")[1])
