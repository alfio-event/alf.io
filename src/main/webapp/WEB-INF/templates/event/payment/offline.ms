<div class="row wMarginBottom wMarginTop">
    <div class="col-xs-12 {{#captchaRequestedForOffline}}col-md-6{{/captchaRequestedForOffline}}">
        <div class="text-muted">{{#i18n}}reservation-page.offline.description [{{delayForOfflinePayment}}]{{/i18n}}</div>
    </div>
{{#captchaRequestedForOffline}}
    <div class="col-xs-12 col-md-6">
        <div id="captcha-OFFLINE" class="g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
    </div>
{{/captchaRequestedForOffline}}
</div>

<script src="{{request.contextPath}}/resources/js/payment/offline.js" defer async></script>