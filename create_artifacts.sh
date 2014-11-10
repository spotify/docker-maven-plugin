#!/bin/bash -ex

# Tar up the staged release, all the pom files, and some build files. We will use these in
# subsequent build steps to perform the actual release.
tar -zcvf target/docker-maven-plugin-staged-release.tar.gz `find . -name nexus-staging && find . -name pom.xml`

# Output build version into file for later Jenkins items
VERSION=$(egrep -o '<version>.*</version>' -m 1 pom.xml | sed 's/<version>\(.*\)<\/version>/\1/')
echo ${VERSION} > target/version

# Output current git commit hash into file for later Jenkins items
git rev-parse HEAD > target/commit_hash
