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
				var promoCodeVal = $("#promo-code").val();
				if(promoCodeVal != null && promoCodeVal.trim() != "") {
					window.location.href = window.location.pathname +"?promoCode="+promoCodeVal;
				}
			});
		} else {
			$('#accessRestrictedTokens').hide();
		}

	});
	
})();