# Quick Start Launch Pages
This directory contains files containing the HTML displayed by default when 
ZAP launches browsers via the Quick Start add-on.

## Locale support
The files can support multiple locales.
Each section must be separated via the string:
```
<!-- - - - - - - - - %< - - - - - - - - -->
```

The first line of the first section should be:
```
<!-- Locale = Default -->
```

The first line of following sections should contain the relevant locale:
```
 <!-- Locale = fr_FR -->
```
