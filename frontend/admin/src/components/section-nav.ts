import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import type {SlSelect} from '@shoelace-style/shoelace';
import {base, creationLayout} from '../styles.ts';

export interface FormSection {
    id: string;
    label: string;
}

/**
 * The section index of a long creation form: a sticky nav on desktop, a compact
 * "Jump to section" select below 900px. The link of the section currently in view is
 * highlighted (primary blue, aria-current="location"); activating a link scrolls to the
 * section and moves the focus to it, without changing the route.
 *
 * Render it as a child of .creation-layout, beside the .creation-content column; the host
 * component renders each section as <section class="form-section" id="<section id>"> with an
 * h2. The sections are looked up in the host's shadow root. Styles: the creationLayout block
 * of styles.ts. The host is display: contents, so the nav and the select are grid items of
 * .creation-layout themselves.
 */
@customElement('alfio-section-nav')
export class SectionNav extends LitElement {

    // the sections of the form, in order
    @property({attribute: false})
    sections: readonly FormSection[] = [];

    // aria-label of the nav, e.g. "Event form sections"
    @property({type: String})
    label = 'Form sections';

    // id of the section currently in view (or last jumped to)
    @property({attribute: false})
    activeSection = '';

    private scrollFrame = 0;

    static readonly styles = [
        base,
        creationLayout,
        css`
            :host {
                display: contents;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        if (this.activeSection === '' && this.sections.length > 0) {
            this.activeSection = this.sections[0].id;
        }
        window.addEventListener('scroll', this.onPageScroll, {passive: true, capture: true});
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener('scroll', this.onPageScroll, true);
        cancelAnimationFrame(this.scrollFrame);
    }

    render(): TemplateResult {
        if (this.sections.length === 0) {
            return html`${nothing}`;
        }
        return html`
            <nav class="section-nav" aria-label=${this.label}>
                ${repeat(this.sections, (section) => section.id, (section) => html`
                    <a href=${`#${section.id}`} aria-current=${this.current(section.id)}
                       @click=${(event: Event) => {
                           event.preventDefault();
                           this.jumpToSection(section.id);
                       }}>${section.label}</a>
                `)}
            </nav>
            <sl-select class="mobile-section-nav" label="Jump to section" hoist .value=${this.activeSection}
                       @sl-change=${(event: Event) => this.jumpToSection(String((event.target as SlSelect).value))}>
                ${repeat(this.sections, (section) => section.id, (section) => html`
                    <sl-option value=${section.id}>${section.label}</sl-option>
                `)}
            </sl-select>
        `;
    }

    // back to the first section, e.g. after resetting the form
    reset(): void {
        if (this.sections.length > 0) {
            this.activeSection = this.sections[0].id;
        }
    }

    jumpToSection(id: string): void {
        const section = this.hostRoot().querySelector<HTMLElement>(`#${CSS.escape(id)}`);
        section?.focus({preventScroll: true});
        section?.scrollIntoView({block: 'start'});
        this.activeSection = id;
    }

    private current(id: string): string {
        if (this.activeSection === id) {
            return 'location';
        }
        return 'false';
    }

    // the shadow root of the host component, where the .form-section elements live
    private hostRoot(): ParentNode {
        return this.getRootNode() as ParentNode;
    }

    private onPageScroll = (): void => {
        cancelAnimationFrame(this.scrollFrame);
        this.scrollFrame = requestAnimationFrame(() => {
            let current = this.sections[0]?.id;
            const root = this.hostRoot();
            for (const section of this.sections) {
                const element = root.querySelector(`#${CSS.escape(section.id)}`);
                if (element != null && element.getBoundingClientRect().top <= 160) {
                    current = section.id;
                }
            }
            if (current != null) {
                this.activeSection = current;
            }
        });
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-section-nav': SectionNav;
    }
}
