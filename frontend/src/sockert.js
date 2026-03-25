// websocket-server.js
import { WebSocket, WebSocketServer } from 'ws';

const wss = new WebSocketServer({ port: 8080 });

wss.on('connection', ws => {
  console.log('Client connected.');

  ws.on('message', message => {
    const msg = message.toString();
    console.log('Received:', msg);
  });

  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      for (let i = 0; i < 5; i += 1) {
        const randomLength = Math.floor(Math.random() * 41) + 10; // 10-50之间的随机数
        const randomText = Array(randomLength)
          .fill()
          .map(() => {
            return String.fromCharCode(0x4e00 + Math.floor(Math.random() * (0x9fff - 0x4e00 + 1)));
          })
          .join('');

        const delay = Math.random() * 3000; // 0-3秒的随机延迟
        setTimeout(() => {
          client.send(randomText);
        }, delay);
      }
    }
  });

  ws.on('close', () => {
    console.log('Client disconnected.');
  });
});

console.log('WebSocket server is running on ws://localhost:8080');
