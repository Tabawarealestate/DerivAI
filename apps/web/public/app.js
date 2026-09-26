const connection = document.querySelector('#connection');
const marketStatus = document.querySelector('#marketStatus');
const connect = document.querySelector('#connect');

async function loadStatus() {
  try {
    const response = await fetch('/api/status', { credentials: 'same-origin' });
    const status = await response.json();
    marketStatus.textContent = status.marketData;
    connection.textContent = status.oauthConfigured ? 'SERVER CONFIGURED — CONNECTION REQUIRED' : 'DATA UNAVAILABLE';
    connect.disabled = !status.oauthConfigured;
    connect.title = status.oauthConfigured ? 'Start secure Deriv OAuth sign-in' : 'Deriv OAuth requires server configuration.';
  } catch {
    marketStatus.textContent = 'DATA UNAVAILABLE';
    connection.textContent = 'DATA UNAVAILABLE';
    connect.disabled = true;
  }
}
connect.addEventListener('click', () => { window.location.assign('/api/auth/deriv/start'); });
loadStatus();
