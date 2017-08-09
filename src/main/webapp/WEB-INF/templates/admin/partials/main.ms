<div class="container">
    <h1>Dashboard</h1>
    <hr />
    <div class="row">
        <div class="col-sm-12">
            <div class="panel panel-default">
                <div class="panel-heading">
                    <h1 class="panel-title">{{#i18n}}admin.main.events{{/i18n}}</h1>
                </div>
                <div class="panel-body">
                    <active-events-list></active-events-list>
                </div>
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-12">
            <expired-events-list></expired-events-list>
        </div>
    </div>
</div>