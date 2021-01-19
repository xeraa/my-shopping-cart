#!/usr/bin/env bash
set -x

export OPEN_TELEMETRY_AGENT_VERSION=0.14.0

##########################################################################################
# PARENT DIRECTORY
# code copied from Tomcat's `catalina.sh`
##########################################################################################
# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`


export OPEN_TELEMETRY_AGENT_HOME=$PRGDIR/../.otel
mkdir -p "$OPEN_TELEMETRY_AGENT_HOME"

##########################################################################################
# DOWNLOAD OPEN TELEMETRY AGENT IF NOT FOUND
# code copied from Maven Wrappers's mvnw`
##########################################################################################
export OPEN_TELEMETRY_AGENT_JAR=$OPEN_TELEMETRY_AGENT_HOME/opentelemetry-javaagent-all-$OPEN_TELEMETRY_AGENT_VERSION.jar
if [ -r "$OPEN_TELEMETRY_AGENT_JAR" ]; then
    echo "Found $OPEN_TELEMETRY_AGENT_JAR"
else
    echo "Couldn't find $OPEN_TELEMETRY_AGENT_JAR, downloading it ..."
    jarUrl="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$OPEN_TELEMETRY_AGENT_VERSION/opentelemetry-javaagent-all.jar"

    if command -v wget > /dev/null; then
        wget "$jarUrl" -O "$OPEN_TELEMETRY_AGENT_JAR"
    elif command -v curl > /dev/null; then
        curl -o "$OPEN_TELEMETRY_AGENT_JAR" "$jarUrl"
    else
        echo "FAILURE: OpenTelemetry agent not found and  none of curl and wget found"
        exit 1;
    fi
fi

$PRGDIR/../mvnw -DskipTests package

export OTEL_RESOURCE_ATTRIBUTES=service.name=monitor,service.namespace=com-shoppingcart,service.version=1.0-SNAPSHOT,deployment.environment=staging

java -javaagent:$OPEN_TELEMETRY_AGENT_JAR \
     -Dotel.exporter.otlp.endpoint=localhost:4317 \
     -Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=info \
     -classpath target/classes/ com.mycompany.ecommerce.FrontendMonitor

#      -Dota.exporter.jaeger.endpoint=localhost:14250 \
#      -Dota.exporter.jaeger.service.name=monitor \
#      -Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=info \