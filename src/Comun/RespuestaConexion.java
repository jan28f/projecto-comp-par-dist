package Comun;

import java.io.Serializable;

public class RespuestaConexion implements Serializable {
    private boolean estado;
    private String mensaje;

    public RespuestaConexion(boolean estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public boolean getEstado() {
        return estado;
    }

    public String getMensaje() {
        return mensaje;
    }
}
