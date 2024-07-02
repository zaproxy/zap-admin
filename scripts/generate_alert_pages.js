// This is a ZAP standalone script - it will only run in ZAP.
// It generates the alert pages for https://www.zaproxy.org/docs/alerts/
// And it uses some very nasty reflection to get some of the pscan rule details ;)
// The pages were created after starting a ZAP weekly release with the '-addoninstallall' option.

// Change the DIR below to match the local directory containing the alert files
var ROOT = "/zap/wrk/zaproxy-website/site/";
var DIR = ROOT + "/content/docs/alerts/";
var AT_FILE = ROOT + "/data/alerttags.yml";
var Alert = Java.type('org.parosproxy.paros.core.scanner.Alert');
var ArrayList = Java.type('java.util.ArrayList');
var Constant = Java.type('org.parosproxy.paros.Constant');
var HttpMessage = Java.type('org.parosproxy.paros.network.HttpMessage');
var PluginPassiveScanner = Java.type('org.zaproxy.zap.extension.pscan.PluginPassiveScanner');
var PassiveScanData = Java.type('org.zaproxy.zap.extension.pscan.PassiveScanData');
var URI = Java.type('org.apache.commons.httpclient.URI');
var FileWriter = Java.type('java.io.FileWriter');
var PrintWriter = Java.type('java.io.PrintWriter');
var Tech = Java.type('org.zaproxy.zap.model.Tech');
var TechSet = Java.type('org.zaproxy.zap.model.TechSet');
var TreeSet = Java.type('java.util.TreeSet');
var ignoreList = [50000, 50001, 50003, 60000, 60001, 60100, 60101];
var codeMap = {
	40036: 'https://github.com/SasanLabs/owasp-zap-jwt-addon/blob/master/src/main/java/org/zaproxy/zap/extension/jwt/JWTActiveScanRule.java',
	40041: 'https://github.com/SasanLabs/owasp-zap-fileupload-addon/blob/main/src/main/java/org/sasanlabs/fileupload/FileUploadScanRule.java',
	}

var allAlertTags = {}
var emptyTech = new TechSet;
var allTech = Tech.getAll().toArray();

var WebSocketPassiveScript = Java.type('org.zaproxy.zap.extension.websocket.pscan.scripts.WebSocketPassiveScript');

extScript = control.getExtensionLoader().getExtension(org.zaproxy.zap.extension.script.ExtensionScript.NAME);

pscanWs = extScript.getTemplates(extScript.getScriptType("websocketpassive"));

for (var i = 0; i < pscanWs.length; i++) {
	try {
		printWsPscanRule(extScript.getInterface(pscanWs[i], WebSocketPassiveScript.class),
	    	'https://github.com/zaproxy/zap-extensions/blob/main/addOns/websocket/' +
	    	'src/main/zapHomeFiles/scripts/templates/websocketpassive/' + encodeURIComponent(pscanWs[i].getName()));
	} catch (e) {
		print(e);
	}
}

extAscan = control.getExtensionLoader().getExtension(org.zaproxy.zap.extension.ascan.ExtensionActiveScan.NAME);

plugins = extAscan.getPolicyManager().getDefaultScanPolicy().getPluginFactory().getAllPlugin().toArray();

for (var i = 0; i < plugins.length; i++) {
	try {
		printAscanRule(plugins[i]);
	} catch (e) {
		print(e);
	}
}

// Have to use reflection as constructor is not public in 2.12.0
psdCons = PassiveScanData.class.getDeclaredConstructors()[0];
psdCons.setAccessible(true);
passiveScanData = psdCons.newInstance(new HttpMessage(new URI("https://www.example.com", true)));

extPscan = control.getExtensionLoader().getExtension(org.zaproxy.zap.extension.pscan.ExtensionPassiveScan.NAME);

plugins = extPscan.getPluginPassiveScanners().toArray();

for (var i = 0; i < plugins.length; i++) {
	try {
		printPscanRule(plugins[i]);
	} catch (e) {
		print(e);
	}
}

