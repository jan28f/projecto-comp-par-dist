package Servidor;

import Comun.DM.Mensaje;
import Comun.Publiaciones.Interaccion;
import Comun.Publiaciones.Publicacion;
import Servidor.ComunicacionClientes.ClienteConectado;
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
                        case "PUBLICACION": {
                            Publicacion pub = (Publicacion) mensaje.getContenido();
                            GestionUsuarios.difundirPublicacion(pub);
                            servidor.getRegistro().registrar(mensaje.getReloj(), "Recibe PUBLICACION de " + mensaje.getIdEmisor() + " autor=" + pub.getAutor());
                            break;
                        }
                        case "INTERACCION": {
                            Interaccion interaccion = (Interaccion) mensaje.getContenido();
                            GestionUsuarios.procesarInteraccion(interaccion);
                            servidor.getRegistro().registrar(mensaje.getReloj(), "Recibe INTERACCION de " + mensaje.getIdEmisor());
                            break;
                        }
                        case "MENSAJE": {
                            Mensaje msj = (Mensaje) mensaje.getContenido();
                            ClienteConectado clienteDestino = GestionUsuarios.getCliente(msj.getDestinatario());
                            if (clienteDestino != null) {
                                clienteDestino.enviar(msj);
                            }
                            servidor.getRegistro().registrar(mensaje.getReloj(), "Recibe MENSAJE de " + mensaje.getIdEmisor() + " para=" + msj.getDestinatario());
                            break;
                        }
                        default:
                            System.out.println("Tipo de mensaje desconocido: " +  mensaje.getTipo());
                            break;
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
