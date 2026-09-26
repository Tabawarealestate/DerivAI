import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_RISK, evaluateTrade } from '../src/risk-engine.js';

const now = 1_700_000_000_000;
const candidate = { marketAvailable: true, contractAvailable: true, signalAt: now - 100, proposalValid: true, expectedValue: 0.04, signalQuality: 82, sampleSize: 1500, executionLatencyMs: 80, stake: 2 };
const state = { now, authorized: true, tickAt: now - 100, availableBalance: 100, dailyLossPercent: 0, consecutiveLosses: 0, tradesLastHour: 0, tradesToday: 0, openContracts: 0 };
const limits = { ...DEFAULT_RISK, maxStake: 2 };

test('allows a fresh, validated candidate inside conservative limits', () => assert.deepEqual(evaluateTrade(candidate, state, limits), { allowed: true, decision: 'TRADE', reasons: [] }));
test('rejects a stale tick even when other inputs are valid', () => { const result = evaluateTrade(candidate, { ...state, tickAt: now - 4000 }, limits); assert.equal(result.decision, 'NO_TRADE'); assert.match(result.reasons.join(' '), /stale/); });
test('rejects daily loss and duplicate execution', () => { const result = evaluateTrade(candidate, { ...state, dailyLossPercent: .02, duplicateExecution: true }, limits); assert.equal(result.allowed, false); assert.equal(result.reasons.length, 2); });
