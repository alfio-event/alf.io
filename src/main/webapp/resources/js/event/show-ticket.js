(function() {
    
    'use strict';
    
    
    $(function() {
        $("[data-dismiss=alert]").click(function() {
            $(this).parent().hide('medium');
        });
    });
})();