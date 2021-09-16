// This is a ZAP standalone script - it will only run in ZAP.
// It generates the data for the events page at https://www.zaproxy.org/docs/internal-events/
// The pages were created after starting a ZAP weekly release with the '-addoninstallall' option.

// Change the FILE below to match the local alerts data file
var FILE = "/zap/wrk/zaproxy-website/site/data/events.yaml";

var FileWriter = Java.type('java.io.FileWriter');
var PrintWriter = Java.type('java.io.PrintWriter');
var ZAP = Java.type('org.zaproxy.zap.ZAP');

var fw = new FileWriter(FILE);
var pw = new PrintWriter(fw);

pw.println ("# The events raised in ZAP");
pw.println ("---");

var publishers = ZAP.eventBus.getPublisherNames().toArray();
for (var i=0; i < publishers.length; i++) {
	var events = ZAP.eventBus.getEventTypesForPublisher(publishers[i]).toArray();
	for (var j=0; j < events.length; j++) {
		// Assume the core to start with
		var publisherJava = publishers[i].replaceAll("\\.", "\\/") + ".java";
		var link = "https://github.com/zaproxy/zaproxy/blob/main/zap/src/main/java/" + publisherJava;
		if (publishers[i].startsWith("org.zaproxy.addon") || 
			(publishers[i].startsWith("org.zaproxy.zap.extension") && ! publishers[i].startsWith("org.zaproxy.zap.extension.alert"))) {
			var pkg = publishers[i].split(".")[4];
			if (publishers[i].startsWith("org.zaproxy.addon")) {
				pkg = publishers[i].split(".")[3];
			}
			link = "https://github.com/zaproxy/zap-extensions/blob/main/addOns/" + pkg + "/src/main/java/" + publisherJava;
		}
		
		pw.println();
		pw.println("- publisher: " + publishers[i]);
		pw.println("  link: " + link);
		pw.println("  event: " + events[j]);
	}
}
pw.close();
