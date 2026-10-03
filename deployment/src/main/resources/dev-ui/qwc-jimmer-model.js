import { LitElement, html, css } from 'lit';
import { jimmer } from 'build-time-data';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import '@vaadin/list-box';
import '@vaadin/item';
import '@vaadin/button';
import 'qui-alert';
import 'qui-badge';
import 'qwc-no-data';

/** Browse immutable model metadata without resolving a SQL client. */
export class QwcJimmerModel extends LitElement {
    static properties = {
        _search: { state: true },
        _selectedName: { state: true }
    };

    static styles = css`
        :host { display: block; box-sizing: border-box; padding: var(--lumo-space-m); color: var(--lumo-body-text-color); }
        h2 { margin: 0; font-size: var(--lumo-font-size-xl); }
        h3 { margin: 0; font-size: var(--lumo-font-size-l); }
        p { margin: var(--lumo-space-xs) 0 var(--lumo-space-m); }
        .muted { color: var(--lumo-secondary-text-color); }
        .browser { display: grid; grid-template-columns: minmax(220px, 28%) minmax(0, 1fr); gap: var(--lumo-space-l); margin-top: var(--lumo-space-l); }
        .selector { min-width: 0; }
        vaadin-text-field { width: 100%; }
        vaadin-list-box { max-height: 65vh; overflow-y: auto; border: 1px solid var(--lumo-contrast-10pct); border-radius: var(--lumo-border-radius-m); }
        vaadin-item { white-space: normal; }
        .entity-label { display: flex; min-width: 0; flex-direction: column; gap: var(--lumo-space-xs); }
        .entity-label small { color: var(--lumo-secondary-text-color); font-size: var(--lumo-font-size-xs); overflow-wrap: anywhere; }
        .details { min-width: 0; }
        .heading { display: flex; flex-wrap: wrap; align-items: center; gap: var(--lumo-space-s); margin-bottom: var(--lumo-space-s); }
        code { font-size: var(--lumo-font-size-s); overflow-wrap: anywhere; }
        .qualified-name { display: block; margin-bottom: var(--lumo-space-s); color: var(--lumo-secondary-text-color); }
        .declaration { margin: 0 0 var(--lumo-space-m); }
        .declaration dt { color: var(--lumo-secondary-text-color); font-size: var(--lumo-font-size-s); }
        .declaration dd { margin: var(--lumo-space-xs) 0 var(--lumo-space-s); overflow-wrap: anywhere; }
        .metadata { display: flex; flex-wrap: wrap; gap: var(--lumo-space-s); margin-bottom: var(--lumo-space-m); }
        .properties { width: 100%; height: min(560px, 68vh); min-height: 250px; border-color: var(--lumo-contrast-10pct); }
        .cell { white-space: normal; padding: var(--lumo-space-xs) 0; }
        .cell small { display: block; color: var(--lumo-secondary-text-color); overflow-wrap: anywhere; }
        .flags { display: flex; flex-wrap: wrap; gap: var(--lumo-space-xs); }
        .target { max-width: 100%; height: auto; min-height: var(--lumo-size-s); margin: 0; white-space: normal; text-align: left; }
        .target code { white-space: normal; }
        @media (max-width: 800px) {
            .browser { grid-template-columns: minmax(0, 1fr); gap: var(--lumo-space-m); }
            vaadin-list-box { max-height: 220px; }
            .properties { height: 480px; }
        }
        @media (max-width: 600px) { :host { padding: var(--lumo-space-s); } }
    `;

    constructor() {
        super();
        this._entities = [...(jimmer?.entities || [])].sort((a, b) => a.name.localeCompare(b.name));
        this._search = '';
        this._selectedName = this._entities[0]?.name || '';
    }

    render() {
        const filtered = this._filteredEntities();
        const selected = this._entities.find(entity => entity.name === this._selectedName);
        return html`
            <header>
                <h2>Declared model</h2>
                <p class="muted">Build-time entity declarations, property types and associations. Runtime naming strategies are not evaluated.</p>
            </header>
            ${jimmer?.overview?.enabled ? '' : html`<qui-alert level="info" permanent>
                Jimmer is disabled. The model below contains only metadata discovered during the build.
            </qui-alert>`}
            ${this._entities.length ? html`
                <div class="browser">
                    <aside class="selector" aria-label="Entity selection">
                        <vaadin-text-field label="Search entities" placeholder="Entity or package name" clear-button-visible
                            .value=${this._search} @value-changed=${this._searchChanged}>
                        </vaadin-text-field>
                        <p class="muted" role="status">${filtered.length} of ${this._entities.length} entities</p>
                        ${filtered.length ? html`
                            <vaadin-list-box aria-label="Entities"
                                .selected=${filtered.findIndex(entity => entity.name === this._selectedName)}
                                @selected-changed=${this._selectionChanged}>
                                ${filtered.map(entity => html`<vaadin-item>
                                    <span class="entity-label"><strong>${this._simpleName(entity)}</strong><small>${entity.name}</small></span>
                                </vaadin-item>`)}
                            </vaadin-list-box>
                        ` : html`<qwc-no-data message="No entities match your search."></qwc-no-data>`}
                    </aside>
                    <section class="details" aria-label="Entity properties">
                        ${selected ? this._entity(selected) : html`<qwc-no-data message="Select an entity to inspect its properties."></qwc-no-data>`}
                    </section>
                </div>
            ` : html`<qwc-no-data message="No entities were discovered."></qwc-no-data>`}
        `;
    }

