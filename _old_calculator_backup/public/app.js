// State variables
let currentInput = '0';
let previousInput = null;
let currentOperator = null;
let awaitingNextNumber = false;
let lastCalculation = null; // { expression: string, result: number }

// DOM Elements
const screenMain = document.getElementById('screenMain');
const screenHistory = document.getElementById('screenHistory');
const explainBtn = document.getElementById('explainBtn');
const storyContent = document.getElementById('storyContent');
const lastCalculationBadge = document.getElementById('lastCalculationBadge');
const toggleKeySettings = document.getElementById('toggleKeySettings');
const apiKeyDrawer = document.getElementById('apiKeyDrawer');
const apiKeyInput = document.getElementById('apiKeyInput');
const saveKeyBtn = document.getElementById('saveKeyBtn');
const clearKeyBtn = document.getElementById('clearKeyBtn');

// Initialize API Key from localStorage
const storedKey = localStorage.getItem('gemini_api_key');
if (storedKey) {
  apiKeyInput.value = storedKey;
}

// Update Screen Display
function updateDisplay() {
  screenMain.textContent = currentInput;
  if (previousInput !== null && currentOperator) {
    screenHistory.textContent = `${previousInput} ${currentOperator}`;
  } else if (lastCalculation) {
    screenHistory.textContent = lastCalculation.expression + ' =';
  } else {
    screenHistory.innerHTML = '&nbsp;';
  }
}

// Handle Number Input
function inputDigit(digit) {
  if (awaitingNextNumber) {
    currentInput = digit;
    awaitingNextNumber = false;
  } else {
    currentInput = currentInput === '0' ? digit : currentInput + digit;
  }
  updateDisplay();
}

// Handle Decimal Input
function inputDecimal() {
  if (awaitingNextNumber) {
    currentInput = '0.';
    awaitingNextNumber = false;
    updateDisplay();
    return;
  }
  if (!currentInput.includes('.')) {
    currentInput += '.';
    updateDisplay();
  }
}

// Handle Operator Input
function handleOperator(op) {
  const inputValue = parseFloat(currentInput);

  if (previousInput === null) {
    previousInput = inputValue;
  } else if (currentOperator && !awaitingNextNumber) {
    calculateSync();
    previousInput = parseFloat(currentInput);
  }

  currentOperator = op;
  awaitingNextNumber = true;
  updateDisplay();
}

// Synchronous Fallback Calculation
function calculateSync() {
  if (previousInput === null || currentOperator === null) return;

  const a = previousInput;
  const b = parseFloat(currentInput);
  let res = 0;

  switch (currentOperator) {
    case '+':
      res = a + b;
      break;
    case '-':
    case '−':
      res = a - b;
      break;
    case '×':
    case '*':
      res = a * b;
      break;
    case '÷':
    case '/':
      if (b === 0) {
        currentInput = 'Error';
        previousInput = null;
        currentOperator = null;
        awaitingNextNumber = true;
        updateDisplay();
        return;
      }
      res = a / b;
      break;
    default:
      return;
  }

  const rounded = Number(Math.round(res + 'e+10') + 'e-10');
  const expr = `${a} ${currentOperator} ${b}`;
  
  lastCalculation = {
    expression: expr,
    result: rounded
  };

  currentInput = String(rounded);
  previousInput = null;
  currentOperator = null;
  awaitingNextNumber = true;
  
  if (lastCalculationBadge) {
    lastCalculationBadge.textContent = `${expr} = ${rounded}`;
  }

  updateDisplay();
}

// Calculate via Backend API
async function performCalculation() {
  if (previousInput === null || currentOperator === null) return;

  const a = previousInput;
  const b = parseFloat(currentInput);
  const op = currentOperator;

  try {
    const response = await fetch('/api/calculate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ num1: a, num2: b, operation: op })
    });

    const data = await response.json();

    if (response.ok && data.success) {
      currentInput = String(data.result);
      lastCalculation = {
        expression: `${a} ${op} ${b}`,
        result: data.result
      };
      if (lastCalculationBadge) {
        lastCalculationBadge.textContent = `${lastCalculation.expression} = ${data.result}`;
      }
      previousInput = null;
      currentOperator = null;
      awaitingNextNumber = true;
      updateDisplay();
    } else {
      currentInput = data.error || 'Error';
      previousInput = null;
      currentOperator = null;
      awaitingNextNumber = true;
      updateDisplay();
    }
  } catch (err) {
    calculateSync();
  }
}

