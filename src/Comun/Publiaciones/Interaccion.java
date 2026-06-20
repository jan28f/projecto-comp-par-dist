package Comun.Publiaciones;

import java.io.Serializable;

public class Interaccion implements Serializable {
    private static final long serialVersionUID = 1L;
    private String tipo;
    private long idPublicacion;
    private String autor;
    private String contenido;

    public Interaccion(String tipo, long idPublicacion, String autor, String contenido) {
        this.tipo = tipo;
        this.idPublicacion = idPublicacion;
        this.autor = autor;
        this.contenido = contenido;
    }

    public String getTipo() {
        return tipo;
    }
    public long getIdPublicacion() {
        return idPublicacion;
    }
    public String getAutor() {
        return autor;
    }
    public String getContenido() {
        return contenido;
    }
}