{{>/event/page-top}}

<div class="row">
    <div class="col-xs-2"><h1><i class="fa fa-ambulance fa-3x text-danger"></i></h1></div>
    <div class="col-xs-10 text-danger">
        <h1>Ouch!</h1>
        <h4>{{#i18n}}server-error{{/i18n}}</h4>
    </div>
</div>

<div class="row wMarginTop30px">
    <div class="col-xs-12"><a class="btn btn-default" href="{{request.contextPath}}/">{{#i18n}}to-home{{/i18n}}</a></div>
</div>

{{>/event/page-bottom}}