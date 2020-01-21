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

var CustomerName = Java.type('alfio.model.CustomerName');

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
        var customerName = new CustomerName(ticket.fullName, ticket.firstName, ticket.lastName, event.mustUseFirstAndLastName());
        subscribeUser(ticket.email, customerName, ticket.userLanguage, event);
    } else if ('RESERVATION_CONFIRMED' === scriptEvent) {
        var customerName = new CustomerName(reservation.fullName, reservation.firstName, reservation.lastName, event.mustUseFirstAndLastName());
        subscribeUser(reservation.email, customerName, reservation.userLanguage, event);
    } else if ('WAITING_QUEUE_SUBSCRIPTION' === scriptEvent) {
        var customerName = new CustomerName(waitingQueueSubscription.fullName, waitingQueueSubscription.firstName, waitingQueueSubscription.lastName, event.mustUseFirstAndLastName());
        subscribeUser(waitingQueueSubscription.emailAddress, customerName, waitingQueueSubscription.userLanguage, event);
    }
}


function subscribeUser(email, customerName, language, event) {
  var eventShortName = event.shortName;

  var dataCenter = extensionParameters.apiKey.match(/\-([a-zA-Z0-9]+)$/)[1];

  var listAddress = 'https://' + dataCenter + '.api.mailchimp.com/3.0/lists/' + extensionParameters.listId + '/'
  var apiKey = extensionParameters.apiKey;
  createMergeFieldIfNotPresent(listAddress, apiKey, event.id, eventShortName);
  var md5Email = ExtensionUtils.md5(email);
  send(event.id, listAddress + LIST_MEMBERS + md5Email, apiKey, email, customerName, language, eventShortName);
}


function createMergeFieldIfNotPresent(listAddress, apiKey, eventId, eventShortName) {

  try {
    var res = simpleHttpClient.get(listAddress + MERGE_FIELDS, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)});
    var body = res.body;
    if(body == null) {
      log.warn("null response from mailchimp for list {}", listAddress);
      return;
    }
    if(!body.contains(ALFIO_EVENT_KEY)) {
      log.debug("can't find ALFIO_EKEY for event " + eventShortName);
      createMergeField(listAddress, apiKey, eventShortName, eventId);
    }
  } catch (e) {
    log.warn("exception while reading merge fields for event id "+eventId, e);
    extensionLogger.logWarning(ExtensionUtils.format("Cannot get merge fields for %s, got: %s", eventShortName, e.getMessage ? e.getMessage() : e));
  }
}

function createMergeField(listAddress, apiKey, eventShortName, eventId) {
  var mergeField = new HashMap();
  mergeField.put("tag", ALFIO_EVENT_KEY);
  mergeField.put("name", "Alfio's event key");
  mergeField.put("type", "text");
  mergeField.put("required", false);
  mergeField.put("public", false);
  try {
    var response = simpleHttpClient.post(listAddress + MERGE_FIELDS, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)}, mergeField);
    if(!response.isSuccessful()) {
      var body = response.body;
      log.warn("can't create {} merge field. Got: {}", ALFIO_EVENT_KEY, body != null ? body : "null");
    }
  } catch(e) {
    log.warn("exception while creating ALFIO_EKEY for event id "+eventId, e);
    extensionLogger.logWarning(ExtensionUtils.format("Cannot create merge field for %s, got: %s", eventShortName, e.getMessage ? e.getMessage() : e));
  }
}

function send(eventId, address, apiKey, email, name, language, eventShortName) {
  var content = new HashMap();
  content.put("email_address", email);
  content.put("status", "subscribed");
  var mergeFields = new HashMap();
  mergeFields.put("FNAME", name.isHasFirstAndLastName() ? name.getFirstName() : name.getFullName());
  mergeFields.put(ALFIO_EVENT_KEY, eventShortName);
  content.put("merge_fields", mergeFields);
  content.put("language", language);
  try {
    var response = simpleHttpClient.put(address, {'Authorization': simpleHttpClient.basicCredentials('alfio', apiKey)}, content);
    if(response.isSuccessful()) {
      extensionLogger.logSuccess(ExtensionUtils.format("user %s has been subscribed to list", email));
      return;
    }
    var body = response.body;
    if(body == null) {
      return;
    }

    if (response.code != 400 || body.contains("\"errors\"")) {
      extensionLogger.logError(ExtensionUtils.format(FAILURE_MSG, email, name, language, body));
    } else {
      extensionLogger.logWarning(ExtensionUtils.format(FAILURE_MSG, email, name, language, body));
    }
  } catch(e) {
    extensionLogger.logError(ExtensionUtils.format(FAILURE_MSG, email, name, language, e.getMessage ? e.getMessage() : e));
  }
}