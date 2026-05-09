package Comun;

import java.io.Serializable;
import java.time.Instant;

public class Publicacion implements Serializable {
    private String autor;
    private String descripcion;
    private Instant fechaPublicacion;
    private byte[] archivo;
    private String nombreArchivo;

    public Publicacion(String autor, String descripcion, byte[] archivo, String nombreArchivo) {
        this.autor = autor;
        this.descripcion = descripcion;
        this.archivo = archivo;
        this.nombreArchivo = nombreArchivo;
    }

    public void setFechaPublicacion(Instant fechaPublicacion) {
        this.fechaPublicacion = fechaPublicacion;
    }

    public String getAutor() {
        return autor;
    }
    public String getDescripcion() {
        return descripcion;
    }
    public byte[] getArchivo() {
        return archivo;
    }
    public String getNombreArchivo() {
        return nombreArchivo;
    }
    public Instant getFechaPublicacion() {
        return fechaPublicacion;
    }
}
