const connection = document.querySelector('#connection');
const marketStatus = document.querySelector('#marketStatus');
const connect = document.querySelector('#connect');
const detail = document.querySelector('#connection-detail');
const dialog = document.querySelector('#dialog');
const toast = document.querySelector('#toast');

function openDialog(label, title, body) {
  document.querySelector('#dialog-label').textContent = label;
  document.querySelector('#dialog-title').textContent = title;
  document.querySelector('#dialog-body').textContent = body;
  dialog.showModal();
}
function announce(message) { toast.textContent = message; toast.classList.add('visible'); setTimeout(() => toast.classList.remove('visible'), 3500); }

async function loadStatus() {
  try {
    const response = await fetch('/api/status', { credentials: 'same-origin' });
    const status = await response.json();
    marketStatus.textContent = status.marketData;
    const ready = status.oauthConfigured;
    connection.textContent = ready ? 'CONNECTION REQUIRED' : 'SETUP REQUIRED';
    detail.textContent = ready ? 'Secure server-side Deriv OAuth is ready. Sign in to retrieve verified account and market data.' : 'The server needs Deriv OAuth configuration before a secure account connection can begin.';
    connect.textContent = ready ? 'LOGIN WITH DERIV' : 'CONNECTION SETUP REQUIRED';
    connect.dataset.ready = String(ready);
  } catch {
    connection.textContent = 'DATA UNAVAILABLE'; marketStatus.textContent = 'DATA UNAVAILABLE';
  }
}
connect.addEventListener('click', () => {
  if (connect.dataset.ready === 'true') window.location.assign('/api/auth/deriv/start');
  else openDialog('CONNECTION SETUP', 'Deriv connection is not configured', 'For your security, credentials must be configured on the server by the owner. No token is accepted or stored in this browser.');
});
document.querySelector('#access-code').addEventListener('click', () => openDialog('SUBSCRIPTION ACCESS', 'Access-code service is coming next', 'Access codes will be verified securely by the backend. Subscription access never grants trading permission.'));
document.querySelector('#help').addEventListener('click', () => openDialog('SUPPORT', 'Need help?', 'Use Contact Support below to reach the configured support channel.'));
document.querySelector('#support').addEventListener('click', () => openDialog('SUPPORT', 'Support contact not configured', 'The application owner must configure the WhatsApp, Telegram, or email support channel before it can be used.'));
document.querySelector('#emergency').addEventListener('click', () => openDialog('EMERGENCY STOP', 'No active trading session', 'No trading session is active. When live trading is implemented and explicitly enabled, this control will stop new executions and preserve position monitoring.'));
document.querySelectorAll('.dialog-close').forEach(button => button.addEventListener('click', () => dialog.close()));
document.querySelectorAll('[data-section]').forEach(button => button.addEventListener('click', () => announce(`${button.dataset.section} is coming soon. Live data is never simulated.`)));
loadStatus();
