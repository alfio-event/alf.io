Good news!

We've found {{matchingCount}} matching payment for event {{eventName}}.

{{#pendingReviewMatches}}
The following reservations are pending review. Please log in to the admin area and check them:
{{#pendingReview}}- {{.}}{{/pendingReview}}
{{/pendingReviewMatches}}

{{#automaticApprovedMatches}}
The following reservations have been automatically approved
{{#automaticApproved}}- {{.}}{{/automaticApproved}}
{{/automaticApprovedMatches}}

{{#automaticApprovalErrors}}
The following reservations couldn't be approved automatically
{{#approvalErrors}}- {{.}}{{/approvalErrors}}
{{/automaticApprovalErrors}}