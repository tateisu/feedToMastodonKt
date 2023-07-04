#!/bin/bash --
set -eux

./gradlew shadowJar

cp build/libs/feedToMastodonKt-*-all.jar .

# shellcheck disable=SC2012
LATEST_JAR=$(ls -1t ./*.jar|head -n 1 |sed -e "s/[\r\n]\+//g")

chmod 0644 "$LATEST_JAR" crawl.js
scp "$LATEST_JAR" juggler-v3:/v/feedToMastodon/feedToMastodon.jar
scp crawl.js juggler-v3:/v/feedToMastodon/
