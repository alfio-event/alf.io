(function() {
    
    'use strict';
    
    $(function() {
        
        $("form").each(function(i, v) {
            H5F.setup(v);
        });
        
        
        

        var initListeners = function() {
            
            $(".update-ticket-owner,.unbind-btn").click(function() {
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

            $('.loading').hide();

            var activeForms = $('form.show-by-default');
            if(activeForms.length === 0) {
                $('#back-to-event-site').removeClass('hidden');
            }

            $('.submit-assignee-data').click(function() {
                var frm = $(this.form);
                var action = frm.attr('action');
                var uuid = frm.attr('data-ticket-uuid');
                frm.find('.has-error').removeClass('has-error');
                $('#generic-'+uuid+'-error').removeClass('show');
                if (!frm[0].checkValidity()) {

                    // Find all invalid fields within the form.
                    frm.find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).each( function( index, node ) {
                        $(node).parent().addClass('has-error');
                        if($(node).parent().parent().hasClass('form-group')) {
                            $(node).parent().parent().addClass('has-error');
                        }
                    });
                    
                    frm.find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).first().focus();
                    
                    return true;//trigger the HTML5 error messages. Thanks to Abraham http://stackoverflow.com/a/11867013
                }
                $('#loading-'+uuid).show();
                $('#buttons-bar-'+uuid).hide();
                frm.parents('div[data-ticket-update-page=true]').find('input[name=single-ticket]').val('true');
                jQuery.ajax({
                    url: action,
                    type: 'POST',
                    data: frm.serialize(),
                    success: function(result) {
                        var validationResult = result.validationResult;
                        if(validationResult.success) {
                            $('#ticket-detail-'+uuid).replaceWith(result.partial);
                            initListeners();
                        } else {
                            validationResult.validationErrors.forEach(function(error) {
                                var element = frm.find('[name='+(error.fieldName.replace('[', '\\[\\\'').replace(']','\\\'\\]'))+']').parents('.form-group');
                                if(element.length > 0) {
                                    element.addClass('has-error');
                                } else {
                                    $('#generic-'+uuid+'-error').addClass('show');
                                }
                            });
                        }
                    },
                    error: function(xhr, textStatus, errorThrown) {
                        $('#error-'+uuid).addClass('show');
                        $('#loading-'+uuid).hide();
                        $('#buttons-bar-'+uuid).show();
                    },
                    complete: function(xhr) {
                        xhr.done(function() {
                            $('#error-'+uuid).removeClass('show');
                            $('#loading-'+uuid).hide();
                            $('#buttons-bar-'+uuid).show();
                        });
                    }
                });
                return false;
            });

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
        };

        initListeners();
    });
    
    
    
    
})();