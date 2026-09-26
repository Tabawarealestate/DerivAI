const ENDPOINT = 'wss://ws.derivws.com/websockets/v3';

export class DerivConnector {
  #socket; #pending = new Map(); #subscriptions = new Map(); #requestId = 0; #connected = false;
  constructor({ appId, onEvent = () => {}, WebSocketImpl = globalThis.WebSocket } = {}) {
    if (!appId) throw new Error('DERIV_APP_ID is required for a Deriv connection.');
    this.appId = appId; this.onEvent = onEvent; this.WebSocketImpl = WebSocketImpl;
  }
  async connect() {
    if (this.#connected) return;
    this.#socket = new this.WebSocketImpl(`${ENDPOINT}?app_id=${encodeURIComponent(this.appId)}`);
    await new Promise((resolve, reject) => {
      this.#socket.addEventListener('open', () => { this.#connected = true; resolve(); }, { once: true });
      this.#socket.addEventListener('error', () => reject(new Error('Deriv WebSocket connection failed.')), { once: true });
    });
    this.#socket.addEventListener('message', event => this.#handle(event.data));
    this.#socket.addEventListener('close', () => { this.#connected = false; this.onEvent({ type: 'connection_lost' }); });
  }
  async request(payload) {
    await this.connect();
    const req_id = ++this.#requestId;
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { this.#pending.delete(req_id); reject(new Error('Deriv request timed out.')); }, 10000);
      this.#pending.set(req_id, { resolve, reject, timeout });
      this.#socket.send(JSON.stringify({ ...payload, req_id }));
    });
  }
  activeSymbols() { return this.request({ active_symbols: 'brief' }); }
  contractsFor(underlyingSymbol) { return this.request({ contracts_for: underlyingSymbol }); }
  tickHistory(symbol, count = 1000) { return this.request({ ticks_history: symbol, count, end: 'latest', style: 'ticks' }); }
  subscribeTicks(symbol) { return this.request({ ticks: symbol, subscribe: 1 }); }
  balance() { return this.request({ balance: 1 }); }
  portfolio() { return this.request({ portfolio: 1 }); }
  openContract(contractId) { return this.request({ proposal_open_contract: 1, contract_id: contractId, subscribe: 1 }); }
  // Proposal/buy/sell remain deliberately unavailable until phases 1–11 have passed in credentialed staging.
  #handle(raw) {
    let message; try { message = JSON.parse(raw); } catch { this.onEvent({ type: 'invalid_message' }); return; }
    if (!message || typeof message !== 'object') return;
    const pending = this.#pending.get(message.req_id);
    if (pending) { clearTimeout(pending.timeout); this.#pending.delete(message.req_id); message.error ? pending.reject(new Error(message.error.message || 'Deriv rejected the request.')) : pending.resolve(message); }
    if (message.msg_type === 'tick' && message.tick?.epoch && message.tick?.quote != null) this.onEvent({ type: 'tick_received', symbol: message.echo_req?.ticks, tick: { epoch: message.tick.epoch, quote: message.tick.quote } });
  }
}
