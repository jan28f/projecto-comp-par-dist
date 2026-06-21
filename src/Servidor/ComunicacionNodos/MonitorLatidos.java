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
            long hora = System.currentTimeMillis();
            boolean huboCambios = false;

            for (String idNodo : servidor.getMembresia().idsNodos()) {
                InfoNodo info = servidor.getMembresia().getNodo(idNodo);
                if (info.getActivo() && hora - info.getUltimoLatido() > TimeOut) {
                    if (servidor.getMembresia().marcarCaido(idNodo)) {
                        huboCambios = true;
                        servidor.getRegistro().registrar(
                                servidor.incrementarReloj(), "DETECTA CAIDA del nodo " + idNodo);
                        String coord = servidor.getBully().getCoordinador();
                        System.out.println("[DEBUG] Nodo caído: " + idNodo + " | Coordinador actual: " + coord);
                        if (coord == null || idNodo.equals(coord)) {
                            System.out.println("[BULLY] Iniciando elección por caída de " + idNodo);
                            servidor.getBully().iniciarEleccion();
                        }
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