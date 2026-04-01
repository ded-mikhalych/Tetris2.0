package ru.danil.tetris.rmi.server;

import ru.danil.tetris.rmi.common.GameService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public final class ServerMain {
    private static final String BINDING_NAME = "TetrisGameService";

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Registry.REGISTRY_PORT;
        Registry registry = LocateRegistry.createRegistry(port);
        GameService service = new GameServiceImpl();
        registry.rebind(BINDING_NAME, service);

        System.out.printf("RMI-сервер игры Tetris запущен на порту %d.%n", port);
        System.out.printf("Имя сервиса: %s%n", BINDING_NAME);
        System.out.println("Нажмите Ctrl+C для остановки сервера.");
    }
}
