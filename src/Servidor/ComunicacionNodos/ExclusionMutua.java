package Servidor.ComunicacionNodos;

import Servidor.Servidor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ExclusionMutua {
    private final Servidor servidor;
    private final Object candado = new Object();
    private boolean solicitando = false;
    private long timestampSolicitud = -1;
    private CountDownLatch latchRespuestas;
    private final List<String> solicitudesDiferidas = new ArrayList<>();

    public ExclusionMutua(Servidor servidor) {
        this.servidor = servidor;
    }

    public void solicitarAcceso() {
        List<String> nodosActivos;
        CountDownLatch latchLocal;

        synchronized (candado) {
            solicitando = true;
            timestampSolicitud = servidor.incrementarReloj();
            nodosActivos = nodosActivos();
            latchLocal = new CountDownLatch(nodosActivos.size());
            latchRespuestas = latchLocal;
        }

        for (String idNodo : nodosActivos) {
            ConexionNodo conexion = servidor.getMembresia().getConexion(idNodo);
            if (conexion != null) {
                MensajeNodo solicitud = new MensajeNodo(
                        servidor.getId(), "RA_REQUEST", timestampSolicitud, servidor.getId());
                conexion.enviar(solicitud);
            } else {
                latchLocal.countDown();
            }
        }

        servidor.getRegistro().registrar(timestampSolicitud, "RA_SOLICITA acceso al recurso compartido (likes)");

        try {
            latchLocal.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        servidor.getRegistro().registrar(servidor.getReloj(), "RA_OBTIENE acceso al recurso compartido (likes)");
    }

    public void liberarAcceso() {
        List<String> diferidas;

        synchronized (candado) {
            solicitando = false;
            diferidas = new ArrayList<>(solicitudesDiferidas);
            solicitudesDiferidas.clear();
        }

        servidor.getRegistro().registrar(servidor.incrementarReloj(), "RA_LIBERA acceso al recurso compartido (likes)");

        for (String idDestino : diferidas) {
            ConexionNodo conexion = servidor.getMembresia().getConexion(idDestino);
            if (conexion != null) {
                MensajeNodo respuesta = new MensajeNodo(servidor.getId(), "RA_REPLY", servidor.incrementarReloj(), null);
                conexion.enviar(respuesta);
            }
        }
    }

    public void recibirSolicitud(String idSolicitante, long timestampSolicitante) {
        boolean otorgarInmediato;

        synchronized (candado) {
            otorgarInmediato = !solicitando
                    || timestampSolicitante < timestampSolicitud
                    || (timestampSolicitante == timestampSolicitud && idSolicitante.compareTo(servidor.getId()) < 0);
            if (!otorgarInmediato) {
                solicitudesDiferidas.add(idSolicitante);
            }
        }

        servidor.getRegistro().registrar(servidor.getReloj(), "RA_RECIBE solicitud de " + idSolicitante);

        if (otorgarInmediato) {
            ConexionNodo conexion = servidor.getMembresia().getConexion(idSolicitante);
            if (conexion != null) {
                MensajeNodo respuesta = new MensajeNodo(servidor.getId(), "RA_REPLY", servidor.incrementarReloj(), null);
                conexion.enviar(respuesta);
            }
        }
    }

    public void recibirRespuesta() {
        CountDownLatch latchActual;

        synchronized (candado) {
            latchActual = latchRespuestas;
        }

        if (latchActual != null) {
            latchActual.countDown();
        }
    }

    private List<String> nodosActivos() {
        List<String> activos = new ArrayList<>();
        for (String idNodo : servidor.getMembresia().idsNodos()) {
            InfoNodo info = servidor.getMembresia().getNodo(idNodo);
            if (info != null && info.getActivo()) {
                activos.add(idNodo);
            }
        }
        return activos;
    }
}
