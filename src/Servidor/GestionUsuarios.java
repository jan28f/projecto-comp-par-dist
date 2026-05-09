package Servidor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import Comun.PerfilUsuario;
import Comun.Publicacion;
import Comun.Interaccion;

import java.io.ObjectOutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GestionUsuarios {
    private static HashMap<String, PerfilUsuario> perfiles = new HashMap<>();
    private static HashMap<String, ObjectOutputStream> conectados = new HashMap<>();
    private static ArrayDeque<Publicacion> historialPublicaciones = new ArrayDeque<>();
    private static final int tamanoHistorialPublicaciones = 10;
    private static final ReentrantReadWriteLock candado = new ReentrantReadWriteLock();

    public static boolean conectar(String usuario, ObjectOutputStream salida) {
        candado.writeLock().lock();
        try {
            if (conectados.containsKey(usuario)) {
                return false;
            }
            conectados.put(usuario, salida);
            if (!perfiles.containsKey(usuario)) {
                perfiles.put(usuario, new PerfilUsuario(usuario));
            }
            return true;
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static void desconectar(String usuario) {
        candado.writeLock().lock();
        try {
            conectados.remove(usuario);
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static ObjectOutputStream getSalida(String usuario) {
        candado.readLock().lock();
        try {
            return conectados.get(usuario);
        } finally {
            candado.readLock().unlock();
        }
    }

    public static boolean estaConectado(String usuario) {
        candado.readLock().lock();
        try {
            return conectados.containsKey(usuario);
        } finally {
            candado.readLock().unlock();
        }
    }

    public static void difundirPublicacion(Publicacion pub) {
        candado.writeLock().lock();
        try {
            if (historialPublicaciones.size() > tamanoHistorialPublicaciones) {
                historialPublicaciones.pollFirst();
            }
            historialPublicaciones.addLast(pub);

            PerfilUsuario perfil = perfiles.get(pub.getAutor());
            perfil.agregarPublicacion(pub);

            for (ObjectOutputStream salida : conectados.values()) {
                try {
                    salida.reset();
                    salida.writeObject(pub);
                    salida.flush();
                } catch (IOException e) {
                    System.out.println("Error: No se pudo enviar la publicacion");
                }
            }
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static ArrayList<Publicacion> obtenerUltimasPublicaciones() {
        return new ArrayList<Publicacion>(historialPublicaciones);
    }

    public static void procesarInteraccion(Interaccion interaccionRecibida) {
        candado.writeLock().lock();
        try {
            Publicacion publicacionObjetivo = null;
            for (Publicacion iteradorPub : historialPublicaciones) {
                if (iteradorPub.getIdPublicacion() == interaccionRecibida.getIdPublicacion()) {
                    publicacionObjetivo = iteradorPub;
                    break;
                }
            }
            if (publicacionObjetivo != null) {
                if (interaccionRecibida.getTipo().equals("LIKE")) {
                    publicacionObjetivo.agregarMeGusta(interaccionRecibida.getAutor());
                } else if (interaccionRecibida.getTipo().equals("COMENTARIO")) {
                    String comentarioEstructurado = interaccionRecibida.getAutor() + ": " + interaccionRecibida.getContenido();
                    publicacionObjetivo.agregarComentario(comentarioEstructurado);
                }
                for (ObjectOutputStream salidaConectada : conectados.values()) {
                    try {
                        salidaConectada.reset();
                        salidaConectada.writeObject(publicacionObjetivo);
                        salidaConectada.flush();
                    } catch (IOException e) {
                        System.out.println("Error actualizando publicacion para un cliente");
                    }
                }
            }
        } finally {
            candado.writeLock().unlock();
        }
    }
}