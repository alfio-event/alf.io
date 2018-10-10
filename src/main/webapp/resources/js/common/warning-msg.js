(function() {


    function buildContainer(lvl, msg, dismiss) {
        var elem = document.createElement('div');
        elem.setAttribute('class', 'alert alert-'+lvl+' ' + (dismiss ? 'alert-dismissible' : ''));
        elem.setAttribute('style', 'position: '+(dismiss ? 'sticky' : 'fixed')+';bottom: 0;width: 100%;margin: 0;'
            +'border-bottom: none;border-left: none;border-right: none;border-radius: 0;');

        if(dismiss) {
            //<button type = "button" class="close" data-dismiss = "alert">x</button>
            var b = document.createElement('button');
            b.setAttribute('type', 'button')
            b.setAttribute('class', 'close')
            b.textContent = 'x';
            elem.appendChild(b);
            b.addEventListener('click', function() {
                if(elem.parentElement) {
                    elem.parentElement.removeChild(elem);
                }
            });
        }

        elem.appendChild(document.createTextNode(msg));

        return elem;
    }

    function handleCheckMsg() {

        if(document.querySelector("html[dev-mode-enabled]")) {
            // dev mode, display dev mode message
            document.body.appendChild(buildContainer('info', 'You are running alf.io in development mode', true));
        } else if(document.querySelector("html[prod-mode-enabled]") && location.protocol.indexOf('https:') === -1) {
            //prod mode but we are in http mode, display warning message
            document.body.appendChild(buildContainer('danger', 'You are running alf.io in production mode not over https. '
                                        +'This is not a recommended configuration.', false));

        }

    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", handleCheckMsg);
    } else {
        handleCheckMsg();
    }

})();