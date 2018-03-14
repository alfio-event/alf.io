(function() {
    
    'use strict';
    
    $(function() {
        
        $("form").each(function(i, v) {
            H5F.setup(v);
        });
        
        
        

        var initListeners = function() {

            $(document).ready(function() {
                $(":input:not(input[type=button],input[type=submit],button):visible:first").focus();
                $("select").map(function() {
                    var value = $(this).attr('value');
                    if(value && value.length > 0) {
                        $(this).val(value);
                    }
                });

            });
            
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


            $('.loading').hide();

            var activeForms = $('form.show-by-default.not-assigned');
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
                            $('#ticket-detail-'+uuid).find("select").each(function(index, select) {
                                var $select = jQuery(select);
                                var $selectValue = jQuery(select).attr('value');
                                if($selectValue && $selectValue.length > 0) {
                                    $select.find("option[value="+$selectValue+"]").attr('selected', 'selected')
                                }

                            });
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

            $('.send-ticket-by-email').click(function() {
                var frm = $(this.form);
                var action = frm.attr('action');
                var uuid = frm.attr('data-ticket-uuid');
                frm.find('.has-error').removeClass('has-error');
                $('#success-'+uuid).removeClass('show');
                $('#error-'+uuid).removeClass('show');
                $('#loading-'+uuid).show();
                jQuery.ajax({
                    url: action,
                    type: 'POST',
                    data: frm.serialize(),
                    success: function(result) {
                        $('#success-'+uuid).removeClass('hidden');
                        $('#error-'+uuid).addClass('hidden');
                    },
                    error: function(xhr, textStatus, errorThrown) {
                        $('#success-'+uuid).addClass('hidden');
                        $('#error-'+uuid).removeClass('hidden');
                    },
                    complete: function(xhr) {
                        xhr.done(function() {
                            $('#loading-'+uuid).hide();
                        });
                    }
                });
                return false;
            });

        };

        initListeners();
    });
    
    
    
    
})();