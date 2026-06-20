package Carga;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;

import Comun.DM.*;
import Comun.Publiaciones.*;
import Comun.Sesion.*;

public class CargaCliente implements Runnable {
    private final String usuario;
    private final long duracionMs;
    private final List<String> todosUsuarios;
    private final AtomicLong exitoOps;
    private final AtomicLong errorOps;
    private final List<Long> latencias;
    private final long inicioGlobal;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected = false;
    private volatile boolean shouldRun = true;

    // Estado local
    private final List<Publicacion> feed = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> grupos = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, InvitacionGrupo> invitaciones = Collections.synchronizedMap(new HashMap<>());

    // Medición de caída y recuperación
    private volatile long caidaTimestamp = 0;
    private volatile long recuperacionTimestamp = 0;
    private volatile boolean haCaido = false;

    public CargaCliente(String usuario, long duracionSegundos, List<String> todosUsuarios,
                        AtomicLong exitoOps, AtomicLong errorOps, List<Long> latencias, long inicioGlobal) {
        this.usuario = usuario;
        this.duracionMs = duracionSegundos * 1000;
        this.todosUsuarios = todosUsuarios;
        this.exitoOps = exitoOps;
        this.errorOps = errorOps;
        this.latencias = latencias;
        this.inicioGlobal = inicioGlobal;
    }

