package Comun;

import java.io.Serializable;

public class Mensaje implements Serializable {
    private String remitente;
    private String destinatario;
    private String contenido;
    private long tiempo;

    public Mensaje(String remitente, String destinatario, String contenido) {
        this.remitente = remitente;
        this.destinatario = destinatario;
        this.contenido = contenido;
        this.tiempo = System.currentTimeMillis();
    }

    public String getRemitente() {
        return remitente;
    }
    public String getDestinatario() {
        return destinatario;
    }
    public String getContenido() {
        return contenido;
    }
    public long getTiempo() {
        return tiempo;
    }
}
