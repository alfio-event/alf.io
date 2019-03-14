{{#event.imageIsPresent}}
    {{#event.fileBlobIdIsPresent}}
        <img src="{{request.contextPath}}/file/{{event.fileBlobId}}" alt="{{event.displayName}}" class="img-responsive img-center logo" id="event-logo">
    {{/event.fileBlobIdIsPresent}}
    {{^event.fileBlobIdIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.displayName}}" class="img-responsive img-center logo" id="event-logo">
    {{/event.fileBlobIdIsPresent}}
{{/event.imageIsPresent}}