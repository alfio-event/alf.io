{{>/event/page-top}}

<div class="row">
    <div class="col-xs-6">
        <img class="img-responsive img-center" src="{{request.contextPath}}/resources/images/error-picard-riker.jpg"/>
    </div>
    <div class="col-xs-6 text-danger">
        <h1>D'oh!</h1>
        <h4>{{#i18n}}server-error{{/i18n}}</h4>
        <a class="btn btn-default" href="{{request.contextPath}}/">{{#i18n}}to-home{{/i18n}}</a>
    </div>
</div>

{{>/event/page-bottom}}