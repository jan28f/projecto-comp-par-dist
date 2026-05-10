package Comun.DM;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Mensaje implements Serializable {
    private String remitente;
    private String destinatario;
    private boolean esGrupo;
    private String contenido;
    private Instant fechaMensaje;

    public Mensaje(String remitente, String destinatario, boolean esGrupo, String contenido) {
        this.remitente = remitente;
        this.destinatario = destinatario;
        this.esGrupo = esGrupo;
        this.contenido = contenido;
    }

    public String getRemitente() {
        return remitente;
    }
    public String getDestinatario() {
        return destinatario;
    }
    public boolean getEsGrupo() {
        return esGrupo;
    }
    public String getContenido() {
        return contenido;
    }
    public void setFechaPublicacion(Instant fechaPublicacion) {
        this.fechaMensaje = fechaPublicacion;
    }
    public String getFechaFormateada() {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        if (fechaMensaje == null) {
            return "Pendiente";
        }
        return LocalDateTime.ofInstant(fechaMensaje, ZoneId.systemDefault()).format(formato);
    }
}
