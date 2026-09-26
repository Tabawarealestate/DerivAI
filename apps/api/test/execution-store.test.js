import test from 'node:test';
import assert from 'node:assert/strict';
import { ExecutionStore } from '../src/execution-store.js';

test('reserves an execution ID once to prevent duplicate orders', () => {
  const store = new ExecutionStore();
  assert.equal(store.reserve('signal-1', { signalId: 'signal-1' }).reserved, true);
  const duplicate = store.reserve('signal-1', { signalId: 'signal-1' });
  assert.equal(duplicate.reserved, false);
  assert.equal(duplicate.record.status, 'GATED');
});
