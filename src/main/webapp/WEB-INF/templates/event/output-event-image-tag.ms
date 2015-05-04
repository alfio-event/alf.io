{{#event.imageIsPresent}}
    {{#event.fileBlobIdIsPresent}}
        <img src="{{request.contextPath}}/file/{{event.fileBlobId}}" alt="{{event.shortName}}" class="img-responsive img-center">
    {{/event.fileBlobIdIsPresent}}
    {{^event.fileBlobIdIsPresent}}
        <img src="{{event.imageUrl}}" alt="{{event.shortName}}" class="img-responsive img-center">
    {{/event.fileBlobIdIsPresent}}
{{/event.imageIsPresent}}