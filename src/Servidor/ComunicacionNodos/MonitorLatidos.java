package Servidor.ComunicacionNodos;

import Servidor.Servidor;

public class MonitorLatidos implements Runnable {
    private static final int intervaloEspera = 5000;
    private static final long TimeOut = 10000;
    private final Servidor servidor;

    public MonitorLatidos(Servidor servidor) {
        this.servidor = servidor;
    }

    public void run() {
        while (true) {
            long ahora = System.currentTimeMillis();
            boolean huboCambios = false;

            for (String idNodo : servidor.getMembresia().idsNodos()) {
                InfoNodo info = servidor.getMembresia().getNodo(idNodo);
                if (info.getActivo() && ahora - info.getUltimoLatido() > TimeOut) {
                    if (servidor.getMembresia().marcarCaido(idNodo)) {
                        huboCambios = true;
                    }
                }
            }
            if (huboCambios) {
                servidor.getMembresia().mostrarEstado();
            }

            try {
                Thread.sleep(intervaloEspera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}