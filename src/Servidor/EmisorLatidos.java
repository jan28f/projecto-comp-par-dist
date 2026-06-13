package Servidor;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.net.Socket;

public class EmisorLatidos implements Runnable {
    private final Servidor servidor;
    private static final int intervaloEnvio = 3000;
    private final HashMap<String, ObjectOutputStream> conexiones;

    public EmisorLatidos(Servidor servidor) {
        this.servidor = servidor;
        this.conexiones = new HashMap<>();
    }

    public void enviarLatido(String idNodo) {
        ObjectOutputStream salida = conexiones.get(idNodo);
        if (salida == null) {
            InfoNodo info = servidor.obtenerNodo(idNodo);
            try {
                Socket socket = new Socket(info.getIp(), info.getPuerto_servidor());
                salida = new ObjectOutputStream(socket.getOutputStream());
                conexiones.put(idNodo, salida);
            }
            catch (Exception e) {
                return;
            }
        }

        try {
            MensajeNodo latido = new MensajeNodo(servidor.getId(), "LATIDO", servidor.getReloj(), null);
            salida.writeObject(latido);
            salida.flush();
        }
        catch (IOException e) {
            conexiones.remove(idNodo);
            System.out.println("Se perdio la conexion con el nodo " + idNodo);
        }
    }

    public void run() {
        while (true) {
            for (String idNodo : servidor.obtenerNodos().keySet()) {
                enviarLatido(idNodo);
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
