package Servidor.ComunicacionNodos;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.ObjectOutputStream;

public class ConexionNodo {
    private static final int timeOut = 2000;
    private final String id;
    private final String ip;
    private final int puerto;
    private Socket socket;
    private ObjectOutputStream salida;

    public ConexionNodo(String id, String ip, int puerto) {
        this.id = id;
        this.ip = ip;
        this.puerto = puerto;
    }

    public synchronized void cerrar() {
        try {
            if (salida != null) {
                salida.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        catch (IOException e) {
            System.out.println("Error al cerrar el conexión con el nodo " + id);
        }
        salida = null;
        socket = null;
    }

    private boolean conectar() {
        if (this.salida != null) {
            return true;
        }
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, puerto), timeOut);
            salida = new ObjectOutputStream(socket.getOutputStream());
            return true;
        }
        catch (IOException e) {
            cerrar();
            return false;
        }
    }

    public synchronized void enviar(Object mensaje) {
        if (!conectar()) {
            return;
        }
        try {
            salida.reset();
            salida.writeObject(mensaje);
            salida.flush();
        }
        catch (IOException e) {
            System.out.println("Error se ha perdido la conexion con el nodo " + id);
            cerrar();
        }
    }
}