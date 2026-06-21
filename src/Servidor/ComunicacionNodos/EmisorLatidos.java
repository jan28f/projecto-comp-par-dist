package Servidor.ComunicacionNodos;

import Servidor.Servidor;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.net.Socket;

public class EmisorLatidos implements Runnable {
    private final Servidor servidor;
    private static final int intervaloEnvio = 3000;

    public EmisorLatidos(Servidor servidor) {
        this.servidor = servidor;
    }

    public void run() {
        while (true) {
            MensajeNodo mensaje = new MensajeNodo(servidor.getId(), "LATIDO", servidor.getReloj(), null);
            for (String idNodo : servidor.getMembresia().idsNodos()) {
                ConexionNodo conexion = servidor.getMembresia().getConexion(idNodo);
                if (conexion != null) {
                    conexion.enviar(mensaje);
                }
            }
            try {
                Thread.sleep(intervaloEnvio);
            } catch (InterruptedException e) {
                System.out.println("Error al dormir hilo de envio de latidos");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
