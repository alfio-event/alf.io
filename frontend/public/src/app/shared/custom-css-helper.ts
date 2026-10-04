import {Event} from '../model/event';

export function removeAllCustomEventCss() {
    document.head.querySelectorAll('style[data-event-custom-css]').forEach(e => e.remove());
}

export function handleCustomCss(event: Event): void {
    if (event.customCss === null) {
        //remove all custom event styles, as this event does not have any override
        removeAllCustomEventCss();
    } else {
        const id = 'event-custom-css-' + event.organizationName + '-' + event.shortName;
        const a = document.getElementById('event-custom-css-' + event.organizationName + '-' + event.shortName);
        const found = document.head.querySelectorAll('style[data-event-custom-css]');

        //remove all custom event styles already present that don't have the expected id
        found.forEach(e => {
            if (e.id !== id) {e.remove()}
        });

        //create custom css
        if (a === null) {
            const style = document.createElement('style');
            style.setAttribute('id', id);
            style.setAttribute('type', 'text/css')
            style.setAttribute('data-event-custom-css', 'true');
            style.textContent = event.customCss;
            document.head.appendChild(style)
        }
    }
}
