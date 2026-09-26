import crypto from 'node:crypto';

export function createPkceChallenge() {
  const verifier = crypto.randomBytes(48).toString('base64url');
  const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  return { verifier, challenge, state: crypto.randomBytes(24).toString('base64url') };
}

export function oauthConfigured(env) {
  return Boolean(env.DERIV_OAUTH_CLIENT_ID && env.DERIV_REDIRECT_URI && env.DERIV_OAUTH_AUTHORIZE_URL && env.DERIV_OAUTH_TOKEN_URL);
}
