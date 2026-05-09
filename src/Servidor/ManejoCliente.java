package Servidor;

import java.io.IOException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;

import Comun.Publicacion;
import Comun.SolicitudInicio;
import Comun.RespuestaInicio;
import Comun.Mensaje;

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
                salida.writeObject(new RespuestaInicio(true, "Bienvenido " + nombreUsuario));
                while (true) {
                    Object obj = entrada.readObject();
                    if (obj instanceof Mensaje) {
                        Mensaje msj = (Mensaje) obj;

                        if (GestionUsuarios.estaConectado(msj.getDestinatario())) {
                            ObjectOutputStream salidaDestinatario = GestionUsuarios.getSalida(msj.getDestinatario());
                            salidaDestinatario.writeObject(msj);
                        }
                        else {
                            Mensaje error = new Mensaje("Servidor", nombreUsuario, "El usuario " + msj.getDestinatario() + " no esta en linea.");
                            salida.writeObject(error);
                        }
                    }
                    else if (obj instanceof Publicacion) {
                        Publicacion pub = (Publicacion) obj;
                        pub.setFechaPublicacion(Instant.now());
                        System.out.println("Actualizando perfil de " + pub.getAutor());
                        GestionUsuarios.almacenarPublicacion(pub);
                        System.out.println("Difundiendo la nueva publicacion de " + pub.getAutor());
                        GestionUsuarios.difundirPublicacion(pub);
                    }
                }
            }
            else {
                salida.writeObject(new RespuestaInicio(false, "El usuario " + nombreUsuario + " ya se encuentra conectado"));
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
