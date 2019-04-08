(function() {
    
    'use strict';

    jQuery(function() {

        var hiddenClasses = 'hidden-xs hidden-sm hidden-md hidden-lg';

        $(document).ready(function() {
            $(":input:not(input[type=button],input[type=submit],button):visible:first").focus();
        });
        
        H5F.setup(document.getElementById("payment-form"));
        
        
        //validity
        //ready for ECMAScript6?
        var parser = Number && Number.parseInt ? Number : window;
        var validity = new Date(parser.parseInt($("#validity").attr('data-validity')));
        
        var displayMessage = function() {

            var validityElem = $("#validity");
            var template = validityElem.attr('data-message');

            countdown.setLabels(
                validityElem.attr('data-labels-singular'),
                validityElem.attr('data-labels-plural'),
                ' '+validityElem.attr('data-labels-and')+' ',
                ', ');

            var timerId = countdown(validity, function(ts) {
                        if(ts.value < 0) {
                            validityElem.html(template.replace('##time##', ts.toHTML("strong")));
                        } else {
                            clearInterval(timerId);
                            $('#validity-container').html('<strong>'+validityElem.attr('data-message-time-elapsed')+'</strong>');
                            $('#continue-button').addClass('hidden');
                        }
                    }, countdown.MONTHS|countdown.WEEKS|countdown.DAYS|countdown.HOURS|countdown.MINUTES|countdown.SECONDS);

        };
        
        displayMessage();
        

        function submitForm(e) {
            var $form = $(this);
            
            if(!this.checkValidity()) {
                return false;
            }
            
            // Disable the submit button to prevent repeated clicks
            $form.find('button').prop('disabled', true);

            return true;
        }
        
        
        
        $("#cancel-reservation").click(function(e) {
            var $form = $('#payment-form');
            
            $("input[type=submit], button:not([type=button])", $form ).unbind('click');
            $form.unbind('submit');
            $("input", $form).unbind('keypress');
            
            $form
                .attr('novalidate', 'novalidate')
                .unbind('submit', submitForm)
                .find('button').prop('disabled', true);
            $form.trigger("reset");
            $form.append($('<input type="hidden" name="cancelReservation" />').val(true))
            $form.submit();
        });
        
        $('#payment-form').submit(submitForm);

        function markFieldAsError(node) {
            $(node).parent().addClass('has-error');
            if($(node).parent().parent().parent().hasClass('form-group')) {
                $(node).parent().parent().parent().addClass('has-error');
            }
        }

        
        // based on http://tjvantoll.com/2012/08/05/html5-form-validation-showing-all-error-messages/
        // http://stackoverflow.com/questions/13798313/set-custom-html5-required-field-validation-message
        var createAllErrors = function() {
            var form = $(this);

            var showAllErrorMessages = function() {
                $(form).find('.has-error').removeClass('has-error');
                // Find all invalid fields within the form.
                var invalidFields = form.find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).each( function( index, node ) {
                    markFieldAsError(node);
                });
            };

            // Support Safari
            form.on("submit", function( event ) {
                if (this.checkValidity && !this.checkValidity() ) {
                    $(this).find("input,select,textarea").filter(function(i,v) {return !v.validity.valid;}).first().focus();
                    event.preventDefault();
                }
            });

            $("input[type=submit], button:not([type=button])", form ).on("click", showAllErrorMessages);

            $("input", form).on("keypress", function(event) {
                var type = $(this).attr("type");
                if ( /date|email|month|number|search|tel|text|time|url|week/.test(type) && event.keyCode == 13 ) {
                    showAllErrorMessages();
                }
            });
        };


        $('#first-name.autocomplete-src, #last-name.autocomplete-src').change(function() {
            fillAttendeeData($('#first-name').val(), $('#last-name').val());
        });
        $('#full-name.autocomplete-src').change(function() {
            fillAttendeeData($(this).val());
        });
        $('#email.autocomplete-src').change(function() {
            updateIfNotTouched($('#attendeesData').find('.attendee-email').first(), $(this).val());
        });

        $('#attendeesData').find('.attendee-full-name,.attendee-first-name,.attendee-last-name,.attendee-email').first()
            .change(function() {
                $(this).removeClass('untouched');
            });

        $('.copy-from-contact-data').click(function() {
            var firstOrFullName = $('#first-name').val() || $('#full-name').val();
            var ticket = $(this).attr('data-ticket');
            fillAttendeeData(firstOrFullName, $('#last-name').val(), ticket);
            getTargetTicketData(ticket).find('.attendee-email').first().val($('#email').val());
        });

        var postponeAssignment = $('#postpone-assignment');

        postponeAssignment.change(function() {
            var element = $('#attendeesData');
            if($(this).is(':checked')) {
                element.find('.field-required').attr('required', false);
                element.addClass(hiddenClasses)
            } else {
                element.find('.field-required').attr('required', true);
                element.removeClass(hiddenClasses)
            }
        });

        if(postponeAssignment.is(':checked')) {
            $('#attendeesData').find('.field-required').attr('required', false);
        }

        function fillAttendeeData(firstOrFullName, lastName, ticketUUID) {
            var useFullName = (typeof lastName === "undefined");
            var element = getTargetTicketData(ticketUUID);
            if(useFullName) {
                updateIfNotTouched(element.find('.attendee-full-name').first(), firstOrFullName);
            } else {
                updateIfNotTouched(element.find('.attendee-first-name').first(), firstOrFullName);
                updateIfNotTouched(element.find('.attendee-last-name').first(), lastName);
            }
        }

        function getTargetTicketData(ticketUUID) {
            var id = ticketUUID ? '#attendee-data-'+ticketUUID : '#attendeesData';
            return $(id);
        }

        function updateIfNotTouched(element, newValue) {
            if(element.hasClass('untouched')) {
                element.val(newValue);
            }
        }

        function updateInvoiceFields(radio) {
            if(radio.length > 0) {
                var elements = $('.invoice-business');
                if(radio.val() === 'true') {
                    elements.removeClass('hide');
                    elements.find('#vatNr').attr('required', true);
                } else {
                    elements.find('input').val('').removeAttr('required');
                    elements.addClass('hide');
                }
            }
        }

        $('input[name=addCompanyBillingDetails]').change(function() {
            updateInvoiceFields($(this));
        });

        updateInvoiceFields($('input[name=addCompanyBillingDetails]:checked'));

        $("select").map(function() {
            var value = $(this).attr('value');
            if(value && value.length > 0) {
                $(this).val(value);
            }
        });


        $("#invoice-requested").change(function() {
            if($("#invoice-requested:checked").length) {
                $(".invoice-details-section").removeClass(hiddenClasses);
                $("#billingAddressLine1, #billingAddressZip, #billingAddressCity, #vatCountry").attr('required', true)

            } else {
                $(".invoice-details-section").addClass(hiddenClasses);
                $("#billingAddressLine1, #billingAddressZip, #billingAddressCity, #vatCountry").removeAttr('required');
            }
        });


        var enabledItalyEInvoicing = $("#italyEInvoicing").length === 1;

        $("#italyEInvoicing").hide();

        $("#vatCountry").change(function() {
            var vatCountryValue = $("#vatCountry").val();
            $("#selected-country-code").text(vatCountryValue);

            if(enabledItalyEInvoicing && vatCountryValue === 'IT') {
                $("#italyEInvoicing").show();
                $("#italyEInvoicingFiscalCode").attr('required', true);
            } else {
                $("#italyEInvoicing").hide();
                $("#italyEInvoicingFiscalCode").removeAttr('required');
            }
        });

        $("input[name=italyEInvoicingReferenceType]").change(function() {
            var referenceType = $("input[name=italyEInvoicingReferenceType]:checked").val();

            $("input[name=italyEInvoicingReferenceAddresseeCode]").attr('disabled', true);
            $("input[name=italyEInvoicingReferencePEC]").attr('disabled', true);

            if(referenceType === 'ADDRESSEE_CODE') {
                $("input[name=italyEInvoicingReferencePEC]").val('')
                $("input[name=italyEInvoicingReferenceAddresseeCode]").removeAttr('disabled');
            }
            else if(referenceType === 'PEC') {
                $("input[name=italyEInvoicingReferenceAddresseeCode]").val('')
                $("input[name=italyEInvoicingReferencePEC]").removeAttr('disabled');
            }
            else if(referenceType === 'NONE') {
                $("input[name=italyEInvoicingReferenceAddresseeCode]").val('')
                $("input[name=italyEInvoicingReferencePEC]").val('')
            }
        });

        $("#skip-vat-nr").change(function() {
            if($(this).is(':checked')) {
                $("#vatNr").removeAttr('required');
            } else {
                $("#vatNr").attr('required', true);
            }
        });

        //
        $("#vatCountry").change();
        $("#invoice-requested").change();
        $("input[name=italyEInvoicingReferenceType]").change();


        $("form").each(createAllErrors);
        $("input,select,textarea").change(function() {
            if( !this.validity.valid) {
                $(this).parent().addClass('has-error');
                if($(this).parent().parent().parent().hasClass('form-group')) {
                    $(this).parent().parent().parent().addClass('has-error');
                }
            } else {
                $(this).parent().removeClass('has-error');
                if($(this).parent().parent().parent().hasClass('form-group')) {
                    $(this).parent().parent().parent().removeClass('has-error');
                }
            }
        });

    });
})();