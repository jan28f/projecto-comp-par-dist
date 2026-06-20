package Servidor.ComunicacionNodos;

import java.io.Serializable;

public class MensajeNodo implements Serializable {
    private final String idEmisor;
    private final String tipo;
    private final int reloj;
    private final Object contenido;

    public MensajeNodo(String id, String tipo, int reloj, Object contenido) {
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
    public int getReloj() {
        return reloj;
    }
    public Object getContenido() {
        return contenido;
    }
}
