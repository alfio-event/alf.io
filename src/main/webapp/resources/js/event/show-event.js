(function() {
    
    'use strict';
    
    $(function() {

        var restrictedCategories = $('.ticket-category-restricted-true');
        if(restrictedCategories.length === 0) {
            $("#promo-code").keydown(function(e) {
                if (e.keyCode == 13) {
                    $("#apply-promo-codes").click();
                    return false;
                }
            });

            $("#apply-promo-codes").click(function() {
                var contextPath = window.location.pathname;
                if(!/\/$/.test(contextPath)) {
                    contextPath = contextPath + '/';
                }
                var button = $(this);
                var frm = $(this.form);
                var promoCodeVal = $("#promo-code").val();
                $('#error-code-not-found').addClass('hidden');
                if(promoCodeVal != null && promoCodeVal.trim() != "") {
                    jQuery.ajax({
                        url: contextPath + 'promoCode/'+encodeURIComponent(promoCodeVal),
                        type: 'POST',
                        data: frm.serialize(),
                        success: function(result) {
                            if(result.success) {
                                window.location.reload();
                            } else {
                                $('#error-code-not-found').removeClass('hidden');
                            }
                        }
                    });
                }
            });
        } else {
            $('#accessRestrictedTokens').hide();
            //original code: http://stackoverflow.com/a/6677069
            $('html, body').animate({
                scrollTop: restrictedCategories.first().offset().top
            }, 500);
        }

        $('div.event-description>div.preformatted').shorten({showChars: 250});

        var collapsible = $('#expiredCategories');
        var showExpiredCategoriesLink = $('a.show-expired-categories');
        collapsible.on('shown.bs.collapse', function() {
            showExpiredCategoriesLink.find('i.fa-angle-double-down').removeClass('fa-angle-double-down').addClass('fa-angle-double-up');
        });
        collapsible.on('hidden.bs.collapse', function() {
            showExpiredCategoriesLink.find('i.fa-angle-double-up').removeClass('fa-angle-double-up').addClass('fa-angle-double-down');
        });
        $('#collapseOne').on('shown.bs.collapse', function () {
            $('#promo-code').focus();
        })

        function countTotalTicketSelected() {
            var selected = $("[data-ticket-selector]").map(function() {
                return parseInt(this.value);
            });
            var tot = 0;
            for(var i = 0; i < selected.length; i++) {
                tot+=selected[i];
            }
            return tot;
        }

        function updateSelect(select, totalSelected) {
            var maxAmount = parseInt($(select).attr('data-max-amount-per-ticket')) * totalSelected;
            var options = [];
            for(var i = 0; i <= maxAmount; i++) {
                options.push($("<option value='"+i+"'>"+i+"</option>"));
            }
            var value = select.value;
            $(select).empty();
            $(select).append(options);
            select.value = Math.min(value, maxAmount);
        }

        $("[data-ticket-selector]").change(function() {
            var totalSelected = countTotalTicketSelected();
            $("[data-max-amount-per-ticket]").each(function() {
               updateSelect(this, totalSelected);
            });
        });
    });
    
})();