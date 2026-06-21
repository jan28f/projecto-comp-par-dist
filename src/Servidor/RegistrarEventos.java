package Servidor;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalTime;

public class RegistrarEventos {
    private PrintWriter escritor = null;
    private final String idNodo;

    public RegistrarEventos(String idNodo) {
        this.idNodo = idNodo;
        try {
            this.escritor = new PrintWriter(new FileWriter("log_" + idNodo + ".txt", true), true);
        } catch (Exception e) {
            System.out.println("No se pudo abrir el log del nodo " + idNodo);
        }
    }

    public synchronized void registrar(long lamport, String descripcion) {
        if (escritor == null) {
            return;
        }
        escritor.println("[Hora=" + LocalTime.now() + "][ID=" + idNodo + "][lamport=" + lamport + "] " + descripcion);
    }
}
