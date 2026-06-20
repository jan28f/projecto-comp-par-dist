package Comun.Sesion;

import java.io.Serializable;
import java.util.ArrayList;
import Comun.Publiaciones.Publicacion;

public class RespuestaInicio implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean estado;
    private final String mensaje;
    private ArrayList<Publicacion> ultimasPublicaciones;

    public RespuestaInicio(boolean estado, String mensaje, ArrayList<Publicacion> ultimasPublicaciones) {
        this.estado = estado;
        this.mensaje = mensaje;
        this.ultimasPublicaciones = ultimasPublicaciones;
    }

    public boolean getEstado() {
        return estado;
    }
    public String getMensaje() {
        return mensaje;
    }
    public ArrayList<Publicacion> getUltimasPublicaciones() {
        return ultimasPublicaciones;
    }
}
