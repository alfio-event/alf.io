<div class="row wMarginBottom wMarginTop">
    <div class="col-xs-12 {{#captchaRequestedForOffline}}col-md-6{{/captchaRequestedForOffline}}">
        <div class="text-muted">{{#i18n}}reservation-page.on-site.description{{/i18n}}</div>
    </div>
{{#captchaRequestedForOffline}}
    <div class="col-xs-12 col-md-6">
        <div id="captcha-ON_SITE" class="g-recaptcha" data-sitekey="{{recaptchaApiKey}}"></div>
    </div>
{{/captchaRequestedForOffline}}
</div>
<script src="{{request.contextPath}}/resources/js/payment/on-site.js" defer async></script>
