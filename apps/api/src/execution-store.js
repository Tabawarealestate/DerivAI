/** In production this interface is backed by a PostgreSQL unique execution_id constraint. */
export class ExecutionStore {
  #executions = new Map();
  reserve(executionId, metadata) {
    if (this.#executions.has(executionId)) return { reserved: false, record: this.#executions.get(executionId) };
    const record = Object.freeze({ executionId, status: 'GATED', createdAt: new Date().toISOString(), ...metadata });
    this.#executions.set(executionId, record);
    return { reserved: true, record };
  }
  get(executionId) { return this.#executions.get(executionId) ?? null; }
}
