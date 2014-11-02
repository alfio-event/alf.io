(function() {
	
	'use strict';
	
	$(function() {
		
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
	});
	
})();