package Servidor;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.time.Instant;
import java.util.ArrayList;
import Comun.DM.AccionGrupo;
import Comun.DM.Mensaje;
import Comun.DM.SolicitudGrupo;
import Comun.Publiaciones.Interaccion;
import Comun.Publiaciones.Publicacion;
import Comun.Sesion.PerfilUsuario;
import Comun.Sesion.RespuestaInicio;
import Comun.Sesion.SolicitudInicio;
import Servidor.ComunicacionClientes.ClienteConectado;
import Servidor.ComunicacionNodos.MensajeNodo;

public class ManejoCliente implements Runnable {
    private final Socket socket;
    private final Servidor servidor;
    private ObjectInputStream entrada = null;
    private ObjectOutputStream salida = null;

    public ManejoCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    public void run () {
        String nombreUsuario = null;
        try {
            System.out.println("Conexion establecida desde la IP: " + socket.getInetAddress());
            // NO voltear, se bloquea
            salida = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());

            SolicitudInicio solicitud = (SolicitudInicio)entrada.readObject();
            nombreUsuario = solicitud.getUsuario();

            boolean conectadoExitosamente = GestionUsuarios.conectar(nombreUsuario, salida);
            if (conectadoExitosamente) {
                ClienteConectado miCliente = GestionUsuarios.getCliente(nombreUsuario);
                miCliente.enviar(new RespuestaInicio(true, "Bienvenido " + nombreUsuario, GestionUsuarios.obtenerUltimasPublicaciones()));
                while (true) {
                    Object obj = entrada.readObject();
                    if (obj instanceof Mensaje) {
                        Mensaje msj = (Mensaje) obj;

                        if (msj.getEsGrupo()) {
                            PerfilUsuario perfil = GestionUsuarios.obtenerPerfil(msj.getRemitente());
                            if (perfil != null) {
                                String idGrupo = perfil.obtenerIDGrupo(msj.getDestinatario());
                                if (idGrupo != null) {
                                    ArrayList<String> miembros = GestionUsuarios.obtenerIntegrantesGrupo(idGrupo);
                                    if (miembros != null) {
                                        for (String miembro : miembros) {
                                            if (!miembro.equals(msj.getRemitente()) && GestionUsuarios.estaConectado(miembro)) {
                                                ClienteConectado clienteDestino = GestionUsuarios.getCliente(miembro);
                                                clienteDestino.enviar(msj);
                                            }
                                        }
                                    }
                                }
                                else {
                                    miCliente.enviar(new Mensaje("Servidor", nombreUsuario, false, "No se encontro el grupo"));
                                }
                            }
                        }
                        else {
                            long lamport = servidor.incrementarReloj();
                            servidor.getRegistro().registrar(lamport, "Emite MENSAJE " + nombreUsuario + " -> " + msj.getDestinatario());
                            ClienteConectado clienteDestino = GestionUsuarios.getCliente(msj.getDestinatario());
                            if (clienteDestino != null) {
                                clienteDestino.enviar(msj);
                            }
                            else {
                                MensajeNodo evento = new MensajeNodo(servidor.getId(), "MENSAJE", lamport, msj);
                                servidor.getMembresia().difundirEntreNodos(evento);
                            }
                        }
                    }
                    else if (obj instanceof SolicitudGrupo) {
                        SolicitudGrupo soliGrupo = (SolicitudGrupo) obj;
                        System.out.println("Procesando creacion de grupo: " + soliGrupo.getNombreGrupo());
                        boolean creado = GestionUsuarios.crearGrupo(soliGrupo.getNombreGrupo(), nombreUsuario, soliGrupo.getIntegrantes());

                        if (!creado) {
                            Mensaje error = new Mensaje("Servidor", nombreUsuario, false, "Error: Ya tienes un grupo llamado " + soliGrupo.getNombreGrupo());
                            miCliente.enviar(error);
                        }
                    }
                    else if (obj instanceof AccionGrupo) {
                        AccionGrupo accion = (AccionGrupo) obj;

                        switch (accion.getAccion()) {
                            case "aceptar" -> {
                                GestionUsuarios.responderSolicitudGrupo(nombreUsuario, accion.getNombreGrupo(), true);
                                System.out.println(nombreUsuario + " acepto unirse a " + accion.getNombreGrupo());
                            }
                            case "rechazar" -> {
                                GestionUsuarios.responderSolicitudGrupo(nombreUsuario, accion.getNombreGrupo(), false);
                                System.out.println(nombreUsuario + " rechazo unirse a " + accion.getNombreGrupo());
                            }
                            case "salir" -> {
                                boolean salio = GestionUsuarios.salirDeGrupo(nombreUsuario, accion.getNombreGrupo());
                                if (!salio) {
                                    miCliente.enviar(new Mensaje("Servidor", nombreUsuario, false, "No perteneces al grupo " + accion.getNombreGrupo()));
                                }
                            }
                        }
                    }
                    else if (obj instanceof Publicacion) {
                        Publicacion pub = (Publicacion) obj;
                        pub.setFechaPublicacion(Instant.now());
                        long lamport = servidor.incrementarReloj();
                        pub.setLamport(lamport);
                        pub.setIdNodoOrigen(servidor.getId());
                        servidor.getRegistro().registrar(lamport, "Emite PUBLICACION autor=" + pub.getAutor());
                        System.out.println("Difundiendo la nueva publicacion de " + pub.getAutor());
                        GestionUsuarios.difundirPublicacion(pub);
                        MensajeNodo evento = new MensajeNodo(servidor.getId(), "PUBLICACION", lamport, pub);
                        servidor.getMembresia().difundirEntreNodos(evento);
                    }
                    else if (obj instanceof Interaccion) {
                        Interaccion interaccionEntrante = (Interaccion) obj;
                        GestionUsuarios.procesarInteraccion(interaccionEntrante);
                        long lamport = servidor.incrementarReloj();
                        servidor.getRegistro().registrar(lamport, "Emite INTERACCION " + interaccionEntrante.getTipo() + " autor=" + interaccionEntrante.getAutor());
                        MensajeNodo evento = new MensajeNodo(servidor.getId(), "INTERACCION", lamport, interaccionEntrante);
                        servidor.getMembresia().difundirEntreNodos(evento);
                    }
                }
            }
            else {
                salida.writeObject(new RespuestaInicio(false, "El usuario " + nombreUsuario + " ya se encuentra conectado", null));
                salida.flush();
            }

        }
        catch (EOFException e) {
            System.out.println("Se desconecto el usuario " + nombreUsuario);
        }
        catch (SocketException e) {
            System.out.println("Error: Conexion perdida abruptamente con " + nombreUsuario);
        }
        catch (IOException e) {
            System.out.println("Error: IO perdida abruptamente con " + nombreUsuario);
        }
        catch (ClassNotFoundException e) {
            System.out.println("Error: " + nombreUsuario + " recibio un objeto con clase desconocida");
        } finally {
            try {
                if (nombreUsuario != null) {
                    GestionUsuarios.desconectar(nombreUsuario);
                    System.out.println("Desconectado: " + nombreUsuario);
                }
                if (entrada != null) entrada.close();
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    }
                    catch (IOException e) {
                        System.out.println("Error al cerrar conexion con usuario " + nombreUsuario);
                    }
                }
            }
            catch (IOException e) {
                System.out.println("Error al cerrar el servidor");
            }
        }
    }
}
