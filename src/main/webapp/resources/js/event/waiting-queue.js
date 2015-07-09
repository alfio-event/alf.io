(function() {
    "use strict";

    $('#waiting-queue-subscribe').click(function() {
        var frm = $(this.form);
        var action = frm.attr('action');
        var uuid = frm.attr('data-ticket-uuid');
        frm.find('.has-error').removeClass('has-error');
        frm.find('#generic-error').removeClass('show');
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
        $('#loading').addClass('show');
        $('#waiting-queue-subscribe').attr('disabled', true);
        jQuery.ajax({
            url: action,
            type: 'POST',
            data: frm.serialize(),
            success: function(result) {
                var validationResult = result.validationResult;
                if(validationResult.success) {
                    $('#waiting-queue-subscription').replaceWith(result.partial);
                } else {
                    validationResult.validationErrors.forEach(function(error) {
                        var element = frm.find('[name='+error.fieldName+']').parents('label');
                        if(element.length > 0) {
                            element.addClass('has-error');
                        } else {
                            $('#generic-error').addClass('show');
                        }
                    });
                }
            },
            error: function(xhr, textStatus, errorThrown) {
                frm.find('#generic-error').addClass('show');
                $('#loading').removeClass('show');
                $('#waiting-queue-subscribe').attr('disabled', false);
            },
            complete: function(xhr) {
                xhr.done(function() {
                    frm.find('#generic-error').removeClass('show');
                    $('#loading').removeClass('show');
                    $('#waiting-queue-subscribe').attr('disabled', false);
                });
            }
        });
        return false;
    });


    $("select").map(function() {
        if($(this).attr('value').length > 0) {
            $(this).find("option[value="+$(this).attr('value')+"]").attr('selected','selected');
        }
    });
})();