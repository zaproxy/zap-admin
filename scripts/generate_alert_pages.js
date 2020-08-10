// This is a ZAP standalone script - it will only run in ZAP.
// It generates the alert pages for https://www.zaproxy.org/docs/alerts/
// And it uses some very nasty reflection to get some of the pscan rule details ;)
// The pages were created after starting a ZAP weekly release with the '-addoninstallall' option.

// Change the DIR below to match the local directory containing the alert files
var DIR = "/zap/wrk/zaproxy-website/site/content/docs/alerts/";
var Alert = Java.type('org.parosproxy.paros.core.scanner.Alert');
var Constant = Java.type('org.parosproxy.paros.Constant');
var PluginPassiveScanner = Java.type('org.zaproxy.zap.extension.pscan.PluginPassiveScanner');
var FileWriter = Java.type('java.io.FileWriter');
var PrintWriter = Java.type('java.io.PrintWriter');
var date = (new Date()).toISOString().replace('T', ' ');
var ignoreList = [50000, 50001, 50003];

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

function printAscanRule(plugin) {
	if (ignoreList.indexOf(plugin.getId()) !== -1) {
		return;
	}
	print('Plugin ID: ' + plugin.getId());
	var fw = new FileWriter(DIR + plugin.getId() + ".md");
	var pw = new PrintWriter(fw);
	pw.println('---');
	pw.println('title: "' + plugin.getName() + '"');
	pw.println('alertid: ' + plugin.getId());
	pw.println('alerttype: "Active Scan Rule"');
	pw.println('status: ' + plugin.getStatus());
	pw.println('type: alert');
	pw.println('date: ' + date);
	pw.println('lastmod: ' + date);
	pw.println('---');
	pw.println('### Type: Active Scan');
	pw.println('');
	pw.println('### Risk: ' + Alert.MSG_RISK[plugin.getRisk()]);
	pw.println('');
	pw.println('### Description');
	pw.println('');
	pw.println(plugin.getDescription());
	pw.println('');
	pw.println('### Solution');
	pw.println('');
	pw.println(plugin.getSolution());
	pw.println('');
	var refs = plugin.getReference();
	if (refs && refs.length() > 0) {
		pw.println('### References');
		pw.println('');
		var refsArray = refs.split('\n');
		for (var i = 0; i < refsArray.length; i++) {
			pw.println('* ' + refsArray[i]);
		}
		pw.println('');
	}
	var cweId = plugin.getCweId();
	if (cweId > 0) {
		pw.println('### CWE: [' + plugin.getCweId() + '](https://cwe.mitre.org/data/definitions/' + plugin.getCweId() + '.html)');
		pw.println('');
	}
	var wascId = plugin.getWascId();
	if (wascId > 0) {
		pw.println('### WASC:  ' + wascId);
		pw.println('');
	}

	pw.println('### Code');
	pw.println('');
	var clazz = plugin.getClass().getName();
	var pkgs = clazz.split('.');
	var pkg = pkgs[pkgs.length - 2];
	var url = 'https://github.com/zaproxy/zap-extensions/blob/master/addOns/' + pkg + '/src/main/java/' + pkgs.join('/') + '.java';
	pw.println(' * [' + pkgs.join('/') + '.java' + '](' + url + ')');
	pw.println('');
	pw.println('###### Last updated: ' + date);
	pw.close();
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
	//print ('Failed on ' + obj.getName() + ' ' + methods);
	return defaultVal;
}

function printPscanRule(plugin) {
	if (ignoreList.indexOf(plugin.getPluginId()) !== -1) {
		return;
	}
	print('Plugin ID: ' + plugin.getPluginId());
	var fw = new FileWriter(DIR + plugin.getPluginId() + ".md");
	var pw = new PrintWriter(fw);
	pw.println('---');
	pw.println('title: "' + plugin.getName().replaceAll("\"", "'") + '"');
	pw.println('alertid: ' + plugin.getPluginId());
	pw.println('alerttype: "Passive Scan Rule"');
	pw.println('status: ' + plugin.getStatus());
	pw.println('type: alert');
	pw.println('date: ' + date);
	pw.println('lastmod: ' + date);
	pw.println('---');
	pw.println('### Type: Passive Scan');
	pw.println('');
	pw.println('### Description');
	pw.println(getPrivateMethod(plugin, ['getDescription', 'getDesc', 'getDescriptionMessage'], 'desc', '_Unavailable_'));
	pw.println('');
	pw.println('### Solution');
	pw.println('');
	pw.println(getPrivateMethod(plugin, ['getSolution', 'getSoln', 'getSolutionMessage'], 'soln', '_Unavailable_'));
	pw.println('');
	var refs = getPrivateMethod(plugin, ['getReferences', 'getRefs', 'getReferenceMessage', 'getReferencesMessage'], 'refs', '');
	if (refs.length() > 0) {
		pw.println('### References');
		pw.println('');
		var refsArray = refs.split('\n');
		for (var i = 0; i < refsArray.length; i++) {
			pw.println('* ' + refsArray[i]);
		}
		pw.println('');
	}
	try {
		var cweId = getPrivateMethod(plugin, ['getCweId'], '', '');
		if (cweId.length() > 0 && parseInt(cweId) > 0) {
			pw.println('### CWE: [' + cweId + '](https://cwe.mitre.org/data/definitions/' + cweId + '.html)');
			pw.println('');
		}
	} catch (e) {
	}
	try {
		var wascId = getPrivateMethod(plugin, ['getWascId'], '', '');
		if (wascId.length() > 0 && parseInt(wascId) > 0) {
			pw.println('### WASC:  ' + wascId);
			pw.println('');
		}
	} catch (e) {
	}
	pw.println('### Code');
	pw.println('');
	var clazz = plugin.getClass().getName();
	var pkgs = clazz.split('.');
	var pkg = pkgs[pkgs.length - 2];
	var url = 'https://github.com/zaproxy/zap-extensions/blob/master/addOns/' + pkg + '/src/main/java/' + pkgs.join('/') + '.java';
	pw.println(' * [' + pkgs.join('/') + '.java' + '](' + url + ')');
	pw.println('');
	pw.println('###### Last updated: ' + date);
	pw.close();
}