package Servidor;

import java.io.IOException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ManejoCliente implements Runnable {
    private Socket socket = null;
    private ObjectInputStream entrada = null;
    private ObjectOutputStream salida = null;

    public ManejoCliente(Socket socket) {
        this.socket = socket;
    }

    public void run () {
        try {
            System.out.println("Conexion establecida desde la IP: " + socket.getInetAddress());
            // NO voltear, se bloquea
            salida = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());

            String mensajeCliente = (String)entrada.readObject();
            salida.writeObject(("Hola " + mensajeCliente + "\nHora envio: " + System.currentTimeMillis()));

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (entrada != null) entrada.close();
                if (salida != null) salida.close();
                if (socket != null) socket.close();
                System.out.println("Conexion finalizada exitosamente");
            }
            catch (IOException e) {
                System.out.println("Error al cerrar el servidor");
            }
        }
    }
}
