#!/bin/sh
# Use following settings:
# -Dconfig.resource=acceptance.conf -Dplay.mailer.port=9025 -Dsentrana.um.server.url=http://localhost:9000 -Dmongodb.um.url=mongodb://localhost:<port from server logs>/test
# to run individual acceptance tests from IDE with this server
SMTP_PORT=9025
#sbt ";project umServiceTestLauncher; launch 9000"
sbt ";project umServiceTestLauncher; launch 9000 -Dplay.mailer.host=localhost -Dplay.mailer.port=$SMTP_PORT -Dplay.mailer.mock=false"