    _filteredEntities() {
        const search = this._search.trim().toLowerCase();
        return this._entities.filter(entity => entity.name.toLowerCase().includes(search)
            || this._simpleName(entity).toLowerCase().includes(search));
    }

    _searchChanged(event) {
        this._search = event.detail.value || '';
        const filtered = this._filteredEntities();
        if (!filtered.some(entity => entity.name === this._selectedName)) {
            this._selectedName = filtered[0]?.name || '';
        }
    }

    _selectionChanged(event) {
        const entity = this._filteredEntities()[event.detail.value];
        if (entity) {
            this._selectedName = entity.name;
        }
    }

    _entity(entity) {
        const properties = entity.properties || [];
        return html`
            <div class="heading">
                <h3>${this._simpleName(entity)}</h3>
                <qui-badge small><span>${properties.length} properties</span></qui-badge>
            </div>
            <code class="qualified-name">${entity.name}</code>
            <div class="metadata">
                ${entity.microServiceName ? html`<qui-badge small><span>Microservice: ${entity.microServiceName}</span></qui-badge>` : ''}
            </div>
            <dl class="declaration">
                <dt>Declared table</dt>
                <dd>${entity.tableName ? html`<code>${entity.tableName}</code>` : 'Not explicitly declared'}</dd>
                ${(entity.superTypes || []).length ? html`
                    <dt>Supertypes</dt><dd><code>${entity.superTypes.join(', ')}</code></dd>
                ` : ''}
            </dl>
            ${properties.length ? html`
                <vaadin-grid aria-label="Entity properties" .items=${properties} class="properties" theme="no-border row-stripes">
                    <vaadin-grid-sort-column path="name" header="Property" auto-width resizable
                        ${columnBodyRenderer(property => html`<div class="cell"><code>${property.name}</code>
                            ${property.getterName ? html`<small>${property.getterName}()</small>` : ''}
                            ${property.declaredIn && property.declaredIn !== entity.name
                                ? html`<small>Inherited from ${property.declaredIn}</small>` : ''}
                        </div>`, [entity.name])}>
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="type" header="Type" auto-width resizable flex-grow="2"
                        ${columnBodyRenderer(property => html`<div class="cell"><code>${property.type}</code></div>`, [])}>
                    </vaadin-grid-sort-column>
                    <vaadin-grid-column header="Characteristics" width="240px" resizable
                        ${columnBodyRenderer(property => this._characteristics(property), [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Association target" width="260px" resizable
                        ${columnBodyRenderer(property => this._target(property), [])}>
                    </vaadin-grid-column>
                </vaadin-grid>
            ` : html`<qwc-no-data message="This entity has no discovered properties."></qwc-no-data>`}
        `;
    }

    _characteristics(property) {
        return html`<div class="cell flags">
            ${property.id ? html`<qui-badge level="primary" small><span>ID</span></qui-badge>` : ''}
            <qui-badge level="contrast" small><span>${property.nullable ? 'Nullable' : 'Required'}</span></qui-badge>
            ${property.association ? html`<qui-badge small><span>${property.association}</span></qui-badge>` : ''}
            ${property.transientProperty ? html`<qui-badge level="warning" small><span>Transient</span></qui-badge>` : ''}
            ${property.logicalDeleted ? html`<qui-badge level="warning" small><span>Logical delete</span></qui-badge>` : ''}
        </div>`;
    }

    _target(property) {
        if (!property.targetType) {
            return html`<span class="muted">—</span>`;
        }
        const target = this._entities.find(entity => entity.name === property.targetType);
        if (!target) {
            return html`<div class="cell"><code>${property.targetType}</code></div>`;
        }
        return html`<vaadin-button theme="tertiary small" class="target" title=${`View ${target.name}`}
            @click=${() => { this._search = ''; this._selectedName = target.name; }}>
            <code>${target.name}</code>
        </vaadin-button>`;
    }

    _simpleName(entity) {
        return entity.simpleName || entity.name.substring(entity.name.lastIndexOf('.') + 1);
    }
}

customElements.define('qwc-jimmer-model', QwcJimmerModel);
