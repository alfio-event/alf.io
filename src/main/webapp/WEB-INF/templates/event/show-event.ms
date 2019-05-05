{{>/event/page-top}}

{{>/event/header}}

<div class="row">
    <div class="{{#locationDescriptor.hasMapUrl}}col-md-7{{/locationDescriptor.hasMapUrl}}{{^locationDescriptor.hasMapUrl}}col-md-12{{/locationDescriptor.hasMapUrl}}">
        {{>/event/event-details}}
    </div>
    {{#locationDescriptor.hasMapUrl}}
    <div class="col-md-5">
        <img src="{{locationDescriptor.mapUrl}}" class="img-responsive img-center">
    </div>
    {{/locationDescriptor.hasMapUrl}}
</div>

<hr/>

<div class="row wMarginBottom">
    <div class="col-xs-12 event-description">
        <div>{{#commonmark}}{{event.description}}{{/commonmark}}</div>
    </div>
</div>

<h2>{{#i18n}}show-event.tickets{{/i18n}}</h2>

{{#hasErrors}}
<div class="alert alert-danger" role="alert">

{{#error.globalErrors}}
    <p><strong>{{#i18n}}{{.}}{{/i18n}}</strong></p>
{{/error.globalErrors}}

</div>
{{/hasErrors}}

{{#displayWaitingQueueForm}}
    {{^subscriptionComplete}}
        <div class="alert alert-warning" role="alert" id="waiting-queue-subscription">
            {{#preSales}}
                <h3 class="wMarginBottom">{{#i18n}}show-event.pre-sales.header{{/i18n}}</h3>
                <h4>{{#i18n}}show-event.pre-sales.message{{/i18n}}</h4>
            {{/preSales}}
            {{^preSales}}
                <h3 class="wMarginBottom">{{#i18n}}show-event.sold-out.header{{/i18n}}</h3>
                <h4>{{#i18n}}show-event.sold-out.message{{/i18n}}</h4>
            {{/preSales}}
            <hr/>
            <form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/waiting-queue/subscribe" role="form">
                <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
                {{#useFirstAndLastName}}
                <label for="firstName">{{#i18n}}common.first-name{{/i18n}} </label><input type="text" id="firstName" class="form-control" name="firstName" required="required" aria-required="true" maxlength="255">
                <label for="lastName">{{#i18n}}common.last-name{{/i18n}} </label><input type="text" id="lastName" class="form-control" name="lastName" required="required" aria-required="true" maxlength="255">
                {{/useFirstAndLastName}}
                {{^useFirstAndLastName}}
                <label for="fullName">{{#i18n}}common.full-name{{/i18n}} </label><input type="text" id="fullName" class="form-control" name="fullName" required="required" aria-required="true" maxlength="255">
                {{/useFirstAndLastName}}
                <label for="email">{{#i18n}}common.email{{/i18n}}</label><input type="email" id="email" class="form-control" name="email"  required="required" aria-required="true" maxlength="255">
                {{#displayCategorySelectionForWaitingQueue}}
                    <label for="selectedCategory">{{#i18n}}reservation-page.category{{/i18n}}</label>
                    <select name="selectedCategory" id="selectedCategory" class="form-control" required="required" aria-required="true">
                        <option value=""></option>
                        {{#unboundedCategories}}
                            <option value="{{id}}">{{name}}</option>
                        {{/unboundedCategories}}
                    </select>
                {{/displayCategorySelectionForWaitingQueue}}
                <label for="userLanguage">{{#i18n}}reservation-page-complete.language{{/i18n}}</label>
                <select name="userLanguage" id="userLanguage" value="{{userLanguage}}" id="waiting-queue-language" class="form-control" required="required" aria-required="true">
                    <option value=""></option>
                    {{#availableLanguages}}
                        <option value="{{language}}">{{displayLanguage}}</option>
                    {{/availableLanguages}}
                </select>
                {{#event.privacyPolicyLinkOrNull}}
                <div class="checkbox wMarginTop wMarginBottom">
                    <label>
                        <input type="checkbox" required name="privacyPolicyAccepted" value="true">
                        {{#i18n}}reservation-page.privacy.prefix{{/i18n}} <a href="{{event.privacyPolicyLinkOrNull}}" target="_blank">{{#i18n}}reservation-page.privacy.link.text{{/i18n}}</a>{{#i18n}}reservation-page.privacy.suffix{{/i18n}}
                    </label>
                </div>
                {{/event.privacyPolicyLinkOrNull}}
                <div class="checkbox">
                    <label>
                        <input type="checkbox" required name="termAndConditionsAccepted" aria-required="true">
                        {{#i18n}}reservation-page.tc.prefix{{/i18n}} <a href="{{event.termsAndConditionsUrl}}" target="_blank">{{#i18n}}reservation-page.tc.link.text{{/i18n}}</a>{{#i18n}}reservation-page.tc.suffix{{/i18n}}
                    </label>
                </div>
                <div class="row">
                    <div class="col-md-4 col-md-push-8 col-xs-12"><button type="submit" id="waiting-queue-subscribe" class="btn btn-success btn-block">{{#i18n}}show-event.sold-out.subscribe{{/i18n}}</button></div>
                </div>
                <div class="alert alert-danger wMarginTop wMarginBottom hide" id="generic-error">{{#i18n}}reservation-page-complete.please-check-input-fields{{/i18n}}</div>
            </form>
            <div class="text-center loading hide" id="loading"><i class="fa fa-cog fa-2x fa-spin"></i></div>
        </div>
    {{/subscriptionComplete}}
    {{#subscriptionComplete}}
        {{>/event/waiting-queue-subscription-result.ms}}
    {{/subscriptionComplete}}
{{/displayWaitingQueueForm}}

<form method="post" action="{{request.contextPath}}/event/{{event.shortName}}/reserve-tickets" class="form-horizontal">
    <ul class="list-group">
    {{#ticketCategories}}
        {{> /event/ticket-category.ms }}
    {{/ticketCategories}}
    </ul>
    {{#showAdditionalServices}}
        {{#showAdditionalServicesSupplements}}
            <h2>{{#i18n}}show-event.additional-services{{/i18n}}</h2>
            <ul class="list-group">
                {{#enabledAdditionalServicesSupplements}}
                    {{> /event/additional-service.ms }}
                {{/enabledAdditionalServicesSupplements}}
            </ul>
        {{/showAdditionalServicesSupplements}}
        {{#showAdditionalServicesDonations}}
            <h2>{{#i18n}}show-event.donations{{/i18n}}</h2>
            <ul class="list-group">
                {{#enabledAdditionalServicesDonations}}
                    {{> /event/additional-service.ms }}
                {{/enabledAdditionalServicesDonations}}
            </ul>
        {{/showAdditionalServicesDonations}}
    {{/showAdditionalServices}}
    {{#showNoCategoriesWarning}}
        <div class="alert alert-warning">
            <h3>{{#i18n}}show-event.sold-out.header{{/i18n}}</h3>
        </div>
    {{/showNoCategoriesWarning}}
    {{#containsExpiredCategories}}
    <div class="expired">
        <div class="text-center">
            <a role="button" class="show-expired-categories" data-toggle="collapse" href="#expiredCategories" aria-expanded="false" aria-controls="expiredCategories">
                <i class="fa fa-angle-double-down"></i> {{#i18n}}show-event.expired-categories{{/i18n}}
            </a>
        </div>
        <div class="collapse" id="expiredCategories">
            <ul class="list-group">
                {{#expiredCategories}}
                    {{> /event/ticket-category.ms }}
                {{/expiredCategories}}
            </ul>
        </div>
    </div>
    {{/containsExpiredCategories}}

{{#hasPromoCodeDiscount}}

    <hr/>

    <div>
        <input type="hidden" name="promoCodeDiscount" value="{{promoCodeDiscount.promoCode}}">
        <ul class="list-group">
            <li class="list-group-item">
                <div class="row">
                    <div class="col-sm-7">{{#i18n}}show-event.promo-code-applied [{{promoCodeDescription}}]{{/i18n}} {{promoCodeDiscount.promoCode}}</div>
                    <div class="col-sm-5">
                        {{#promoCodeDiscount.fixedAmount}}
                            {{#i18n}}show-event.promo-code-fixed-amount-discount [{{promoCodeDiscount.formattedDiscountAmount}} {{event.currency}}]{{/i18n}}
                        {{/promoCodeDiscount.fixedAmount}}
                        {{^promoCodeDiscount.fixedAmount}}
                            {{#i18n}}show-event.promo-code-percentage-discount [{{promoCodeDiscount.discountAmount}}%]{{/i18n}}
                        {{/promoCodeDiscount.fixedAmount}}
                    </div>
                </div>
            </li>
        </ul>
    </div>
{{/hasPromoCodeDiscount}}

<div class="wMarginBottom"></div>
{{#hasAccessPromotions}}
<div id="accessRestrictedTokens" class="wMarginBottom">
    <div class="panel panel-default">
        <div class="panel-heading" role="tab" id="headingOne">
            <h4 class="panel-title">
                <i class="fa {{^usePartnerCode}}fa-gift{{/usePartnerCode}}{{#usePartnerCode}}fa-handshake-o{{/usePartnerCode}}"></i>
                <a data-toggle="collapse" href="#collapseOne" aria-expanded="false" aria-controls="collapseOne">
                    {{#i18n}}show-event.promo-code [{{promoCodeDescription}}]{{/i18n}}
                </a>
            </h4>
        </div>
        <div id="collapseOne" class="panel-collapse collapse" role="tabpanel" aria-labelledby="headingOne">
            <div class="panel-body">
                <div class="hidden text-danger text-center wMarginBottom" id="error-code-not-found">{{#i18n}}error.STEP_1_CODE_NOT_FOUND{{/i18n}}</div>
                <div class="form-inline text-center">
                    <label>{{#i18n}}show-event.promo-code-insert [{{promoCodeDescription}}]{{/i18n}} <input name="promoCodes" autocomplete="off" class="form-control" id="promo-code"></label>
                    <button class="btn btn-success" type="button" id="apply-promo-codes">{{#i18n}}show-event.promo-code.apply{{/i18n}}</button>
                </div>
            </div>
        </div>
    </div>
</div>
{{/hasAccessPromotions}}
{{#captchaForTicketSelectionEnabled}}
    <div class="show-event g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
{{/captchaForTicketSelectionEnabled}}
    <hr/>

    <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
    <input type="hidden" name="promoCode" value="{{promoCode}}">

    <div class="row">
        <div class="col-md-4 col-md-push-8 col-xs-12 wMarginBottom"><button type="submit" class="btn btn-success {{#forwardButtonDisabled}}disabled{{/forwardButtonDisabled}} btn-block">{{#i18n}}show-event.continue{{/i18n}}</button></div>
        <div class="col-md-4 col-md-pull-4 col-xs-12"><a href="{{event.websiteUrl}}" class="btn btn-default btn-block">{{#i18n}}to-event-site{{/i18n}}</a></div>
    </div>

    <div class="clearfix"></div>
</form>
<script src="{{request.contextPath}}/resources/bower_components/jquery/dist/jquery.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/jquery.shorten.js"></script>
<script src="{{request.contextPath}}/resources/js/event/show-event.js"></script>
{{#displayWaitingQueueForm}}
<script src="{{request.contextPath}}/resources/js/event/waiting-queue.js"></script>
{{/displayWaitingQueueForm}}
<script src="{{request.contextPath}}/resources/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="{{request.contextPath}}/resources/js/event/bootstrap-handler.js"></script>

{{#captchaForTicketSelectionEnabled}}
<script src="https://www.google.com/recaptcha/api.js" async defer></script>
{{/captchaForTicketSelectionEnabled}}

{{>/event/page-bottom}}