(function() {
	
	'use strict';
	
	$(function() {

		var initListeners = function() {
			$(".update-ticket-owner").click(function() {
				$($(this).attr('href')).show().find("input:first").focus();
				return false;
			});


			$(".cancel-update").click(function() {
				$('#' + $(this).attr('data-for-form')).hide();
			});


			$("[data-dismiss=alert]").click(function() {
				$(this).parent().hide('medium');
			});


			$("select").map(function() {

				if($(this).attr('value').length > 0) {
					$(this).find("option[value="+$(this).attr('value')+"]").attr('selected','selected');
				}
			});

			$('.submit-assignee-data').click(function() {
				var frm = $(this.form);
				var action = frm.attr('action');
				var uuid = frm.attr('data-ticket-uuid');
				if (!frm[0].checkValidity()) {
					return true;//trigger the HTML5 error messages. Thanks to Abraham http://stackoverflow.com/a/11867013
				}
				jQuery.ajax({
					url: action,
					type: 'POST',
					data: frm.serialize(),
					success: function(result) {
						if(result.validationResult.success) {
							$('#ticket-detail-'+uuid).replaceWith(result.partial);
						}
						initListeners();
					}
				});
				return false;
			});
		};

		initListeners();
	});
	
	
	
	
})();