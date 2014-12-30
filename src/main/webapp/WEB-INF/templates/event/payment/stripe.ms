<div class="row wMarginTop wMarginBottom">
    <div class="col-md-3 col-sm-3 col-xs-9 col-md-offset-0 col-xs-offset-3"><a href="https://stripe.com/" target="_blank"><img src="{{request.contextPath}}/resources/images/outline.png"></a></div>
    <div class="col-md-9 col-sm-9 col-xs-12 text-muted">{{#i18n}}reservation-page.credit-card.description{{/i18n}}</div>
</div>

<div class="alert alert-danger payment-errors hide" role="alert">
</div>

<div class="form-group">
    <label for="card-name" class="control-label">{{#i18n}}reservation-page.cardholder.name{{/i18n}}</label>
    <input type="text" size="20" data-stripe="name" required class="form-control" id="card-name" autocomplete="name">
</div>
<div class="form-group">
    <label for="card-number" class="control-label">{{#i18n}}reservation-page.card-number{{/i18n}}</label>
    <input type="text" size="20" data-stripe="number" required class="form-control" id="card-number" autocomplete="off">
</div>

<div class="row">
    <div class="col-md-4">
        <div class="form-group">
            <label for="card-mm">{{#i18n}}reservation-page.expiration-month{{/i18n}}</label>
            <input type="text" class="form-control" size="2" data-stripe="exp-month" required maxlength="2" autocomplete="off" id="card-mm" pattern="\d+">
        </div>
    </div>
    <div class="col-md-4">
        <div class="form-group">
            <label for="card-yyyy">{{#i18n}}reservation-page.expiration-year{{/i18n}}</label>
            <input type="text" class="form-control" size="4" data-stripe="exp-year" required maxlength="4" autocomplete="off" id="card-yyyy" pattern="\d+">
        </div>
    </div>
    <div class="col-md-4">
        <div class="form-group">
            <label for="card-cvc">{{#i18n}}reservation-page.cvc{{/i18n}}</label>
            <input type="text" class="form-control" size="4" data-stripe="cvc" required id="card-cvc" autocomplete="off">
        </div>
    </div>
</div>