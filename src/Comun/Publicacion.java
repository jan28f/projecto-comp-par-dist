package Comun;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Publicacion implements Serializable {
    private long idPublicacion;
    private String autor;
    private String descripcion;
    private Instant fechaPublicacion;
    private byte[] archivo;
    private String nombreArchivo;
    private ArrayList<String> meGusta;
    private ArrayList<String> comentarios;

    public Publicacion(String autor, String descripcion, byte[] archivo, String nombreArchivo) {
        this.idPublicacion = System.currentTimeMillis();
        this.autor = autor;
        this.descripcion = descripcion;
        this.archivo = archivo;
        this.nombreArchivo = nombreArchivo;
        this.meGusta = new ArrayList<>();
        this.comentarios = new ArrayList<>();
    }

    public long getIdPublicacion() {
        return idPublicacion;
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
    public ArrayList<String> getMeGusta() {
        return meGusta;
    }
    public ArrayList<String> getComentarios() {
        return comentarios;
    }

    public void agregarMeGusta(String usuario) {
        if (!meGusta.contains(usuario)) {
            meGusta.add(usuario);
        }
    }

    public void agregarComentario(String comentario) {
        comentarios.add(comentario);
    }

    public String getFechaFormateada() {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        if (fechaPublicacion == null) {
            return "Pendiente";
        }
        return LocalDateTime.ofInstant(fechaPublicacion, ZoneId.systemDefault()).format(formato);
    }

    public void imprimirConsola() {
        System.out.println("\n==============================================");
        System.out.println("ID PUB : " + idPublicacion);
        System.out.println("AUTOR  : " + autor);
        System.out.println("FECHA  : " + getFechaFormateada());
        System.out.println("ARCHIVO: " + nombreArchivo);
        System.out.println("TEXTO  : " + descripcion);
        System.out.println("----------------------------------------------");
        System.out.println("LIKES  : " + meGusta.size());
        System.out.println("COMENTARIOS (" + comentarios.size() + "):");
        for (String iteradorComentario : comentarios) {
            System.out.println(" -> " + iteradorComentario);
        }
        System.out.println("==============================================\n");
    }
}