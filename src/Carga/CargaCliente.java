package Carga;

import java.io.*;
import java.net.Socket;
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
    private final AtomicLong exitoOpsNormales;
    private final AtomicLong errorOpsNormales;
    private final List<Long> latenciasNormales;
    private final AtomicLong exitoOpsCaida;
    private final AtomicLong errorOpsCaida;
    private final List<Long> latenciasCaida;
    private final AtomicLong tiempoFalla;
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
                        AtomicLong exitoOpsNormales, AtomicLong errorOpsNormales, List<Long> latenciasNormales,
                        AtomicLong exitoOpsCaida, AtomicLong errorOpsCaida, List<Long> latenciasCaida,
                        AtomicLong tiempoFalla, long inicioGlobal, List<NodoInfo> nodos) {
        this.usuario = usuario;
        this.destinoFijo = destinoFijo;
        this.duracionMs = duracionSegundos * 1000;
        this.exitoOpsNormales = exitoOpsNormales;
        this.errorOpsNormales = errorOpsNormales;
        this.latenciasNormales = latenciasNormales;
        this.exitoOpsCaida = exitoOpsCaida;
        this.errorOpsCaida = errorOpsCaida;
        this.latenciasCaida = latenciasCaida;
        this.tiempoFalla = tiempoFalla;
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
                enviarOperacionAleatoria();
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
                    return;
                } else {
                    cerrarConexion();
                }
            } catch (Exception e) {
                cerrarConexion();
            }
        }
        throw new IOException("No se pudo conectar a ningun nodo");
    }

    private void reconectar() {
        try {
            cerrarConexion();
            conectar();
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
        } catch (Exception e) {
            connected = false;
        }
    }

    private void enviarOperacionAleatoria() {
        long inicio = System.nanoTime();
        boolean exito = false;
        try {
            int op = ThreadLocalRandom.current().nextInt(100);
            if (op < 40) {
                Mensaje msj = new Mensaje(usuario, destinoFijo, false, "Carga " + System.currentTimeMillis());
                out.writeObject(msj);
            } else if (op < 75 && !feed.isEmpty()) {
                long idPub;
                synchronized (feed) {
                    idPub = feed.get(ThreadLocalRandom.current().nextInt(feed.size())).getIdPublicacion();
                }
                Interaccion interaccion = new Interaccion("LIKE", idPub, usuario, "");
                out.writeObject(interaccion);
            } else {
                Publicacion pub = new Publicacion(usuario, "Post " + System.currentTimeMillis(), null, null);
                out.writeObject(pub);
            }
            out.flush();
            out.reset();
            exito = true;
        } catch (Exception e) {
            connected = false;
            exito = false;
        }
        long fin = System.nanoTime();
        long latencia = (fin - inicio) / 1_000_000;
        long tf = tiempoFalla.get();
        boolean trasCaida = (tf > 0 && System.currentTimeMillis() >= tf);
        if (trasCaida) {
            if (exito) {
                exitoOpsCaida.incrementAndGet();
                synchronized (latenciasCaida) { latenciasCaida.add(latencia); }
            } else {
                errorOpsCaida.incrementAndGet();
            }
        } else {
            if (exito) {
                exitoOpsNormales.incrementAndGet();
                synchronized (latenciasNormales) { latenciasNormales.add(latencia); }
            } else {
                errorOpsNormales.incrementAndGet();
            }
        }
    }

    public long getCaidaTimestamp() { return caidaTimestamp; }
    public long getRecuperacionTimestamp() { return recuperacionTimestamp; }
    public boolean haCaido() { return haCaido; }
}