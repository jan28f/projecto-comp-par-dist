package Servidor.ComunicacionNodos;

import Servidor.Servidor;
import java.util.concurrent.atomic.AtomicBoolean;

public class EleccionBully {
    private final Servidor servidor;
    private volatile String idCoordinador;
    private final AtomicBoolean eleccionEnCurso = new AtomicBoolean(false);
    private volatile boolean recibiOk = false;
    private static final long TIMEOUT_OK = 3000;

    public EleccionBully(Servidor servidor) {
        this.servidor = servidor;
        this.idCoordinador = null;
        System.out.println("[BULLY] Sin coordinador, elección en 8 segundos...");

        new Thread(() -> {
            try {
                Thread.sleep(8000); // espera a que los otros nodos arranquen
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            iniciarEleccion();
        }).start();
    }
    public String getCoordinador() {
        return idCoordinador;
    }

    public boolean soyCoordinador() {
        return servidor.getId().equals(idCoordinador);
    }

    public void iniciarEleccion() {
        if (!eleccionEnCurso.compareAndSet(false, true)) return;
        recibiOk = false;

        System.out.println("[BULLY] " + servidor.getId() + " inicia elección");
        servidor.getRegistro().registrar(
                servidor.incrementarReloj(), "INICIA ELECCION");

        for (String idNodo : servidor.getMembresia().idsNodos()) {
            if (esMayor(idNodo, servidor.getId())) {
                InfoNodo info = servidor.getMembresia().getNodo(idNodo);
                if (info.getActivo()) {
                    ConexionNodo conexion = servidor.getMembresia().getConexion(idNodo);
                    if (conexion != null) {
                        MensajeNodo eleccion = new MensajeNodo(
                                servidor.getId(), "ELECCION",
                                servidor.incrementarReloj(), null);
                        conexion.enviar(eleccion);
                    }
                }
            }
        }

        new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_OK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!recibiOk) {
                proclamarseCoordinador();
            }
            eleccionEnCurso.set(false);
        }).start();
    }

    public void recibirOk() {
        recibiOk = true;
        System.out.println("[BULLY] Recibí OK, espero al nuevo coordinador");
    }

    public void recibirCoordinador(String idNuevoCoord) {
        this.idCoordinador = idNuevoCoord;
        eleccionEnCurso.set(false);
        System.out.println("[BULLY] Nuevo coordinador: " + idNuevoCoord);
        servidor.getRegistro().registrar(
                servidor.incrementarReloj(), "COORDINADOR establecido: " + idNuevoCoord);
    }

    private void proclamarseCoordinador() {
        this.idCoordinador = servidor.getId();
        System.out.println("[BULLY] " + servidor.getId() + " ES EL NUEVO COORDINADOR");
        servidor.getRegistro().registrar(
                servidor.incrementarReloj(), "SOY COORDINADOR");

        MensajeNodo anuncio = new MensajeNodo(
                servidor.getId(), "COORDINADOR",
                servidor.incrementarReloj(), servidor.getId());
        servidor.getMembresia().difundirEntreNodos(anuncio);
    }

    private boolean esMayor(String idA, String idB) {
        return idA.compareTo(idB) > 0;
    }
}