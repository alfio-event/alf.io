<h1>here payment info for confirmation and stuff</h1>

<form action="" method="POST">
    <script
            src="https://checkout.stripe.com/checkout.js" class="stripe-button"
            data-key="pk_test_gY3X0UiTgKCeStUG67i2kEFq"
            data-amount="{{event.regularPriceInCents}}"
            data-currency="{{event.currency}}"
            data-name="Bagarino Site"
            data-description="Ticket price ({{event.currency}}{{event.regularPrice}})"
            data-image="/128x128.png">
    </script>
    <input type="hidden" name="{{_csrf.parameterName}}" value="{{_csrf.token}}">
</form>