// Extensions can now declare example alerts
for (var i = 0; i < control.getExtensionLoader().getExtensionCount(); i++) {
	try {
		var ext = control.getExtensionLoader().getExtension(i);
		var examples = ext.getExampleAlerts();
		// Extension level examples can have different IDs, just to complicate matters
		var exList = new ArrayList();
		var lastId = -1;
		for (var y=0; y < examples.size(); y++) {
			var ex = examples.get(y);
			if (exList.size() > 0 && ex.getPluginId() !== lastId) {
				// Must have some examples
				printAlerts(exList, ext.getName(), "Tool", ext.getAddOn().getStatus(), ext.getClass().getName(), null, null);
				exList.clear();
			}
			exList.add(ex);
			lastId = ex.getPluginId();
		}
		if (exList.size() > 0) {
			// Include the last set
			printAlerts(exList, ext.getName(), "Tool", ext.getAddOn().getStatus(), ext.getClass().getName(), null, null, getHelp(ext));
		}
	} catch (e) {
		if (e.toString().indexOf('is not a function') > 0) {
			// This one doesnt ;)
		} else {
			print("Failed accessing extension examples: " + e);
		}
	}
}

var extClient = control.getExtensionLoader().getExtension(org.zaproxy.addon.client.ExtensionClientIntegration.NAME);

if (extClient != null) {
	var pscanCl = extClient.getPassiveScanController().getAllScanRules();

	for (var i = 0; i < pscanCl.length; i++) {
		var plugin = pscanCl.get(i);
		var examples = getPrivateMethod(plugin, ['getExampleAlerts'], '', null);
		if (examples != null && examples.length > 0) {
			printAlerts(examples, plugin.getName(), "Client Passive", "alpha", plugin.getClass().getName(), null, null, getHelp(plugin));
		}
	}
}

// Dump out the alert tags
var fw = new FileWriter(AT_FILE);
var pw = new PrintWriter(fw);
var sortedKeys = [];
for (var key in allAlertTags) {
	sortedKeys[sortedKeys.length] = key;
}
sortedKeys.sort();
for (var i in sortedKeys) {
	var key = sortedKeys[i];
	pw.println(key + ':');
	pw.println('  link: ' + allAlertTags[key]);
	pw.println('');
}
pw.close();

