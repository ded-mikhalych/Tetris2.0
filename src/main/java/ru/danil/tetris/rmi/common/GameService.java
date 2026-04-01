package ru.danil.tetris.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameService extends Remote {
    GameSnapshot startNewGame(int width, int height) throws RemoteException;

    GameSnapshot getState() throws RemoteException;

    GameSnapshot moveLeft() throws RemoteException;

    GameSnapshot moveRight() throws RemoteException;

    GameSnapshot rotate() throws RemoteException;

    GameSnapshot moveDown() throws RemoteException;

    GameSnapshot dropFigure() throws RemoteException;

    GameSnapshot tick() throws RemoteException;

    GameSnapshot finishGame() throws RemoteException;
}
