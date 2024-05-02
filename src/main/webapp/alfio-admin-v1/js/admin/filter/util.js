(function () {
    "use strict";
    var filters = angular.module('utilFilters', []);

    filters.value('PAYMENT_PROXY_DESCRIPTIONS', {
        'STRIPE'   : 'Stripe: Credit cards',
        'ON_SITE'  : 'On site (cash) payment',
        'OFFLINE'  : 'Offline payment (bank transfer, invoice, etc.)',
        'PAYPAL'   : 'PayPal',
        'MOLLIE'   : 'Mollie: Credit cards, iDEAL, Bancontact, ING Home Pay, Belfius, KBC/CBC, Przelewy24',
        'SAFERPAY' : 'Saferpay By SIX Payments'
    });

    filters.filter('printSelectedOrganization', function() {
        return function(organizations, id) {
            var existing = _.find(organizations, function(organization) {
                return id && organization.id == id;
            }) || {};
            return existing.name;
        }
    });

    filters.filter('formatDate' , function() {
        return function(dateAsString, pattern) {
            if(!angular.isDefined(dateAsString) || dateAsString === null) {
                return dateAsString;
            }
            var formatPattern = angular.isDefined(pattern) ? pattern : 'DD.MM.YYYY HH:mm';
            var date = moment(dateAsString.replace(/\[[A-Za-z0-9\-\/]+]/, ''));
            if(date.isValid()) {
                return date.format(formatPattern);
            }
            return dateAsString;
        };
    });

    filters.filter('statusText', function() {
        return function(status) {
            if(!status) {
                return '';
            }
            return status.replace(/_/g, ' ').toLowerCase();
        };
    });

    filters.filter('mailSettingsFilter', function() {
        return function(list, mailType) {
            if(angular.isUndefined(mailType) || mailType === null) {
                return [];
            }
            var referenceKey = mailType.toUpperCase();
            return _.filter(list, function(e) {
                return e.key.toUpperCase().indexOf(referenceKey) > -1;
            });
        };
    });

    filters.filter('showSelectedCategories', function() {
        return function(categories, criteria) {
            if(criteria.active && criteria.expired && criteria.freeText === '') {
                return categories;
            }
            return _.filter(categories, function(category) {
                var result = ((criteria.active && !category.expired) || (criteria.expired && category.expired));
                if(result && criteria.freeText !== '') {
                    return category.name.toLowerCase().indexOf(criteria.freeText.toLowerCase()) > -1;
                }
                return result;
            });
        };
    });

    filters.filter('addTrailingSlash', ['$window', function($window) {
        return function(targetUrl) {
            if(!$window.location.href.endsWith('/')) {
                return 'admin/' + targetUrl;
            }
            return targetUrl;
        }
    }]);

    filters.filter('paymentMethodFilter', function() {
        return function(list, disabled, currency) {
            var query = function(m) {
                return m.enabled && (m.onlyForCurrency.length === 0 || m.onlyForCurrency.indexOf(currency) > -1);
            };

            if(disabled) {
                query = function(m) {
                    return !m.enabled || (m.enabled && m.onlyForCurrency.length > 0 && m.onlyForCurrency.indexOf(currency) === -1);
                }
            }

            return _.uniq(_.filter(list, query), false, 'id');
        }
    });

    filters.filter('boundedCategories', function() {
        return function(list, remove) {
            var query = 'bounded';
            if(remove) {
                query = function(c) {
                    return !c.bounded;
                };
            }
            return _.filter(list, query);
        }
    });

    filters.filter('translatePaymentProxies', ['PAYMENT_PROXY_DESCRIPTIONS', function(PAYMENT_PROXY_DESCRIPTIONS) {
        return function(list) {
            return _.map(list, function(p) {
                return PAYMENT_PROXY_DESCRIPTIONS[p] ? PAYMENT_PROXY_DESCRIPTIONS[p] : p;
            }).join(', ');
        }
    }]);

    filters.filter('selectedLanguages', function() {
        return function(list, eventLocales) {
            return (list || []).filter(function(lang) {
                return (eventLocales & lang.value) > 0
            })
        }
    });

    filters.filter('flattenAdditionalServices', function() {
        return function(list) {
            return list.map(function(as, index) {
                return {
                    title: as.title.map(function(t) { return t.value;}),
                    id: as.id,
                    index: index
                }
            })
        };
    });

    filters.filter('keys', function() {
        return function(o) {
            return Object.keys(o);
        }
    });

    filters.filter('money', function() {
        return function(amount, currency, hideCurrency) {
            if(!currency || typeof amount === 'undefined') {
                return "";
            }
            var formatted = new Intl.NumberFormat(navigator.language, { style: 'currency', currency: currency, currencyDisplay: 'code' }).format(amount);
            if(hideCurrency) {
                var currencyIndex = formatted.indexOf(currency);
                return (formatted.substring(0, currencyIndex) + formatted.substring(currencyIndex + currency.length)).trim();
            }
            return formatted;
        }
    });

    filters.filter('removeIncompatiblePaymentMethods', function() {
        return function(methods, event) {
            return _.filter(methods, function(method) {
                return method.proxy.id !== 'ON_SITE' || event.format !== 'ONLINE';
            });
        }
    });

    filters.filter('ellipsisAfterFirstRow', function() {
        return function(str) {
            var split = str.split(/\n/g, 2);
            if (split.length > 1) {
                return split[0] + '...';
            }
            return str;
        }
    });

})();