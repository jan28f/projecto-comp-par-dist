package Servidor;

import Servidor.ComunicacionNodos.InfoNodo;
import Servidor.ComunicacionNodos.MensajeNodo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

public class ManejoNodo implements Runnable {
    private final Socket socketNodo;
    private final Servidor servidor;

    public ManejoNodo(Socket nodo, Servidor servidor) {
        this.socketNodo = nodo;
        this.servidor = servidor;
    }

    public void run() {
        try {
            ObjectInputStream entrada = new ObjectInputStream(socketNodo.getInputStream());

            while (true) {
                MensajeNodo mensaje = (MensajeNodo) entrada.readObject();
                if (mensaje != null) {
                    servidor.actualizarReloj(mensaje.getReloj());
                    System.out.println("Mensaje recibido de: " + mensaje.getIdEmisor());
                    System.out.println("Tipo de mensaje: " +  mensaje.getTipo());
                    System.out.println("Reloj actualizado: " + servidor.getReloj());

                    switch (mensaje.getTipo()) {
                        case "LATIDO":
                            InfoNodo nodo = servidor.obtenerNodo(mensaje.getIdEmisor());
                            if (nodo != null) {
                                boolean estabaCaido = !nodo.getActivo();
                                nodo.setActivo(true);
                                nodo.setUltimoLatido(System.currentTimeMillis());
                                if (estabaCaido) {
                                    System.out.println("El nodo " + mensaje.getIdEmisor() + " está activo");
                                    servidor.mostrarEstadoNodos();
                                }
                            }
                            break;
                        default:
                            System.out.println("Tipo de mensaje desconocido: " +  mensaje.getTipo());
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Error: Mensaje distinto de clase MensajeNodo");
        } catch (IOException e) {
            System.out.println("Error de comunicación entre nodos");
        }
        finally {
            try {
                if (socketNodo != null && !socketNodo.isClosed()) {
                    socketNodo.close();
                }
            }
            catch (IOException e) {
                System.out.println("Error al cerrar el socket del nodo");
            }
        }
    }
}
