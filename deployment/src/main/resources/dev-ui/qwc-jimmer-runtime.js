import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/progress-bar';
import 'qui-alert';
import 'qui-badge';
import 'qwc-no-data';

/** Read-only diagnostics; inspecting a client does not initialize it. */
export class QwcJimmerRuntime extends LitElement {
    jsonRpc = new JsonRpc(this);

    static properties = {
        _enabled: { state: true },
        _language: { state: true },
        _clients: { state: true },
        _selectedName: { state: true },
        _client: { state: true },
        _listLoading: { state: true },
        _detailLoading: { state: true },
        _listError: { state: true },
        _detailError: { state: true }
    };

    static styles = css`
        :host { display: block; box-sizing: border-box; padding: var(--lumo-space-m); color: var(--lumo-body-text-color); }
        h2 { margin: 0; font-size: var(--lumo-font-size-xl); }
        h3 { margin: 0; font-size: var(--lumo-font-size-l); }
        h4 { margin: 0 0 var(--lumo-space-s); font-size: var(--lumo-font-size-m); }
        p { margin: var(--lumo-space-xs) 0 var(--lumo-space-m); }
        .muted { color: var(--lumo-secondary-text-color); }
        .toolbar, .heading { display: flex; align-items: center; flex-wrap: wrap; gap: var(--lumo-space-s); }
        .toolbar { justify-content: space-between; margin-bottom: var(--lumo-space-s); }
        .browser { display: grid; grid-template-columns: minmax(220px, 30%) minmax(0, 1fr); gap: var(--lumo-space-l); margin-top: var(--lumo-space-l); }
        .clients { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--lumo-space-s); }
        .client { padding: var(--lumo-space-m); border: 1px solid var(--lumo-contrast-10pct); border-radius: var(--lumo-border-radius-m); min-width: 0; }
        .client.selected { border-color: var(--lumo-primary-color); background: var(--lumo-primary-color-10pct); }
        .client p { margin: var(--lumo-space-s) 0; font-size: var(--lumo-font-size-s); overflow-wrap: anywhere; }
        .client strong { overflow-wrap: anywhere; }
        .details { min-width: 0; }
        .details > section { margin: var(--lumo-space-l) 0; }
        .status-detail { margin-top: var(--lumo-space-s); overflow-wrap: anywhere; }
        dl { margin: 0; border: 1px solid var(--lumo-contrast-10pct); border-radius: var(--lumo-border-radius-m); overflow: hidden; }
        .value-row { display: grid; grid-template-columns: minmax(160px, 42%) minmax(0, 1fr); gap: var(--lumo-space-m); padding: var(--lumo-space-s) var(--lumo-space-m); }
        .value-row:nth-child(even) { background: var(--lumo-contrast-5pct); }
        dt { color: var(--lumo-secondary-text-color); }
        dd { margin: 0; min-width: 0; }
        code { font-size: var(--lumo-font-size-s); overflow-wrap: anywhere; white-space: normal; }
        vaadin-progress-bar { margin: var(--lumo-space-s) 0; }
        qui-alert { display: block; margin: var(--lumo-space-m) 0; }
        @media (max-width: 800px) {
            .browser { grid-template-columns: minmax(0, 1fr); gap: var(--lumo-space-m); }
            .clients { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr)); }
        }
        @media (max-width: 600px) {
            :host { padding: var(--lumo-space-s); }
            .value-row { grid-template-columns: minmax(0, 1fr); gap: var(--lumo-space-xs); }
        }
    `;

