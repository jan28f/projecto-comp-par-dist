package Servidor.ComunicacionNodos;

import java.io.Serializable;

public class MensajeNodo implements Serializable {
    private final String idEmisor;
    private final String tipo;
    private final long reloj;
    private final Object contenido;

    public MensajeNodo(String id, String tipo, long reloj, Object contenido) {
        this.idEmisor = id;
        this.tipo = tipo;
        this.reloj = reloj;
        this.contenido = contenido;
    }

    public String getIdEmisor() {
        return idEmisor;
    }
    public String getTipo() {
        return tipo;
    }
    public long getReloj() {
        return reloj;
    }
    public Object getContenido() {
        return contenido;
    }
}