    @Override
    public void run() {
        try {
            conectar();
            Thread escucha = new Thread(this::escuchar);
            escucha.setDaemon(true);
            escucha.start();

            long fin = inicioGlobal + duracionMs;
            while (System.currentTimeMillis() < fin && shouldRun) {
                if (!connected) {
                    if (caidaTimestamp == 0) {
                        caidaTimestamp = System.currentTimeMillis();
                        haCaido = true;
                    }
                    reconectar();
                    if (connected) {
                        recuperacionTimestamp = System.currentTimeMillis();
                        escucha = new Thread(this::escuchar);
                        escucha.setDaemon(true);
                        escucha.start();
                    } else {
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}
                        continue;
                    }
                }
                realizarAccion();
                try { Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500)); } catch (InterruptedException e) {}
            }
            cerrarConexion();
        } catch (Exception e) {
            System.err.println("Error en hilo " + usuario + ": " + e.getMessage());
        }
    }

    private void conectar() throws IOException, ClassNotFoundException {
        socket = new Socket("127.0.0.1", 1003);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        out.writeObject(new SolicitudInicio(usuario, System.currentTimeMillis()));
        out.flush();
        RespuestaInicio respuesta = (RespuestaInicio) in.readObject();
        if (respuesta.getEstado()) {
            connected = true;
            feed.addAll(respuesta.getUltimasPublicaciones());
            System.out.println("Cliente " + usuario + " conectado.");
        } else {
            throw new IOException("Conexión rechazada: " + respuesta.getMensaje());
        }
    }

    private void reconectar() {
        try {
            cerrarConexion();
            conectar();
            System.out.println("Cliente " + usuario + " reconectado.");
        } catch (Exception e) {
            connected = false;
        }
    }

    private void cerrarConexion() {
        try { if (in != null) in.close(); } catch (IOException e) {}
        try { if (out != null) out.close(); } catch (IOException e) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) {}
        connected = false;
    }

    private void escuchar() {
        try {
            while (connected && !Thread.currentThread().isInterrupted()) {
                Object obj = in.readObject();
                if (obj instanceof Mensaje) {
                    // no hacer nada, solo mantener estado
                } else if (obj instanceof InvitacionGrupo) {
                    InvitacionGrupo inv = (InvitacionGrupo) obj;
                    invitaciones.put(inv.getNombreGrupo(), inv);
                } else if (obj instanceof Publicacion) {
                    Publicacion pub = (Publicacion) obj;
                    synchronized (feed) {
                        boolean actualizada = false;
                        for (int i = 0; i < feed.size(); i++) {
                            if (feed.get(i).getIdPublicacion() == pub.getIdPublicacion()) {
                                feed.set(i, pub);
                                actualizada = true;
                                break;
                            }
                        }
                        if (!actualizada) {
                            feed.add(pub);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            connected = false;
        } catch (IOException | ClassNotFoundException e) {
            connected = false;
        } catch (Exception e) {
            // Captura NullPointerException y cualquier otra
            connected = false;
            e.printStackTrace();
        }
    }

    private void realizarAccion() {
        if (!connected) return;
        // 7 tipos de acciones: 0-6
        int accion = ThreadLocalRandom.current().nextInt(7);
        try {
            switch (accion) {
                case 0 -> publicar();
                case 1 -> like();
                case 2 -> comentar();
                case 3 -> mensajeDirecto();
                case 4 -> crearGrupo();
                case 5 -> mensajeGrupo();
                case 6 -> aceptarInvitacion();
            }
        } catch (Exception e) {
            errorOps.incrementAndGet();
            if (e instanceof IOException) connected = false;
        }
    }

    // ---- Operaciones de publicaciones ----
    private void publicar() throws IOException {
        byte[] datos = new byte[1024];
        String texto = "Publicación auto " + System.currentTimeMillis();
        Publicacion pub = new Publicacion(usuario, texto, datos, "dummy.jpg");
        long inicio = System.nanoTime();
        out.writeObject(pub);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    private void like() throws IOException {
        if (feed.isEmpty()) return;
        Publicacion pub = feed.get(ThreadLocalRandom.current().nextInt(feed.size()));
        Interaccion inter = new Interaccion("LIKE", pub.getIdPublicacion(), usuario, "");
        long inicio = System.nanoTime();
        out.writeObject(inter);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    private void comentar() throws IOException {
        if (feed.isEmpty()) return;
        Publicacion pub = feed.get(ThreadLocalRandom.current().nextInt(feed.size()));
        String coment = "Comentario auto " + System.currentTimeMillis();
        Interaccion inter = new Interaccion("COMENTARIO", pub.getIdPublicacion(), usuario, coment);
        long inicio = System.nanoTime();
        out.writeObject(inter);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    // ---- Operaciones de mensajería ----
    private void mensajeDirecto() throws IOException {
        String destino;
        do {
            destino = todosUsuarios.get(ThreadLocalRandom.current().nextInt(todosUsuarios.size()));
        } while (destino.equals(usuario));
        Mensaje msj = new Mensaje(usuario, destino, false, "DM auto " + System.currentTimeMillis());
        long inicio = System.nanoTime();
        out.writeObject(msj);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    private void crearGrupo() throws IOException {
        // Elegir 2 o 3 usuarios aleatorios para invitar
        int numInvitados = ThreadLocalRandom.current().nextInt(2, 4);
        ArrayList<String> invitados = new ArrayList<>();
        List<String> candidatos = new ArrayList<>(todosUsuarios);
        candidatos.remove(usuario);
        Collections.shuffle(candidatos);
        for (int i = 0; i < Math.min(numInvitados, candidatos.size()); i++) {
            invitados.add(candidatos.get(i));
        }
        if (invitados.isEmpty()) return;
        String nombreGrupo = "grupo_" + usuario + "_" + System.currentTimeMillis();
        SolicitudGrupo solicitud = new SolicitudGrupo(nombreGrupo, invitados);
        long inicio = System.nanoTime();
        out.writeObject(solicitud);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
        // El creador se agrega automáticamente al grupo, lo añadimos localmente
        grupos.add(nombreGrupo);
    }

    private void mensajeGrupo() throws IOException {
        if (grupos.isEmpty()) return;
        String grupo = grupos.iterator().next(); // elegir el primero
        Mensaje msj = new Mensaje(usuario, grupo, true, "Mensaje a grupo " + System.currentTimeMillis());
        long inicio = System.nanoTime();
        out.writeObject(msj);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    private void aceptarInvitacion() throws IOException {
        if (invitaciones.isEmpty()) return;
        String nombreGrupo = invitaciones.keySet().iterator().next();
        AccionGrupo accion = new AccionGrupo("aceptar", nombreGrupo);
        long inicio = System.nanoTime();
        out.writeObject(accion);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
        grupos.add(nombreGrupo);
        invitaciones.remove(nombreGrupo);
    }

    // ---- Getters para estadísticas de falla ----
    public long getCaidaTimestamp() { return caidaTimestamp; }
    public long getRecuperacionTimestamp() { return recuperacionTimestamp; }
    public boolean haCaido() { return haCaido; }
}
