#!/bin/bash
#
# To use this script the server should be listening at localhost:8080 with an API enabled user
# username: api
# password: abcd
#
API_KEY="e210468a-1831-4cf2-93d8-8cc093c7bd7d"

#
# Utility for color output
#
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

TODAY=`date -I"date"`
EVENT_DATE=`date -d "+10 days" -I"date"`
REQUEST_BODY=`cat create-event.json | sed -e "s/--TODAY--/${TODAY}/g" | sed -e "s/--EVENT_DATE--/${EVENT_DATE}/g"`

#
# Creating an event
#
curl --silent  -X POST http://localhost:8080/api/v1/admin/event/create -H "Authorization: ApiKey $API_KEY" -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' -d "${REQUEST_BODY}" > /dev/null


#
# Updating the event
#
curl --silent   -X POST  http://localhost:8080/api/v1/admin/event/update/titolo -H "Authorization: ApiKey $API_KEY" -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' -d "{
    \"title\": \"Titolo2\",
    \"startDate\": \"${EVENT_DATE}T08:01:00\"
}" > /dev/null

#
# check the event stats
stats=`curl --silent  -X GET http://localhost:8080/api/v1/admin/event/titolo/stats -H "Authorization: ApiKey ${API_KEY}" -H 'Cache-Control: no-cache' -H 'Content-Type: application/json'`

#
# Check the result
if [[ $stats = *"Titolo2"* && $stats = *"${EVENT_DATE}T08:01:00"* ]]; then
    echo -e "[ ${GREEN}OK${NC} ] Event updated"
else
    echo -e "[ ${RED}ERROR${NC} ] Event NOT updated, check that the server is listening on port 8080 and it has the API enabled user, ApiKey ${API_KEY}"
fi


#
# Finally delete the event
curl --silent  -X DELETE http://localhost:8080/api/v1/admin/event/titolo -H "Authorization: ApiKey $API_KEY" -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' > /dev/null


