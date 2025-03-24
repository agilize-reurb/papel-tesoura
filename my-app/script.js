let stompClient = null;
let currentRoom = null;
let playerName = "Player" + Math.floor(Math.random() * 1000); // Nome aleatório para o jogador

function connect() {
    const socket = new SockJS('http://localhost:8080/ws'); // Conecta ao backend
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        updateRoomsList();
    });
}

function updateRoomsList() {
    fetch('http://localhost:8080/game/rooms') // Lista salas do backend
        .then(response => response.json())
        .then(rooms => {
            const roomsList = document.getElementById('rooms');
            roomsList.innerHTML = '';
            rooms.forEach(room => {
                const li = document.createElement('li');
                li.textContent = room;
                roomsList.appendChild(li);
            });
        });
}

function createRoom() {
    const roomName = document.getElementById('room-name').value;
    if (roomName) {
        fetch('http://localhost:8080/game/rooms', { // Cria uma sala no backend
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `name=${roomName}`
        }).then(response => response.text())
          .then(message => {
              alert(message);
              updateRoomsList();
          });
    }
}

function joinRoom() {
    const roomName = document.getElementById('room-name').value;
    if (roomName) {
        currentRoom = roomName;
        stompClient.subscribe(`/topic/${roomName}`, function (message) {
            console.log(message);
            const gameResult = document.getElementById('game-result');
            gameResult.textContent = message.body;
        });

        fetch(`http://localhost:8080/game/rooms/${roomName}/join`, { // Entra na sala
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `player=${playerName}`
        }).then(response => {
            if (response.ok) {
                document.getElementById('room-controls').style.display = 'none';
                document.getElementById('game-controls').style.display = 'block';
                document.getElementById('room-status').textContent = `Sala: ${roomName}`;
            }
        });
    }
}

function makeChoice(choice) {
    if (currentRoom) {
        stompClient.send(`/rooms/${currentRoom}/choice`, {}, JSON.stringify({ player: playerName, choice: choice }));
    }
}

document.getElementById('create-room').addEventListener('click', createRoom);
document.getElementById('join-room').addEventListener('click', joinRoom);
document.getElementById('rock').addEventListener('click', () => makeChoice('Rock'));
document.getElementById('paper').addEventListener('click', () => makeChoice('Paper'));
document.getElementById('scissors').addEventListener('click', () => makeChoice('Scissors'));

connect();