function quoteText(txt) {
  return '"' + txt
    .replace(/[\\]/g, '\\\\')
    .replace(/[\"]/g, '\\\"')
    .replace(/[\b]/g, '\\b')
    .replace(/[\f]/g, '\\f')
    .replace(/[\n]/g, ' ')
    .replace(/[\r]/g, '')
    .replace(/[\t]/g, '\\t') + '"';
}

function printAlerts(alerts, name, type, status, clazz, scripturl, tech, help) {
	var pluginId = alerts[0].getPluginId();
	if (ignoreList.indexOf(pluginId) !== -1) {
		print('Plugin ID: ' + pluginId + ' - ignored');
		return;
	}
	var pkgs = clazz.split('.');
	// Works if package hierarchy is org.zaproxy.addon.<package>
	var pkg = pkgs[3];
	if (clazz.indexOf('.extension.') > 0) {
		// Package hierarchy is actually org.zaproxy.zap.extension.<package>
		pkg = pkgs[4];
	}
	var codeurl = 'https://github.com/zaproxy/zap-extensions/blob/main/addOns/' + pkg + '/src/main/java/' + pkgs.join('/') + '.java';
	if (pluginId in codeMap) {
		codeurl = codeMap[pluginId]
	}
     if (scripturl != null) {
          codeurl = scripturl;
     }
     var linktext;
     if (codeurl.indexOf('/main/java/') !== -1) {
         linktext = codeurl.split('/main/java/')[1];
     } else if (codeurl.indexOf('/main/zapHomeFiles/') !== -1) {
         linktext = codeurl.split('/main/zapHomeFiles/')[1];
     } else if (codeurl.indexOf('/main/') !== -1) {
         linktext = codeurl.split('/main/')[1];
     } else {
         linktext = codeurl;
     }
     linktext = linktext.replaceAll('\%20', ' ');

	if (alerts.length > 1) {
		print('Plugin ID: ' + pluginId);
		var fw = new FileWriter(DIR + pluginId + ".md");
		var pw = new PrintWriter(fw);
		pw.println('---');
		pw.println('title: ' + quoteText(name));
		pw.println('alertid: ' + pluginId);
		pw.println('alertindex: ' + pluginId * 100);
		pw.println('alerttype: "' + type + '"');
		pw.println('status: ' + status);
		pw.println('type: alertset');
		pw.println('alerts:');
	     for (var a=0; a < alerts.length; a++) {
			pw.println('   ' + alerts[a].getPluginId() + "-" + (a+1) + ':');
			pw.println('      alertid: ' + alerts[a].getPluginId() + "-" + (a+1));
			pw.println('      name: ' + quoteText(alerts[a].getName()));
		}
		pw.println('code: ' + codeurl);
		pw.println('linktext: ' + quoteText(linktext));
		pw.println('---');
		pw.close();
	}

     for (var a=0; a < alerts.length; a++) {
		alertindex = alerts[a].getPluginId() * 100;
		if (alerts.length > 1) {
			pluginId = alerts[a].getPluginId() + "-" + (a+1);
			alertindex += a + 1;
		}
		print('Plugin ID: ' + pluginId);
		var fw = new FileWriter(DIR + pluginId + ".md");
		var pw = new PrintWriter(fw);
		var alert = alerts[a];
		pw.println('---');
		pw.println('title: ' + quoteText(alert.getName()));
		pw.println('alertid: ' + pluginId);
		pw.println('alertindex: ' + alertindex);
		pw.println('alerttype: "' + type + '"');
		pw.println('alertcount: ' + alerts.length);
		pw.println('status: ' + status);
		pw.println('type: alert');
		if (alert.getRisk() >= 0) {
			pw.println('risk: ' + Alert.MSG_RISK[alert.getRisk()]);
		}
		pw.println('solution: ' + quoteText(alert.getSolution()));
		var refs = alert.getReference();
		if (refs && refs.length() > 0) {
			pw.println('references:');
			var refsArray = refs.split('\n');
			for (var i = 0; i < refsArray.length; i++) {
				if (refsArray[i].length > 0) {
					pw.println('   - ' + refsArray[i]);
				}
			}
		}
		pw.println('other: ' + quoteText(alert.getOtherInfo()));
		var cweId = alert.getCweId();
		if (cweId > 0) {
			pw.println('cwe: ' + cweId);
		}
		var wascId = alert.getWascId();
		if (wascId > 0) {
			pw.println('wasc: ' + wascId);
		}
		var tags = alert.getTags();
		if (tech) {
			pw.println('techtags: ');
			for (var tag_i in tech) {
				var tag = tech[tag_i];
				pw.println('  - ' + tag);
			}
		}
		if (tags) {
			pw.println('alerttags: ');
			var sorted_tags = (new TreeSet(tags.keySet())).toArray();
			for (var tag_i in sorted_tags) {
				var tag = sorted_tags[tag_i];
				pw.println('  - ' + tag);
				allAlertTags[tag] = tags[tag]
			}
		}
		pw.println('code: ' + codeurl);
		pw.println('linktext: ' + quoteText(linktext));
		if (help) {
			pw.println('help: ' + help);
		}
		pw.println('---');
		pw.println(alert.getDescription());
		pw.close();
	}

}

function getHelp(obj) {
    try {
        return obj.getHelpLink();
    } catch (e) {
        return null;
    }
}

function printAscanRule(plugin) {
	var examples = getPrivateMethod(plugin, ['getExampleAlerts'], '', null);
	if (examples == null || examples.length == 0) {
		var alert = new Alert(plugin.getId());
		alert.setName(plugin.getName());
		alert.setRisk(plugin.getRisk());
		alert.setDescription(plugin.getDescription());
		alert.setSolution(plugin.getSolution());
		alert.setReference(plugin.getReference());
		alert.setOtherInfo(getPrivateMethod(plugin, ['getOther', 'getOtherInfo'], 'other', ''));
		alert.setCweId(plugin.getCweId());
		alert.setWascId(plugin.getWascId());
		alert.setTags(plugin.getAlertTags());
		
		examples = new ArrayList();
		examples.add(alert);
	}
	
	var tech;
	if (! plugin.targets(emptyTech)) {
		tech = [ ];
		for (var i = 0; i < allTech.length; i++) {
			var t = allTech[i];
			// Ignore top level techs - those without dots in their name
			if (t.toString().indexOf('\.') > 0 && plugin.targets(new TechSet([t]))) {
				tech.push(t.toString().replaceAll(' ', '_'));
			}
		}
	}

	var type = "Active"
	var clazz = plugin.getClass().getName()
	var scripturl = null
	if (clazz.indexOf("ActiveScriptScanRule") > 0) {
		type = "Script Active"
		try {
			scripturl = plugin.getCodeLink();
		} catch (e) {
			// Ignore
		}
	}
	
	printAlerts(examples, plugin.getName(), type, plugin.getStatus(), clazz, scripturl, tech, getHelp(plugin));
}

function getPrivateMethod(obj, methods, key, defaultVal) {
	for (var i = 0; i < methods.length; i++) {
		try {
			var method = obj.getClass().getDeclaredMethod(methods[i]);
			method.setAccessible(true);
			return method.invoke(obj);
		} catch (e) {
		}
	}
	if (key.length() > 0) {
		try {
			var f = obj.getClass().getDeclaredField("MESSAGE_PREFIX");
			f.setAccessible(true);
			var fullkey = f.get(obj) + key;
			if (Constant.messages.containsKey(fullkey)) {
				//print(' *** ' + Constant.messages.getString(f.get(obj) + key));
				return Constant.messages.getString(f.get(obj) + key);
			}
		} catch (e) {
		}
	}
	print ('  Failed on ' + obj.getName() + ' ' + methods);
	return defaultVal;
}

function printPscanRule(plugin) {
	plugin.setHelper(passiveScanData);

	try {
		var examples = plugin.getExampleAlerts()
	} catch (e) {
		var examples = getPrivateMethod(plugin, ['getExampleAlerts'], '', null);
	}

	if (examples == null || examples.length == 0) {
		var alert = new Alert(plugin.getPluginId());
		alert.setName(plugin.getName());
		alert.setRisk(getPrivateMethod(plugin, ['getRisk'], '', -1));
		alert.setDescription(getPrivateMethod(plugin, ['getDescription', 'getDesc', 'getDescriptionMessage'], 'desc', '_Unavailable_'));
		alert.setSolution(getPrivateMethod(plugin, ['getSolution', 'getSoln', 'getSolutionMessage'], 'soln', '_Unavailable_'));
		alert.setReference(getPrivateMethod(plugin, ['getReferences', 'getReference', 'getRefs', 'getReferenceMessage', 'getReferencesMessage'], 'refs', ''));
		alert.setOtherInfo(getPrivateMethod(plugin, ['getOther', 'getOtherInfo'], 'other', ''));
		alert.setCweId(getPrivateMethod(plugin, ['getCweId'], '', 0));
		alert.setWascId(getPrivateMethod(plugin, ['getWascId'], '', 0));
		alert.setTags(getPrivateMethod(plugin, ['getAlertTags'], '', null));
		
		examples = new ArrayList();
		examples.add(alert);
	}

	var type = "Passive"
	var clazz = plugin.getClass().getName()
	var scripturl = null
	if (clazz.indexOf("PassiveScriptScanRule") > 0) {
		type = "Script Passive"
		try {
			scripturl = plugin.getCodeLink();
		} catch (e) {
			// Ignore
		}
	}

	printAlerts(examples, plugin.getName(), type, plugin.getStatus(), clazz, scripturl, null, getHelp(plugin));
}

function printWsPscanRule(plugin, scriptUrl) {
	var examples = getPrivateMethod(plugin, ['getExampleAlerts'], '', null);

	if (examples != null && examples.length > 0) {
         printAlerts(examples, plugin.getName(), "WebSocket Passive", "release", plugin.getClass().getName(), scriptUrl, null, getHelp(plugin));
	}
}
