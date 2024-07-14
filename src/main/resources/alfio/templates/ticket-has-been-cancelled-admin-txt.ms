{{#i18n}}email-ticket-released.admin.text [{{ticket.id}}] [{{ticket.publicUuid}}] [{{ticket.fullName}}] [{{ticket.email}}] [{{ticketCategoryDescription}}]{{/i18n}}
{{#hasAdditionalServices}}

    ***** WARNING *****
    There are still additional items linked to this reservation:
    {{#additionalServices}}
        - id: {{id}}, name: {{name}}, amount: {{amount}}
    {{/additionalServices}}
{{/hasAdditionalServices}}