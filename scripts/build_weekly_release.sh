#!/usr/bin/env bash
# Simple script for generating the ZAP weekly release from the command line
# You can run it in a clean directory and then delete it after moving the release file
# Or you can keep the ZAP dirs and they will be updated when you rerun the script

# Pull the repos (if they havnt been pulled already)
git clone --depth 1 https://github.com/zaproxy/zaproxy.git
git clone --depth 1 https://github.com/zaproxy/zap-extensions.git
git clone --branch beta --depth 1 https://github.com/zaproxy/zap-extensions.git zap-extensions_beta
git clone --branch alpha --depth 1 https://github.com/zaproxy/zap-extensions.git zap-extensions_alpha
git clone --depth 1 https://github.com/zaproxy/zap-core-help.git

# Get latest changes (if the repos already exist)
cd zaproxy
git pull --depth 1
cd ..
cd zap-extensions
git pull --depth 1
cd ..
cd zap-extensions_beta
git pull --depth 1
cd ..
cd zap-extensions_alpha
git pull --depth 1
cd ..
cd zap-core-help
git pull --depth 1
cd ..

# Clean files so they are always downloaded
cd zap-extensions
# TODO add the clean-files target to the build file
#ant -f build/build.xml clean-files
cd ..
cd zap-extensions_beta
ant -f build/build.xml clean-files
cd ..
cd zap-extensions_alpha
ant -f build/build.xml clean-files
cd ..

# build
cd zaproxy
ant -f build/build.xml deploy-weekly-addons
ant -f build/build.xml day-stamped-release
