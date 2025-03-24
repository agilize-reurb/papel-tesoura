package com.example.demo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.*;
import java.util.concurrent.*;

@SpringBootApplication
@EnableWebSocketMessageBroker
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Controller
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}


@RestController
@RequestMapping("/game")
class GameController {
    private static final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public GameController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/rooms")
    public List<String> listRooms() {
        return new ArrayList<>(rooms.keySet());
    }

    @PostMapping("/rooms")
    public String createRoom(@RequestParam String name) {
        System.out.println( "Creating room: " + name);
        if (!rooms.containsKey(name)) {
            rooms.put(name, new GameRoom(name, messagingTemplate));
            return "Room created: " + name;
        }
        return "Room already exists.";
    }

    @PostMapping("/rooms/{name}/join")
    public String joinRoom(@PathVariable String name, @RequestParam String player) {
        GameRoom room = rooms.get(name);
        System.out.println("Player " + player + " joining room " + name);
        if (room != null && room.addPlayer(player)) {
            return "Player " + player + " joined room " + name;
        }
        return "Room is full or does not exist.";
    }

    @MessageMapping("/rooms/{roomName}/choice")
    public void makeChoice(@DestinationVariable String roomName, @Payload PlayerChoice playerChoice) {
        System.out.println("Choice: " + playerChoice.getChoice() + " by " + playerChoice.getPlayer());
        System.out.println("Room: " + roomName);
        GameRoom room = rooms.get(roomName);
        if (room != null) {
            room.makeChoice(playerChoice.getPlayer(), playerChoice.getChoice());
        }
    }
}

class PlayerChoice {
    private String player;
    private String choice;

    // Getters e Setters
    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }
}

class GameRoom {
    private final String name;
    private final List<String> players = new ArrayList<>();
    private final Map<String, String> choices = new ConcurrentHashMap<>();
    private final Map<String, Integer> wins = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public GameRoom(String name, SimpMessagingTemplate messagingTemplate) {
        this.name = name;
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized boolean addPlayer(String player) {
        if (players.size() < 2) {
            players.add(player);
            wins.put(player, 0);

            messagingTemplate.convertAndSend("/topic/" + this.name, "Player " + player + " joined.");
            if (players.size() == 2) {
                messagingTemplate.convertAndSend("/topic/" + this.name, "Game ready to start!");
            }
            return true;
        }
        return false;
    }

    public synchronized void makeChoice(String player, String choice) {
        if (!players.contains(player)) {
            return;
        }
        System.out.println("Player " + player + " made a move.");
        System.out.println(this.choices.size());
        this.choices.put(player, choice);
        messagingTemplate.convertAndSend("/topic/" + this.name, "Player " + player + " made a move.");
        if (this.choices.size() == 2) {
            determineWinner();
        }
    }

    private void determineWinner() {
        String p1 = players.get(0);
        String p2 = players.get(1);
        String c1 = choices.get(p1);
        String c2 = choices.get(p2);

        String result;
        if (c1.equals(c2)) {
            result = "Draw!";
        } else if ((c1.equals("Rock") && c2.equals("Scissors")) ||
                   (c1.equals("Scissors") && c2.equals("Paper")) ||
                   (c1.equals("Paper") && c2.equals("Rock"))) {
            result = p1 + " wins!";
            wins.put(p1, wins.get(p1) + 1);
        } else {
            result = p2 + " wins!";
            wins.put(p2, wins.get(p2) + 1);
        }

        messagingTemplate.convertAndSend("/topic/" + name, result);
        choices.clear();
    }
}
