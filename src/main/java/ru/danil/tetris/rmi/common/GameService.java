package ru.danil.tetris.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameService extends Remote {
    String createSession(String nickname) throws RemoteException;

    GameSnapshot updatePlayerNickname(String sessionId, String nickname) throws RemoteException;

    GameSnapshot startNewGame(String sessionId, int width, int height) throws RemoteException;

    GameSnapshot getState(String sessionId) throws RemoteException;

    GameSnapshot moveLeft(String sessionId) throws RemoteException;

    GameSnapshot moveRight(String sessionId) throws RemoteException;

    GameSnapshot rotate(String sessionId) throws RemoteException;

    GameSnapshot moveDown(String sessionId) throws RemoteException;

    GameSnapshot dropFigure(String sessionId) throws RemoteException;

    GameSnapshot tick(String sessionId) throws RemoteException;

    GameSnapshot finishGame(String sessionId) throws RemoteException;
}
