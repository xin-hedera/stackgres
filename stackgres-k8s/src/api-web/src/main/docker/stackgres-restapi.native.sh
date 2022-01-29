#!/bin/sh

if [ "$DEBUG_RESTAPI" = true ]
then
  set -x
fi
if [ -n "$RESTAPI_LOG_LEVEL" ]
then
  APP_OPTS="$APP_OPTS -Dquarkus.log.level=$RESTAPI_LOG_LEVEL"
fi
if [ "$RESTAPI_SHOW_STACK_TRACES" = true ]
then
  APP_OPTS="$APP_OPTS -Dquarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{4.}] (%t) %s%e%n"
fi
exec /app/stackgres-restapi \
  -Dquarkus.http.host=0.0.0.0 \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  $APP_OPTS
