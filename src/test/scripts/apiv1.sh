#!/bin/bash
#
# To use this script the server should be listening at localhost:8080 with an API enabled user
# username: api
# password: abcd
#


#
# Utility for color output
#
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'


#
# Creating an event
#
curl --silent  -X POST http://localhost:8080/api/v1/admin/event/create -H 'Authorization: ApiKey e210468a-1831-4cf2-93d8-8cc093c7bd7d' -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' -d '{
    "title": "Titolo",
    "slug": "titolo",
    "description": [{
        "lang": "de",
        "body": "text description"
    }],
    "location": {
        "fullAddress": "Pollegio 6742 Switzerland",
        "coordinate": {
            "latitude": "45.55",
            "longitude": "9.00"
        }
    },
    "timezone": "Europe/Zurich",
    "startDate": "2018-02-15T15:24:22",
    "endDate": "2018-02-15T16:24:22",
    "websiteUrl": "http://www.amicidelticino.ch/aktivitaten/exklusivbesuch-des-kommandoturms-der-sbb",
    "termsAndConditionsUrl": "http://www.amicidelticino.ch/agb",
    "imageUrl": "https://www.amicidelticino.ch/assets/adt/images/amicidelticino-logo.png",
    "tickets": {
        "freeOfCharge": false,
        "max": 10,
        "currency": "CHF",
        "taxPercentage": 7.7,
        "taxIncludedInPrice": true,
        "paymentMethods": ["CREDIT_CARD","ONSITE","OFFLINE"],
        "categories": [
            {
                "name":"Standard",
                "description": [{
        "lang": "de",
        "body": "text description"
    }],
                "maxTickets": null,
                "accessRestricted": false,
                "price": 10.0,
                "startSellingDate": "2018-02-15T15:24:22",
                "endSellingDate": "2018-02-15T16:24:22"
            }
        ]
    }
}' > /dev/null


#
# Updating the event
#
curl --silent   -X POST  http://localhost:8080/api/v1/admin/event/update/titolo -H 'Authorization: Basic YXBpOmFiY2Q=' -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' -d '{
    "title": "Titolo2",
    "startDate": "2018-01-15T15:24:22"
}' > /dev/null

#
# Getting the event stats
stats=`curl --silent  -X GET http://localhost:8080/api/v1/admin/event/titolo/stats -H 'Authorization: Basic YXBpOmFiY2Q=' -H 'Cache-Control: no-cache' -H 'Content-Type: application/json'`

#
# Check the result
if [[ $stats = *"Titolo2"* && $stats = *"2018-01-15T15:24:22"* ]]; then
    echo -e "[ ${GREEN}OK${NC} ] Event updated"
else
    echo -e "[ ${RED}ERROR${NC} ] Event NOT updated, check that the server is listening on port 8080 and it has the API enabled user, username: 'api' password:'abcd'"
fi


#
# Finally delete the event
curl --silent  -X DELETE http://localhost:8080/api/v1/admin/event/titolo -H 'Authorization: Basic YXBpOmFiY2Q=' -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' > /dev/null


