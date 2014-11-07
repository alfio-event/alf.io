(function() {
	
	'use strict';
	
	$(function() {
		$(".update-ticket-owner").click(function() {
			$($(this).attr('href')).show().find("input:first").focus();
			return false;
		});
		
		
		$(".cancel-update").click(function() {
			$('#' + $(this).attr('data-for-form')).hide();
		})
		
		
		$("[data-dismiss=alert]").click(function() {
			$(this).parent().hide('medium');
		});
		
		
		$("select").map(function() {
			
			if($(this).attr('value').length > 0) {
				$(this).find("option[value="+$(this).attr('value')+"]").attr('selected','selected');
			}
		});
	});
	
	
	
	
})();