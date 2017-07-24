(function() {

    'use strict';

    $(function() {
        var collapsibleContainer = $('.collapsible-container[data-collapse-enabled="true"]');
        if(collapsibleContainer.length > 1) {
            var collapsibleElement = collapsibleContainer.find('.collapsible');
            collapsibleElement.addClass('hidden');
            collapsibleElement.attr('aria-expanded', 'false');
            collapsibleContainer.find('div.toggle-collapse').removeClass('hidden');
            collapsibleContainer.find('a.toggle-collapse').click(function() {
                var expanded = collapsibleElement.attr('aria-expanded');
                if(expanded === 'true') {
                    collapsibleElement.addClass('hidden');
                    collapsibleElement.attr('aria-expanded', 'false');
                    collapsibleContainer.find('span.collapse-less').addClass('hidden');
                    collapsibleContainer.find('span.collapse-more').removeClass('hidden');
                } else {
                    collapsibleElement.removeClass('hidden');
                    collapsibleElement.attr('aria-expanded', 'true');
                    collapsibleContainer.find('span.collapse-more').addClass('hidden');
                    collapsibleContainer.find('span.collapse-less').removeClass('hidden');
                }
            });
        }
        $("select").map(function() {
            var value = $(this).attr('value');
            if(value && value.length > 0) {
                $(this).val(value);
            }
        });
    });
})();
