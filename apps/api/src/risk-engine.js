export const DEFAULT_RISK = Object.freeze({
  maxStake: 0, riskPercent: 0.005, maxDailyLossPercent: 0.02,
  maxConsecutiveLosses: 3, maxTradesPerHour: 10, maxTradesPerDay: 30,
  maxOpenContracts: 1, minExpectedValue: 0, minSignalQuality: 70,
  minSampleSize: 1000, maxTickAgeMs: 3000, maxSignalAgeMs: 5000,
  maxExecutionLatencyMs: 1500,
});

export function evaluateTrade(candidate, state, limits = DEFAULT_RISK) {
  const reasons = [];
  const now = state.now ?? Date.now();
  if (state.emergencyStop) reasons.push('Emergency stop is active.');
  if (!state.authorized) reasons.push('Deriv trading authorization is unavailable.');
  if (!candidate.marketAvailable || !candidate.contractAvailable) reasons.push('Market or contract is unavailable.');
  if (!state.tickAt || now - state.tickAt > limits.maxTickAgeMs) reasons.push('Market data is stale.');
  if (!candidate.signalAt || now - candidate.signalAt > limits.maxSignalAgeMs) reasons.push('Signal has expired.');
  if (!candidate.proposalValid) reasons.push('Proposal is invalid or unavailable.');
  if (candidate.expectedValue == null || candidate.expectedValue < limits.minExpectedValue) reasons.push('Expected value is below the configured threshold.');
  if (candidate.signalQuality == null || candidate.signalQuality < limits.minSignalQuality) reasons.push('Signal quality is below the configured threshold.');
  if (candidate.sampleSize == null || candidate.sampleSize < limits.minSampleSize) reasons.push('Insufficient validated sample size.');
  if ((candidate.executionLatencyMs ?? Infinity) > limits.maxExecutionLatencyMs) reasons.push('Execution latency exceeds the configured limit.');
  if (candidate.stake == null || candidate.stake <= 0 || candidate.stake > limits.maxStake) reasons.push('Stake exceeds the configured risk cap.');
  if ((state.availableBalance ?? 0) < (candidate.stake ?? Infinity)) reasons.push('Available balance is insufficient.');
  if ((state.dailyLossPercent ?? 0) >= limits.maxDailyLossPercent) reasons.push('Daily loss limit has been reached.');
  if ((state.consecutiveLosses ?? 0) >= limits.maxConsecutiveLosses) reasons.push('Consecutive loss limit has been reached.');
  if ((state.tradesLastHour ?? 0) >= limits.maxTradesPerHour || (state.tradesToday ?? 0) >= limits.maxTradesPerDay) reasons.push('Trade-rate limit has been reached.');
  if ((state.openContracts ?? 0) >= limits.maxOpenContracts) reasons.push('Open-contract limit has been reached.');
  if (state.duplicateExecution) reasons.push('Duplicate execution was prevented.');
  return { allowed: reasons.length === 0, decision: reasons.length ? 'NO_TRADE' : 'TRADE', reasons };
}
