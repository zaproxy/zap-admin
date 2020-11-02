// This is a ZAP standalone script - it will only run in ZAP.
// It generates the alert pages for https://www.zaproxy.org/docs/alerts/
// And it uses some very nasty reflection to get some of the pscan rule details ;)
// The pages were created after starting a ZAP weekly release with the '-addoninstallall' option.

// Change the DIR below to match the local directory containing the alert files
var DIR = "/zap/wrk/zaproxy-website/site/content/docs/alerts/";
var Alert = Java.type('org.parosproxy.paros.core.scanner.Alert');
var ArrayList = Java.type('java.util.ArrayList');
var Constant = Java.type('org.parosproxy.paros.Constant');
var PluginPassiveScanner = Java.type('org.zaproxy.zap.extension.pscan.PluginPassiveScanner');
var FileWriter = Java.type('java.io.FileWriter');
var PrintWriter = Java.type('java.io.PrintWriter');
var date = (new Date()).toISOString().replace('T', ' ');
var ignoreList = [50000, 50001, 50003, 60000, 60001, 60100, 60101];
var codeMap = {40036: 'https://github.com/SasanLabs/owasp-zap-jwt-addon/blob/master/src/main/java/org/zaproxy/zap/extension/jwt/JWTActiveScanRule.java'}

extAscan = org.parosproxy.paros.control.Control.getSingleton().
	getExtensionLoader().getExtension(
		org.zaproxy.zap.extension.ascan.ExtensionActiveScan.NAME);

plugins = extAscan.getPolicyManager().getDefaultScanPolicy().getPluginFactory().getAllPlugin().toArray();

for (var i = 0; i < plugins.length; i++) {
	try {
		printAscanRule(plugins[i]);
	} catch (e) {
		print(e);
	}
}

extPscan = org.parosproxy.paros.control.Control.getSingleton().
	getExtensionLoader().getExtension(
		org.zaproxy.zap.extension.pscan.ExtensionPassiveScan.NAME);

plugins = extPscan.getPluginPassiveScanners().toArray();

for (var i = 0; i < plugins.length; i++) {
	try {
		printPscanRule(plugins[i]);
	} catch (e) {
		print(e);
	}
}

print("Date: " + date);

function printAlerts(alerts, name, type, status, clazz) {
	var pluginId = alerts[0].getPluginId();
	if (ignoreList.indexOf(pluginId) !== -1) {
		print('Plugin ID: ' + pluginId + ' - ignored');
		return;
	}
	var pkgs = clazz.split('.');
	var pkg = pkgs[pkgs.length - 2];
	var codeurl = 'https://github.com/zaproxy/zap-extensions/blob/master/addOns/' + pkg + '/src/main/java/' + pkgs.join('/') + '.java';
	if (pluginId in codeMap) {
		codeurl = codeMap[pluginId]
	}

	if (alerts.length > 1) {
		print('Plugin ID: ' + pluginId);
		var fw = new FileWriter(DIR + pluginId + ".md");
		var pw = new PrintWriter(fw);
		pw.println('---');
		pw.println('title: "' + name.replaceAll("\"", "'") + '"');
		pw.println('alertid: ' + pluginId);
		pw.println('alertindex: ' + pluginId * 100);
		pw.println('alerttype: "' + type + '"');
		pw.println('status: ' + status);
		pw.println('type: alertset');
		pw.println('alerts:');
	     for (var a=0; a < alerts.length; a++) {
			pw.println('   ' + alerts[a].getPluginId() + "-" + (a+1) + ':');
			pw.println('      alertid: ' + alerts[a].getPluginId() + "-" + (a+1));
			pw.println('      name: ' + alerts[a].getName());
		}
		pw.println('code: ' + codeurl);
		pw.println('date: ' + date);
		pw.println('lastmod: ' + date);
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
		pw.println('title: "' + alert.getName().replaceAll("\"", "'") + '"');
		pw.println('alertid: ' + pluginId);
		pw.println('alertindex: ' + alertindex);
		pw.println('alerttype: "' + type + '"');
		pw.println('alertcount: ' + alerts.length);
		pw.println('status: ' + status);
		pw.println('type: alert');
		if (alert.getRisk() >= 0) {
			pw.println('risk: ' + Alert.MSG_RISK[alert.getRisk()]);
		}
		pw.println('solution: "' + alert.getSolution().replaceAll("\"", "'") + '"');
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
		var cweId = alert.getCweId();
		if (cweId > 0) {
			pw.println('cwe: ' + cweId);
		}
		var wascId = alert.getWascId();
		if (wascId > 0) {
			pw.println('wasc: ' + wascId);
		}
		pw.println('code: ' + codeurl);
		pw.println('date: ' + date);
		pw.println('lastmod: ' + date);
		pw.println('---');
		pw.println(alert.getDescription());
		pw.close();
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
		alert.setCweId(plugin.getCweId());
		alert.setWascId(plugin.getWascId());
		
		examples = new ArrayList();
		examples.add(alert);
	}
	
	printAlerts(examples, plugin.getName(), "Active Scan Rule", plugin.getStatus(), plugin.getClass().getName());
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
	var examples = getPrivateMethod(plugin, ['getExampleAlerts'], '', null);

	if (examples == null || examples.length == 0) {
		var alert = new Alert(plugin.getPluginId());
		alert.setName(plugin.getName());
		alert.setRisk(getPrivateMethod(plugin, ['getRisk'], '', -1));
		alert.setDescription(getPrivateMethod(plugin, ['getDescription', 'getDesc', 'getDescriptionMessage'], 'desc', '_Unavailable_'));
		alert.setSolution(getPrivateMethod(plugin, ['getSolution', 'getSoln', 'getSolutionMessage'], 'soln', '_Unavailable_'));
		alert.setReference(getPrivateMethod(plugin, ['getReferences', 'getReference', 'getRefs', 'getReferenceMessage', 'getReferencesMessage'], 'refs', ''));
		alert.setCweId(getPrivateMethod(plugin, ['getCweId'], '', 0));
		alert.setWascId(getPrivateMethod(plugin, ['getWascId'], '', 0));
		
		examples = new ArrayList();
		examples.add(alert);
	}

	printAlerts(examples, plugin.getName(), "Passive Scan Rule", plugin.getStatus(), plugin.getClass().getName());
}