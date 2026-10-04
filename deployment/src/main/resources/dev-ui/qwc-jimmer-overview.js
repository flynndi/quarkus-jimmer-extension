import { LitElement, html, css } from 'lit';
import { jimmer } from 'build-time-data';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import 'qui-alert';
import 'qui-badge';
import 'qwc-no-data';

/** Build-time configuration and discovered repository mappings. */
export class QwcJimmerOverview extends LitElement {
    static properties = {
        _repositorySearch: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            box-sizing: border-box;
            color: var(--lumo-body-text-color);
            padding: var(--lumo-space-m);
        }
        h2 { margin: 0; font-size: var(--lumo-font-size-xl); }
        h3 { margin: 0; font-size: var(--lumo-font-size-l); }
        p { margin: var(--lumo-space-xs) 0 var(--lumo-space-m); }
        .muted { color: var(--lumo-secondary-text-color); }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
            gap: var(--lumo-space-m);
            margin: var(--lumo-space-l) 0;
        }
        .summary-item, .feature {
            padding: var(--lumo-space-m);
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-base-color);
        }
        .summary-item dt { color: var(--lumo-secondary-text-color); font-size: var(--lumo-font-size-s); }
        .summary-item dd { margin: var(--lumo-space-xs) 0 0; font-size: var(--lumo-font-size-xl); font-weight: 600; }
        section { margin: var(--lumo-space-l) 0; }
        .section-heading { display: flex; flex-wrap: wrap; align-items: center; gap: var(--lumo-space-s); margin-bottom: var(--lumo-space-s); }
        .features { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr)); gap: var(--lumo-space-s); }
        .feature-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--lumo-space-s); }
        .feature p { margin: var(--lumo-space-s) 0 0; font-size: var(--lumo-font-size-s); overflow-wrap: anywhere; }
        vaadin-text-field { width: min(100%, 38rem); margin-bottom: var(--lumo-space-s); }
        vaadin-grid { width: 100%; border-color: var(--lumo-contrast-10pct); }
        .repositories { height: min(480px, 65vh); min-height: 220px; }
        code { font-size: var(--lumo-font-size-s); overflow-wrap: anywhere; }
        .cell { white-space: normal; padding: var(--lumo-space-xs) 0; }
        .cell small { display: block; margin-top: var(--lumo-space-xs); color: var(--lumo-secondary-text-color); }
        @media (max-width: 600px) {
            :host { padding: var(--lumo-space-s); }
            .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--lumo-space-s); }
        }
    `;

    constructor() {
        super();
        this._repositorySearch = '';
    }

    render() {
        const data = jimmer?.overview || {};
        const sources = data.dataSources || [];
        const repositories = data.repositories || [];
        return html`
            <header>
                <div class="section-heading">
                    <h2>Jimmer overview</h2>
                    <qui-badge level=${data.enabled ? 'success' : 'contrast'} small>
                        <span>${data.enabled ? 'Enabled' : 'Disabled'}</span>
                    </qui-badge>
                    ${data.microServiceName ? html`<qui-badge small><span>Microservice: ${data.microServiceName}</span></qui-badge>` : ''}
                </div>
                <p class="muted">Build-time configuration and discovered mappings.</p>
            </header>
            ${data.enabled ? '' : html`<qui-alert level="info" permanent>
                Jimmer is disabled. Set <code>quarkus.jimmer.enable=true</code> to enable the extension.
            </qui-alert>`}
            <dl class="summary">
                ${this._summary('Language', data.language || 'Not configured')}
                ${this._summary('JDBC data sources', sources.length)}
                ${this._summary('Entities', (jimmer?.entities || []).length)}
                ${this._summary('Repositories', repositories.length)}
            </dl>
            <section aria-labelledby="features-heading">
                <h3 id="features-heading" class="section-heading">Features</h3>
                ${(data.features || []).length ? html`<div class="features">
                    ${data.features.map(feature => this._feature(feature))}
                </div>` : html`<qwc-no-data message="No feature information is available."></qwc-no-data>`}
            </section>
            <section aria-labelledby="sources-heading">
                <h3 id="sources-heading" class="section-heading">Data sources</h3>
                <p class="muted">JDBC data sources discovered during the build. Runtime activation and connectivity are not checked.</p>
                ${sources.length ? html`
                    <vaadin-grid aria-label="Data sources" .items=${sources} theme="no-border row-stripes" all-rows-visible>
                        <vaadin-grid-column path="name" header="Data source" auto-width flex-grow="1"
                            ${columnBodyRenderer(source => html`<code>${this._sourceName(source.name)}</code>`, [])}>
                        </vaadin-grid-column>
                        <vaadin-grid-column path="dbKind" header="Database kind" auto-width flex-grow="1"></vaadin-grid-column>
                    </vaadin-grid>
                ` : html`<qwc-no-data message="No JDBC data sources were discovered."></qwc-no-data>`}
            </section>
            <section aria-labelledby="repositories-heading">
                <h3 id="repositories-heading" class="section-heading">Repository mappings</h3>
                ${this._repositories(repositories)}
            </section>
        `;
    }

    _summary(label, value) {
        return html`<div class="summary-item"><dt>${label}</dt><dd>${value}</dd></div>`;
    }

    _feature(feature) {
        const status = String(feature.status || 'Unknown');
        const positive = ['enabled', 'available', 'registered'].includes(status);
        return html`<div class="feature">
            <div class="feature-heading">
                <strong>${feature.name}</strong>
                <qui-badge level=${positive ? 'success' : status === 'unavailable' ? 'warning' : 'contrast'} small>
                    <span>${status.charAt(0).toUpperCase() + status.slice(1)}</span>
                </qui-badge>
            </div>
            ${feature.detail ? html`<p class="muted">${feature.detail}</p>` : ''}
        </div>`;
    }

    _repositories(repositories) {
        if (!repositories.length) {
            return html`<qwc-no-data message="No repository mappings were discovered."></qwc-no-data>`;
        }
        const search = this._repositorySearch.trim().toLowerCase();
        const filtered = repositories.filter(repository => [repository.name, repository.entityType,
            repository.dataSource, repository.kind, repository.style]
            .some(value => String(value || '').toLowerCase().includes(search)));
        return html`
            <vaadin-text-field label="Search repositories" placeholder="Repository, entity or data source" clear-button-visible
                .value=${this._repositorySearch} @value-changed=${event => { this._repositorySearch = event.detail.value || ''; }}>
            </vaadin-text-field>
            <p class="muted" role="status">${filtered.length} of ${repositories.length} repositories</p>
            ${filtered.length ? html`
                <vaadin-grid aria-label="Repository mappings" .items=${filtered} class="repositories" theme="no-border row-stripes">
                    <vaadin-grid-sort-column path="name" header="Repository" auto-width resizable flex-grow="2"
                        ${columnBodyRenderer(repository => html`<div class="cell"><code>${repository.name}</code>
                            <small>${repository.style === 'legacy' ? 'Legacy repository' : 'Application repository'}</small></div>`, [])}>
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="entityType" header="Entity / ID" auto-width resizable flex-grow="2"
                        ${columnBodyRenderer(repository => html`<div class="cell"><code>${repository.entityType || '—'}</code>
                            <small>ID: <code>${repository.idType || '—'}</code></small></div>`, [])}>
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="dataSource" header="Data source" auto-width resizable
                        ${columnBodyRenderer(repository => html`<div class="cell">
                            <code>${repository.dataSource ? this._sourceName(repository.dataSource) : 'Not determined'}</code>
                            <small>${repository.bindingSource || ''}</small></div>`, [])}>
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="kind" header="Language" auto-width resizable></vaadin-grid-sort-column>
                </vaadin-grid>
            ` : html`<qwc-no-data message="No repositories match your search."></qwc-no-data>`}
        `;
    }

    _sourceName(name) {
        return !name || name === '<default>' ? 'Default' : name;
    }
}

customElements.define('qwc-jimmer-overview', QwcJimmerOverview);
