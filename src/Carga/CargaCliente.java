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
    private final String destinoFijo;
    private final long duracionMs;
    private final AtomicLong exitoOps;
    private final AtomicLong errorOps;
    private final List<Long> latencias;
    private final long inicioGlobal;
    private final List<NodoInfo> nodos;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected = false;
    private volatile boolean shouldRun = true;

    private final List<Publicacion> feed = Collections.synchronizedList(new ArrayList<>());
    private volatile long caidaTimestamp = 0;
    private volatile long recuperacionTimestamp = 0;
    private volatile boolean haCaido = false;

    public CargaCliente(String usuario, long duracionSegundos, String destinoFijo,
                        AtomicLong exitoOps, AtomicLong errorOps, List<Long> latencias,
                        long inicioGlobal, List<NodoInfo> nodos) {
        this.usuario = usuario;
        this.destinoFijo = destinoFijo;
        this.duracionMs = duracionSegundos * 1000;
        this.exitoOps = exitoOps;
        this.errorOps = errorOps;
        this.latencias = latencias;
        this.inicioGlobal = inicioGlobal;
        this.nodos = nodos;
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
                enviarMensajeFijo();
                try { Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200)); } catch (InterruptedException e) {}
            }
            cerrarConexion();
        } catch (Exception e) {
            System.err.println("Error en hilo " + usuario + ": " + e.getMessage());
        }
    }

    private void conectar() throws IOException, ClassNotFoundException {
        for (NodoInfo nodo : nodos) {
            try {
                socket = new Socket(nodo.getIp(), nodo.getPuertoCliente());
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(new SolicitudInicio(usuario, System.currentTimeMillis()));
                out.flush();
                RespuestaInicio respuesta = (RespuestaInicio) in.readObject();
                if (respuesta.getEstado()) {
                    connected = true;
                    feed.addAll(respuesta.getUltimasPublicaciones());
                    System.out.println("Cliente " + usuario + " conectado al nodo " + nodo.getPuertoCliente());
                    return;
                } else {
                    cerrarConexion();
                }
            } catch (Exception e) {
                cerrarConexion();
            }
        }
        throw new IOException("No se pudo conectar a ningún nodo");
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
                if (obj instanceof Publicacion) {
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
                        if (!actualizada) feed.add(pub);
                    }
                }
            }
        } catch (SocketException e) {
            connected = false;
        } catch (IOException | ClassNotFoundException e) {
            connected = false;
        } catch (Exception e) {
            connected = false;
        }
    }

    private void enviarMensajeFijo() throws IOException {
        Mensaje msj = new Mensaje(usuario, destinoFijo, false,
                "Mensaje " + System.currentTimeMillis());
        long inicio = System.nanoTime();
        out.writeObject(msj);
        out.flush();
        long fin = System.nanoTime();
        exitoOps.incrementAndGet();
        synchronized (latencias) {
            latencias.add((fin - inicio) / 1_000_000);
        }
    }

    public long getCaidaTimestamp() { return caidaTimestamp; }
    public long getRecuperacionTimestamp() { return recuperacionTimestamp; }
    public boolean haCaido() { return haCaido; }
}