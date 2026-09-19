(() => {
  const canvas = document.querySelector('#board');
  const ctx = canvas.getContext('2d');
  const size = 20, cell = canvas.width / size;
  const scoreEl = document.querySelector('#score');
  const bestEl = document.querySelector('#best');
  const overlay = document.querySelector('#overlay');
  const title = document.querySelector('#overlay-title');
  const subtitle = document.querySelector('#overlay-text');
  const saveButton = document.querySelector('#save');
  const message = document.querySelector('#message');
  let snake, food, direction, nextDirection, score = 0, timer = null, state = 'idle';
  let best = Number(localStorage.getItem('snake-best') || 0);
  bestEl.textContent = best;

  function draw() {
    ctx.fillStyle = '#10251f'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = '#1a342b'; ctx.lineWidth = 1;
    for (let n = 0; n <= size; n++) {
      ctx.beginPath(); ctx.moveTo(n * cell, 0); ctx.lineTo(n * cell, canvas.height); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, n * cell); ctx.lineTo(canvas.width, n * cell); ctx.stroke();
    }
    if (food) { ctx.fillStyle = '#ff8d73'; ctx.beginPath(); ctx.arc((food.x + .5) * cell, (food.y + .5) * cell, cell * .36, 0, Math.PI * 2); ctx.fill(); }
    if (snake) snake.forEach((part, i) => {
      ctx.fillStyle = i === 0 ? '#e1ff95' : '#62d998';
      ctx.fillRect(part.x * cell + 2, part.y * cell + 2, cell - 4, cell - 4);
    });
  }
  function placeFood() {
    const free = [];
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++)
      if (!snake.some(p => p.x === x && p.y === y)) free.push({x, y});
    return free.length ? free[Math.floor(Math.random() * free.length)] : null;
  }
  function show(headline, detail) { title.textContent = headline; subtitle.textContent = detail; overlay.classList.remove('hidden'); }
  function finish(won = false) {
    clearInterval(timer); timer = null; state = 'over'; saveButton.disabled = false;
    show(won ? '¡Ganaste! 🎉' : 'Fin del juego', `Conseguiste ${score} puntos. Presiona R para volver a jugar.`);
  }
  function step() {
    direction = nextDirection;
    const head = {x: snake[0].x + direction.x, y: snake[0].y + direction.y};
    const eating = head.x === food.x && head.y === food.y;
    const body = eating ? snake : snake.slice(0, -1);
    if (head.x < 0 || head.x >= size || head.y < 0 || head.y >= size || body.some(p => p.x === head.x && p.y === head.y)) { finish(); return; }
    snake.unshift(head);
    if (eating) {
      score++; scoreEl.textContent = score;
      if (score > best) { best = score; bestEl.textContent = best; localStorage.setItem('snake-best', String(best)); }
      food = placeFood();
      if (!food) { draw(); finish(true); return; }
    } else snake.pop();
    draw();
  }
  function start() {
    clearInterval(timer); snake = [{x: 9,y: 10},{x: 8,y: 10},{x: 7,y: 10}];
    direction = {x: 1,y: 0}; nextDirection = direction; score = 0; scoreEl.textContent = '0';
    food = placeFood(); state = 'running'; saveButton.disabled = true; message.textContent = '';
    overlay.classList.add('hidden'); draw(); timer = setInterval(step, 135);
  }
  function pause() {
    if (state === 'running') { clearInterval(timer); timer = null; state = 'paused'; show('Pausa', 'Presiona Espacio para continuar.'); }
    else if (state === 'paused') { state = 'running'; overlay.classList.add('hidden'); timer = setInterval(step, 135); }
  }
  const keys = {ArrowUp:{x:0,y:-1},ArrowDown:{x:0,y:1},ArrowLeft:{x:-1,y:0},ArrowRight:{x:1,y:0},w:{x:0,y:-1},s:{x:0,y:1},a:{x:-1,y:0},d:{x:1,y:0}};
  document.addEventListener('keydown', event => {
    if (event.target instanceof HTMLInputElement) return;
    const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
    if (keys[key] || event.code === 'Space') event.preventDefault();
    if (key === 'r') { start(); return; }
    if (event.code === 'Space') { if (state === 'idle' || state === 'over') start(); else pause(); return; }
    if (state !== 'running' || !keys[key]) return;
    const turn = keys[key];
    // One turn per tick keeps rapid key presses from reversing into the body.
    if (nextDirection === direction && (turn.x !== -direction.x || turn.y !== -direction.y)) nextDirection = turn;
  });
  document.querySelector('#start').addEventListener('click', start);
  document.querySelector('#pause').addEventListener('click', pause);
  async function loadScores() {
    try {
      const [info, scores] = await Promise.all([fetch('/api/game'), fetch('/api/game/scores')]);
      if (!info.ok || !scores.ok) throw new Error('API no disponible');
      const data = await info.json();
      document.querySelector('#api-status').textContent = `${data.name} conectada ✓`;
      const list = document.querySelector('#scores'); list.replaceChildren();
      (await scores.json()).forEach(item => {
        const li = document.createElement('li'); li.textContent = `${item.player} `;
        const points = document.createElement('span'); points.textContent = `${item.points} pts`; li.append(points); list.append(li);
      });
    } catch { document.querySelector('#api-status').textContent = 'Sin conexión'; }
  }
  saveButton.addEventListener('click', async () => {
    const player = document.querySelector('#player').value.trim();
    if (!player) { message.textContent = 'Escribe un nombre.'; return; }
    saveButton.disabled = true;
    try {
      const response = await fetch('/api/game/scores', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({player, points:score})});
      if (!response.ok) throw new Error('No se pudo guardar');
      message.textContent = '¡Puntuación guardada!'; await loadScores();
    } catch { saveButton.disabled = false; message.textContent = 'No se pudo guardar; inténtalo otra vez.'; }
  });
  draw(); loadScores();
})();
