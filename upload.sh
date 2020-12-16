#!/bin/bash --
set -eux
rsync build/libs/feedToMastodon-*.jar .

# shellcheck disable=SC2012
LATEST_JAR=$(ls -1t ./*.jar|head -n 1 |sed -e "s/[\r\n]\+//g")

chmod 0644 "$LATEST_JAR"

scp "$LATEST_JAR" juggler-v3:~/feedToMastodon/feedToMastodon.jar

# /c/Java/zulu14.28.21-ca-jdk14.0.1-win_x64/bin/java -Dfile.encoding=UTF-8 -jar "$LATEST_JAR" -d
