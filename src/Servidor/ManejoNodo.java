package Servidor;

import Comun.DM.Mensaje;
import Comun.Publiaciones.Interaccion;
import Comun.Publiaciones.Publicacion;
import Servidor.ComunicacionClientes.ClienteConectado;
import Servidor.ComunicacionNodos.MensajeNodo;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import Servidor.ComunicacionNodos.ConexionNodo;
import Servidor.ComunicacionNodos.EleccionBully;

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
                        case "ELECCION": {
                            // Alguien con ID menor nos pregunta si seguimos vivos
                            servidor.getRegistro().registrar(mensaje.getReloj(),
                                    "Recibe ELECCION de " + mensaje.getIdEmisor());

                            // Responder OK (soy mayor, me encargo yo)
                            ConexionNodo conexionEmisor = servidor.getMembresia().getConexion(mensaje.getIdEmisor());
                            if (conexionEmisor != null) {
                                MensajeNodo ok = new MensajeNodo(
                                        servidor.getId(), "OK", servidor.incrementarReloj(), null);
                                conexionEmisor.enviar(ok);
                            }
                            // Iniciar mi propia elección (si no está en curso)
                            servidor.getBully().iniciarEleccion();
                            break;
                        }
                        case "OK": {
                            // Alguien mayor que yo sigue vivo — cancelo mi elección
                            servidor.getRegistro().registrar(mensaje.getReloj(),
                                    "Recibe OK de " + mensaje.getIdEmisor());
                            servidor.getBully().recibirOk();
                            break;
                        }
                        case "COORDINADOR": {
                            // Alguien se autoproclamó coordinador
                            String nuevoCoord = (String) mensaje.getContenido();
                            servidor.getBully().recibirCoordinador(nuevoCoord);
                            servidor.getRegistro().registrar(mensaje.getReloj(),
                                    "Nuevo COORDINADOR: " + nuevoCoord);

                            // NUEVO: Si no soy yo el coordinador, le pido el estado actual
                            if (!nuevoCoord.equals(servidor.getId())) {
                                ConexionNodo conexionCoord = servidor.getMembresia().getConexion(nuevoCoord);
                                if (conexionCoord != null) {
                                    MensajeNodo reqEstado = new MensajeNodo(
                                            servidor.getId(), "SOLICITAR_ESTADO", servidor.incrementarReloj(), null);
                                    conexionCoord.enviar(reqEstado);
                                    System.out.println("[ESTADO] Solicitando sincronización al coordinador: " + nuevoCoord);
                                }
                            }
                            break;
                        }
                        case "SOLICITAR_ESTADO": {
                            // El coordinador recibe esta petición de un nodo recién iniciado
                            if (!GestionUsuarios.tieneEstado()) {
                                System.out.println("[ESTADO] Aún sin estado válido, no respondo a " + mensaje.getIdEmisor());
                                break;
                            }
                            servidor.getRegistro().registrar(mensaje.getReloj(), "Recibe SOLICITAR_ESTADO de " + mensaje.getIdEmisor());
                            System.out.println("[ESTADO] Enviando copia de memoria global a nodo " + mensaje.getIdEmisor());

                            Object[] estadoActual = GestionUsuarios.exportarEstado();
                            ConexionNodo conexionEmisor = servidor.getMembresia().getConexion(mensaje.getIdEmisor());

                            if (conexionEmisor != null) {
                                MensajeNodo resEstado = new MensajeNodo(
                                        servidor.getId(), "SINCRONIZAR_ESTADO", servidor.incrementarReloj(), estadoActual);
                                conexionEmisor.enviar(resEstado);
                            }
                            break;
                        }
                        case "SINCRONIZAR_ESTADO": {
                            // El nodo recién iniciado recibe la memoria del coordinador
                            servidor.getRegistro().registrar(mensaje.getReloj(), "Recibe SINCRONIZAR_ESTADO de " + mensaje.getIdEmisor());
                            Object[] estadoRecibido = (Object[]) mensaje.getContenido();
                            GestionUsuarios.importarEstado(estadoRecibido);
                            break;
                        }
                        case "LATIDO": {
                            servidor.getMembresia().registrarLatido(mensaje.getIdEmisor());
                            if (!GestionUsuarios.tieneEstado() && servidor.solicitarSincronizacionUnaVez()) {
                                ConexionNodo c = servidor.getMembresia().getConexion(mensaje.getIdEmisor());
                                if (c != null) {
                                    c.enviar(new MensajeNodo(servidor.getId(), "SOLICITAR_ESTADO",
                                            servidor.incrementarReloj(), null));
                                    System.out.println("[ESTADO] Pido sincronización a " + mensaje.getIdEmisor() + " (memoria vacía)");
                                }
                            }
                            break;
                        }
                        case "RA_REQUEST": {
                            String idSolicitante = (String) mensaje.getContenido();
                            servidor.getExclusionMutua().recibirSolicitud(idSolicitante, mensaje.getReloj());
                            break;
                        }
                        case "RA_REPLY": {
                            servidor.getRegistro().registrar(mensaje.getReloj(), "RA recibe REPLY de " + mensaje.getIdEmisor());
                            servidor.getExclusionMutua().recibirRespuesta();
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
