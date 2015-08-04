{{#event.imageIsPresent}}
    {{#event.fileBlobIdIsPresent}}
        <img src="{{request.contextPath}}/file/{{event.fileBlobId}}" alt="{{event.displayName}}" class="img-responsive img-center logo">
    {{/event.fileBlobIdIsPresent}}
    {{^event.fileBlobIdIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.displayName}}" class="img-responsive img-center logo">
    {{/event.fileBlobIdIsPresent}}
{{/event.imageIsPresent}}