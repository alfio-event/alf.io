import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {Chart, registerables} from 'chart.js';
import type {ChartConfiguration, ChartData, ChartOptions, ChartType} from 'chart.js';

Chart.register(...registerables);

/**
 * Reads a Shoelace color token (e.g. '--sl-color-success-500') from the document, so that
 * charts use the same palette as the rest of the admin
 */
export function slColor(token: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim();
    if (value !== '') {
        return value;
    }
    return fallback;
}

export type AlfioChartType = 'pie' | 'doughnut' | 'bar' | 'line';

/**
 * A Chart.js chart as a Lit component: pass the chart type, the data and the options as
 * properties; the canvas chart is created on first render, updated when they change and
 * destroyed when the component is disconnected.
 *
 *   <alfio-chart type="pie" .data=${pieData} .options=${pieOptions} aria-label="Ticket status"></alfio-chart>
 *
 * The canvas gets role="img" and the aria-label, so screen readers announce the graph;
 * the data should also be available as text nearby (e.g. a legend or a summary list).
 */
@customElement('alfio-chart')
export class AlfioChart extends LitElement {

    @property({type: String}) type: AlfioChartType = 'pie';

    @property({attribute: false}) data: ChartData | null = null;

    @property({attribute: false}) options: ChartOptions | null = null;

    @property({type: String, attribute: 'aria-label'}) ariaLabel = '';

    @query('canvas') private canvas?: HTMLCanvasElement;

    private chart: Chart | null = null;

    // Chart.js sizes the canvas from its container: the host, which fills the box the page gives it
    // (e.g. a div with a fixed height). Without a height, the chart would follow its aspect ratio
    // and overflow that box.
    static readonly styles = css`
        :host {
            display: block;
            position: relative;
            height: 100%;
            min-width: 0;
        }
        canvas {
            max-width: 100%;
        }
    `;

    render(): TemplateResult {
        return html`<canvas role="img" aria-label=${this.ariaLabel || nothing}></canvas>`;
    }

    protected firstUpdated(): void {
        this.createChart();
    }

    protected updated(changed: PropertyValues<this>): void {
        if (changed.has('type')) {
            this.createChart();
            return;
        }
        if (this.chart != null && (changed.has('data') || changed.has('options'))) {
            this.chart.data = this.data ?? {labels: [], datasets: []};
            if (this.options != null) {
                this.chart.options = this.options;
            }
            this.chart.update();
        }
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        this.chart?.destroy();
        this.chart = null;
    }

    private createChart(): void {
        if (this.canvas == null) {
            return;
        }
        this.chart?.destroy();
        const type: ChartType = this.type;
        this.chart = new Chart(this.canvas, {
            type,
            data: this.data ?? {labels: [], datasets: []},
            options: this.options ?? {},
        } as ChartConfiguration);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-chart': AlfioChart;
    }
}
