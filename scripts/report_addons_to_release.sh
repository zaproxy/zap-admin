#!/usr/bin/env bash
# Simple script for raising issues with details of any add-ons ready to be published
# Run from the top level directory, eg:
#	zap-admin/scripts/report_addons_to_release.sh
#
# It assumed all repos are in the cwd and are up to date
# Suitable credentials should be in the ~/.netrc file

cd zap-admin
# The ``` are to escape all of the characters in the issue, otherwise it messes up a bit ;)
echo "\`\`\`" > addons_to_release
./gradlew pendingAddOnReleases >> addons_to_release
echo "\`\`\`" >> addons_to_release

# Extract the password
netrc=`cat ~/.netrc`
words=( $netrc )
pwd=${words[5]}

scripts/raise_issue.py -t "Addons ready to release " -f addons_to_release -a zapbot -p "$pwd"
rm addons_to_release
