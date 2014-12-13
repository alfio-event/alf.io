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
				var button = $(this);
				var frm = $(this.form);
				var promoCodeVal = $("#promo-code").val();
				$('#error-code-not-found').addClass('hidden');
				if(promoCodeVal != null && promoCodeVal.trim() != "") {
					jQuery.ajax({
						url: 'promoCode/'+promoCodeVal,
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
		}

	});
	
})();