/**
 *
 *
 */
function getScriptMetadata() {
    return {
        id: 'mailchimp', //
        displayName: 'MailChimp integration',
        version: 2, //
        async: true,
        events: [
            'RESERVATION_CONFIRMED', //fired on reservation confirmation. No results expected.
            'TICKET_ASSIGNED', //fired on ticket assignment. No results expected.
            'WAITING_QUEUE_SUBSCRIPTION' //fired on waiting queue subscription. No results expected.
        ],
        parameters: {
            fields: [
              {name:'apiKey',description:'The Mailchimp API Key',type:'TEXT',required:true},
              {name:'listId',description:'The list ID, see http://kb.mailchimp.com/lists/manage-contacts/find-your-list-id',type:'TEXT',required:true}
            ],
            configurationLevels: ['ORGANIZATION', 'EVENT']
        }
    };
}

var MERGE_FIELDS = "merge-fields/";
var ALFIO_EVENT_KEY = "ALFIO_EKEY";
var APPLICATION_JSON = "application/json";
var FAILURE_MSG = "cannot add user {email: %s, name:%s, language: %s} to the list (%s)";
var LIST_MEMBERS = "members/";

/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    if('TICKET_ASSIGNED' === scriptEvent) {
        var firstName = event.mustUseFirstAndLastName ? ticket.firstName : ticket.fullName;
        subscribeUser(ticket.email, firstName, ticket.fullName, ticket.userLanguage, event);
    } else if ('RESERVATION_CONFIRMED' === scriptEvent) {
        var firstName = event.mustUseFirstAndLastName ? reservation.firstName : reservation.fullName;
        subscribeUser(reservation.email, firstName, reservation.fullName, reservation.userLanguage, event);
    } else if ('WAITING_QUEUE_SUBSCRIPTION' === scriptEvent) {
        var firstName = event.mustUseFirstAndLastName ? waitingQueueSubscription.firstName : waitingQueueSubscription.fullName;
        subscribeUser(waitingQueueSubscription.emailAddress, firstName, waitingQueueSubscription.fullName, waitingQueueSubscription.userLanguage, event);
    }
}


function subscribeUser(email, firstName, fullName, language, event) {
  var eventShortName = event.shortName;

  var dataCenter = extensionParameters.apiKey.match(/\-([a-zA-Z0-9]+)$/)[1];

  var listAddress = 'https://' + dataCenter + '.api.mailchimp.com/3.0/lists/' + extensionParameters.listId + '/'
  var apiKey = extensionParameters.apiKey;
  createMergeFieldIfNotPresent(listAddress, apiKey, event.id, eventShortName);
  var md5Email = ExtensionUtils.md5(email);
  send(event.id, listAddress + LIST_MEMBERS + md5Email, apiKey, email, firstName, fullName, language, eventShortName);
}


function createMergeFieldIfNotPresent(listAddress, apiKey, eventId, eventShortName) {

  try {
    var res = simpleHttpClient.get(listAddress + MERGE_FIELDS, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)});
    var body = res.body;
    if(body == null) {
      log.warn("null response from mailchimp for list " + listAddress);
      return;
    }
    if(body.indexOf(ALFIO_EVENT_KEY) === -1) {
      log.debug("can't find ALFIO_EKEY for event " + eventShortName);
      createMergeField(listAddress, apiKey, eventShortName, eventId);
    }
  } catch (e) {
    log.warn("exception while reading merge fields for event id " + eventId + ": " + (e.message || e));
    extensionLogger.logWarning(ExtensionUtils.format("Cannot get merge fields for %s, got: %s", eventShortName, e.message || e));
  }
}

function createMergeField(listAddress, apiKey, eventShortName, eventId) {
  var mergeField = {
    "tag": ALFIO_EVENT_KEY,
    "name": "Alfio's event key",
    "type": "text",
    "required": false,
    "public": false
  };
  try {
    var response = simpleHttpClient.post(listAddress + MERGE_FIELDS, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)}, mergeField);
    if(!response.successful) {
      var body = response.body;
      log.warn("can't create " + ALFIO_EVENT_KEY + " merge field. Got: " + (body != null ? body : "null"));
    }
  } catch(e) {
    log.warn("exception while creating ALFIO_EKEY for event id " + eventId + ": " + (e.message || e));
    extensionLogger.logWarning(ExtensionUtils.format("Cannot create merge field for %s, got: %s", eventShortName, e.message || e));
  }
}

function send(eventId, address, apiKey, email, firstName, fullName, language, eventShortName) {
  var content = {
    "email_address": email,
    "status": "subscribed",
    "merge_fields": {
      "FNAME": firstName,
      "ALFIO_EKEY": eventShortName
    },
    "language": language
  };
  try {
    var response = simpleHttpClient.put(address, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)}, content);
    if(response.successful) {
      extensionLogger.logSuccess(ExtensionUtils.format("user %s has been subscribed to list", email));
      return;
    }
    var body = response.body;
    if(body == null) {
      return;
    }

    if (response.code != 400 || body.indexOf("\"errors\"") !== -1) {
      extensionLogger.logError(ExtensionUtils.format(FAILURE_MSG, email, fullName, language, body));
    } else {
      extensionLogger.logWarning(ExtensionUtils.format(FAILURE_MSG, email, fullName, language, body));
    }
  } catch(e) {
    extensionLogger.logError(ExtensionUtils.format(FAILURE_MSG, email, fullName, language, e.message || e));
  }
}
