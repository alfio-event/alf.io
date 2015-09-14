function submitPageView() {
    if(window.ga) {
        ga('create', '{{account}}', {'anonymizeIp': true, 'storage': 'none', 'clientId': '{{clientId}}'});
        ga('send', 'pageview');
    } else {
        window.setTimeout(submitPageView, 50);
    }
};
window.setTimeout(submitPageView, 50);