// Clear all
function allClear() {
  currentInput = '0';
  previousInput = null;
  currentOperator = null;
  awaitingNextNumber = false;
  updateDisplay();
}

// Backspace
function backspace() {
  if (awaitingNextNumber) return;
  if (currentInput.length > 1) {
    currentInput = currentInput.slice(0, -1);
  } else {
    currentInput = '0';
  }
  updateDisplay();
}

// Negate sign
function negate() {
  const val = parseFloat(currentInput);
  if (val !== 0) {
    currentInput = String(-val);
    updateDisplay();
  }
}

// Percentage
function percent() {
  const val = parseFloat(currentInput);
  currentInput = String(val / 100);
  updateDisplay();
}

/**
 * Direct call from Browser to Gemini API (failsafe if backend network is restricted)
 */
async function callGeminiDirectlyFromBrowser(expression, result, apiKey) {
  const prompt = `You are a friendly, witty math companion. Convert the following calculation into a single, fun, imaginative real-world English sentence using everyday objects, animals, or situations (for instance, for 3 + 2 = 5: "3 potatoes put together with 2 more gives 5 total potatoes!"). Keep it creative and within 1-2 short sentences. Do not return quotes or markdown headers.

Calculation: ${expression} = ${result}`;

  const payload = {
    contents: [
      {
        parts: [{ text: prompt }]
      }
    ]
  };

  const candidateModels = [
    'gemini-2.5-flash',
    'gemini-2.0-flash',
    'gemini-1.5-flash',
    'gemini-3.8-flash',
    'gemini-flash-latest'
  ];

  let lastError = null;

  for (const model of candidateModels) {
    try {
      const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(apiKey)}`;
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await response.json();

      if (!response.ok) {
        lastError = new Error(data?.error?.message || `HTTP ${response.status}`);
        continue;
      }

      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (text) {
        return { success: true, story: text.trim(), modelUsed: model };
      }
    } catch (err) {
      lastError = err;
    }
  }

  throw lastError || new Error('Failed to generate story from Gemini API');
}

// Render Story Card helper
function renderStorySuccess(story, modelUsed, expr, result) {
  storyContent.innerHTML = `
    <div class="story-bubble">
      <div class="story-tag">✨ Gemini Story (${escapeHtml(expr)} = ${escapeHtml(String(result))})</div>
      <div class="story-text">"${escapeHtml(story)}"</div>
      <div class="story-meta">
        <span>Powered by Google Gemini</span>
        <span>Model: ${escapeHtml(modelUsed || 'gemini-2.5-flash')}</span>
      </div>
    </div>
  `;
}

// Explain with Gemini AI
async function explainWithAI() {
  if (!lastCalculation) {
    const currentVal = parseFloat(currentInput);
    if (!isNaN(currentVal) && currentVal !== 0) {
      lastCalculation = {
        expression: String(currentVal),
        result: currentVal
      };
    } else {
      storyContent.innerHTML = `
        <div class="alert-box info">
          <h4>💡 Ready for Math!</h4>
          <p>Please perform a calculation first (e.g. <code>9 × 2 =</code>), then tap <strong>"Explain with Gemini AI"</strong>.</p>
        </div>
      `;
      return;
    }
  }

  // Display loading indicator
  storyContent.innerHTML = `
    <div class="loading-box">
      <div class="spinner"></div>
      <p>Gemini is weaving a story from your math...</p>
    </div>
  `;
  explainBtn.disabled = true;

  const keyToUse = apiKeyInput.value.trim() || localStorage.getItem('gemini_api_key') || '';

  if (!keyToUse) {
    apiKeyDrawer.classList.remove('hidden');
    storyContent.innerHTML = `
      <div class="alert-box">
        <h4>🔑 Optional Gemini Key Needed for AI Stories</h4>
        <p>No Gemini API key detected. You can paste your key in the box above and click Save, or run the server with <code>export GEMINI_API_KEY="..."</code>.</p>
      </div>
    `;
    explainBtn.disabled = false;
    return;
  }

  try {
    // 1. Try via backend proxy first
    let data = null;
    let backendFailed = false;

    try {
      const response = await fetch('/api/explain', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expression: lastCalculation.expression,
          result: lastCalculation.result,
          apiKey: keyToUse
        })
      });
      data = await response.json();
    } catch (netErr) {
      backendFailed = true;
    }

    // If backend succeeded
    if (data && data.success) {
      renderStorySuccess(data.story, data.modelUsed, lastCalculation.expression, lastCalculation.result);
      return;
    }

    // 2. If backend failed due to fetch failed/network restriction, fallback to direct browser fetch
    if (backendFailed || (data && (data.error === 'NETWORK_ERROR' || (data.message && data.message.includes('fetch failed'))))) {
      console.log('Backend network blocked or offline. Falling back to direct browser call to Gemini...');
      try {
        const directResult = await callGeminiDirectlyFromBrowser(
          lastCalculation.expression,
          lastCalculation.result,
          keyToUse
        );
        renderStorySuccess(directResult.story, directResult.modelUsed, lastCalculation.expression, lastCalculation.result);
        return;
      } catch (directErr) {
        storyContent.innerHTML = `
          <div class="alert-box">
            <h4>⚠️ Gemini API Error</h4>
            <p>${escapeHtml(directErr.message || 'Error reaching Gemini API.')}</p>
            <p style="margin-top: 6px; font-size: 0.8rem; color: #94a3b8;">Check that your API key is valid and has Gemini API enabled in Google AI Studio.</p>
          </div>
        `;
        return;
      }
    }

    // If backend returned another API error (e.g. invalid key)
    storyContent.innerHTML = `
      <div class="alert-box">
        <h4>⚠️ Gemini API Error</h4>
        <p>${escapeHtml(data.message || 'Unknown error occurred.')}</p>
        <p style="margin-top: 6px; font-size: 0.8rem; color: #94a3b8;">Check that your API key is valid and has Gemini API enabled in Google AI Studio.</p>
      </div>
    `;
  } catch (err) {
    storyContent.innerHTML = `
      <div class="alert-box">
        <h4>⚠️ Error</h4>
        <p>${escapeHtml(err.message)}</p>
      </div>
    `;
  } finally {
    explainBtn.disabled = false;
  }
}

// Helper: Escape HTML
function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&<>"']/g, match => {
    switch (match) {
      case '&': return '&amp;';
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '"': return '&quot;';
      case "'": return '&#39;';
    }
  });
}

// Event Listeners for Keypad
document.querySelector('.keypad').addEventListener('click', (e) => {
  const target = e.target;
  if (!target.matches('button')) return;

  if (target.dataset.num) {
    inputDigit(target.dataset.num);
  } else if (target.dataset.operator) {
    handleOperator(target.dataset.operator);
  } else if (target.dataset.action) {
    switch (target.dataset.action) {
      case 'all-clear':
        allClear();
        break;
      case 'backspace':
        backspace();
        break;
      case 'decimal':
        inputDecimal();
        break;
      case 'negate':
        negate();
        break;
      case 'percent':
        percent();
        break;
      case 'equals':
        performCalculation();
        break;
    }
  }
});

// Explain Button
explainBtn.addEventListener('click', explainWithAI);

// Toggle Key Drawer
toggleKeySettings.addEventListener('click', () => {
  apiKeyDrawer.classList.toggle('hidden');
});

// Save API Key to localStorage
saveKeyBtn.addEventListener('click', () => {
  const key = apiKeyInput.value.trim();
  if (key) {
    localStorage.setItem('gemini_api_key', key);
    alert('API Key saved locally in your browser!');
  } else {
    localStorage.removeItem('gemini_api_key');
  }
});

// Clear API Key
clearKeyBtn.addEventListener('click', () => {
  apiKeyInput.value = '';
  localStorage.removeItem('gemini_api_key');
  alert('API Key cleared.');
});

// Keyboard Support
window.addEventListener('keydown', (e) => {
  if (document.activeElement === apiKeyInput) return;

  if (e.key >= '0' && e.key <= '9') {
    e.preventDefault();
    inputDigit(e.key);
  } else if (e.key === '.') {
    e.preventDefault();
    inputDecimal();
  } else if (e.key === '+') {
    e.preventDefault();
    handleOperator('+');
  } else if (e.key === '-') {
    e.preventDefault();
    handleOperator('-');
  } else if (e.key === '*' || e.key === 'x') {
    e.preventDefault();
    handleOperator('×');
  } else if (e.key === '/') {
    e.preventDefault();
    handleOperator('÷');
  } else if (e.key === 'Enter' || e.key === '=') {
    e.preventDefault();
    performCalculation();
  } else if (e.key === 'Backspace') {
    e.preventDefault();
    backspace();
  } else if (e.key === 'Escape' || e.key.toLowerCase() === 'c') {
    e.preventDefault();
    allClear();
  }
});

// Initial screen render
updateDisplay();
