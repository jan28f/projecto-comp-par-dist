package Servidor;

import java.io.IOException;
import java.util.HashMap;
import Comun.PerfilUsuario;
import Comun.Publicacion;

import java.io.ObjectOutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GestionUsuarios {
    private static HashMap<String, PerfilUsuario> perfiles = new HashMap<>();
    private static HashMap<String, ObjectOutputStream> conectados = new HashMap<>();
    private static final ReentrantReadWriteLock candado = new ReentrantReadWriteLock();

    public static boolean conectar(String usuario, ObjectOutputStream salida) {
        candado.writeLock().lock();
        try {
            // El mismo usuario solo puede estar conectado una vez
            if (conectados.containsKey(usuario)) {
                return false;
            }
            conectados.put(usuario, salida);
            // Si el usuario se conecta por primera vez le crea el perfil
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
    public static void almacenarPublicacion(Publicacion pub) {
        PerfilUsuario perfil = perfiles.get(pub.getAutor());
        perfil.agregarPublicacion(pub);
        perfiles.put(pub.getAutor(), perfil);
    }
    public static void difundirPublicacion(Publicacion pub) {
        candado.readLock().lock();
        try {
            for (ObjectOutputStream salida : conectados.values()) {
                try {
                    salida.writeObject(pub);
                }
                catch (IOException e) {
                    System.out.println("Error: No se pudo enviar la publicacion");
                }
            }
        } finally {
            candado.readLock().unlock();
        }
    }
}