package Servidor;

import java.io.IOException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

public class ManejoCliente implements Runnable {
    private final Socket socket;
    private ObjectInputStream entrada = null;
    private ObjectOutputStream salida = null;

    public ManejoCliente(Socket socket) {
        this.socket = socket;
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
                salida.writeObject(new RespuestaInicio(true, "Bienvenido " + nombreUsuario, GestionUsuarios.obtenerUltimasPublicaciones()));
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
                                                ObjectOutputStream salidaDest = GestionUsuarios.getSalida(miembro);
                                                salidaDest.writeObject(msj);
                                                salidaDest.flush();
                                            }
                                        }
                                    }
                                }
                                else {
                                    salida.writeObject(new Mensaje("Servidor", nombreUsuario, false, "No se encontro el grupo"));
                                }
                            }
                        }
                        else {
                            if (GestionUsuarios.estaConectado(msj.getDestinatario())) {
                                ObjectOutputStream salidaDestinatario = GestionUsuarios.getSalida(msj.getDestinatario());
                                salidaDestinatario.writeObject(msj);
                            }
                            else {
                                Mensaje error = new Mensaje("Servidor", nombreUsuario, false, "El usuario " + msj.getDestinatario() + " no esta en linea.");
                                salida.writeObject(error);
                            }
                        }
                    }
                    else if (obj instanceof SolicitudGrupo) {
                        SolicitudGrupo soliGrupo = (SolicitudGrupo) obj;
                        System.out.println("Procesando creacion de grupo: " + soliGrupo.getNombreGrupo());
                        boolean creado = GestionUsuarios.crearGrupo(soliGrupo.getNombreGrupo(), nombreUsuario, soliGrupo.getIntegrantes());

                        if (!creado) {
                            Mensaje error = new Mensaje("Servidor", nombreUsuario, false, "Error: Ya tienes un grupo llamado " + soliGrupo.getNombreGrupo());
                            salida.writeObject(error);
                            salida.flush();
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
                                    salida.writeObject(new Mensaje("Servidor", nombreUsuario, false, "No perteneces al grupo " + accion.getNombreGrupo()));
                                    salida.flush();
                                }
                            }
                        }
                    }
                    else if (obj instanceof Publicacion) {
                        Publicacion pub = (Publicacion) obj;
                        pub.setFechaPublicacion(Instant.now());
                        System.out.println("Difundiendo la nueva publicacion de " + pub.getAutor());
                        GestionUsuarios.difundirPublicacion(pub);
                    }
                    else if (obj instanceof Interaccion) {
                        Interaccion interaccionEntrante = (Interaccion) obj;
                        GestionUsuarios.procesarInteraccion(interaccionEntrante);
                    }
                }
            }
            else {
                salida.writeObject(new RespuestaInicio(false, "El usuario " + nombreUsuario + " ya se encuentra conectado", null));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            System.out.println("Error: Clase desconocida para el objeto");
        } finally {
            try {
                if (nombreUsuario != null) {
                    GestionUsuarios.desconectar(nombreUsuario);
                    System.out.println("Desconectado: " + nombreUsuario);
                }
                if (entrada != null) entrada.close();
                if (salida != null) salida.close();
                if (socket != null) socket.close();
            }
            catch (IOException e) {
                System.out.println("Error al cerrar el servidor");
            }
        }
    }
}
