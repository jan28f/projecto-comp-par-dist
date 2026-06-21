package Servidor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import Comun.DM.InvitacionGrupo;
import Comun.Sesion.PerfilUsuario;
import Comun.Publiaciones.Publicacion;
import Comun.Publiaciones.Interaccion;
import Servidor.ComunicacionClientes.ClienteConectado;
import java.io.ObjectOutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GestionUsuarios {
    private static final HashMap<String, PerfilUsuario> perfiles = new HashMap<>();
    private static final HashMap<String, ClienteConectado> conectados = new HashMap<>();
    private static final HashMap<String, ArrayList<String>> grupos = new HashMap<>();
    private static final ArrayDeque<Publicacion> historialPublicaciones = new ArrayDeque<>();
    private static final int tamanoHistorialPublicaciones = 10;
    private static final ReentrantReadWriteLock candado = new ReentrantReadWriteLock();
    private static final int tamano_cola = 25;

    public static boolean conectar(String usuario, ObjectOutputStream salida) {
        candado.writeLock().lock();
        try {
            if (conectados.containsKey(usuario)) {
                return false;
            }

            ClienteConectado nuevoCliente = new ClienteConectado(usuario, salida, tamano_cola);
            conectados.put(usuario, nuevoCliente);
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
            ClienteConectado cliente = conectados.remove(usuario);
            if (cliente != null) {
                cliente.cerrar();
            }
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static ClienteConectado getCliente(String usuario) {
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

    public static PerfilUsuario obtenerPerfil(String usuario) {
        return perfiles.get(usuario);
    }

    private static String obtenerNombreUnicoGrupo(PerfilUsuario perfil, String nombreGrupoBase){
        String nombreNuevoGrupo = nombreGrupoBase;
        int cont = 1;
        while (perfil.obtenerIDGrupo(nombreNuevoGrupo) != null) {
            nombreNuevoGrupo = nombreGrupoBase + "_" + cont;
            cont++;
        }
        return nombreNuevoGrupo;
    }

    public static boolean crearGrupo(String nombreGrupo, String creador, ArrayList<String> invitados){
        candado.writeLock().lock();
        try {
            PerfilUsuario perfilCreador = perfiles.get(creador);
            String nombreFinalGrupo = obtenerNombreUnicoGrupo(perfilCreador, nombreGrupo);
            String idGrupo = java.util.UUID.randomUUID().toString();
            ArrayList<String> integrantesActuales = new ArrayList<>();
            integrantesActuales.add(creador);
            grupos.put(idGrupo, integrantesActuales);
            perfilCreador.agregarGrupo(nombreFinalGrupo, idGrupo);

            for (String invitado: invitados) {
                if (!perfiles.containsKey(invitado)) {
                    continue;
                }

                PerfilUsuario perfilInvitado = perfiles.get(invitado);
                perfilInvitado.agregarInvitacionGrupo(nombreGrupo, idGrupo, creador);
                if (conectados.containsKey(invitado)) {
                    ClienteConectado clienteDestino = conectados.get(invitado);
                    clienteDestino.enviar(new InvitacionGrupo(nombreGrupo, idGrupo, invitado));
                }
            }
            return true;
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static void responderSolicitudGrupo(String usuario, String nombreGrupo, boolean acepta) {
        candado.writeLock().lock();
        try {
            PerfilUsuario perfil = perfiles.get(usuario);
            InvitacionGrupo infoGrupo = perfil.obtenerInvitacionGrupo(nombreGrupo);
            if (infoGrupo != null) {
                if (acepta) {
                    String nombreFinalGrupo = obtenerNombreUnicoGrupo(perfil, infoGrupo.getNombreGrupo());
                    perfil.agregarGrupo(nombreFinalGrupo, infoGrupo.getIdGrupo());
                    ArrayList<String> integrantesActuales = grupos.get(infoGrupo.getIdGrupo());
                    integrantesActuales.add(usuario);
                    grupos.put(infoGrupo.getIdGrupo(), integrantesActuales);
                }

                perfil.eliminarInvitacionGrupo(nombreGrupo);
            }
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static boolean salirDeGrupo(String usuario, String nombreGrupo) {
        candado.writeLock().lock();
        try {
            PerfilUsuario perfil = perfiles.get(usuario);
            String idGrupo = perfil.obtenerIDGrupo(nombreGrupo);

            if (idGrupo != null) {
                perfil.eliminarGrupo(nombreGrupo);

                ArrayList<String> integrantes = grupos.get(idGrupo);
                if (integrantes != null) {
                    integrantes.remove(usuario);

                    if (integrantes.isEmpty()) {
                        grupos.remove(idGrupo);
                    }
                }
                return true;
            }
            return false;
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static ArrayList<String> obtenerIntegrantesGrupo(String idGrupo) {
        return grupos.get(idGrupo);
    }

    public static void difundirPublicacion(Publicacion pub) {
        candado.writeLock().lock();
        try {
            if (historialPublicaciones.size() > tamanoHistorialPublicaciones) {
                historialPublicaciones.pollFirst();
            }
            historialPublicaciones.addLast(pub);

            PerfilUsuario perfil = perfiles.get(pub.getAutor());
            if (perfil != null) {
                perfil.agregarPublicacion(pub);
            }

            for (ClienteConectado cliente : conectados.values()) {
                cliente.enviar(pub);
            }
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static ArrayList<Publicacion> obtenerUltimasPublicaciones() {
        candado.readLock().lock();
        try {
            ArrayList<Publicacion> lista = new ArrayList<>(historialPublicaciones);
            lista.sort(Comparator.comparing(Publicacion::getIdPublicacion).thenComparing(Publicacion::getIdNodoOrigen));
            return lista;
        } finally {
            candado.readLock().unlock();
        }
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
                for (ClienteConectado cliente : conectados.values()) {
                    cliente.enviar(publicacionObjetivo);
                }
            }
        } finally {
            candado.writeLock().unlock();
        }
    }

    public static Object[] exportarEstado() {
        candado.readLock().lock();
        try {
            return new Object[]{
                    new ArrayDeque<>(historialPublicaciones),
                    new HashMap<>(grupos),
                    new HashMap<>(perfiles)
            };
        } finally {
            candado.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public static void importarEstado(Object[] estadoGlobal) {
        candado.writeLock().lock();
        try {
            ArrayDeque<Publicacion> historialRecibido = (ArrayDeque<Publicacion>) estadoGlobal[0];
            HashMap<String, ArrayList<String>> gruposRecibidos = (HashMap<String, ArrayList<String>>) estadoGlobal[1];
            HashMap<String, PerfilUsuario> perfilesRecibidos = (HashMap<String, PerfilUsuario>) estadoGlobal[2];

            historialPublicaciones.clear();
            historialPublicaciones.addAll(historialRecibido);

            grupos.clear();
            grupos.putAll(gruposRecibidos);

            perfiles.clear();
            perfiles.putAll(perfilesRecibidos);

            System.out.println("[SINCRONIZACIÓN] Memoria local actualizada. Publicaciones: " + historialPublicaciones.size());
        } catch (Exception e) {
            System.out.println("[ERROR] Fallo al importar el estado global: " + e.getMessage());
        } finally {
            candado.writeLock().unlock();
        }
    }
}