    constructor() {
        super();
        this._enabled = null;
        this._language = '';
        this._clients = [];
        this._selectedName = null;
        this._client = null;
        this._listLoading = false;
        this._detailLoading = false;
        this._listError = '';
        this._detailError = '';
        this._listRequest = 0;
        this._detailRequest = 0;
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    disconnectedCallback() {
        // JSON-RPC promises are not cancellable. Invalidate responses, including after a later reconnection.
        this._listRequest++;
        this._detailRequest++;
        super.disconnectedCallback();
    }

    async _refresh() {
        const request = ++this._listRequest;
        this._detailRequest++;
        this._client = null;
        this._detailError = '';
        this._detailLoading = false;
        this._listError = '';
        this._listLoading = true;
        try {
            const response = await this.jsonRpc.getClients();
            if (!this.isConnected || request !== this._listRequest) return;
            const result = response?.result;
            if (!result || !Array.isArray(result.clients)) throw new Error('Invalid client list');
            this._enabled = result.enabled;
            this._language = result.language || '';
            this._clients = result.clients;
            if (!this._enabled || !this._clients.some(client => client.name === this._selectedName)) {
                this._selectedName = null;
            }
            this._listLoading = false;
            // A refresh may update an explicitly selected client; initial loading never selects one.
            if (this._selectedName !== null) this._loadClient(this._selectedName);
        } catch (_) {
            if (!this.isConnected || request !== this._listRequest) return;
            this._clients = [];
            this._selectedName = null;
            this._enabled = null;
            this._listLoading = false;
            this._listError = 'Unable to load client diagnostics. Refresh to retry.';
        }
    }

    _selectClient(name) {
        if (this._listLoading) return;
        this._selectedName = name;
        this._loadClient(name);
    }

    async _loadClient(name) {
        const request = ++this._detailRequest;
        this._client = null;
        this._detailError = '';
        this._detailLoading = true;
        try {
            const response = await this.jsonRpc.getClient({ name });
            if (!this.isConnected || request !== this._detailRequest || name !== this._selectedName) return;
            const result = response?.result;
            if (!result || result.name !== name) throw new Error('Invalid client details');
            this._client = result;
            // Keep the list's status consistent with the newer detail snapshot.
            this._clients = this._clients.map(client => client.name === name
                ? { ...client, active: result.active, state: result.state, detail: result.detail } : client);
            this._detailLoading = false;
        } catch (_) {
            if (!this.isConnected || request !== this._detailRequest || name !== this._selectedName) return;
            this._detailLoading = false;
            this._detailError = 'Unable to inspect this client. Select it again or refresh to retry.';
        }
    }

    render() {
        return html`
            <header>
                <div class="toolbar">
                    <h2>Runtime diagnostics</h2>
                    <vaadin-button @click=${this._refresh}>Refresh clients</vaadin-button>
                </div>
                <p class="muted">Inspect client state and selected configuration without initializing SQL clients or opening database connections.</p>
                <p class="muted">Snapshots update when you refresh or inspect a client.</p>
            </header>
            ${this._listLoading ? html`<div role="status">Loading client states…</div>
                <vaadin-progress-bar indeterminate aria-label="Loading client states"></vaadin-progress-bar>` : ''}
            ${this._listError ? html`<qui-alert level="error" permanent>${this._listError}</qui-alert>` : ''}
            ${this._enabled === false ? html`<qui-alert level="info" permanent>
                Jimmer is disabled. There are no managed SQL clients to inspect.
            </qui-alert>` : ''}
            ${this._clients.length ? html`
                <p class="muted" role="status">${this._clients.length} clients${this._language ? ` · ${this._language}` : ''}</p>
                <div class="browser">
                    <ul class="clients" aria-label="SQL clients">
                        ${this._clients.map(client => this._clientItem(client))}
                    </ul>
                    <section class="details" aria-label="Selected client diagnostics" aria-busy=${this._detailLoading}>
                        ${this._renderDetail()}
                    </section>
                </div>
            ` : !this._listLoading && !this._listError && this._enabled !== false
                ? html`<qwc-no-data message="No managed SQL clients are available."></qwc-no-data>` : ''}
        `;
    }

    _clientItem(client) {
        return html`<li class=${`client${client.name === this._selectedName ? ' selected' : ''}`}>
            <div class="heading"><strong>${this._displayName(client.name)}</strong>${this._stateBadge(client.state)}</div>
            ${client.detail ? html`<p class="muted">${client.detail}</p>` : ''}
            <vaadin-button theme="small" ?disabled=${this._listLoading || this._enabled === false}
                aria-label=${`Inspect ${this._displayName(client.name)}`} aria-pressed=${client.name === this._selectedName}
                @click=${() => this._selectClient(client.name)}>Inspect</vaadin-button>
        </li>`;
    }

    _renderDetail() {
        if (this._selectedName === null) {
            return html`<qwc-no-data message="Select a client to inspect its configuration and initialized state."></qwc-no-data>`;
        }
        return html`
            <div class="heading"><h3>${this._displayName(this._selectedName)}</h3>
                ${this._client ? this._stateBadge(this._client.state) : ''}</div>
            ${this._detailLoading ? html`<p role="status">Loading client details…</p>
                <vaadin-progress-bar indeterminate aria-label="Loading client details"></vaadin-progress-bar>` : ''}
            ${this._detailError ? html`<qui-alert level="error" permanent>${this._detailError}</qui-alert>` : ''}
            ${this._client ? this._clientDetails(this._client) : ''}
        `;
    }

    _clientDetails(client) {
        const configured = client.configured;
        const actual = client.actual;
        return html`
            ${client.detail ? html`<p class="muted status-detail">${client.detail}</p>` : ''}
            <p class="muted">Active: ${client.active === null || client.active === undefined ? 'Not determined' : client.active ? 'Yes' : 'No'}</p>
            <section aria-label="Configured values">
                <h4>Configured values</h4>
                ${configured ? html`<dl>
                    ${this._value('Activation', configured.active)}
                    ${this._value('Dialect', configured.dialect)}
                    ${this._value('Trigger type', configured.triggerType)}
                    ${this._value('Mutations require a transaction', configured.mutationTransactionRequired)}
                    ${this._value('Database validation', configured.databaseValidationMode)}
                    ${this._value('Cache retry interval', configured.cacheRetryInterval)}
                </dl>` : html`<p class="muted">Configuration is unavailable.</p>`}
            </section>
            <section aria-label="Initialized client">
                <h4>Initialized client</h4>
                ${actual ? html`<dl>
                    ${this._value('Dialect', actual.dialect)}
                    ${this._value('Connection manager', actual.connectionManager)}
                    ${this._value('Transaction capable', actual.transactionCapable)}
                    ${this._value('Trigger type', actual.triggerType)}
                    ${this._value('Mutations require a transaction', actual.mutationTransactionRequired)}
                    ${this._value('Cache operator', actual.cacheOperator)}
                    ${this._value('Transaction cache operator', actual.transactionCacheOperator)}
                    ${this._value('Object caches', actual.objectCacheCount)}
                    ${this._value('Property caches', actual.propertyCacheCount)}
                </dl>` : html`<qwc-no-data .message=${client.actualDetail || 'No initialized client is available. Inspection does not initialize it.'}></qwc-no-data>`}
                ${actual && client.actualDetail ? html`<p class="muted status-detail">${client.actualDetail}</p>` : ''}
            </section>
        `;
    }

    _value(label, value) {
        const text = value === null || value === undefined || value === '' ? 'Not available'
            : typeof value === 'boolean' ? value ? 'Yes' : 'No' : String(value);
        return html`<div class="value-row"><dt>${label}</dt><dd><code>${text}</code></dd></div>`;
    }

    _stateBadge(state) {
        const labels = { initialized: 'Initialized', uninitialized: 'Uninitialized', inactive: 'Inactive',
            unavailable: 'Unavailable', ambiguous: 'Ambiguous' };
        const level = state === 'initialized' ? 'success'
            : state === 'unavailable' || state === 'ambiguous' ? 'warning' : 'contrast';
        return html`<qui-badge level=${level} small><span>${labels[state] || 'Unknown'}</span></qui-badge>`;
    }

    _displayName(name) {
        return name === '<default>' ? 'Default' : name;
    }
}

customElements.define('qwc-jimmer-runtime', QwcJimmerRuntime);
