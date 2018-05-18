<li class="list-group-item {{#expired}}text-muted expired{{/expired}} ticket-category-restricted-{{accessRestricted}}">
    <div class="form-group">
        <div class="col-sm-7" id="{{id}}-label">
            <label class="control-label">{{title}}</label>
            {{^expired}}
                <div>
                    {{#saleInFuture}}
                        {{#i18n}}show-event.sales-not-started [{{#format-date}}{{zonedInception}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
                    {{/saleInFuture}}
                    {{^saleInFuture}}
                        {{#i18n}}show-event.sales-end [{{#format-date}}{{zonedExpiration}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
                    {{/saleInFuture}}
                </div>
            {{/expired}}
            {{#expired}}
                <div>
                    {{#i18n}}show-event.sales-ended [{{#format-date}}{{zonedExpiration}} dd.MM.yyyy HH:mm locale:{{#i18n}}locale{{/i18n}}{{/format-date}}]{{/i18n}}
                </div>
            {{/expired}}
            <div>
                {{#commonmark}}{{description}}{{/commonmark}}
            </div>
        </div>
        <div class="col-sm-3">
            <label class="control-label">
                {{#free}}
                    {{#i18n}}common.free{{/i18n}}
                {{/free}}
                {{^free}}
                    {{#expired}}
                        {{formattedFinalPrice}} {{currencyCode}}
                    {{/expired}}
                    {{^expired}}
                        {{^userDefinedPrice}}
                            {{#supportsDiscount}}
                                <del>{{formattedFinalPrice}}</del>
                                <mark>{{discountedPrice}}</mark>
                                {{currencyCode}}
                            {{/supportsDiscount}}
                            {{^supportsDiscount}}
                                {{formattedFinalPrice}} {{currencyCode}}
                            {{/supportsDiscount}}
                            {{#vatApplies}}
                            <i class="fa fa-info-circle text-info tooltip-handler" title="{{#vatIncluded}}{{#i18n}}show-event.incVat [{{vatPercentage}}] [{{vatTranslation}}]{{/i18n}}{{/vatIncluded}}{{^vatIncluded}}{{#i18n}}show-event.excVat [{{vatPercentage}}] [{{vatTranslation}}]{{/i18n}}{{/vatIncluded}}"></i>
                            {{/vatApplies}}
                        {{/userDefinedPrice}}
                    {{/expired}}
                {{/free}}
            </label>
        </div>
        <div class="col-sm-2 text-align-center">
            {{#saleable}}
                {{#fixPrice}}
                    {{#mandatoryOneForTicket}}
                        {{#i18n}}show-event.mandatoryOneForTicket{{/i18n}}
                    {{/mandatoryOneForTicket}}
                    {{#unlimitedAmount}}
                        <input name="additionalService[{{index}}].additionalServiceId" value="{{id}}" type="hidden">
                        <input type="number" min="0" step="1" aria-labelledby="{{id}}-label" name="additionalService[{{index}}].quantity" class="form-control" placeholder="0" autocomplete="off">
                    {{/unlimitedAmount}}
                    {{#limitedAmount}}
                        <input name="additionalService[{{index}}].additionalServiceId" value="{{id}}" type="hidden">
                        <select {{#maxAmountPerTicket}}data-max-amount-per-ticket="{{maxQtyPerOrder}}"{{/maxAmountPerTicket}} aria-labelledby="{{id}}-label" name="additionalService[{{index}}].quantity" class="form-control text-align-center" placeholder="0" autocomplete="off">
                            {{#maxAmountPerTicket}}
                                <option value="0">0</option>
                            {{/maxAmountPerTicket}}
                            {{^maxAmountPerTicket}}
                                {{#amountOfTickets}}<option value="{{this}}">{{this}}</option>{{/amountOfTickets}}
                            {{/maxAmountPerTicket}}
                        </select>
                    {{/limitedAmount}}
                {{/fixPrice}}
                {{^fixPrice}}
                    <input name="additionalService[{{index}}].additionalServiceId" value="{{id}}" type="hidden">
                    <input type="number" min="0" step="any" name="additionalService[{{index}}].amount" class="form-control" title="" value="0">
                {{/fixPrice}}
            {{/saleable}}
            {{^saleable}}
                <label class="control-label">
                    {{#soldOut}}
                        {{#i18n}}show-event.sold-out{{/i18n}}
                    {{/soldOut}}
                    {{^soldOut}}
                        {{#i18n}}show-event.not-available{{/i18n}}
                    {{/soldOut}}
                </label>
            {{/saleable}}
        </div>
    </div>
</li>